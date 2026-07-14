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
import java.util.Set;
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
                } else {
                    // 존재만으로 PASS 하지 않고 값의 강도까지 검증
                    String weakness = headerWeakness(headerName, value.get());
                    outcomes.add(weakness == null
                            ? CheckOutcome.pass(rule)
                            : CheckOutcome.fail(rule, weakness));
                }
            }
        } catch (Exception ex) {
            outcomes.add(CheckOutcome.fail("HTTP 연결", target.getUrl() + " 요청 실패: " + ex.getMessage()));
        }
        return outcomes;
    }

    private static final Pattern MAX_AGE = Pattern.compile("max-age\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final long MIN_HSTS_MAX_AGE = 31536000L; // 1년(초) — 권장 최소

    // 유효한 Referrer-Policy 토큰(소문자)
    private static final Set<String> REFERRER_TOKENS = Set.of(
            "no-referrer", "no-referrer-when-downgrade", "origin",
            "origin-when-cross-origin", "same-origin", "strict-origin",
            "strict-origin-when-cross-origin", "unsafe-url");

    /**
     * 헤더가 '존재하지만 약한' 경우의 사유를 반환(정상이면 null).
     * 단순 존재만으로 PASS 하면 CSP 'unsafe-inline', X-Frame-Options 'ALLOWALL',
     * HSTS 'max-age=1' 같은 무력한 설정이 통과한다.
     */
    static String headerWeakness(String headerName, String rawValue) {
        String v = rawValue == null ? "" : rawValue.trim();
        String lower = v.toLowerCase();
        switch (headerName) {
            case "Strict-Transport-Security":
                return hstsEnabled(v) ? null : "max-age 부족(1년 미만 또는 비활성): " + v;
            case "X-Frame-Options":
                return (lower.equals("deny") || lower.equals("sameorigin"))
                        ? null : "약한 값(DENY/SAMEORIGIN 필요): " + v;
            case "X-Content-Type-Options":
                return lower.equals("nosniff") ? null : "nosniff 아님: " + v;
            case "Content-Security-Policy":
                if (lower.contains("unsafe-inline")) return "unsafe-inline 포함: " + v;
                if (lower.contains("default-src *") || v.equals("*")) return "와일드카드 default-src: " + v;
                return null;
            case "Referrer-Policy":
                for (String tok : lower.split(",")) {
                    if (REFERRER_TOKENS.contains(tok.trim())) return null;
                }
                return "유효한 정책 토큰 없음: " + v;
            default:
                return null;
        }
    }

    /** HSTS가 실효하려면 max-age 디렉티브가 있고 권장 최소(1년) 이상이어야 한다. */
    static boolean hstsEnabled(String value) {
        Matcher m = MAX_AGE.matcher(value);
        if (!m.find()) {
            return false;
        }
        String digits = m.group(1);
        if (digits.length() > 18) {
            return true;  // 매우 큰 값 → parseLong 오버플로 회피 겸 확실히 충분
        }
        return Long.parseLong(digits) >= MIN_HSTS_MAX_AGE;
    }
}
