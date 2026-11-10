# TestWeave — Web Security Regression Scanner

> 웹/API의 보안 설정(응답 헤더·TLS·쿠키 플래그)을 점검하고, **직전 결과(baseline) 대비 "회귀"** 를 잡아내는 도구.

## Why

배포를 거듭하다 보면 보안 설정이 조용히 후퇴합니다 — 어제까지 있던 `HSTS`가 빠지거나, 쿠키의 `Secure` 플래그가 사라지거나, 인증서 만료가 임박합니다. 단발성 스캐너는 "지금 상태"만 보여줄 뿐, **"지난번보다 나빠졌다"** 는 알려주지 않습니다.

TestWeave는 대상별 점검 결과를 저장하고, 매 스캔을 직전 결과와 비교해 **`REGRESSION`(PASS→FAIL, 후퇴) / `FIXED`(FAIL→PASS, 개선) / `NEW` / `SAME`** 으로 판정합니다. 정기 실행(예: GitHub Actions 스케줄)과 결합하면 보안 설정의 후퇴를 배포 직후 감지할 수 있습니다.

## Status

🟢 **MVP + 정기 회귀 동작** — 2026-06-30 보안 회귀 스캐너로 피벗, 2026-07-02 HTML 리포트·`ci` 프로파일·GitHub Actions 스케줄 스캔까지 배선 완료. 아래 "구현됨"은 코드에 실제 배선된 것, "로드맵"은 아직입니다.

## Features

**구현됨 (MVP 골격):**
- **점검 대상 관리** — `SecurityTarget`(name/url/checkTypes) 등록·조회 (`GET/POST /api/targets`)
- **보안 점검 모듈** (`SecurityCheck` 인터페이스 — 새 모듈은 구현체 추가만으로 자동 인식)
  - `HeaderCheck` — HSTS · CSP · X-Frame-Options · X-Content-Type-Options · Referrer-Policy. **존재만이 아니라 값의 강도까지 검증** — HSTS `max-age≥1년`, X-Frame-Options ∈ {DENY,SAMEORIGIN}, CSP `unsafe-inline`/와일드카드 거부, Referrer-Policy 유효 토큰, X-Content-Type-Options `nosniff`
  - `CookieCheck` — `Set-Cookie`의 Secure / HttpOnly / SameSite 플래그 (속성 토큰 파싱 — 값 속 문자열 오탐 방지). **`SameSite=None`은 `Secure` 동반 필수**로 검증
  - `TlsCheck` — HTTPS 사용 여부 · **호스트네임(SAN/CN) 검증** · **협상 프로토콜 버전 검증(TLS 1.2 미만 FAIL)** · 서버 인증서 만료 임박(임계 14일, 규칙명 고정 → PASS→FAIL 회귀 추적)
  - `SsrfGuard` + `SafeHttpFetcher` — fetch 전에 대상 URL을 검증해 **내부/사설/링크로컬 주소(루프백·10/8·172.16/12·192.168/16·메타데이터 169.254.169.254)를 차단**한다 (스캐너 자체 SSRF 방어). **리다이렉트도 수동 추적하며 매 홉을 재검증** — 공개 URL이 302로 내부 주소로 튀는 우회를 막는다. *잔여: DNS 리바인딩*
- **API 인증(HTTP Basic)** — 대상 등록·스캔 등 모든 엔드포인트를 인증으로 보호(Spring Security). 자격 미지정 시 기동 로그에 임의 비밀번호 출력(하드코딩 없음), 운영은 `SPRING_SECURITY_USER_*` 환경변수 주입. **H2 콘솔은 기본 프로파일에서 off** — `dev` 프로파일에서만 켜지며 그것도 인증 필요
- **입력 검증** — 대상 등록 시 `url`/`checkTypes` 누락·비-http(s) URL을 저장 전 **400으로 반려**(`@Valid`), 존재하지 않는 대상은 **404** — 5xx로 새지 않음
- **회귀 판정** — 스캔 결과를 직전 baseline과 비교해 `NEW/REGRESSION/FIXED/SAME` 산출 (`POST /api/targets/{id}/scan`)
- **HTML 리포트** — `REGRESSION` 강조 리포트 (`POST /api/targets/{id}/report`, ci 모드는 `reports/scan-report.html` 파일 생성)
- **정기 회귀 (GitHub Actions)** — `ci` 프로파일: 서버 없이 스캔→리포트 파일→종료. baseline은 H2 파일 DB를 actions/cache로 실행 간 유지. 매일 06:20 KST 스케줄, `REGRESSION`/스캔 오류 시 워크플로우 실패 → GitHub 기본 알림. `FAIL_ON=fail`로 두면 회귀가 아닌 **상시 FAIL**(신규 대상이 첫날부터 미충족 등)도 빌드 실패로 승격
- **위험 수용 등록부** - 고칠 수 없는 항목을 사유와 기한을 적어 `exceptions.txt`에 등록하면 기한까지 미충족 집계에서 빠진다. **회귀와 소멸은 수용할 수 없다.** 만료되면 자동으로 미충족 복귀 + 별도 판정. 아래 절 참고
- **데모 실행** — `demo` 프로파일로 띄우면 샘플 대상을 스캔해 회귀 판정을 콘솔에 출력

**실제로 쓰려면:**

```bash
# 1) 대상 목록을 만든다 (targets.local.txt 는 gitignore 대상)
cp targets.sample.txt targets.local.txt

# 2) 공개 자산만 점검 (기본)
./gradlew bootRun --args='--spring.profiles.active=ci'

# 3) 사내 자산까지 점검
./gradlew bootRun --args='--spring.profiles.active=ci --testweave.allow-private=true'
```

대상 목록을 `application-ci.properties`가 아니라 파일에 두는 이유는 이 저장소가 공개라
업무 대상 URL을 커밋할 수 없기 때문이다. 저장소에는 `targets.sample.txt`만 남는다.

`testweave.allow-private`는 기본 `false`다. 켜는 순간 이 프로세스는 내부망에 요청을 대신
보내 주는 통로가 되므로, 자기 자산을 아는 사람이 자기 환경에서 켜는 스위치로 둔다.
**켜도 링크로컬(169.254/16)은 막힌다.** 클라우드 메타데이터 엔드포인트가 거기 있고,
그건 내부 자산 점검과 무관하면서 인스턴스 자격증명을 흘린다. 켜져 있으면 기동할 때마다
경고가 뜬다.

결과는 GitHub Actions 실행 페이지 상단에 요약 표로 바로 나온다. 자세한 내용은
`scan-report` 아티팩트.

**로드맵:**
- [ ] `AuthCheck` — 보호 엔드포인트가 무인증 시 401/403을 반환하는지(인가 회귀)
- [ ] Slack 알림 (현재는 워크플로우 실패 → GitHub 기본 알림)
- [ ] 운영 DB(PostgreSQL) 프로파일 분리 + 서버 상시 배포
- [ ] OWASP ZAP(DAST) 연동으로 실제 취약점 스캔까지 확장

## 위험 수용 등록부

고칠 방법이 없는 항목이 매일 같은 숫자로 경고에 남으면, 사람은 그 경고를 안 보게 된다.
그리고 진짜 회귀가 같은 화면에 섞여 들어온다. 등록부는 이 문제를 푼다.

실제 사례가 이 저장소의 CI 대상이다. `https://leekh8.github.io`는 GitHub Pages라
커스텀 응답 헤더를 붙일 수 없어서 헤더 4건이 매일 FAIL이었다. 15회 연속 같은 값이었고,
그동안 알림은 0건이었다.

```
# exceptions.txt  (대상URL | 모듈 | 규칙 | 기한 | 사유)
https://leekh8.github.io | HEADER | CSP 적용 | 2026-12-31 | GitHub Pages는 커스텀 응답 헤더를 지원하지 않는다
```

```
# 등록 전                                   # 등록 후
REGRESSION=0 MISSING=0 FAIL=4 ERROR=0      REGRESSION=0 MISSING=0 FAIL=0 ERROR=0
                                           수용=4 (만료=0, 기한임박=0)
```

**지키는 선**

| 규칙 | 이유 |
|---|---|
| 회귀와 소멸은 수용 불가 | 수용은 "지금 상태를 안다"는 선언인데 회귀는 상태가 바뀐 것이라 전제가 깨진다. 등록해 둬도 "수용 무효"로 표시만 된다 |
| 기한 필수, 상한 1년 | 무기한 수용은 잊힌 수용과 구별되지 않는다. 먼 날짜는 무기한 우회다 |
| 사유 필수 | 없으면 기동 실패. 다음 사람이 판단을 이어받을 수 없는 기록은 수용이 아니라 방치다 |
| 형식 오류는 기동 실패 | 조용히 넘기면 오타 하나로 수용이 사라지거나 엉뚱한 규칙에 붙는다 |
| 만료는 삭제가 아니라 표면화 | 지난 기한도 파싱은 통과시키고 "수용 만료"로 따로 보여준다 |
| 안 쓰인 줄을 보고 | 규칙명이 바뀌었는데 줄만 남는 식으로 등록부는 썩는다. 매 회차 정리 대상을 출력한다 |

수용해도 판정과 상태는 그대로 `FAIL`이다. 바뀌는 것은 "지금 조치해야 하는가"뿐이다.

`exceptions.txt`는 일부러 저장소에 커밋한다. 누가 언제 무엇을 왜 수용했는지가 git 이력에
남아야 감사 증적이 된다. 공개할 수 없는 업무 대상은 `exceptions.local.txt`에 두면
그쪽이 이긴다(gitignore 대상).

관련 설정:

| 키 | 기본값 | 의미 |
|---|---|---|
| `testweave.exceptions-file` | `exceptions.txt` | 등록부 경로 |
| `testweave.exceptions-local-file` | `exceptions.local.txt` | 있으면 이쪽이 이긴다 |
| `testweave.exceptions-max-days` | `365` | 기한 상한(일) |

## Stack

- **Java 17**, **Spring Boot 3.4.4** (Spring Web, Spring Data JPA, **Spring Security**, Validation)
- **H2** — 로컬 개발은 인메모리, `ci` 프로파일은 파일 DB(baseline 영속). 운영 PostgreSQL은 로드맵
- HTTP/TLS 점검은 **표준 라이브러리**(`java.net.http`, `javax.net.ssl`) — 외부 의존성 최소화
- **Thymeleaf** (엔진 코어만) — HTML 리포트 렌더링
- Lombok, JUnit 5

> 이전 프로토타입의 Selenium · REST Assured · iText · Mail 의존성은 피벗하며 제거했습니다.

## Run

```bash
# 일반 실행 (localhost:8080). 자격을 지정하지 않으면 기동 로그에 임의 비밀번호가 출력된다.
# 안정적 자격이 필요하면 환경변수로 주입:
#   SPRING_SECURITY_USER_NAME=admin SPRING_SECURITY_USER_PASSWORD=<강한값> ./gradlew bootRun
./gradlew bootRun

# 데모: 샘플 대상 스캔 결과를 콘솔에 출력
./gradlew bootRun --args='--spring.profiles.active=demo'

# CI 모드: 서버 없이 스캔 → reports/scan-report.html + summary.json 생성 후 종료
#   대상은 src/main/resources/application-ci.properties 의 testweave.ci.targets
./gradlew bootRun --args='--spring.profiles.active=ci'
```

REST 예시:

```bash
# 대상 등록 (Basic 인증 필요 — -u 사용자:비밀번호)
curl -u admin:$PW -X POST localhost:8080/api/targets -H 'Content-Type: application/json' \
  -d '{"name":"example","url":"https://example.com","checkTypes":"HEADER,COOKIE,TLS"}'

# 스캔 + 회귀 판정
curl -u admin:$PW -X POST localhost:8080/api/targets/1/scan
```

H2 콘솔: `dev` 프로파일에서만 활성 — `./gradlew bootRun --args='--spring.profiles.active=dev'` 후
`http://localhost:8080/h2-console` (Basic 인증 후, JDBC URL `jdbc:h2:mem:testweave`, user `sa`)

## Structure

```
src/main/java/com/testweave/
  TestweaveApplication.java          # Spring Boot 진입점
  domain/
    SecurityTarget.java              # 점검 대상 (url, checkTypes)
    ScanResult.java                  # 규칙별 스캔 결과 (PASS/FAIL)
  check/
    SecurityCheck.java               # 점검 모듈 인터페이스
    CheckOutcome.java                # 규칙 하나의 결과 (record)
    HeaderCheck.java / CookieCheck.java / TlsCheck.java
  scan/
    Regression.java                  # 회귀 판정 결과 (record)
    Acceptance.java                  # 위험 수용 판단 (ACTIVE/EXPIRED/REJECTED)
    AcceptanceRegistry.java          # exceptions.txt 파싱, 적용, 등록부 부패 탐지
  service/
    ScanService.java                 # 스캔 실행 + baseline 대비 회귀 계산
  report/
    ReportService.java               # HTML 리포트 렌더링 (Thymeleaf 엔진 직접 구성)
    TargetReport.java                # 리포트 단위 (record)
  repository/                        # Spring Data JPA
  controller/
    ScanController.java              # REST: 대상 등록/조회, 스캔/리포트 실행
  config/
    DemoDataRunner.java              # demo 프로파일 CLI 실행기
    CiScanRunner.java                # ci 프로파일: 스캔 → 리포트 파일 → 종료
src/main/resources/
  templates/report.html              # 리포트 템플릿 (REGRESSION 강조)
  application-ci.properties          # ci 대상/체크 설정 + H2 파일 DB
.github/workflows/security-scan.yml  # 매일 06:20 KST 스케줄 스캔 + 회귀 시 실패
tools/browser-probe/probe.py         # 브라우저 점검기(파이썬). 아래 «브라우저 점검층» 참고
```

설계 상세는 [`docs/DESIGN.md`](docs/DESIGN.md) 참고.

## 브라우저 점검층 (tools/browser-probe)

본체(Java)는 브라우저를 띄우지 않는다. 헤더만 봐서는 안 보이는 층이 따로 있어서
파이썬 점검기를 분리해 뒀다.

어떤 결함은 응답 헤더가 전부 정상인데도 렌더된 HTML 에만 있다. 2026-09-17 에 개인
블로그에서 시리즈 페이지 18개가 전부 canonical 을 홈으로 찍고 있었다. 글 40편으로 가는
발견 경로가 통째로 끊긴 상태였는데 응답 헤더는 멀쩡했고 소스도 한 줄짜리 컴포넌트
호출이라 멀쩡해 보였다. 헤더만 보는 스캐너로는 영원히 못 잡는 층이 있다.

```bash
python tools/browser-probe/probe.py --targets targets.local.txt \
    --out reports/browser-findings.json
```

매일 도는 스캔에 브라우저 런타임을 얹지 않기 위해 따로 둔다. 지금은 json 까지만 만들고,
**본체가 이 결과를 읽어 점검 결과로 흡수하는 배선은 아직이다** (`grep -r browser-findings src`
가 0건). 회귀 판정과 수용 등록부와 리포트는 본체에 이미 있어서 다시 만들 이유가 없다.
