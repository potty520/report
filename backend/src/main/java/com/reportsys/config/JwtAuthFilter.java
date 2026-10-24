package com.reportsys.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportsys.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JWT 认证过滤器。
 *
 * 安全加固点：
 * 1. 认证成功后按用户真实角色装载权限（ROLE_xxx），此前 authorities 恒为空，
 *    导致任何登录用户都能调用全部接口；
 * 2. 鉴权失败返回真实 HTTP 401（此前一律 HTTP 200 + body code=401）；
 * 3. 白名单收紧为登录/验证码接口（不再放行 /actuator、/h2-console 等）。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    /** 角色缓存 30 秒，避免每个请求都查库 */
    private static final long ROLE_CACHE_MILLIS = 30 * 1000L;

    /** 在线心跳写入节流：同一会话 60 秒最多写一次 */
    private static final long HEARTBEAT_MILLIS = 60 * 1000L;

    /** 会话 → 上次心跳时间 */
    private final Map<String, Long> heartbeatAt = new ConcurrentHashMap<>();

    private static final class CachedRoles {
        private final List<GrantedAuthority> authorities;
        private final long loadedAt;

        private CachedRoles(List<GrantedAuthority> authorities, long loadedAt) {
            this.authorities = authorities;
            this.loadedAt = loadedAt;
        }
    }

    @Resource
    private JwtTokenUtil jwtTokenUtil;

    @Resource
    private JdbcTemplate jdbcTemplate;

    private final Map<Long, CachedRoles> roleCache = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestURI = request.getRequestURI();

        // 白名单：登录与验证码；跨域预检直接放行
        if (requestURI.contains("/auth/login") || requestURI.contains("/auth/captcha")
                || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = request.getHeader("Authorization");
        if (!StringUtils.hasText(token)) {
            writeError(response, 401, "未登录，请先登录");
            return;
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        if (jwtTokenUtil.isTokenExpired(token)) {
            writeError(response, 401, "Token已过期，请重新登录");
            return;
        }

        Long userId = jwtTokenUtil.getUserId(token);
        if (userId == null) {
            writeError(response, 401, "Token无效");
            return;
        }

        // 在线用户/踢下线：按 jti 检查会话是否已被管理员下线（表未迁移时 isKicked 恒为 false）
        String sessionId = jwtTokenUtil.getSessionId(token);
        // 会话白名单（第二轮安全修复）：token 里的 sessionId 必须对应"在线"会话记录，
        // 否则拒绝——这样登出、被踢、会话被清理后 token 立即失效，
        // 修复"logout 后 JWT 仍可继续调用接口"的问题。
        if (sessionId == null || sessionId.trim().isEmpty()) {
            writeError(response, 401, "会话信息缺失，请重新登录");
            return;
        }
        String sessionState = sessionState(sessionId);
        if ("OFFLINE".equals(sessionState)) {
            writeError(response, 401, "该会话已下线（登出或被管理员踢出），请重新登录");
            return;
        }
        if ("MISSING".equals(sessionState)) {
            writeError(response, 401, "会话已失效，请重新登录");
            return;
        }
        request.setAttribute("sessionId", sessionId);

        // 心跳：刷新在线会话的活跃时间，供"在线用户"判断真实在线（30 分钟无活动视为离线）。
        // 按会话 60 秒节流，避免每个请求都写库。
        if (sessionId != null) {
            long now = System.currentTimeMillis();
            Long last = heartbeatAt.get(sessionId);
            if (last == null || now - last > HEARTBEAT_MILLIS) {
                heartbeatAt.put(sessionId, now);
                try {
                    jdbcTemplate.update(
                            "UPDATE rpt_online_user SET last_active_time = NOW() WHERE session_id = ?", sessionId);
                } catch (Exception e) {
                    // 表未迁移时忽略，不影响认证
                }
            }
        }

        request.setAttribute("userId", userId);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, loadAuthorities(userId));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    /** 从库中装载角色（ROLE_<roleCode>），供 @PreAuthorize 使用 */
    private List<GrantedAuthority> loadAuthorities(Long userId) {
        CachedRoles cached = roleCache.get(userId);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.loadedAt < ROLE_CACHE_MILLIS) {
            return cached.authorities;
        }
        List<GrantedAuthority> authorities = new ArrayList<>();
        try {
            List<String> roleCodes = jdbcTemplate.queryForList(
                    "SELECT r.role_code FROM sys_role r JOIN sys_user_role ur ON ur.role_id = r.id "
                            + "WHERE ur.user_id = ? AND r.status = 1", String.class, userId);
            for (String code : roleCodes) {
                if (code != null && !code.trim().isEmpty()) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + code.trim()));
                }
            }
        } catch (Exception e) {
            log.warn("加载用户 {} 的角色失败，按无角色处理: {}", userId, e.getMessage());
        }
        authorities = Collections.unmodifiableList(authorities);
        roleCache.put(userId, new CachedRoles(authorities, now));
        return authorities;
    }

    /** 会话状态：ONLINE / OFFLINE / MISSING（查不到即 MISSING，用于白名单校验） */
    private String sessionState(String sessionId) {
        try {
            List<Integer> status = jdbcTemplate.queryForList(
                    "SELECT status FROM rpt_online_user WHERE session_id = ? ORDER BY id DESC LIMIT 1",
                    Integer.class, sessionId);
            if (status.isEmpty() || status.get(0) == null) {
                return "MISSING";
            }
            return status.get(0) == 1 ? "ONLINE" : "OFFLINE";
        } catch (Exception e) {
            // 在线表不可用时按在线处理，避免整体不可用
            return "ONLINE";
        }
    }

    /** 会话是否已被踢下线；表不存在/无记录时放行（兼容历史 token） */
    private boolean isKicked(String sessionId) {
        try {
            List<Integer> status = jdbcTemplate.queryForList(
                    "SELECT status FROM rpt_online_user WHERE session_id = ? ORDER BY id DESC LIMIT 1",
                    Integer.class, sessionId);
            return !status.isEmpty() && status.get(0) != null && status.get(0) == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void writeError(HttpServletResponse response, int code, String msg) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        // 真实 HTTP 状态码（401），不再一律返回 200
        response.setStatus(code);
        response.getWriter().write(new ObjectMapper().writeValueAsString(Result.error(code, msg)));
    }
}
