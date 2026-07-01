package com.testweave.check;

import com.testweave.domain.SecurityTarget;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLSession;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** HTTPS 사용 여부와 서버 인증서 만료 임박을 점검한다. */
@Component
public class TlsCheck implements SecurityCheck {

    private static final int WARN_DAYS = 14;

    @Override
    public String type() {
        return "TLS";
    }

    @Override
    public List<CheckOutcome> run(SecurityTarget target) {
        List<CheckOutcome> outcomes = new ArrayList<>();
        try {
            URI uri = URI.create(target.getUrl());
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                outcomes.add(CheckOutcome.fail("HTTPS 사용", "https 아님: " + uri.getScheme()));
                return outcomes;
            }
            int port = uri.getPort() == -1 ? 443 : uri.getPort();
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            try (SSLSocket socket = (SSLSocket) factory.createSocket(uri.getHost(), port)) {
                socket.setSoTimeout(10000);
                socket.startHandshake();
                SSLSession session = socket.getSession();
                outcomes.add(CheckOutcome.pass("TLS 핸드셰이크(" + session.getProtocol() + ")"));

                X509Certificate cert = (X509Certificate) session.getPeerCertificates()[0];
                long days = ChronoUnit.DAYS.between(LocalDate.now(),
                        cert.getNotAfter().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
                outcomes.add(days >= WARN_DAYS
                        ? CheckOutcome.pass("인증서 만료 여유(" + days + "일)")
                        : CheckOutcome.fail("인증서 만료 임박", days + "일 남음(임계 " + WARN_DAYS + "일)"));
            }
        } catch (Exception ex) {
            outcomes.add(CheckOutcome.fail("TLS 연결", target.getUrl() + " 실패: " + ex.getMessage()));
        }
        return outcomes;
    }
}
