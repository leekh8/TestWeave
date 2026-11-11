package com.testweave.check;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testweave.domain.SecurityTarget;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 브라우저 점검 결과를 흡수하는 모듈.
 *
 * <p>본체(Java)는 브라우저를 띄우지 않는다. 매일 도는 스캔에 브라우저 런타임을
 * 얹고 싶지 않았고, 회귀 판정과 수용 등록부와 리포트는 이미 본체에 있어서 다시
 * 만들 이유가 없었다. 그래서 띄우는 쪽은 파이썬({@code tools/browser-probe})이
 * 맡고, 그 결과 json 을 이 모듈이 읽어 다른 점검과 똑같이 취급한다.
 *
 * <p>헤더만 봐서는 안 보이는 층이 따로 있다. 2026-09-17 에 개인 블로그에서
 * 시리즈 페이지 18개가 전부 canonical 을 홈으로 찍고 있었다. 글 40편으로 가는
 * 발견 경로가 통째로 끊긴 상태였는데 응답 헤더는 멀쩡했다. 결함은 렌더된
 * HTML 에만 있었다.
 *
 * <h2>지키는 선</h2>
 * <ul>
 *   <li><b>결과가 없으면 통과가 아니라 미충족이다.</b> 파일이 없거나 이 대상이
 *       그 안에 없으면 FAIL 을 낸다. 조용히 비우면 「브라우저 점검을 켰는데
 *       아무 말도 없다」가 되고, 그것은 「문제가 없다」와 구별되지 않는다.
 *       이 저장소가 같은 모양으로 한 번 죽은 적이 있다(매일 초록불이 켜지는
 *       동안 아무것도 안 하고 있었다).</li>
 *   <li><b>낡은 결과는 현재가 아니다.</b> json 이 오래됐으면 FAIL 이다. 회귀
 *       판정은 「지난번 대비 지금」을 보는 것인데, 낡은 파일을 현재로 읽으면
 *       그 비교가 거짓말이 된다. 어제 고친 것이 오늘도 FAIL 로 남거나,
 *       오늘 깨진 것이 어제 값으로 PASS 가 된다.</li>
 *   <li><b>모르는 status 를 통과로 읽지 않는다.</b> {@code PASS} 와
 *       {@code FAIL} 만 받는다. 파이썬 쪽이 값을 늘리면 여기서 FAIL 로 떨어져
 *       눈에 띈다. 기본값을 PASS 로 두면 늘어난 값이 조용히 통과한다.</li>
 *   <li><b>규칙 이름을 바꾸지 않는다.</b> 수용 등록부({@code exceptions.txt})가
 *       리포트에 나오는 이름 그대로 매칭한다. 여기서 다듬으면 등록해 둔 수용이
 *       조용히 적용되지 않는다.</li>
 * </ul>
 *
 * <p>기본값으로는 꺼져 있다. {@code testweave.ci.check-types} 에
 * {@code BROWSER} 를 넣어야 돌고, 넣으려면 그 전에 파이썬 점검기를 돌려
 * json 을 만들어 두어야 한다. 순서를 바꾸면 첫 회차가 통째로 FAIL 이다.
 */
@Component
public class BrowserCheck implements SecurityCheck {

    public static final String TYPE = "BROWSER";

    /** 파이썬 점검기가 내놓은 결과 파일. */
    @Value("${testweave.browser-findings-file:reports/browser-findings.json}")
    private String findingsFile;

    /**
     * 결과를 현재로 인정하는 시간. 기본 26시간.
     *
     * <p>24가 아니라 26인 이유는 매일 도는 스캔이 정확히 24시간 간격이 아니기
     * 때문이다. 러너 대기와 스케줄 지연으로 몇십 분씩 밀리는데, 24로 두면
     * 멀쩡한 회차가 「낡았다」로 떨어진다. 여유를 두되 이틀은 넘기지 않는다.
     */
    @Value("${testweave.browser-findings-max-hours:26}")
    private long maxHours;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public List<CheckOutcome> run(SecurityTarget target) {
        Path p = Path.of(findingsFile);
        if (!Files.isRegularFile(p)) {
            return List.of(CheckOutcome.fail("브라우저 점검 결과",
                    findingsFile + " 가 없다. tools/browser-probe/probe.py 를 먼저 돌린다"));
        }

        JsonNode root;
        try {
            root = mapper.readTree(Files.readString(p));
        } catch (IOException ex) {
            return List.of(CheckOutcome.fail("브라우저 점검 결과",
                    findingsFile + " 를 읽지 못했다: " + ex.getMessage()));
        }

        List<CheckOutcome> out = new ArrayList<>();
        CheckOutcome age = freshness(root.path("generatedAt").asText(""));
        out.add(age);
        // 낡았으면 그 안의 값을 현재로 보고하지 않는다. 더 보태면 틀린 현재가 된다
        if ("FAIL".equals(age.status())) {
            return out;
        }

        JsonNode mine = root.path("targets").path(target.getUrl());
        if (!mine.isArray() || mine.isEmpty()) {
            out.add(CheckOutcome.fail("브라우저 점검 대상",
                    target.getUrl() + " 는 결과 파일에 없다. 점검기 대상 목록을 맞춘다"));
            return out;
        }

        out.add(CheckOutcome.pass("브라우저 점검 대상", "규칙 " + mine.size() + "건"));
        for (Iterator<JsonNode> it = mine.elements(); it.hasNext(); ) {
            JsonNode n = it.next();
            String rule = n.path("rule").asText("").trim();
            String status = n.path("status").asText("").trim();
            String detail = n.path("detail").asText("").trim();
            if (rule.isEmpty()) {
                out.add(CheckOutcome.fail("브라우저 점검 결과",
                        "규칙 이름이 빈 항목이 있다: " + n));
                continue;
            }
            if ("PASS".equals(status)) {
                out.add(CheckOutcome.pass(rule, detail));
            } else if ("FAIL".equals(status)) {
                out.add(CheckOutcome.fail(rule, detail));
            } else {
                // 🛑 모르는 값을 PASS 로 떨어뜨리지 않는다. 늘어난 값이 조용히 통과한다
                out.add(CheckOutcome.fail(rule,
                        "알 수 없는 status: " + (status.isEmpty() ? "(없음)" : status)
                                + (detail.isEmpty() ? "" : " / " + detail)));
            }
        }
        return out;
    }

    /** json 이 현재로 쓸 만큼 최근인가. */
    private CheckOutcome freshness(String generatedAt) {
        if (generatedAt.isEmpty()) {
            return CheckOutcome.fail("브라우저 점검 시각",
                    "generatedAt 이 없다. 점검기 판이 너무 옛것이거나 파일이 손상됐다");
        }
        Instant at;
        try {
            at = Instant.parse(generatedAt);
        } catch (DateTimeParseException ex) {
            return CheckOutcome.fail("브라우저 점검 시각",
                    "generatedAt 을 읽지 못했다: " + generatedAt);
        }
        long hours = Duration.between(at, Instant.now()).toHours();
        if (hours > maxHours) {
            return CheckOutcome.fail("브라우저 점검 시각",
                    hours + "시간 전 결과다 (상한 " + maxHours + "시간). 점검기가 안 돌고 있다");
        }
        // 🛑 미래 시각도 막는다. 시계가 어긋나면 영원히 신선해 보인다
        if (hours < -1) {
            return CheckOutcome.fail("브라우저 점검 시각",
                    "미래 시각이다 (" + generatedAt + "). 점검기 쪽 시계를 본다");
        }
        return CheckOutcome.pass("브라우저 점검 시각", hours + "시간 전");
    }
}
