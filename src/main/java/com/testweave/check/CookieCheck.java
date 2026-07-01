package com.testweave.check;

import com.testweave.domain.SecurityTarget;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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
                String name = cookie.split("=", 2)[0].trim();
                String lower = cookie.toLowerCase();
                outcomes.add(flag(name, "Secure", lower.contains("secure")));
                outcomes.add(flag(name, "HttpOnly", lower.contains("httponly")));
                outcomes.add(flag(name, "SameSite", lower.contains("samesite")));
            }
        } catch (Exception ex) {
            outcomes.add(CheckOutcome.fail("HTTP 연결", target.getUrl() + " 요청 실패: " + ex.getMessage()));
        }
        return outcomes;
    }

    private CheckOutcome flag(String cookie, String attr, boolean present) {
        String rule = "쿠키 " + cookie + " " + attr + " 플래그";
        return present ? CheckOutcome.pass(rule) : CheckOutcome.fail(rule, attr + " 누락");
    }
}
