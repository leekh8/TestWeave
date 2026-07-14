package com.testweave.check;

import com.testweave.domain.SecurityTarget;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Set-Cookie 응답의 보안 플래그(Secure/HttpOnly/SameSite)를 점검한다. */
@Component
public class CookieCheck implements SecurityCheck {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public String type() {
        return "COOKIE";
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
            List<String> cookies = res.headers().allValues("Set-Cookie");
            if (cookies.isEmpty()) {
                outcomes.add(CheckOutcome.pass("쿠키 미설정(점검 불필요)"));
                return outcomes;
            }
            for (String cookie : cookies) {
                String name = cookie.split(";", 2)[0].split("=", 2)[0].trim();
                Set<String> attrs = attributesOf(cookie);
                boolean secure = attrs.contains("secure");
                outcomes.add(flag(name, "Secure", secure));
                outcomes.add(flag(name, "HttpOnly", attrs.contains("httponly")));
                outcomes.add(sameSiteOutcome(name, sameSiteValue(cookie), secure));
            }
        } catch (Exception ex) {
            outcomes.add(CheckOutcome.fail("HTTP 연결", target.getUrl() + " 요청 실패: " + ex.getMessage()));
        }
        return outcomes;
    }

    /**
     * Set-Cookie = "name=value; Attr1; Attr2=val" 의 속성 이름 집합(소문자)을 반환.
     * name=value(첫 세그먼트)는 제외한다. 부분문자열 매칭(cookie.contains("secure"))은
     * 값에 "secure"가 들어간 세션 쿠키를 Secure 있는 것으로 오판(false PASS)하므로 토큰 파싱.
     */
    static Set<String> attributesOf(String setCookie) {
        String[] parts = setCookie.split(";");
        Set<String> attrs = new HashSet<>();
        for (int i = 1; i < parts.length; i++) {
            String attr = parts[i].trim();
            if (attr.isEmpty()) {
                continue;
            }
            int eq = attr.indexOf('=');
            attrs.add((eq >= 0 ? attr.substring(0, eq) : attr).trim().toLowerCase());
        }
        return attrs;
    }

    private CheckOutcome flag(String cookie, String attr, boolean present) {
        String rule = "쿠키 " + cookie + " " + attr + " 플래그";
        return present ? CheckOutcome.pass(rule) : CheckOutcome.fail(rule, attr + " 누락");
    }

    /** SameSite 속성 값(소문자)을 반환. 없으면 null. */
    static String sameSiteValue(String setCookie) {
        String[] parts = setCookie.split(";");
        for (int i = 1; i < parts.length; i++) {
            String attr = parts[i].trim();
            int eq = attr.indexOf('=');
            if (eq > 0 && attr.substring(0, eq).trim().equalsIgnoreCase("samesite")) {
                return attr.substring(eq + 1).trim().toLowerCase();
            }
        }
        return null;
    }

    /**
     * SameSite 판정. 누락이면 FAIL. SameSite=None은 반드시 Secure와 함께여야 하며,
     * Secure 없는 None은 최신 브라우저가 거부하거나 CSRF에 취약하므로 FAIL.
     */
    static CheckOutcome sameSiteOutcome(String cookie, String sameSite, boolean secure) {
        String rule = "쿠키 " + cookie + " SameSite 플래그";
        if (sameSite == null) {
            return CheckOutcome.fail(rule, "SameSite 누락");
        }
        if (sameSite.equals("none") && !secure) {
            return CheckOutcome.fail(rule, "SameSite=None 인데 Secure 없음");
        }
        return CheckOutcome.pass(rule);
    }
}
