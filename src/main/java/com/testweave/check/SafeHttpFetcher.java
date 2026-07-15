package com.testweave.check;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * SSRF-안전 HTTP fetch.
 *
 * <p>{@link HttpClient}의 자동 리다이렉트({@code Redirect.NORMAL})는 공개 대상이 302로
 * 내부 주소(예: 클라우드 메타데이터 {@code 169.254.169.254})로 튀어도 그대로 따라간다.
 * {@link SsrfGuard}는 '최초 대상'만 검증하므로 리다이렉트 기반 SSRF를 막지 못했다.
 * 이 fetcher는 리다이렉트를 끄고 수동으로 추적하되, <b>매 홉의 목적지를 fetch 전에
 * {@link SsrfGuard}로 다시 검증</b>한다.
 *
 * <p>잔여 위험: DNS 리바인딩(가드 검증 시점과 실제 연결 시점의 IP가 달라질 수 있음)은
 * {@code HttpClient}가 연결 IP 고정을 노출하지 않아 남는다 — 후속 과제.
 */
@Component
public class SafeHttpFetcher {

    static final int MAX_REDIRECTS = 5;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    /** 한 홉의 응답(상태·헤더·Location). HttpClient를 테스트에서 대체할 수 있게 하는 seam. */
    record Response(int status, HttpHeaders headers, Optional<String> location) {
    }

    interface Transport {
        Response send(String url) throws IOException, InterruptedException;
    }

    private final Transport transport;

    public SafeHttpFetcher() {
        this.transport = realTransport();
    }

    /** 테스트용 — 네트워크 없이 리다이렉트 추적·차단 로직을 검증하기 위한 주입 생성자. */
    SafeHttpFetcher(Transport transport) {
        this.transport = transport;
    }

    /** 리다이렉트를 SsrfGuard로 검증하며 따라간 최종 응답의 헤더. */
    public HttpHeaders fetchHeaders(String url)
            throws IOException, InterruptedException, SsrfBlockedException {
        String current = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            String block = SsrfGuard.blockReason(current);
            if (block != null) {
                throw new SsrfBlockedException(block);
            }
            Response res = transport.send(current);
            if (res.status() >= 300 && res.status() < 400 && res.location().isPresent()) {
                // Location은 상대 경로일 수 있으므로 현재 URI 기준으로 절대화 →
                // 다음 루프에서 SsrfGuard로 재검증한다.
                current = URI.create(current).resolve(res.location().get()).toString();
                continue;
            }
            return res.headers();
        }
        throw new SsrfBlockedException("리다이렉트 횟수 초과(" + MAX_REDIRECTS + ")");
    }

    private static Transport realTransport() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER) // 수동 추적 — 각 홉을 가드로 검증
                .build();
        return url -> {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<Void> res = client.send(req, HttpResponse.BodyHandlers.discarding());
            return new Response(res.statusCode(), res.headers(),
                    res.headers().firstValue("Location"));
        };
    }
}
