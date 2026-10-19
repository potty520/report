package com.reportsys.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    private int code;
    private String msg;
    private T data;

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data);
    }

    public static <T> Result<T> success(String msg, T data) {
        return new Result<>(200, msg, data);
    }

    public static <T> Result<T> success() {
        return new Result<>(200, "操作成功", null);
    }

    /**
     * 业务错误。
     *
     * 缺陷修复(U06)：这里统一做脱敏，而不是要求每个 Controller 自己判断——
     * 项目里 119 处都写成 {@code catch (RuntimeException e) { return Result.error(e.getMessage()); }}，
     * 逐个改既容易漏、也会持续回流。收口在构造处，新代码天然安全。
     */
    public static <T> Result<T> error(String msg) {
        return new Result<>(500, sanitizeMessage(msg), null);
    }

    public static <T> Result<T> error(int code, String msg) {
        // 403/401 这类是业务语义明确的授权提示，保持原样；其余按脱敏处理
        return new Result<>(code, (code == 403 || code == 401) ? msg : sanitizeMessage(msg), null);
    }

    /**
     * 对异常消息做脱敏，用于统一返回给前端。
     *
     * 缺陷修复(U06)：评测发现缺必填字段时接口直接返回了
     * "### Error updating database. Cause: java.sql.SQLException: Field 'username' doesn't have a default value"，
     * 把表结构/字段名/驱动信息泄露给调用方。这里把明显的底层异常特征替换为统一提示。
     *
     * @param msg 原始异常消息
     * @return 可安全返回的消息
     */
    public static String sanitizeMessage(String msg) {
        if (msg == null || msg.trim().isEmpty()) {
            return "操作失败，请稍后重试";
        }
        String lower = msg.toLowerCase();
        // 数据库/ORM 底层异常特征：不要回显原文
        boolean leaky = lower.contains("sqlexception")
                || lower.contains("error updating database")
                || lower.contains("error querying database")
                || lower.contains("sqlstate")
                || lower.contains("syntax error")
                || lower.contains("doesn't have a default value")
                || lower.contains("nested exception")
                || lower.contains("org.springframework.dao")
                || lower.contains("java.lang.")
                || lower.contains("mybatis")
                || lower.contains("ora-")
                || lower.contains("pgjdbc")
                || lower.contains("mysql");
        if (leaky) {
            return "操作失败：数据校验不通过或数据库约束冲突，请检查必填项";
        }
        return msg;
    }

    public static <T> Result<T> unauthorized(String msg) {
        return new Result<>(401, msg, null);
    }

    public static <T> Result<T> forbidden(String msg) {
        return new Result<>(403, msg, null);
    }
}
