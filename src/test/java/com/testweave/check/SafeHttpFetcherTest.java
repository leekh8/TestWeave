package com.testweave.check;

import org.junit.jupiter.api.Test;

import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 리다이렉트 기반 SSRF 방어 검증.
 * 모든 URL을 IP 리터럴로 써 DNS 조회(네트워크)를 피한다:
 * 93.184.216.34(공개) / 169.254.169.254(메타데이터, 링크로컬) / 127.0.0.1(루프백).
 */
class SafeHttpFetcherTest {

    private static final String PUBLIC = "http://93.184.216.34/";
    private static final String METADATA = "http://169.254.169.254/latest/meta-data/";
    private static final HttpHeaders EMPTY =
            HttpHeaders.of(Map.of(), (a, b) -> true);

    private static SafeHttpFetcher.Response redirect(String location) {
        return new SafeHttpFetcher.Response(302, EMPTY, Optional.of(location));
    }

    private static SafeHttpFetcher.Response ok(Map<String, List<String>> headers) {
        return new SafeHttpFetcher.Response(200, HttpHeaders.of(headers, (a, b) -> true), Optional.empty());
    }

    @Test
    void blocksRedirectToMetadataAddress() {
        // 공개 대상이 메타데이터 IP로 302 → 최초 대상은 공개라 통과하지만
        // 리다이렉트 목적지를 재검증해 차단해야 한다.
        SafeHttpFetcher fetcher = new SafeHttpFetcher(url -> redirect(METADATA));
        SsrfBlockedException ex = assertThrows(SsrfBlockedException.class,
                () -> fetcher.fetchHeaders(PUBLIC));
        assertTrue(ex.getMessage().contains("169.254.169.254"), ex.getMessage());
    }

    @Test
    void blocksRedirectToLoopback() {
        SafeHttpFetcher fetcher = new SafeHttpFetcher(url -> redirect("http://127.0.0.1:8080/admin"));
        assertThrows(SsrfBlockedException.class, () -> fetcher.fetchHeaders(PUBLIC));
    }

    @Test
    void returnsHeadersOnDirectOk() throws Exception {
        SafeHttpFetcher fetcher = new SafeHttpFetcher(
                url -> ok(Map.of("X-Test", List.of("v"))));
        HttpHeaders headers = fetcher.fetchHeaders(PUBLIC);
        assertEquals(Optional.of("v"), headers.firstValue("X-Test"));
    }

    @Test
    void followsRedirectToPublicThenReturns() throws Exception {
        // 첫 홉은 공개 IP로 302, 그 목적지에서 200 → 정상적으로 최종 헤더 반환.
        String second = "http://8.8.8.8/";
        SafeHttpFetcher fetcher = new SafeHttpFetcher(url ->
                url.equals(PUBLIC) ? redirect(second) : ok(Map.of("X-Final", List.of("ok"))));
        HttpHeaders headers = fetcher.fetchHeaders(PUBLIC);
        assertEquals(Optional.of("ok"), headers.firstValue("X-Final"));
    }

    @Test
    void failsOnRedirectLoopExceedingMax() {
        // 공개 IP 사이를 무한 리다이렉트 → 홉 상한 초과로 차단(무한 추적 방지).
        SafeHttpFetcher fetcher = new SafeHttpFetcher(url -> redirect(PUBLIC));
        SsrfBlockedException ex = assertThrows(SsrfBlockedException.class,
                () -> fetcher.fetchHeaders(PUBLIC));
        assertTrue(ex.getMessage().contains("리다이렉트"), ex.getMessage());
    }
}
