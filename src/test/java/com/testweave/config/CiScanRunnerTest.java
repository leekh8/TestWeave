package com.testweave.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Actions 요약 마크다운 검증. 표 열과 값이 어긋나는 것을 잡는다. */
class CiScanRunnerTest {

    /** 각 수치를 전부 다른 값으로 준다. 같은 값을 쓰면 열이 바뀌어도 통과한다. */
    private static String md(long regressions, long missing, long fails, long errors) {
        return CiScanRunner.stepSummaryMarkdown(2, 11, regressions, missing, 3, fails, errors, 0, 0);
    }

    private static String md(long regressions, long missing, long fails, long errors,
                             long accepted, long expired) {
        return CiScanRunner.stepSummaryMarkdown(2, 11, regressions, missing, 3, fails, errors,
                accepted, expired);
    }

    @Test
    void 표의_값이_헤더_순서대로_들어간다() {
        // 대상2 규칙11 회귀5 소멸7 미충족8 수용4 수용만료6 FIXED3 오류0
        String row = md(5, 7, 8, 0, 4, 6).lines()
                .filter(l -> l.startsWith("| 2 "))
                .findFirst().orElseThrow();

        assertTrue(row.contains("| 2 | 11 | 5 | 7 | 8 | 4 | 6 | 3 | 0 |"),
                "헤더는 회귀 소멸 미충족 수용 수용만료 FIXED 오류 순인데 실제 행은: " + row);
    }

    @Test
    void 회귀가_있으면_조치_필요다() {
        assertTrue(md(1, 0, 0, 0).contains("조치 필요"));
    }

    @Test
    void 규칙_소멸만_있어도_조치_필요다() {
        // 대상이 죽은 날의 모양. 회귀 0건이라고 넘어가면 이 기능을 만든 이유가 사라진다.
        assertTrue(md(0, 3, 0, 0).contains("조치 필요"));
    }

    @Test
    void 상시_미충족은_이상_없음이_아니다() {
        // 게이트는 통과시키되(FAIL_ON=regression) 문구까지 초록으로 쓰지는 않는다.
        String out = md(0, 0, 8, 0);
        assertTrue(out.contains("미충족 8건"));
        assertFalse(out.contains("이상 없음"));
    }

    @Test
    void 수용_기한_만료는_단순_미충족보다_먼저_쓴다() {
        // 다시 보기로 한 날이 왔는데 아무도 안 봤다는 뜻이다. 기한이 있는 수용만 수용이다.
        String out = md(0, 0, 2, 0, 3, 2);
        assertTrue(out.contains("수용 기한 만료 2건"));
        assertFalse(out.contains("이상 없음"));
    }

    @Test
    void 수용분이_있으면_그냥_이상_없음으로_뭉치지_않는다() {
        // 여기서 뭉치면 수용해 둔 항목이 시야에서 사라지고, 등록부는 곧 아무도 안 읽게 된다.
        String out = md(0, 0, 0, 0, 4, 0);
        assertTrue(out.contains("이상 없음 (수용 4건)"));
    }

    @Test
    void 전부_0이면_이상_없음이다() {
        // 판정 줄(H2)만 본다. 표 헤더에는 '수용' 열 이름이 항상 들어 있다.
        String verdict = md(0, 0, 0, 0).lines()
                .filter(l -> l.startsWith("## TestWeave"))
                .findFirst().orElseThrow();
        assertTrue(verdict.contains("이상 없음"));
        assertFalse(verdict.contains("수용"), "수용이 0이면 판정 줄에 수용 문구를 붙이지 않는다");
    }
}
