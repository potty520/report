package com.reportsys.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 密码策略单元测试。
 *
 * 覆盖：长度边界、字符类别、纯数字、弱口令黑名单、大小写绕过。
 */
class PasswordPolicyTest {

    @Test
    @DisplayName("空密码应被拒绝")
    void rejectNullAndEmpty() {
        assertNotNull(PasswordPolicy.check(null));
        assertNotNull(PasswordPolicy.check(""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456", "12345678", "password", "***REMOVED_DEFAULT_ADMIN_PASSWORD***", "qwerty", "a12345678"})
    @DisplayName("常见弱口令应被拒绝")
    void rejectWeakPasswords(String pwd) {
        assertNotNull(PasswordPolicy.check(pwd), "弱口令应被拒绝: " + pwd);
    }

    @Test
    @DisplayName("黑名单比对忽略大小写，Admin123 也属弱口令")
    void rejectWeakPasswordCaseInsensitive() {
        assertNotNull(PasswordPolicy.check("Admin123"));
        assertNotNull(PasswordPolicy.check("PASSWORD"));
    }

    @Test
    @DisplayName("长度不足 8 位应被拒绝")
    void rejectTooShort() {
        assertNotNull(PasswordPolicy.check("Ab1@"));
        assertNotNull(PasswordPolicy.check("Ab1@xyz"));
    }

    @Test
    @DisplayName("超长密码应被拒绝（防哈希拖慢）")
    void rejectTooLong() {
        String longPwd = "Aa1@" + repeat('x', 70);
        assertNotNull(PasswordPolicy.check(longPwd));
    }

    /** Java 8 无 String.repeat（Java 11+），这里自建 */
    private static String repeat(char c, int n) {
        char[] arr = new char[n];
        java.util.Arrays.fill(arr, c);
        return new String(arr);
    }

    @Test
    @DisplayName("纯数字应被拒绝")
    void rejectPureDigits() {
        assertNotNull(PasswordPolicy.check("12345678901"));
    }

    @Test
    @DisplayName("单一字符类别应被拒绝")
    void rejectSingleCategory() {
        assertNotNull(PasswordPolicy.check("abcdefghij"));  // 只有小写
        assertNotNull(PasswordPolicy.check("ABCDEFGHIJ"));  // 只有大写
    }

    @Test
    @DisplayName("合规密码应通过")
    void acceptStrongPasswords() {
        assertNull(PasswordPolicy.check("***REMOVED_ADMIN_PASSWORD***"));
        assertNull(PasswordPolicy.check("***REMOVED_USER_PASSWORD***"));
        assertNull(PasswordPolicy.check("abcd1234"));      // 小写+数字，两类
        assertNull(PasswordPolicy.check("Hello2026"));     // 大写+小写+数字
    }

    @Test
    @DisplayName("边界长度 8 位应通过")
    void acceptMinLength() {
        assertNull(PasswordPolicy.check("Aa123456"));
    }
}
