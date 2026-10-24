package com.reportsys.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 解析。
 *
 * 安全修复（审计：登录限流可绕过）：原代码在 3 处各自实现
 * {@code X-Forwarded-For 取最左值} 作为客户端 IP，导致：
 * <ul>
 *   <li>攻击者换一个 XFF 头就能绕过登录失败锁定，无限撞库；</li>
 *   <li>也可以伪造 XFF 反向把他人账号锁死；</li>
 *   <li>登录日志/操作日志里的 IP 全部不可信，审计证据链失效。</li>
 * </ul>
 *
 * 修复策略：<b>只有当直连对端是本地/内网代理时才信任转发的头</b>。
 * 生产部署下 nginx 在 127.0.0.1 上反代，此时 remoteAddr 是环回/内网地址，
 * 我们采信 nginx 写入的 X-Real-IP（优先）与 X-Forwarded-For 最左值；
 * 若直连对端是公网地址（说明未经代理或代理配置不当），
 * 一律只认 {@code getRemoteAddr()}，杜绝客户端伪造。
 */
public final class IpUtils {

    private IpUtils() {
    }

    /**
     * 取客户端 IP。
     *
     * @param request 当前请求
     * @return 归一化后的 IP，取不到时返回空串
     */
    public static String clientIp(HttpServletRequest request) {
        if (request == null) return "";
        String remote = request.getRemoteAddr();
        // 只有来自受信代理（本地/内网）时才采信转发头
        if (isTrustedProxy(remote)) {
            String real = request.getHeader("X-Real-IP");
            if (isUsable(real)) return normalize(real.trim());
            String xff = request.getHeader("X-Forwarded-For");
            if (isUsable(xff)) {
                // XFF 可能是 "client, proxy1, proxy2"，最左为原始客户端
                String first = xff.split(",")[0].trim();
                if (isUsable(first)) return normalize(first);
            }
        }
        return normalize(remote);
    }

    private static boolean isUsable(String value) {
        return value != null && !value.isEmpty() && !"unknown".equalsIgnoreCase(value);
    }

    /** 是否来自受信代理（环回或内网地址） */
    static boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        if ("127.0.0.1".equals(ip) || "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) return true;
        if (ip.startsWith("192.168.") || ip.startsWith("10.")) return true;
        if (ip.startsWith("172.")) {
            try {
                int second = Integer.parseInt(ip.split("\\.")[1]);
                return second >= 16 && second <= 31;
            } catch (Exception ignored) {
                return false;
            }
        }
        return false;
    }

    /** 归一化：IPv6 环回写成 127.0.0.1，并截断超长值防止日志表被撑爆 */
    public static String normalize(String ip) {
        if (ip == null) return "";
        String s = ip.trim();
        if ("0:0:0:0:0:0:0:1".equals(s) || "::1".equals(s)) return "127.0.0.1";
        return s.length() > 64 ? s.substring(0, 64) : s;
    }
}
