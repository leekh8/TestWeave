package com.testweave.check;

import com.testweave.domain.SecurityTarget;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 보안 응답 헤더 존재 여부를 점검한다. */
@Component
public class HeaderCheck implements SecurityCheck {

    // 필수 보안 헤더 → 사람이 읽는 규칙명
    private static final Map<String, String> REQUIRED = new LinkedHashMap<>();
    static {
        REQUIRED.put("Strict-Transport-Security", "HSTS 적용");
        REQUIRED.put("Content-Security-Policy", "CSP 적용");
        REQUIRED.put("X-Frame-Options", "클릭재킹 방어(X-Frame-Options)");
        REQUIRED.put("X-Content-Type-Options", "MIME 스니핑 방어(X-Content-Type-Options)");
        REQUIRED.put("Referrer-Policy", "Referrer-Policy 적용");
    }

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public String type() {
        return "HEADER";
    }

    @Override
    public List<CheckOutcome> run(SecurityTarget target) {
        List<CheckOutcome> outcomes = new ArrayList<>();
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(target.getUrl()))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<Void> res = client.send(req, HttpResponse.BodyHandlers.discarding());
            HttpHeaders headers = res.headers();
            for (Map.Entry<String, String> e : REQUIRED.entrySet()) {
                boolean present = headers.firstValue(e.getKey()).isPresent();
                outcomes.add(present
                        ? CheckOutcome.pass(e.getValue())
                        : CheckOutcome.fail(e.getValue(), e.getKey() + " 헤더 없음"));
            }
        } catch (Exception ex) {
            outcomes.add(CheckOutcome.fail("HTTP 연결", target.getUrl() + " 요청 실패: " + ex.getMessage()));
        }
        return outcomes;
    }
}
