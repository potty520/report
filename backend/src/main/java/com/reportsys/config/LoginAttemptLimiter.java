package com.reportsys.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败限流：按 "用户名|IP" 维度计数，连续失败达到阈值后锁定一段时间。
 * 纯内存实现（单实例部署够用）；多实例部署时应换成 Redis 计数。
 */
@Component
public class LoginAttemptLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptLimiter.class);

    /** 连续失败次数阈值 */
    private static final int MAX_FAILURES = 5;
    /** 统计窗口 */
    private static final long WINDOW_MILLIS = 10 * 60 * 1000L;
    /** 锁定时长 */
    private static final long LOCK_MILLIS = 5 * 60 * 1000L;

    private static final class Attempt {
        private int failures;
        private long firstFailureAt;
        private long lockedUntil;
    }

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    private String key(String username, String ip) {
        return (username == null ? "" : username.trim().toLowerCase()) + "|" + (ip == null ? "" : ip);
    }

    /** 返回非 null 表示当前被锁定，值为剩余秒数提示 */
    public String blockedMessage(String username, String ip) {
        Attempt attempt = attempts.get(key(username, ip));
        if (attempt == null) return null;
        long now = System.currentTimeMillis();
        if (attempt.lockedUntil > now) {
            return "登录失败次数过多，请 " + ((attempt.lockedUntil - now) / 1000 + 1) + " 秒后重试";
        }
        if (attempt.firstFailureAt > 0 && now - attempt.firstFailureAt > WINDOW_MILLIS) {
            attempts.remove(key(username, ip));
        }
        return null;
    }

    public void onFailure(String username, String ip) {
        String k = key(username, ip);
        Attempt attempt = attempts.computeIfAbsent(k, x -> new Attempt());
        long now = System.currentTimeMillis();
        synchronized (attempt) {
            if (attempt.firstFailureAt == 0 || now - attempt.firstFailureAt > WINDOW_MILLIS) {
                attempt.firstFailureAt = now;
                attempt.failures = 0;
            }
            attempt.failures++;
            if (attempt.failures >= MAX_FAILURES) {
                attempt.lockedUntil = now + LOCK_MILLIS;
                log.warn("账号 {}（IP {}）连续登录失败 {} 次，已锁定 {} 秒", username, ip, attempt.failures, LOCK_MILLIS / 1000);
            }
        }
    }

    public void onSuccess(String username, String ip) {
        attempts.remove(key(username, ip));
    }
}
