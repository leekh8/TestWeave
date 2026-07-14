package com.testweave.check;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * SSRF 방어 — 스캔 대상 URL이 내부/사설/링크로컬 주소로 해석되면 차단한다.
 *
 * 스캐너는 사용자가 준 임의 URL을 fetch하므로, 가드가 없으면 {@code http://127.0.0.1},
 * 사설망({@code 10/8·172.16/12·192.168/16}), 클라우드 메타데이터({@code 169.254.169.254})를
 * 찔러보게 만들 수 있다. 각 검사 모듈은 fetch 전에 {@link #blockReason(String)}로 검증한다.
 *
 * <p>주의(잔여 위험): HttpClient의 자동 리다이렉트는 공개 주소에서 내부로 튈 수 있다.
 * 이 가드는 '최초 대상'만 검증하므로, 리다이렉트 기반 SSRF는 후속 과제로 남는다.
 */
public final class SsrfGuard {

    private SsrfGuard() {
    }

    /** 차단 사유를 반환. 허용되면 null. */
    public static String blockReason(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return "잘못된 URL: " + url;
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            return "허용되지 않는 스킴: " + scheme;
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return "호스트 없음: " + url;
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            return "DNS 해석 실패: " + host;
        }
        // 한 호스트가 여러 주소로 해석될 수 있으므로 모두 검사(하나라도 내부면 차단)
        for (InetAddress addr : addresses) {
            if (isBlockedAddress(addr)) {
                return "내부/사설 대상 차단: " + host + " → " + addr.getHostAddress();
            }
        }
        return null;
    }

    /** 루프백·사설·링크로컬·와일드카드·멀티캐스트 주소를 내부로 간주해 차단. */
    static boolean isBlockedAddress(InetAddress addr) {
        return addr.isLoopbackAddress()      // 127.0.0.0/8, ::1
                || addr.isAnyLocalAddress()  // 0.0.0.0, ::
                || addr.isLinkLocalAddress() // 169.254.0.0/16(메타데이터 169.254.169.254 포함), fe80::
                || addr.isSiteLocalAddress() // 10/8, 172.16/12, 192.168/16, fec0::
                || addr.isMulticastAddress();
    }
}
