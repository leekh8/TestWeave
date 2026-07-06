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
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                String headerName = e.getKey();
                String rule = e.getValue();
                Optional<String> value = headers.firstValue(headerName);
                if (value.isEmpty()) {
                    outcomes.add(CheckOutcome.fail(rule, headerName + " 헤더 없음"));
                } else if (HSTS.equals(headerName) && !hstsEnabled(value.get())) {
                    // max-age=0 은 HSTS를 끄는 값 — 헤더가 있어도 실질 비활성
                    outcomes.add(CheckOutcome.fail(rule, "max-age=0 (HSTS 비활성): " + value.get()));
                } else {
                    outcomes.add(CheckOutcome.pass(rule));
                }
            }
        } catch (Exception ex) {
            outcomes.add(CheckOutcome.fail("HTTP 연결", target.getUrl() + " 요청 실패: " + ex.getMessage()));
        }
        return outcomes;
    }

    private static final String HSTS = "Strict-Transport-Security";
    private static final Pattern MAX_AGE = Pattern.compile("max-age\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    /** max-age 디렉티브가 있고 값이 0이 아니어야 HSTS가 실제로 켜진 것. */
    static boolean hstsEnabled(String value) {
        Matcher m = MAX_AGE.matcher(value);
        return m.find() && m.group(1).chars().anyMatch(c -> c != '0');  // parseLong 오버플로 회피
    }
}
