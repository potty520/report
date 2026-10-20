package com.reportsys.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 客户端 IP 解析单元测试。
 *
 * 覆盖安全修复点：只有直连对端是受信代理时才采信 X-Forwarded-For / X-Real-IP，
 * 否则客户端伪造的头必须被忽略（原先可用来绕过登录限流）。
 */
class IpUtilsTest {

    @Test
    @DisplayName("公网直连时忽略伪造的 X-Forwarded-For")
    void ignoreXffFromPublicPeer() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("8.8.8.8");
        req.addHeader("X-Forwarded-For", "1.2.3.4");
        assertEquals("8.8.8.8", IpUtils.clientIp(req),
                "公网对端写入的 XFF 不可信，必须回落到 remoteAddr");
    }

    @Test
    @DisplayName("公网直连时忽略伪造的 X-Real-IP")
    void ignoreXRealIpFromPublicPeer() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("8.8.8.8");
        req.addHeader("X-Real-IP", "1.2.3.4");
        assertEquals("8.8.8.8", IpUtils.clientIp(req));
    }

    @Test
    @DisplayName("本地代理反代时采信 X-Real-IP")
    void trustXRealIpFromLoopbackProxy() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("127.0.0.1");
        req.addHeader("X-Real-IP", "203.0.113.9");
        assertEquals("203.0.113.9", IpUtils.clientIp(req));
    }

    @Test
    @DisplayName("本地代理反代时取 XFF 最左值（原始客户端）")
    void trustXffFirstValueFromLoopbackProxy() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("127.0.0.1");
        req.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1, 127.0.0.1");
        assertEquals("203.0.113.7", IpUtils.clientIp(req));
    }

    @Test
    @DisplayName("X-Real-IP 优先于 X-Forwarded-For")
    void xRealIpTakesPrecedence() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("127.0.0.1");
        req.addHeader("X-Real-IP", "203.0.113.9");
        req.addHeader("X-Forwarded-For", "203.0.113.7");
        assertEquals("203.0.113.9", IpUtils.clientIp(req));
    }

    @Test
    @DisplayName("内网代理（10./192.168./172.16-31）也视为受信")
    void trustPrivateProxy() {
        for (String proxy : new String[]{"10.1.2.3", "192.168.1.5", "172.16.0.9", "172.31.255.1"}) {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRemoteAddr(proxy);
            req.addHeader("X-Real-IP", "203.0.113.9");
            assertEquals("203.0.113.9", IpUtils.clientIp(req), "应信任内网代理: " + proxy);
        }
    }

    @Test
    @DisplayName("172.32.x 不属于内网段，不应被信任")
    void doNotTrustNonPrivate172() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("172.32.0.1");
        req.addHeader("X-Real-IP", "203.0.113.9");
        assertEquals("172.32.0.1", IpUtils.clientIp(req));
    }

    @Test
    @DisplayName("unknown 占位值应被忽略")
    void ignoreUnknownPlaceholder() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("127.0.0.1");
        req.addHeader("X-Real-IP", "unknown");
        req.addHeader("X-Forwarded-For", "unknown");
        assertEquals("127.0.0.1", IpUtils.clientIp(req));
    }

    @Test
    @DisplayName("IPv6 环回应归一化为 127.0.0.1")
    void normalizeIpv6Loopback() {
        assertEquals("127.0.0.1", IpUtils.normalize("::1"));
        assertEquals("127.0.0.1", IpUtils.normalize("0:0:0:0:0:0:0:1"));
    }

    @Test
    @DisplayName("超长 IP 值应被截断，防止日志表被撑爆")
    void truncateOverlongIp() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) sb.append('1');
        String result = IpUtils.normalize(sb.toString());
        assertTrue(result.length() <= 64, "截断后长度应 <= 64，实际 " + result.length());
    }

    @Test
    @DisplayName("null 请求返回空串而不抛异常")
    void nullRequestReturnsEmpty() {
        assertEquals("", IpUtils.clientIp(null));
        assertEquals("", IpUtils.normalize(null));
    }
}
