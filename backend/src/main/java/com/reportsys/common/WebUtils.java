package com.reportsys.common;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Web 层通用工具。
 *
 * 背景：项目 target 为 JDK 1.8，而 {@code URLEncoder.encode(String, Charset)} 是 Java 10+ 才有的重载。
 * 直接用 Charset 版本在 1.8 下编译失败；用 String 版本又会抛受检异常 {@link UnsupportedEncodingException}，
 * 到处写 try/catch 噪音很大。这里统一收敛成一个不抛受检异常的方法。
 */
public final class WebUtils {

    private WebUtils() {
    }

    /**
     * 按 UTF-8 对文件名做 URL 编码（用于 Content-Disposition 的 filename*=UTF-8''xxx）。
     *
     * @param raw 原始文件名，可为 null
     * @return 编码后的文件名；UTF-8 必定被 JVM 支持，此方法不会真正抛异常
     */
    public static String encodeFileName(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        try {
            return URLEncoder.encode(raw, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // JVM 规范要求必须支持 UTF-8，正常不会走到这里；兜底返回原值，至少不丢文件名
            return raw;
        }
    }
}
