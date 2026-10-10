package com.reportsys.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 敏感字段（数据源口令等）的对称加密：AES/GCM/NoPadding。
 *
 * 密钥来自配置 report.crypto.key（生产用环境变量注入）。未配置时降级为明文存储并打 WARN，
 * 以便本地开发不被卡住；历史明文数据（不带 enc: 前缀）仍可读。
 */
@Component
public class SecretCrypto {

    private static final Logger log = LoggerFactory.getLogger(SecretCrypto.class);

    public static final String PREFIX = "enc:";
    private static final String MASK = "******";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final boolean enabled;
    private final SecureRandom random = new SecureRandom();

    public SecretCrypto(@Value("${report.crypto.key:}") String rawKey) {
        String k = rawKey == null ? "" : rawKey.trim();
        this.enabled = k.length() >= 16;
        this.key = enabled ? new SecretKeySpec(sha256(k), "AES") : null;
    }

    @PostConstruct
    public void init() {
        if (enabled) {
            log.info("敏感字段加密已启用（AES/GCM）");
        } else {
            log.warn("report.crypto.key 未配置或长度不足 16 位，数据源口令将按【明文】存储，仅可用于本地开发！");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 加密；未启用或已是密文则原样返回 */
    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty() || !enabled || plain.startsWith(PREFIX)) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] packed = ByteBuffer.allocate(iv.length + cipherText.length).put(iv).put(cipherText).array();
            return PREFIX + Base64.getEncoder().encodeToString(packed);
        } catch (Exception e) {
            throw new IllegalStateException("敏感字段加密失败: " + e.getMessage(), e);
        }
    }

    /** 解密；非密文（历史明文）原样返回 */
    public String decrypt(String stored) {
        if (stored == null || stored.isEmpty() || !stored.startsWith(PREFIX)) {
            return stored;
        }
        if (!enabled) {
            throw new IllegalStateException("数据是加密存储的，但 report.crypto.key 未配置，无法解密");
        }
        try {
            byte[] packed = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            ByteBuffer bb = ByteBuffer.wrap(packed);
            byte[] iv = new byte[IV_LENGTH];
            bb.get(iv);
            byte[] cipherText = new byte[bb.remaining()];
            bb.get(cipherText);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("敏感字段解密失败: " + e.getMessage(), e);
        }
    }

    /** 接口回显用掩码 */
    public static String mask(String value) {
        return (value == null || value.isEmpty()) ? value : MASK;
    }

    public static boolean isMask(String value) {
        return MASK.equals(value);
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
