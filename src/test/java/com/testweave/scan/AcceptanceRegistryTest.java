package com.testweave.scan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 수용 등록부 테스트.
 *
 * <p>여기서 지키는 것은 하나다. 수용이 회귀를 덮을 수 없다는 것. 덮을 수 있으면
 * 이 도구는 스캐너가 아니라 알리바이가 된다.
 */
class AcceptanceRegistryTest {

    private static final long MAX_DAYS = 365;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 17);

    private static AcceptanceRegistry registryWith(AcceptanceRegistry.Entry... entries) {
        AcceptanceRegistry r = new AcceptanceRegistry();
        r.configure(List.of(entries), MAX_DAYS);
        return r;
    }

    private static AcceptanceRegistry.Entry entry(LocalDate until) {
        return new AcceptanceRegistry.Entry("https://a.example", "HEADER", "CSP 적용", until, "사유");
    }

    private static Regression fail(String verdict) {
        return new Regression("HEADER", "CSP 적용", "FAIL", "FAIL", verdict);
    }

    // ----------------------------------------------------------------- 파싱

    @Test
    @DisplayName("정상 줄을 읽고 주석과 빈 줄은 건너뛴다")
    void 정상_파싱() {
        List<AcceptanceRegistry.Entry> out = AcceptanceRegistry.parse(List.of(
                "# 주석",
                "",
                "https://a.example | HEADER | CSP 적용 | 2026-12-31 | GitHub Pages는 헤더를 못 붙인다"
        ), MAX_DAYS);
        assertEquals(1, out.size());
        assertEquals("https://a.example", out.get(0).url());
        assertEquals("CSP 적용", out.get(0).rule());
        assertEquals(LocalDate.of(2026, 12, 31), out.get(0).until());
        assertEquals("GitHub Pages는 헤더를 못 붙인다", out.get(0).reason());
    }

    @Test
    @DisplayName("사유가 비면 기동을 실패시킨다")
    void 사유_없으면_거부() {
        // 사유 없는 수용은 다음 사람이 판단을 이어받을 수 없다. 그건 수용이 아니라 방치다.
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> AcceptanceRegistry.parse(
                        List.of("https://a.example | HEADER | CSP 적용 | 2026-12-31 |  "), MAX_DAYS));
        assertTrue(e.getMessage().contains("사유"));
    }

    @Test
    @DisplayName("기한 형식이 틀리면 기동을 실패시킨다")
    void 기한_형식_오류는_거부() {
        assertThrows(IllegalStateException.class, () -> AcceptanceRegistry.parse(
                List.of("https://a.example | HEADER | CSP 적용 | 2026/12/31 | 사유"), MAX_DAYS));
    }

    @Test
    @DisplayName("칸 수가 다르면 조용히 무시하지 않고 실패시킨다")
    void 칸_수_오류는_거부() {
        // 무시하면 오타 하나로 수용이 사라지거나 엉뚱한 규칙에 붙는다.
        assertThrows(IllegalStateException.class, () -> AcceptanceRegistry.parse(
                List.of("https://a.example | HEADER | CSP 적용 | 2026-12-31"), MAX_DAYS));
    }

    @Test
    @DisplayName("기한 상한을 넘는 먼 날짜는 거부한다")
    void 상한_초과_거부() {
        String far = LocalDate.now().plusDays(400).toString();
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> AcceptanceRegistry.parse(
                        List.of("https://a.example | HEADER | CSP 적용 | " + far + " | 사유"), MAX_DAYS));
        assertTrue(e.getMessage().contains("상한"));
    }

    @Test
    @DisplayName("지난 기한은 파싱을 막지 않는다")
    void 만료된_줄도_읽힌다() {
        // 만료된 수용은 지워야 할 것이 아니라 보여야 할 것이다.
        List<AcceptanceRegistry.Entry> out = AcceptanceRegistry.parse(
                List.of("https://a.example | HEADER | CSP 적용 | 2020-01-01 | 사유"), MAX_DAYS);
        assertEquals(1, out.size());
    }

    @Test
    @DisplayName("같은 항목이 두 줄이면 거부한다")
    void 중복_거부() {
        assertThrows(IllegalStateException.class, () -> AcceptanceRegistry.parse(List.of(
                "https://a.example | HEADER | CSP 적용 | 2026-12-31 | 사유 1",
                "https://a.example | HEADER | CSP 적용 | 2026-11-30 | 사유 2"), MAX_DAYS));
    }

    // ----------------------------------------------------------------- 적용

    @Test
    @DisplayName("기한 내 FAIL은 수용된다")
    void 기한_내_수용() {
        AcceptanceRegistry r = registryWith(entry(LocalDate.of(2026, 12, 31)));
        Acceptance a = r.decide("https://a.example", fail("SAME"), TODAY);
        assertNotNull(a);
        assertTrue(a.active());
        assertEquals(105, a.daysLeft());
    }

    @Test
    @DisplayName("기한이 지나면 만료로 되돌아온다")
    void 기한_경과는_만료() {
        AcceptanceRegistry r = registryWith(entry(LocalDate.of(2026, 9, 1)));
        Acceptance a = r.decide("https://a.example", fail("SAME"), TODAY);
        assertTrue(a.expired());
        assertEquals(-16, a.daysLeft());
    }

    @Test
    @DisplayName("회귀는 수용할 수 없다")
    void 회귀는_수용_불가() {
        // 이 테스트가 이 기능의 존재 이유다. 여기서 양보하면 수용으로 회귀를 덮을 수 있다.
        AcceptanceRegistry r = registryWith(entry(LocalDate.of(2026, 12, 31)));
        Acceptance a = r.decide("https://a.example", fail("REGRESSION"), TODAY);
        assertTrue(a.rejected());
        assertTrue(!a.active());
    }

    @Test
    @DisplayName("소멸도 수용할 수 없다")
    void 소멸은_수용_불가() {
        AcceptanceRegistry r = registryWith(entry(LocalDate.of(2026, 12, 31)));
        Regression missing = new Regression("HEADER", "CSP 적용", "PASS", "MISSING", "MISSING");
        Acceptance a = r.decide("https://a.example", missing, TODAY);
        assertTrue(a.rejected());
    }

    @Test
    @DisplayName("대상 URL이 다르면 적용되지 않는다")
    void 대상별로_적용된다() {
        // 한 대상의 수용이 다른 대상까지 덮으면 등록부가 전역 무시 목록이 된다.
        AcceptanceRegistry r = registryWith(entry(LocalDate.of(2026, 12, 31)));
        assertNull(r.decide("https://b.example", fail("SAME"), TODAY));
    }

    @Test
    @DisplayName("이미 충족된 항목에는 적용되지 않는다")
    void PASS에는_적용_안_함() {
        AcceptanceRegistry r = registryWith(entry(LocalDate.of(2026, 12, 31)));
        Regression pass = new Regression("HEADER", "CSP 적용", "FAIL", "PASS", "FIXED");
        assertNull(r.decide("https://a.example", pass, TODAY));
    }

    // ----------------------------------------------------------------- 등록부 관리

    @Test
    @DisplayName("한 번도 적용되지 않은 줄을 찾아낸다")
    void 안_쓰인_줄_집계() {
        // 등록부는 이렇게 썩는다. 규칙명이 바뀌었는데 줄만 남는 식이다.
        AcceptanceRegistry.Entry live = entry(LocalDate.of(2026, 12, 31));
        AcceptanceRegistry.Entry dead = new AcceptanceRegistry.Entry(
                "https://a.example", "HEADER", "없어진 규칙", LocalDate.of(2026, 12, 31), "사유");
        AcceptanceRegistry r = registryWith(live, dead);

        r.decide("https://a.example", fail("SAME"), TODAY);

        assertEquals(1, r.unusedEntries().size());
        assertEquals("없어진 규칙", r.unusedEntries().get(0).rule());
    }

    @Test
    @DisplayName("PASS로 바뀐 항목은 안 쓰인 줄로 잡힌다")
    void 고쳐진_항목은_정리_대상() {
        AcceptanceRegistry r = registryWith(entry(LocalDate.of(2026, 12, 31)));
        Regression pass = new Regression("HEADER", "CSP 적용", "FAIL", "PASS", "FIXED");
        r.decide("https://a.example", pass, TODAY);
        assertEquals(1, r.unusedEntries().size());
    }

    @Test
    @DisplayName("회차마다 사용 흔적을 초기화한다")
    void 사용흔적_초기화() {
        AcceptanceRegistry r = registryWith(entry(LocalDate.of(2026, 12, 31)));
        r.decide("https://a.example", fail("SAME"), TODAY);
        assertEquals(0, r.unusedEntries().size());
        r.resetUsage();
        assertEquals(1, r.unusedEntries().size());
    }

    // ----------------------------------------------------------------- 파생 상태

    @Test
    @DisplayName("기한이 코앞이면 임박으로 잡는다")
    void 기한_임박() {
        AcceptanceRegistry r = registryWith(entry(TODAY.plusDays(10)));
        assertTrue(r.decide("https://a.example", fail("SAME"), TODAY).dueSoon());
    }

    @Test
    @DisplayName("여유 있는 기한은 임박이 아니다")
    void 기한_여유() {
        AcceptanceRegistry r = registryWith(entry(TODAY.plusDays(60)));
        assertTrue(!r.decide("https://a.example", fail("SAME"), TODAY).dueSoon());
    }

    @Test
    @DisplayName("수용해도 판정과 상태는 그대로 FAIL이다")
    void 수용은_판정을_바꾸지_않는다() {
        // 수용은 "조치를 유보"하는 것이지 "충족했다"가 아니다.
        AcceptanceRegistry r = registryWith(entry(LocalDate.of(2026, 12, 31)));
        Regression row = fail("SAME").withAcceptance(
                r.decide("https://a.example", fail("SAME"), TODAY));
        assertEquals("FAIL", row.current());
        assertEquals("SAME", row.verdict());
        assertTrue(row.accepted());
        assertTrue(!row.actionableFail());
    }
}
