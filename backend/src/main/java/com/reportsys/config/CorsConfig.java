package com.reportsys.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * CORS 配置。
 *
 * 安全修复（审计 S1）：原实现为
 * <pre>
 *   allowedOriginPatterns("*") + allowCredentials(true)
 * </pre>
 * 这意味着<b>任意站点</b>都可以携带受害者凭证（Cookie/Authorization）跨域读写全部接口，
 * 是审计报告中的严重问题（S1）。实测已确认：带 {@code Origin: https://evil.example.com}
 * 的预检请求会原样回显该 Origin 并返回 {@code Access-Control-Allow-Credentials: true}。
 *
 * 修复策略：
 *   1. 默认<b>不开启跨域</b>（allowCredentials 需要显式配白名单），生产环境前后端同源（nginx 反代 /api），
 *      本就不需要 CORS；
 *   2. 仅在配置了 {@code report.cors.allowed-origins} 时才注册具体来源，且<b>禁止通配符</b>；
 *   3. 通配符 {@code *} 与 {@code allowCredentials(true)} 永不共存（浏览器规范也不允许）。
 *
 * 生产若确有跨域需求，通过环境变量/配置注入精确来源，例如：
 * <pre>
 * report:
 *   cors:
 *     allowed-origins: "https://report.example.com,https://admin.example.com"
 * </pre>
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    /**
     * 允许跨域的来源列表（逗号分隔）。留空 = 不开启跨域。
     */
    @Value("${report.cors.allowed-origins:}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = parseOrigins(allowedOrigins);
        if (origins.isEmpty()) {
            // 不注册任何 CORS 映射：同源部署下不需要，跨域请求会被浏览器拦截
            log.info("CORS 未启用（report.cors.allowed-origins 为空）。如需跨域请显式配置具体来源。");
            return;
        }
        // 安全兜底：拒绝把通配符写进配置
        for (String origin : origins) {
            if ("*".equals(origin) || origin.contains("*")) {
                throw new IllegalStateException(
                        "report.cors.allowed-origins 不允许使用通配符 '*'，请配置精确来源，当前值: " + allowedOrigins);
            }
        }
        log.info("CORS 已启用，允许来源: {}", origins);
        registry.addMapping("/**")
                .allowedOrigins(origins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    private List<String> parseOrigins(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return out;
        }
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(out::add);
        return out;
    }
}
