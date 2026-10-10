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

import javax.annotation.Resource;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
        if (sessionId != null && isKicked(sessionId)) {
            writeError(response, 401, "该会话已被管理员下线，请重新登录");
            return;
        }
        request.setAttribute("sessionId", sessionId);

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
