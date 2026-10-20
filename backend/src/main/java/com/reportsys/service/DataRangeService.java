package com.reportsys.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.reportsys.config.TargetDataSourceManager;
import com.reportsys.dao.RptDataRangeConfigMapper;
import com.reportsys.dao.RptDatasourceMapper;
import com.reportsys.model.RptDataRangeConfig;
import com.reportsys.model.RptDatasource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 数据范围 SQL 配置（P2-G，对齐原版 data_range_config：管理员配置 SQL + 部门变量，用于限定填报人可见数据）。
 *
 * 安全约束：只允许单条 SELECT；禁止多语句与写操作关键字；变量 Para_DepID / Para_UserId 白名单替换；
 * 强制行数上限与查询超时。
 */
@Service
public class DataRangeService {

    private static final Logger log = LoggerFactory.getLogger(DataRangeService.class);

    /**
     * 禁用关键字（词边界正则）。
     *
     * 安全修复：原实现是带尾随空格的字符串列表（如 "delete "），可被
     * {@code delete/**\/x}、{@code delete&nbsp;} 等手法绕过，且未覆盖 union / into / outfile 等。
     * 现改为词边界正则，配合注释剥离，杜绝拼接绕过。
     */
    private static final java.util.regex.Pattern FORBIDDEN_PATTERN = java.util.regex.Pattern.compile(
            "\\b(insert|update|delete|drop|alter|truncate|create|grant|revoke|merge|call|execute"
                    + "|into|outfile|dumpfile|load_file|load\\s+data|benchmark|sleep|pg_sleep"
                    + "|waitfor\\s+delay|shutdown|handler)\\b");

    /** SELECT 起始（允许前置空白与括号） */
    private static final java.util.regex.Pattern SELECT_START =
            java.util.regex.Pattern.compile("^\\s*\\(*\\s*select\\b");

    /** 已有 LIMIT 判断（词边界，兼容换行/制表符） */
    private static final java.util.regex.Pattern LIMIT_PATTERN =
            java.util.regex.Pattern.compile("\\blimit\\b");

    /** UNION 判断 */
    private static final java.util.regex.Pattern UNION_PATTERN =
            java.util.regex.Pattern.compile("\\bunion\\b");

    /** 禁止访问的系统库 / 元数据库 */
    private static final List<String> FORBIDDEN_SCHEMAS = Arrays.asList(
            "information_schema", "performance_schema", "mysql.", "sys.", "pg_catalog", "sqlite_master");

    /**
     * 禁止查询的本系统敏感表（防止经数据源读主库的账号密码/权限）。
     * 前缀匹配由 {@link #assertSelectOnly} 的词边界正则完成。
     */
    private static final List<String> FORBIDDEN_TABLES = Arrays.asList(
            "sys_user", "sys_role", "sys_menu", "sys_user_role", "sys_role_menu",
            "rpt_online_user", "sys_login_log");

    @Autowired
    private RptDataRangeConfigMapper configMapper;
    @Autowired
    private RptDatasourceMapper datasourceMapper;
    @Autowired
    private TargetDataSourceManager dataSourceManager;

    @Value("${report.data-range.max-rows:200}")
    private int maxRows;

    @Value("${report.data-range.timeout-seconds:30}")
    private int timeoutSeconds;

    public List<RptDataRangeConfig> list() {
        return configMapper.selectList(Wrappers.<RptDataRangeConfig>lambdaQuery()
                .orderByDesc(RptDataRangeConfig::getId));
    }

    public RptDataRangeConfig getById(Long id) {
        return configMapper.selectById(id);
    }

    @Transactional
    public Long save(RptDataRangeConfig config, Long userId) {
        if (StrUtil.isBlank(config.getConfigName())) throw new RuntimeException("配置名称不能为空");
        if (StrUtil.isBlank(config.getSqlText())) throw new RuntimeException("SQL 不能为空");
        assertSelectOnly(config.getSqlText());
        if (config.getDatasourceId() == null) throw new RuntimeException("请先选择数据源");
        if (config.getId() == null) {
            config.setCreateBy(userId);
            config.setStatus(config.getStatus() == null ? 1 : config.getStatus());
            configMapper.insert(config);
        } else {
            configMapper.updateById(config);
        }
        return config.getId();
    }

    @Transactional
    public void delete(Long id) {
        configMapper.deleteById(id);
    }

    /** 预览：执行配置里的 SQL（替换部门变量） */
    public Map<String, Object> preview(Long id, Long deptId, Long userId) {
        RptDataRangeConfig config = configMapper.selectById(id);
        if (config == null) throw new RuntimeException("配置不存在");
        return runSql(config.getDatasourceId(), config.getSqlText(), deptId, userId, maxRows);
    }

    /** 直接执行一段 SQL（供"SQL 试跑"使用） */
    public Map<String, Object> runSql(Long datasourceId, String sql, Long deptId, Long userId, int limit) {
        if (datasourceId == null) throw new RuntimeException("请先选择数据源");
        RptDatasource ds = datasourceMapper.selectById(datasourceId);
        if (ds == null) throw new RuntimeException("数据源不存在");

        String prepared = prepareSql(sql, deptId, userId, limit);
        RptDatasource connectDs = dataSourceManager == null ? ds : decrypt(ds);
        int rowLimit = limit <= 0 ? maxRows : Math.min(limit, maxRows);

        long start = System.currentTimeMillis();
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        try (Connection conn = dataSourceManager.getConnection(ds);
             PreparedStatement ps = conn.prepareStatement(prepared)) {
            ps.setQueryTimeout(timeoutSeconds);
            ps.setMaxRows(rowLimit);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int count = meta.getColumnCount();
                for (int i = 1; i <= count; i++) columns.add(meta.getColumnLabel(i));
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= count; i++) row.put(meta.getColumnLabel(i), rs.getObject(i));
                    rows.add(row);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("SQL 执行失败: " + e.getMessage(), e);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sql", prepared);
        result.put("columns", columns);
        result.put("rows", rows);
        result.put("rowCount", rows.size());
        result.put("costMs", System.currentTimeMillis() - start);
        result.put("limit", rowLimit);
        log.info("数据范围 SQL 试跑: dsId={} rows={} cost={}ms", datasourceId, rows.size(),
                System.currentTimeMillis() - start);
        return result;
    }

    /** 校验 + 变量替换 + 追加行数限制 */
    public String prepareSql(String sql, Long deptId, Long userId, int limit) {
        assertSelectOnly(sql);
        String prepared = sql.replace("Para_DepID", String.valueOf(deptId == null ? 0L : deptId))
                .replace("Para_UserId", String.valueOf(userId == null ? 0L : userId));
        String lower = stripComments(prepared).toLowerCase(Locale.ROOT);
        int rowLimit = limit <= 0 ? maxRows : Math.min(limit, maxRows);
        // 用词边界判断已有的 LIMIT / ROWNUM（原来的 " limit " 空格匹配对 "LIMIT\n3" 之类会漏判）
        if (!LIMIT_PATTERN.matcher(lower).find() && !lower.contains("rownum")) {
            prepared = "SELECT * FROM (" + prepared + ") dr_limit LIMIT " + rowLimit;
        }
        return prepared;
    }

    /**
     * 只允许单条只读 SELECT。
     *
     * 安全修复（审计 H5）：原实现用带尾随空格的字符串黑名单
     * （如 {@code "delete "}），且未剥离注释，导致：
     * <ul>
     *   <li>{@code delete/**\/x} 不含 "delete " → 绕过</li>
     *   <li>{@code into/**\/outfile} 绕过</li>
     *   <li>裸 {@code select username,password from sys_user} 完全合法 → 可读系统表密码哈希</li>
     * </ul>
     * 实测已确认恶意 SQL 能保存并被执行。现在的加固：
     * <ol>
     *   <li>先剥离 {@code /* *\/}、{@code --}、{@code #} 注释，避免用注释拼接绕过关键字</li>
     *   <li>必须以 SELECT 开头（词边界）</li>
     *   <li>禁用关键字改为<b>词边界正则</b>匹配（不再依赖尾随空格）</li>
     *   <li>禁止 UNION（避免用联合查询拼出任意结果集）、禁止访问系统库/元数据</li>
     *   <li>禁止引用本系统的账号/权限表（sys_user/sys_role/sys_menu/...），
     *       阻断「通过数据源读主库密码哈希」这条链</li>
     *   <li>禁止多语句（分号）</li>
     * </ol>
     */
    public void assertSelectOnly(String sql) {
        if (StrUtil.isBlank(sql)) throw new RuntimeException("SQL 不能为空");
        String cleaned = stripComments(sql).trim();
        if (cleaned.isEmpty()) throw new RuntimeException("SQL 不能为空");

        // 结构校验用「原文」（保留字符串内容用于 SELECT 起始判断）
        String lowerOriginal = cleaned.toLowerCase(Locale.ROOT);
        if (!SELECT_START.matcher(lowerOriginal).find()) {
            throw new RuntimeException("只允许执行 SELECT 查询");
        }

        // 关键字 / 敏感表 / 分号校验统一用「掩码版」：
        // 把 '...' 与 "..." 的内容清空，避免业务里正常的字符串值
        // （如 remark = 'please delete this' 或 'a;b'）被误判为注入而误杀。
        // 注意：关键字在字符串<b>外</b>时仍能被检出，安全性不受影响。
        String masked = maskStringLiterals(cleaned);
        String lower = masked.toLowerCase(Locale.ROOT);

        // 词边界关键字黑名单
        java.util.regex.Matcher m = FORBIDDEN_PATTERN.matcher(lower);
        if (m.find()) {
            throw new RuntimeException("SQL 中包含不允许的关键字: " + m.group());
        }
        // 禁止 union（可拼接任意结果集，绕过单表语义）
        if (UNION_PATTERN.matcher(lower).find()) {
            throw new RuntimeException("SQL 中不允许使用 UNION");
        }
        // 禁止系统库 / 元数据探测
        for (String sys : FORBIDDEN_SCHEMAS) {
            if (lower.contains(sys)) {
                throw new RuntimeException("不允许访问系统库或元数据: " + sys);
            }
        }
        // 禁止引用本系统敏感表（防止经数据源读账号密码）
        for (String table : FORBIDDEN_TABLES) {
            if (java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(table) + "\\b")
                    .matcher(lower).find()) {
                throw new RuntimeException("不允许查询系统敏感表: " + table);
            }
        }
        // 禁止多语句（字符串内的分号已被掩码，不会误判）
        String withoutTrailing = masked.replaceAll(";\\s*$", "");
        if (withoutTrailing.contains(";")) {
            throw new RuntimeException("不允许多条语句（含分号）");
        }
    }

    /**
     * 把字符串字面量的内容清空（保留引号边界）。
     *
     * 用于安全校验前的归一化：既避免正常字符串值里的关键字被误杀，
     * 也保证字符串外的真实关键字仍可被检出。
     * 支持 {@code ''} 与 {@code ""} 的转义写法。
     */
    private String maskStringLiterals(String sql) {
        StringBuilder sb = new StringBuilder(sql.length());
        char quote = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (quote == 0) {
                if (c == '\'' || c == '"') {
                    quote = c;
                    sb.append(c);
                } else {
                    sb.append(c);
                }
            } else {
                if (c == quote) {
                    // '' / "" 表示转义的单引号/双引号，继续留在字符串内
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                        i++;
                    } else {
                        quote = 0;
                        sb.append(c);
                    }
                }
                // 字符串内容丢弃
            }
        }
        return sb.toString();
    }

    /** 剥离 SQL 注释：块注释、行注释（-- 与 #），避免用注释拼关键字绕过 */
    private String stripComments(String sql) {
        if (sql == null) return "";
        // 块注释
        String s = sql.replaceAll("(?s)/\\*.*?\\*/", " ");
        // 行注释 -- 与 #（按行处理）
        StringBuilder sb = new StringBuilder();
        for (String line : s.split("\\r?\\n")) {
            String l = line;
            int dash = indexOutsideQuotes(l, "--");
            int hash = indexOutsideQuotes(l, "#");
            int cut = -1;
            if (dash >= 0) cut = dash;
            if (hash >= 0 && (cut < 0 || hash < cut)) cut = hash;
            if (cut >= 0) l = l.substring(0, cut);
            sb.append(l).append(' ');
        }
        return sb.toString();
    }

    /** 找到不在引号内的注释起始位置 */
    private int indexOutsideQuotes(String line, String token) {
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'' && !inDouble) inSingle = !inSingle;
            else if (c == '"' && !inSingle) inDouble = !inDouble;
            if (!inSingle && !inDouble && line.startsWith(token, i)) return i;
        }
        return -1;
    }

    private RptDatasource decrypt(RptDatasource ds) {
        return ds;
    }
}
