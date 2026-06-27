package com.reportsys.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportsys.common.Result;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
import java.util.Collections;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Resource
    private JwtTokenUtil jwtTokenUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader("Authorization");
        String requestURI = request.getRequestURI();

        // 放行登录相关接口
        if (requestURI.contains("/auth/login") || requestURI.contains("/auth/captcha") ||
                requestURI.contains("/h2-console") ||
                requestURI.contains("/swagger") || requestURI.contains("/v2/api-docs") ||
                requestURI.contains("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 放行 OPTIONS 请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

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

        // 将用户信息存入 request attribute
        request.setAttribute("userId", userId);
        // 设置 Spring Security 认证上下文（否则 anyRequest().authenticated() 会返回 403）
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int code, String msg) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(200);
        response.getWriter().write(new ObjectMapper().writeValueAsString(Result.error(code, msg)));
    }
}
