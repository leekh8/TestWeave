package com.testweave.check;

import com.testweave.domain.SecurityTarget;
import org.springframework.stereotype.Component;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLSession;
import java.net.InetSocketAddress;
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
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 10000;

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
            String host = uri.getHost();
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            // unconnected 소켓 → connect(timeout): createSocket(host,port)는 TCP connect에
            // 타임아웃이 없어 방화벽 drop 호스트에서 무한 대기한다.
            try (SSLSocket socket = (SSLSocket) factory.createSocket()) {
                socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
                socket.setSoTimeout(READ_TIMEOUT_MS);
                // 호스트네임(SAN/CN) 검증 활성화 — 기본 SSLSocket은 인증서 체인만 검증하고
                // 호스트 불일치(가장 흔한 TLS 오설정)를 통과시킨다. SNI도 명시.
                SSLParameters params = socket.getSSLParameters();
                params.setEndpointIdentificationAlgorithm("HTTPS");
                params.setServerNames(List.of(new SNIHostName(host)));
                socket.setSSLParameters(params);
                socket.startHandshake();  // 호스트 불일치/체인 오류면 여기서 예외
                SSLSession session = socket.getSession();

                // 협상된 프로토콜 버전을 검증한다. 핸드셰이크 성공만으로 PASS 처리하면
                // TLS 1.0/1.1로 협상된 취약 서버가 통과한다. 규칙명은 고정(버전은 detail).
                String protocol = session.getProtocol();
                outcomes.add(isSecureProtocol(protocol)
                        ? CheckOutcome.pass("TLS 프로토콜 버전", protocol)
                        : CheckOutcome.fail("TLS 프로토콜 버전",
                                "취약한 버전: " + protocol + " (TLS 1.2 이상 필요)"));

                X509Certificate cert = (X509Certificate) session.getPeerCertificates()[0];
                long days = ChronoUnit.DAYS.between(LocalDate.now(),
                        cert.getNotAfter().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
                // 규칙명을 "인증서 만료"로 고정한다. 예전처럼 남은 일수를 규칙명에 넣으면
                // 매일 규칙명이 바뀌어(…30일→29일) 베이스라인 키가 달라져 항상 NEW가 되고,
                // 임계 돌파 시 PASS→FAIL 회귀(REGRESSION)로 잡히지 않는다. 일수는 detail로.
                outcomes.add(days >= WARN_DAYS
                        ? CheckOutcome.pass("인증서 만료", days + "일 남음")
                        : CheckOutcome.fail("인증서 만료", days + "일 남음(임계 " + WARN_DAYS + "일)"));
            }
        } catch (Exception ex) {
            outcomes.add(CheckOutcome.fail("TLS 연결", target.getUrl() + " 실패: " + ex.getMessage()));
        }
        return outcomes;
    }

    /** TLS 1.2 이상만 안전으로 본다. JSSE 프로토콜 문자열 기준(SSLv3/TLSv1/TLSv1.1 거부). */
    static boolean isSecureProtocol(String protocol) {
        return "TLSv1.2".equals(protocol) || "TLSv1.3".equals(protocol);
    }
}
