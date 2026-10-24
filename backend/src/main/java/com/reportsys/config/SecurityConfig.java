package com.reportsys.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportsys.common.Result;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

/**
 * 安全配置（Spring Security 6 / Spring Boot 3 写法）。
 *
 * 安全加固点：
 * 1. 开启方法级鉴权（@PreAuthorize），此前全系统只有"是否登录"一道门；
 * 2. 白名单收紧为登录/验证码，移除 /actuator、/h2-console 等无必要的放行；
 * 3. 未认证返回 HTTP 401、无权限返回 HTTP 403（此前一律 HTTP 200 + body code）。
 *
 * 迁移说明：Spring Security 6 移除了 WebSecurityConfigurerAdapter 与
 * authorizeRequests()/antMatchers() 旧 DSL，这里改为 lambda 风格的
 * authorizeHttpRequests() + requestMatchers()；方法级注解由
 * @EnableGlobalMethodSecurity 改为 @EnableMethodSecurity。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Resource
    private JwtAuthFilter jwtAuthFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> { })
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/auth/login", "/auth/captcha").permitAll()
                    .anyRequest().authenticated())
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(new HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED))
                    .accessDeniedHandler(accessDeniedHandler()))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> writeJson(response, 403, "无权限执行该操作");
    }

    private void writeJson(HttpServletResponse response, int status, String msg) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(new ObjectMapper().writeValueAsString(Result.error(status, msg)));
    }
}
