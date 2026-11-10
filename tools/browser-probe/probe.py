#!/usr/bin/env python3
"""브라우저 점검기. 대상 URL을 실제 Chromium으로 열고, HTTP 응답 헤더만 봐서는
보이지 않는 것들을 기록해 findings json 으로 내보낸다.

TestWeave 본체(Java)는 브라우저를 띄우지 않고 이 파일을 읽어 점검 결과로 흡수한다.
그렇게 나눈 이유는 두 가지다. 매일 도는 스캔에 브라우저 런타임을 얹고 싶지 않았고,
회귀 판정과 수용 등록부와 리포트는 이미 본체에 있어서 다시 만들 이유가 없었다.

왜 브라우저 층이 따로 필요한가
    2026-09-17에 개인 블로그에서 겪은 일이다. 시리즈 페이지 18개와 목록 페이지들이
    전부 canonical을 홈으로 찍고 있었고, 그 결과 글 40편으로 가는 발견 경로가 통째로
    끊겨 있었다. 응답 헤더는 전부 정상이었고 소스도 한 줄짜리 컴포넌트 호출이라
    멀쩡해 보였다. 결함은 렌더된 HTML에만 있었다. 헤더만 보는 스캐너로는 영원히
    못 잡는 층이 따로 있다.

사용:
    python probe.py --targets targets.local.txt --out reports/browser-findings.json
    python probe.py --targets targets.local.txt --allow-private   (자기 환경에서만)
"""
from __future__ import annotations

import argparse
import ipaddress
import json
import os
import socket
import sys
from datetime import datetime, timezone
from urllib.parse import urlparse, urljoin

TIMEOUT_MS = 20000
SETTLE_MS = 1500  # 로드 후 뒤늦게 터지는 스크립트 오류를 잡을 여유


# ---------------------------------------------------------------- 대상 검증

def block_reason(addr: str, allow_private: bool):
    """점검 대상으로 쓸 수 없는 주소면 사유를, 쓸 수 있으면 None을 돌려준다.

    스캐너에 URL 목록을 먹이는 구조라, 목록에 들어온 주소가 곧 이 프로세스가 여는
    주소다. 헤드리스 브라우저는 시키면 클라우드 메타데이터도 그대로 가져온다.
    TestWeave 본체의 SsrfGuard와 같은 선을 여기서도 지킨다.

    allow_private를 켜면 사설 대역과 루프백이 열린다. 그러나 링크로컬(169.254/16,
    fe80::/10)은 켜도 막는다. 거기에 인스턴스 자격증명이 있고 내부 자산 점검과는
    아무 상관이 없다. 완화 스위치를 만들 때는 열어도 끝까지 닫혀 있어야 하는 것을
    먼저 정해야 한다.
    """
    ip = ipaddress.ip_address(addr)
    if ip.is_unspecified:
        return "와일드카드 주소"
    if ip.is_link_local:
        return "링크로컬(클라우드 메타데이터)"
    if ip.is_multicast:
        return "멀티캐스트"
    if allow_private:
        return None
    if ip.is_loopback:
        return "루프백"
    if ip.is_private:
        return "사설 대역"
    return None


def resolve_and_check(raw_url: str, allow_private: bool) -> str:
    """URL을 검증하고 정규화해서 돌려준다. 막아야 하면 예외."""
    u = urlparse(raw_url)
    if u.scheme not in ("http", "https"):
        raise ValueError("http(s)만 점검합니다: %s" % (u.scheme or "(없음)"))
    if not u.hostname:
        raise ValueError("호스트가 없습니다: %s" % raw_url)

    host = u.hostname
    try:
        ipaddress.ip_address(host)
        addrs = [host]
    except ValueError:
        infos = socket.getaddrinfo(host, None)
        addrs = sorted({i[4][0] for i in infos})

    if not addrs:
        raise ValueError("이름을 풀 수 없습니다: %s" % host)

    for addr in addrs:
        reason = block_reason(addr, allow_private)
        if reason:
            raise ValueError("차단: %s -> %s (%s)" % (host, addr, reason))
    return raw_url


# ---------------------------------------------------------------- 점검

def ok(rule, detail=""):
    return {"rule": rule, "status": "PASS", "detail": detail}


def bad(rule, detail):
    return {"rule": rule, "status": "FAIL", "detail": detail}


def trim(s, n=300):
    """detail은 리포트 표의 한 칸에 들어간다. 길면 표가 안 읽힌다."""
    one = " ".join(str(s).split())
    return one if len(one) <= n else one[: n - 3] + "..."


# 문서에서 한 번에 긁어온다. 항목마다 왕복하면 느리고, 그 사이 DOM이 바뀔 수 있다.
_DOC_JS = """() => {
  const pick = sel => document.querySelector(sel);
  const canonical = pick('link[rel="canonical"]');
  const robots = pick('meta[name="robots"]');
  const cspMeta = pick('meta[http-equiv="Content-Security-Policy" i]');
  return {
    canonical: canonical ? canonical.getAttribute('href') : null,
    robots: robots ? robots.getAttribute('content') : null,
    cspMeta: cspMeta ? cspMeta.getAttribute('content') : null,
    title: (document.title || '').trim(),
    href: location.href,
    descriptionCount: document.querySelectorAll('meta[name="description"]').length,
  };
}"""


def probe_one(browser, raw_url: str, allow_private: bool):
    url = resolve_and_check(raw_url, allow_private)
    parsed = urlparse(url)

    context = browser.new_context(ignore_https_errors=False)
    page = context.new_page()

    console_errors, page_errors = [], []
    failed_requests, insecure_requests = [], []
    external_scripts = set()

    def on_console(msg):
        if msg.type == "error":
            console_errors.append(msg.text)

    def on_request(req):
        target = urlparse(req.url)
        # https 문서가 http 하위 자원을 부르면 그 자원은 평문으로 오간다.
        # 브라우저가 막기도 하지만 통과시키는 종류가 있어 직접 본다.
        if parsed.scheme == "https" and target.scheme == "http":
            insecure_requests.append(req.url)
        if req.resource_type == "script" and target.netloc and target.netloc != parsed.netloc:
            external_scripts.add("%s://%s" % (target.scheme, target.netloc))

    def on_request_failed(req):
        why = req.failure or ""
        failed_requests.append("%s %s (%s)" % (req.method, req.url, why))

    page.on("console", on_console)
    page.on("pageerror", lambda e: page_errors.append(str(e)))
    page.on("request", on_request)
    page.on("requestfailed", on_request_failed)

    outcomes = []
    try:
        response = page.goto(url, wait_until="domcontentloaded", timeout=TIMEOUT_MS)
        page.wait_for_timeout(SETTLE_MS)
    except Exception as e:
        context.close()
        # 여기서 빈 목록을 돌려주면 규칙이 통째로 사라진 모양이 되어 본체가 소멸(MISSING)로
        # 잡는다. 그 판정 자체는 맞지만, 왜 못 쟀는지를 같이 남기는 편이 조치가 빠르다.
        return [bad("페이지 로드", trim(e))]

    status = response.status if response else 0
    outcomes.append(
        ok("페이지 로드", "HTTP %d" % status)
        if 200 <= status < 400
        else bad("페이지 로드", "HTTP %d" % status)
    )

    doc = page.evaluate(_DOC_JS)

    # canonical 자기참조. 이 규칙이 있었다면 블로그 사고를 하루 만에 잡았다.
    if not doc["canonical"]:
        outcomes.append(bad("canonical 자기참조", "canonical 태그 없음"))
    else:
        here = urlparse(doc["href"])
        can = urlparse(urljoin(doc["href"], doc["canonical"]))
        same = (
            (can.scheme, can.netloc) == (here.scheme, here.netloc)
            and can.path.rstrip("/") == here.path.rstrip("/")
        )
        outcomes.append(
            ok("canonical 자기참조", can.geturl())
            if same
            else bad("canonical 자기참조", "%s 를 가리킴 (자기 주소가 아님)" % can.geturl())
        )

    robots = doc["robots"] or ""
    outcomes.append(
        bad("색인 차단 태그 없음", "meta robots: %s" % robots)
        if "noindex" in robots.lower()
        else ok("색인 차단 태그 없음")
    )

    outcomes.append(
        ok("제목 존재", trim(doc["title"], 120))
        if doc["title"]
        else bad("제목 존재", "title이 비어 있음")
    )

    # 같은 이름의 메타가 둘이면 어느 쪽이 쓰일지 읽는 쪽마다 달라진다.
    # 블로그에서 실제로 2개였고, 둘째는 50편이 전부 같은 사이트 설명이었다.
    n_desc = doc["descriptionCount"]
    outcomes.append(
        ok("description 중복 없음")
        if n_desc <= 1
        else bad("description 중복 없음", "%d개 존재" % n_desc)
    )

    # 헤더 스캔으로는 안 보이는 값이다. 헤더에 CSP가 없어도 여기 있으면 브라우저는 적용한다.
    outcomes.append(
        ok("meta CSP", trim(doc["cspMeta"], 200))
        if doc["cspMeta"]
        else ok("meta CSP", "없음 (헤더 CSP는 HEADER 모듈이 본다)")
    )

    js_errors = page_errors + console_errors
    outcomes.append(
        ok("스크립트 오류 없음")
        if not js_errors
        else bad("스크립트 오류 없음", "%d건: %s" % (len(js_errors), trim(js_errors[0], 200)))
    )

    outcomes.append(
        ok("하위 자원 로드 실패 없음")
        if not failed_requests
        else bad(
            "하위 자원 로드 실패 없음",
            "%d건: %s" % (len(failed_requests), trim(failed_requests[0], 200)),
        )
    )

    outcomes.append(
        ok("혼합 콘텐츠 없음")
        if not insecure_requests
        else bad(
            "혼합 콘텐츠 없음",
            "%d건: %s" % (len(insecure_requests), trim(insecure_requests[0])),
        )
    )

    # 외부 스크립트는 그 자체로 결함이 아니다. 다만 그 출처가 침해되면 이 페이지도 같이
    # 침해된다. 목록을 규칙 값으로 고정해 두면 새 출처가 붙는 날 회귀로 잡힌다.
    origins = sorted(external_scripts)
    outcomes.append(ok("외부 스크립트 출처", ", ".join(origins) if origins else "없음"))

    context.close()
    return outcomes


# ---------------------------------------------------------------- 실행

EXIT_OK = 0
EXIT_USAGE = 2
EXIT_PROBE_ERROR = 3


def read_targets(path):
    with open(path, encoding="utf-8") as f:
        return [
            line.strip()
            for line in f
            if line.strip() and not line.strip().startswith("#")
        ]


def main() -> int:
    ap = argparse.ArgumentParser(description="TestWeave 브라우저 점검기")
    ap.add_argument("--targets", required=True, help="대상 목록 파일 (한 줄에 URL 하나)")
    ap.add_argument("--out", default="reports/browser-findings.json")
    ap.add_argument(
        "--allow-private",
        action="store_true",
        help="사설 대역과 루프백 허용. 링크로컬은 이 옵션과 무관하게 계속 막힌다",
    )
    args = ap.parse_args()

    if not os.path.exists(args.targets):
        print("대상 목록이 없습니다: %s" % args.targets, file=sys.stderr)
        return EXIT_USAGE

    targets = read_targets(args.targets)
    if not targets:
        # 대상 0건으로 조용히 성공하면 "이상 없음" 보고가 나간다. 그게 제일 나쁘다.
        print("대상이 0건입니다: %s" % args.targets, file=sys.stderr)
        return EXIT_USAGE

    if args.allow_private:
        print(
            "[경고] --allow-private 로 실행합니다. 사설 대역과 루프백이 열립니다. "
            "링크로컬(169.254/16, fe80::/10)은 이 옵션과 무관하게 계속 막힙니다.",
            file=sys.stderr,
        )

    from playwright.sync_api import sync_playwright

    findings = {}
    failed = 0
    with sync_playwright() as pw:
        browser = pw.chromium.launch()
        try:
            for url in targets:
                print("점검: %s ... " % url, end="", flush=True)
                try:
                    outcomes = probe_one(browser, url, args.allow_private)
                    findings[url] = outcomes
                    n_bad = sum(1 for o in outcomes if o["status"] == "FAIL")
                    print("규칙 %d / 미충족 %d" % (len(outcomes), n_bad))
                except Exception as e:
                    # 대상 하나가 죽어도 나머지는 계속한다. 다만 결과에 반드시 남긴다.
                    findings[url] = [bad("점검 실행", trim(e))]
                    failed += 1
                    print("실패 (%s)" % trim(e, 120))
        finally:
            browser.close()

    out = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "probe": "browser-probe/0.1",
        "targets": findings,
    }
    out_path = os.path.abspath(args.out)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)

    print("\n결과: %s (대상 %d, 점검 실패 %d)" % (out_path, len(targets), failed))
    return EXIT_PROBE_ERROR if failed else EXIT_OK


if __name__ == "__main__":
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass
    sys.exit(main())
