package com.testweave.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Actions 요약 마크다운 검증. 표 열과 값이 어긋나는 것을 잡는다. */
class CiScanRunnerTest {

    /** 각 수치를 전부 다른 값으로 준다. 같은 값을 쓰면 열이 바뀌어도 통과한다. */
    private static String md(long regressions, long missing, long fails, long errors) {
        return CiScanRunner.stepSummaryMarkdown(2, 11, regressions, missing, 3, fails, errors);
    }

    @Test
    void 표의_값이_헤더_순서대로_들어간다() {
        // 대상2 규칙11 REGRESSION5 MISSING7 FAIL8 FIXED3 오류0
        String row = md(5, 7, 8, 0).lines()
                .filter(l -> l.startsWith("| 2 "))
                .findFirst().orElseThrow();

        assertTrue(row.contains("| 2 | 11 | 5 | 7 | 8 | 3 | 0 |"),
                "헤더는 REGRESSION MISSING FAIL FIXED 순인데 실제 행은: " + row);
    }

    @Test
    void 회귀가_있으면_조치_필요다() {
        assertTrue(md(1, 0, 0, 0).contains("🛑 조치 필요"));
    }

    @Test
    void 규칙_소멸만_있어도_조치_필요다() {
        // 대상이 죽은 날의 모양. 회귀 0건이라고 넘어가면 이 기능을 만든 이유가 사라진다.
        assertTrue(md(0, 3, 0, 0).contains("🛑 조치 필요"));
    }

    @Test
    void 상시_미충족은_이상_없음이_아니다() {
        // 게이트는 통과시키되(FAIL_ON=regression) 문구까지 초록으로 쓰지는 않는다.
        String out = md(0, 0, 8, 0);
        assertTrue(out.contains("⚠️ 미충족 8건"));
        assertFalse(out.contains("이상 없음"));
    }

    @Test
    void 전부_0이면_이상_없음이다() {
        assertTrue(md(0, 0, 0, 0).contains("✅ 이상 없음"));
    }
}
