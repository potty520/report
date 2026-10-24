package com.reportsys.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 验证码存取：生成端与校验端共用同一份内存存储。
 *
 * 修复原实现的两个问题：
 * 1. AuthController 把验证码写进 HttpSession，而 SysUserService 从静态 Map 读，
 *    两边永不交汇，导致任何验证码（甚至不传）都能通过；
 * 2. 原校验逻辑在缓存未命中时直接放行，并留有 uuid.equals(captcha) 后门。
 *
 * report.captcha.fixed-code 仅供自动化测试使用（默认空 = 关闭）。
 */
@Component
public class CaptchaStore {

    private static final Logger log = LoggerFactory.getLogger(CaptchaStore.class);

    /** 验证码有效期 5 分钟 */
    private static final long TTL_MILLIS = 5 * 60 * 1000L;
    /** 防止内存无限增长 */
    private static final int MAX_ENTRIES = 10000;

    public enum Status {
        /** 校验通过 */
        OK,
        /** uuid/验证码缺失，或该 uuid 不存在（已使用/已过期） */
        MISSING,
        /** 已过期 */
        EXPIRED,
        /** 不匹配 */
        MISMATCH
    }

    private static final class Entry {
        private final String code;
        private final long expireAt;

        private Entry(String code, long expireAt) {
            this.code = code;
            this.expireAt = expireAt;
        }
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @Value("${report.captcha.fixed-code:}")
    private String fixedCode;

    @PostConstruct
    public void init() {
        if (fixedCode != null && !fixedCode.trim().isEmpty()) {
            log.warn("【注意】report.captcha.fixed-code 已启用（值: {}），验证码校验将被固定值绕过，仅可用于自动化测试！", fixedCode);
        }
    }

    /** 生成验证码时写入 */
    public void put(String uuid, String code) {
        if (uuid == null || uuid.isEmpty() || code == null) {
            return;
        }
        cleanup();
        store.put(uuid, new Entry(code.trim().toLowerCase(), System.currentTimeMillis() + TTL_MILLIS));
    }

    /**
     * 校验验证码。无论成功失败都会消费掉该 uuid（一次性），避免暴力重试。
     * 测试开关只放宽"码是否相等"，不放宽 uuid 必须存在与一次性语义。
     */
    public Status verify(String uuid, String input) {
        if (uuid == null || uuid.trim().isEmpty() || input == null || input.trim().isEmpty()) {
            return Status.MISSING;
        }
        Entry entry = store.remove(uuid);
        if (entry == null) {
            return Status.MISSING;
        }
        if (entry.expireAt < System.currentTimeMillis()) {
            return Status.EXPIRED;
        }
        if (entry.code.equals(input.trim().toLowerCase())) {
            return Status.OK;
        }
        if (fixedCode != null && !fixedCode.trim().isEmpty() && fixedCode.trim().equalsIgnoreCase(input.trim())) {
            return Status.OK;
        }
        return Status.MISMATCH;
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> e.getValue().expireAt < now);
        if (store.size() > MAX_ENTRIES) {
            store.clear();
            log.warn("验证码缓存超过 {} 条，已整体清空", MAX_ENTRIES);
        }
    }
}
