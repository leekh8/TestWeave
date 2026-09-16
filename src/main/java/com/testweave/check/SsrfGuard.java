package com.testweave.check;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * SSRF 방어 — 스캔 대상 URL이 내부/사설/링크로컬 주소로 해석되면 차단한다.
 *
 * 스캐너는 사용자가 준 임의 URL을 fetch하므로, 가드가 없으면 {@code http://127.0.0.1},
 * 사설망({@code 10/8·172.16/12·192.168/16}), 클라우드 메타데이터({@code 169.254.169.254})를
 * 찔러보게 만들 수 있다. HTTP 검사 모듈은 {@link SafeHttpFetcher}를 통해 fetch하며,
 * 이 fetcher가 최초 대상과 모든 리다이렉트 홉을 {@link #blockReason(String)}로 검증한다.
 *
 * <p>잔여 위험: DNS 리바인딩(검증 시점과 연결 시점의 IP 불일치)은 남는다 — {@link SafeHttpFetcher} 참고.
 */
public final class SsrfGuard {

    private SsrfGuard() {
    }

    /**
     * 사설 대역을 허용한 상태로 차단 사유를 반환. 허용되면 null.
     *
     * <p>사내 자산을 점검하려면 사설 대역을 열어야 하는데, 여는 순간 이 스캐너는
     * 내부망에 요청을 대신 쏴 주는 프록시가 된다. 그래서 두 가지는 열어도 남긴다.
     * <ul>
     *   <li>링크로컬(169.254/16)은 <b>항상</b> 막는다. 클라우드 메타데이터 엔드포인트가
     *       여기 있고, 그건 내부 자산 점검과 아무 상관이 없으면서 자격증명을 흘린다.</li>
     *   <li>와일드카드와 멀티캐스트도 항상 막는다. 점검 대상이 될 수 없는 주소다.</li>
     * </ul>
     * 완화는 공개 CI에서 켜면 안 된다. 자기 자산을 아는 사람이 손으로 켜는 스위치다.
     */
    public static String blockReason(String url, boolean allowPrivate) {
        return blockReasonInternal(url, allowPrivate);
    }

    /** 차단 사유를 반환. 허용되면 null. 기본은 사설 대역 전면 차단이다. */
    public static String blockReason(String url) {
        return blockReasonInternal(url, false);
    }

    private static String blockReasonInternal(String url, boolean allowPrivate) {
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
            if (isBlockedAddress(addr, allowPrivate)) {
                return "내부/사설 대상 차단: " + host + " → " + addr.getHostAddress();
            }
        }
        return null;
    }

    /** 루프백, 사설, 링크로컬, 와일드카드, 멀티캐스트 주소를 내부로 간주해 차단. */
    static boolean isBlockedAddress(InetAddress addr) {
        return isBlockedAddress(addr, false);
    }

    static boolean isBlockedAddress(InetAddress addr, boolean allowPrivate) {
        // 완화해도 뚫리지 않는 것들. 링크로컬에 클라우드 메타데이터(169.254.169.254)가 있다.
        if (addr.isAnyLocalAddress()          // 0.0.0.0, ::
                || addr.isLinkLocalAddress()  // 169.254.0.0/16, fe80::
                || addr.isMulticastAddress()) {
            return true;
        }
        if (allowPrivate) {
            return false;
        }
        return addr.isLoopbackAddress()       // 127.0.0.0/8, ::1
                || addr.isSiteLocalAddress(); // 10/8, 172.16/12, 192.168/16, fec0::
    }
}
