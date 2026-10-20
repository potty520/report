package com.reportsys.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据范围 SQL 校验单元测试（安全修复核心）。
 *
 * 背景：原实现是带尾随空格的字符串黑名单，实测可绕过，
 * 且完全允许裸查系统表，可经数据源读到 sys_user 的密码哈希。
 *
 * 这里只测 assertSelectOnly（纯校验逻辑，不依赖 Spring 容器与数据库）。
 */
class DataRangeServiceSqlGuardTest {

    private final DataRangeService service = new DataRangeService();

    /** 断言被拒绝 */
    private void assertRejected(String sql, String why) {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.assertSelectOnly(sql), "应拒绝：" + why + " → " + sql);
        assertNotNull(ex.getMessage());
    }

    /** 断言通过 */
    private void assertAccepted(String sql) {
        assertDoesNotThrow(() -> service.assertSelectOnly(sql), "应放行 → " + sql);
    }

    // ==================== 写操作 ====================

    @ParameterizedTest
    @ValueSource(strings = {
            "delete from sys_user",
            "DELETE FROM sys_user",
            "  delete from t where 1=1",
            "update t set a=1",
            "insert into t values(1)",
            "drop table t",
            "truncate table t",
            "alter table t add c int",
            "create table t (id int)",
            "grant all on *.* to x",
            "revoke select on t from x",
    })
    @DisplayName("写操作与非 SELECT 语句必须被拒绝")
    void rejectWriteStatements(String sql) {
        assertRejected(sql, "写操作");
    }

    @Test
    @DisplayName("注释内隐藏的写操作也要被识别（原黑名单绕过点）")
    void rejectKeywordHiddenInComment() {
        // 剥离注释后变成 "delete from t"，必须拒绝
        assertRejected("delete/**/from t", "块注释拼接");
        assertRejected("delete -- x\n from t", "行注释拼接");
        assertRejected("delete # x\n from t", "井号注释拼接");
    }

    @Test
    @DisplayName("关键字与注释混合但实际是写操作，仍须拒绝")
    void rejectMixedCaseWithComments() {
        assertRejected("DeLeTe/*c*/FROM t", "大小写+注释");
    }

    // ==================== UNION ====================

    @ParameterizedTest
    @ValueSource(strings = {
            "select 1 union select 2",
            "select a from t union all select b from u",
            "SELECT * FROM t UNION SELECT * FROM sys_user",
    })
    @DisplayName("UNION 必须被拒绝（可拼接任意结果集）")
    void rejectUnion(String sql) {
        assertRejected(sql, "UNION");
    }

    // ==================== 敏感表 ====================

    @ParameterizedTest
    @ValueSource(strings = {
            "select username, password from sys_user",
            "select username,password from sys_user LIMIT 3",
            "SELECT * FROM SYS_USER",
            "select * from sys_role",
            "select * from sys_menu",
            "select * from sys_user_role",
            "select * from sys_role_menu",
            "select * from rpt_online_user",
            "select * from sys_login_log",
    })
    @DisplayName("系统账号/权限表必须被拒绝（阻断读密码哈希链路）")
    void rejectSensitiveTables(String sql) {
        assertRejected(sql, "敏感表");
    }

    // ==================== 元数据库 ====================

    @ParameterizedTest
    @ValueSource(strings = {
            "select table_name from information_schema.tables",
            "select * from mysql.user",
            "select * from performance_schema.threads",
            "select * from sys.session",
            "select * from pg_catalog.pg_user",
    })
    @DisplayName("系统库/元数据探测必须被拒绝")
    void rejectSystemSchemas(String sql) {
        assertRejected(sql, "元数据库");
    }

    // ==================== 危险函数 ====================

    @ParameterizedTest
    @ValueSource(strings = {
            "select load_file('/etc/passwd')",
            "select 1 into outfile '/tmp/x'",
            "select 1 into dumpfile '/tmp/x'",
            "select sleep(10)",
            "select benchmark(1000000, md5('x'))",
    })
    @DisplayName("文件读写与耗时函数必须被拒绝")
    void rejectDangerousFunctions(String sql) {
        assertRejected(sql, "危险函数");
    }

    // ==================== 多语句 ====================

    @Test
    @DisplayName("多语句（分号分隔）必须被拒绝")
    void rejectMultipleStatements() {
        assertRejected("select 1 from t; delete from t", "多语句");
        assertRejected("select 1 from t;select 2 from u", "多语句");
    }

    @Test
    @DisplayName("末尾单个分号应被允许（常见书写习惯）")
    void allowTrailingSemicolon() {
        assertAccepted("select id, name from rpt_target_dept where dept_id = 1;");
    }

    // ==================== 空值 ====================

    @Test
    @DisplayName("null / 空串 / 纯注释必须被拒绝")
    void rejectBlank() {
        assertRejected(null, "null");
        assertRejected("", "空串");
        assertRejected("   ", "空白");
        assertRejected("/* only comment */", "纯注释");
    }

    // ==================== 合法 SQL 必须放行（防误杀） ====================

    @ParameterizedTest
    @ValueSource(strings = {
            "select id, name from rpt_target_dept where dept_id = Para_DepID",
            "SELECT a.id, b.name FROM t1 a LEFT JOIN t2 b ON a.id = b.id WHERE a.status = 1",
            "select count(*) from rpt_target_dept",
            "select * from t where name = 'delete me'",          // 字符串字面量含关键字
            "select replace(name, 'a', 'b') from t",              // REPLACE 函数应放行
            "select * from t order by id desc limit 100",
            "select * from t limit 10",
            "(select 1 from t)",
            "select * from t where dt >= '2026-01-01'",
            "select * from t -- 注释\n",
    })
    @DisplayName("合法业务 SQL 必须放行（避免过度拦截）")
    void acceptLegitSql(String sql) {
        assertAccepted(sql);
    }

    @Test
    @DisplayName("字符串字面量里出现 delete 字样不应误杀")
    void allowKeywordInsideStringLiteral() {
        assertAccepted("select * from t where remark = 'please delete this row'");
    }
}
