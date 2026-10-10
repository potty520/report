package com.reportsys.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 生成与解析。
 *
 * 安全加固：密钥不再硬编码在源码里，改为从配置 report.jwt.secret 读取
 * （生产用环境变量 REPORT_JWT_SECRET 注入）。长度不足 32 字节或使用历史泄漏密钥时，
 * 应用启动阶段直接失败，避免弱密钥/已泄漏密钥继续生效。
 */
@Component
public class JwtTokenUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenUtil.class);

    /** 历史版本里硬编码在公开仓库中的密钥，出现即拒绝启动 */
    private static final String LEAKED_DEFAULT = "ReportSystemJwtSecretKey2026ForHS256AlgorithmMinimum32Bytes!";

    private static final int MIN_SECRET_BYTES = 32;

    private final String secret;
    private final long expireMillis;

    public JwtTokenUtil(@Value("${report.jwt.secret:}") String secret,
                        @Value("${report.jwt.expire-hours:24}") long expireHours) {
        this.secret = secret == null ? "" : secret.trim();
        this.expireMillis = Math.max(1, expireHours) * 60 * 60 * 1000L;
    }

    @PostConstruct
    public void validate() {
        if (secret.isEmpty()) {
            throw new IllegalStateException("未配置 report.jwt.secret，拒绝启动（生产请用环境变量 REPORT_JWT_SECRET 注入至少 32 字节随机串）");
        }
        if (LEAKED_DEFAULT.equals(secret)) {
            throw new IllegalStateException("report.jwt.secret 仍是已泄漏在公开仓库中的旧密钥，请立即更换！");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("report.jwt.secret 长度不足 " + MIN_SECRET_BYTES + " 字节，存在被暴破风险");
        }
        log.info("JWT 密钥已从配置加载，token 有效期 {} 小时", expireMillis / 3600000);
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expireMillis))
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        if (claims == null) return null;
        Object userId = claims.get("userId");
        if (userId instanceof Number) return ((Number) userId).longValue();
        return userId == null ? null : Long.valueOf(userId.toString());
    }

    public boolean isTokenExpired(String token) {
        Claims claims = parseToken(token);
        return claims == null || claims.getExpiration().before(new Date());
    }
}
