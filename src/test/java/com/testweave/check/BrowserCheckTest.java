package com.testweave.check;

import com.testweave.domain.SecurityTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 브라우저 점검 결과 흡수 검증. 네트워크와 브라우저 없이 json 만으로 돈다.
 *
 * <p>여기서 지키는 것은 하나다. <b>결과가 없거나 낡았을 때 통과로 떨어지지
 * 않는가.</b> 조용히 비우면 「켰는데 아무 말도 없다」가 되고, 그것은 「문제가
 * 없다」와 구별되지 않는다. 이 저장소가 같은 모양으로 한 번 죽은 적이 있다.
 */
class BrowserCheckTest {

    @TempDir
    Path dir;

    private BrowserCheck check;
    private Path json;

    private static final String URL = "https://example.com";

    @BeforeEach
    void setUp() {
        check = new BrowserCheck();
        json = dir.resolve("browser-findings.json");
        ReflectionTestUtils.setField(check, "findingsFile", json.toString());
        ReflectionTestUtils.setField(check, "maxHours", 26L);
    }

    private SecurityTarget target() {
        return new SecurityTarget("예시", URL, "BROWSER");
    }

    private void write(String body) throws IOException {
        Files.writeString(json, body);
    }

    /** 지금 시각으로 결과 하나를 적는다. */
    private void writeNow(String outcomes) throws IOException {
        write("{\"generatedAt\":\"" + Instant.now() + "\","
                + "\"probe\":\"browser-probe/0.1\","
                + "\"targets\":{\"" + URL + "\":" + outcomes + "}}");
    }

    private static boolean hasFail(List<CheckOutcome> os, String rule) {
        return os.stream().anyMatch(o -> rule.equals(o.rule()) && "FAIL".equals(o.status()));
    }

    private static boolean hasPass(List<CheckOutcome> os, String rule) {
        return os.stream().anyMatch(o -> rule.equals(o.rule()) && "PASS".equals(o.status()));
    }

    @Test
    void type_isBrowser() {
        assertEquals("BROWSER", check.type());
    }

    // ---------------------------------------------------- 결과가 없을 때

    @Test
    void 파일이_없으면_통과가_아니라_미충족() {
        List<CheckOutcome> os = check.run(target());
        assertTrue(hasFail(os, "브라우저 점검 결과"),
                "파일이 없는데 FAIL 이 없다. 조용히 통과하면 「문제 없음」과 구별이 안 된다");
        assertFalse(os.isEmpty(), "빈 목록을 돌려주면 스캔이 아무 말도 안 한다");
    }

    @Test
    void 깨진_json_이면_미충족() throws IOException {
        write("{not json");
        assertTrue(hasFail(check.run(target()), "브라우저 점검 결과"));
    }

    @Test
    void 대상이_결과에_없으면_미충족() throws IOException {
        write("{\"generatedAt\":\"" + Instant.now() + "\",\"targets\":{\"https://other.com\":[]}}");
        assertTrue(hasFail(check.run(target()), "브라우저 점검 대상"),
                "점검기 대상 목록과 어긋난 것을 조용히 넘기면 그 대상은 영원히 안 보인다");
    }

    @Test
    void 대상_배열이_비면_미충족() throws IOException {
        writeNow("[]");
        assertTrue(hasFail(check.run(target()), "브라우저 점검 대상"));
    }

    // ---------------------------------------------------- 낡은 결과

    @Test
    void 낡은_결과는_미충족() throws IOException {
        String old = Instant.now().minus(48, ChronoUnit.HOURS).toString();
        write("{\"generatedAt\":\"" + old + "\",\"targets\":{\"" + URL
                + "\":[{\"rule\":\"제목 존재\",\"status\":\"PASS\",\"detail\":\"\"}]}}");
        List<CheckOutcome> os = check.run(target());
        assertTrue(hasFail(os, "브라우저 점검 시각"), "48시간 전 결과가 현재로 통과했다");
        // 🛑 낡았으면 그 안의 값을 현재로 보고하지 않는다
        assertFalse(hasPass(os, "제목 존재"),
                "낡은 파일의 PASS 를 현재로 올리면 회귀 판정이 거짓말이 된다");
    }

    @Test
    void generatedAt_이_없으면_미충족() throws IOException {
        write("{\"targets\":{\"" + URL + "\":[{\"rule\":\"제목 존재\",\"status\":\"PASS\"}]}}");
        assertTrue(hasFail(check.run(target()), "브라우저 점검 시각"));
    }

    @Test
    void generatedAt_이_날짜가_아니면_미충족() throws IOException {
        write("{\"generatedAt\":\"어제\",\"targets\":{\"" + URL + "\":[]}}");
        assertTrue(hasFail(check.run(target()), "브라우저 점검 시각"));
    }

    @Test
    void 미래_시각이면_미충족() throws IOException {
        String future = Instant.now().plus(72, ChronoUnit.HOURS).toString();
        write("{\"generatedAt\":\"" + future + "\",\"targets\":{\"" + URL + "\":[]}}");
        assertTrue(hasFail(check.run(target()), "브라우저 점검 시각"),
                "미래 시각을 받으면 시계가 어긋난 동안 영원히 신선해 보인다");
    }

    @Test
    void 몇_시간_밀린_것은_통과() throws IOException {
        String ok = Instant.now().minus(25, ChronoUnit.HOURS).toString();
        write("{\"generatedAt\":\"" + ok + "\",\"targets\":{\"" + URL
                + "\":[{\"rule\":\"제목 존재\",\"status\":\"PASS\",\"detail\":\"\"}]}}");
        List<CheckOutcome> os = check.run(target());
        assertTrue(hasPass(os, "브라우저 점검 시각"),
                "상한을 24로 두면 러너 지연으로 멀쩡한 회차가 낡았다고 떨어진다");
        assertTrue(hasPass(os, "제목 존재"));
    }

    // ---------------------------------------------------- 정상 흡수

    @Test
    void 규칙을_그대로_옮긴다() throws IOException {
        writeNow("[{\"rule\":\"canonical 자기참조\",\"status\":\"FAIL\","
                + "\"detail\":\"https://example.com/ 를 가리킴\"},"
                + "{\"rule\":\"제목 존재\",\"status\":\"PASS\",\"detail\":\"\"}]");
        List<CheckOutcome> os = check.run(target());
        assertTrue(hasFail(os, "canonical 자기참조"));
        assertTrue(hasPass(os, "제목 존재"));
        CheckOutcome can = os.stream().filter(o -> "canonical 자기참조".equals(o.rule()))
                .findFirst().orElseThrow();
        assertEquals("https://example.com/ 를 가리킴", can.detail(),
                "detail 을 잃으면 리포트에서 왜 FAIL 인지 알 수 없다");
    }

    /**
     * 🛑 규칙 이름을 다듬으면 안 된다. 수용 등록부(exceptions.txt)가 리포트에
     * 나오는 이름 그대로 매칭한다. 한 글자라도 다르면 등록해 둔 수용이 조용히
     * 적용되지 않고, 고칠 수 없는 항목이 매일 빨간 숫자로 남는다.
     */
    @Test
    void 규칙_이름을_바꾸지_않는다() throws IOException {
        writeNow("[{\"rule\":\"색인 차단 태그 없음\",\"status\":\"PASS\",\"detail\":\"\"}]");
        List<String> rules = check.run(target()).stream().map(CheckOutcome::rule).toList();
        assertTrue(rules.contains("색인 차단 태그 없음"),
                "이름이 바뀌면 exceptions.txt 의 수용이 안 먹는다. 실제 이름: " + rules);
    }

    /**
     * 🛑 모르는 status 를 PASS 로 떨어뜨리지 않는다. 파이썬 쪽이 값을 늘렸을 때
     * 기본값이 PASS 면 새 값이 전부 조용히 통과한다.
     */
    @Test
    void 모르는_status_는_미충족() throws IOException {
        writeNow("[{\"rule\":\"제목 존재\",\"status\":\"SKIP\",\"detail\":\"건너뜀\"},"
                + "{\"rule\":\"description 중복 없음\",\"status\":\"\",\"detail\":\"\"},"
                + "{\"rule\":\"스크립트 오류 없음\",\"status\":\"warn\",\"detail\":\"\"}]");
        List<CheckOutcome> os = check.run(target());
        assertTrue(hasFail(os, "제목 존재"), "SKIP 이 통과로 떨어졌다");
        assertTrue(hasFail(os, "description 중복 없음"), "빈 status 가 통과로 떨어졌다");
        assertTrue(hasFail(os, "스크립트 오류 없음"), "소문자 warn 이 통과로 떨어졌다");
        assertFalse(os.stream().anyMatch(o -> "PASS".equals(o.status())
                        && List.of("제목 존재", "description 중복 없음", "스크립트 오류 없음")
                        .contains(o.rule())),
                "모르는 값이 하나라도 PASS 로 새면 안 된다");
    }

    @Test
    void 규칙_이름이_비면_미충족() throws IOException {
        writeNow("[{\"rule\":\"\",\"status\":\"PASS\",\"detail\":\"\"}]");
        assertTrue(hasFail(check.run(target()), "브라우저 점검 결과"),
                "이름 없는 규칙을 그냥 넘기면 리포트에서 가리킬 수 없다");
    }

    /** 점검기가 대상 하나에서 죽었을 때 적는 꼴(`점검 실행` FAIL)도 그대로 올라온다. */
    @Test
    void 점검기_실패도_그대로_올린다() throws IOException {
        writeNow("[{\"rule\":\"점검 실행\",\"status\":\"FAIL\",\"detail\":\"Timeout 30000ms\"}]");
        assertTrue(hasFail(check.run(target()), "점검 실행"));
    }
}
