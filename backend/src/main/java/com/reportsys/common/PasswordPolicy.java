package com.reportsys.common;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 密码强度策略。
 *
 * 缺陷修复(U10)：原来新建用户/改密对密码强度毫无约束，"123456" 可注册成功，属等保测评扣分项。
 * 这里集中一个策略，供「新建用户」与「修改密码」两条入口共用，避免各写一套导致口径漂移。
 */
public final class PasswordPolicy {

    /** 最短长度 */
    public static final int MIN_LENGTH = 8;
    /** 最长长度，防止 bcrypt 输入过长被截断 / 恶意超长串拖慢哈希 */
    public static final int MAX_LENGTH = 64;

    /**
     * 常见弱口令黑名单（覆盖评测里出现的 123456 以及排名前若干的高频口令）。
     * 真实项目建议改为读取可配置字典或接入统一身份平台策略。
     */
    private static final Set<String> WEAK_PASSWORDS = new HashSet<>(Arrays.asList(
            "123456", "1234567", "12345678", "123456789", "1234567890",
            "111111", "11111111", "000000", "00000000", "888888", "88888888",
            "666666", "999999", "123123", "123321", "654321", "abcdef",
            "abc123", "abc123456", "a123456", "a12345678", "qwerty", "qwerty123",
            "qwertyuiop", "password", "password1", "password123", "passw0rd",
            "admin", "***REMOVED_DEFAULT_ADMIN_PASSWORD***", "admin888", "root", "root123", "system",
            "iloveyou", "welcome", "letmein", "monkey", "dragon", "master",
            "superman", "sunshine", "princess", "football", "baseball"
    ));

    private PasswordPolicy() {
    }

    /**
     * 校验密码强度。
     *
     * @param password 待校验密码
     * @return null 表示通过；否则返回可直接展示给用户的失败原因
     */
    public static String check(String password) {
        if (password == null || password.isEmpty()) {
            return "密码不能为空";
        }
        if (password.length() < MIN_LENGTH) {
            return "密码长度不能少于" + MIN_LENGTH + "位";
        }
        if (password.length() > MAX_LENGTH) {
            return "密码长度不能超过" + MAX_LENGTH + "位";
        }
        // 黑名单比对忽略大小写，避免 "Admin123" 之类绕过
        if (WEAK_PASSWORDS.contains(password.toLowerCase())) {
            return "密码过于简单，请勿使用常见弱口令";
        }
        int categories = 0;
        if (password.chars().anyMatch(Character::isUpperCase)) categories++;
        if (password.chars().anyMatch(Character::isLowerCase)) categories++;
        if (password.chars().anyMatch(Character::isDigit)) categories++;
        if (password.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) categories++;
        if (categories < 2) {
            return "密码需包含大写字母、小写字母、数字、符号中的至少两类";
        }
        // 纯数字直接拒绝（如 13800138000、20240101 这类弱口令）
        if (password.chars().allMatch(Character::isDigit)) {
            return "密码不能为纯数字";
        }
        return null;
    }
}
