package com.reportsys.service;

import cn.hutool.core.util.StrUtil;
import com.reportsys.config.TargetDataSourceManager;
import com.reportsys.dao.RptDatasourceMapper;
import com.reportsys.dao.RptTemplateFieldMapper;
import com.reportsys.model.RptDatasource;
import com.reportsys.model.RptTemplate;
import com.reportsys.model.RptTemplateField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模板物化与数据写回（原版 "模板—物化—填报—入库" 链路的落地实现）。
 *
 * - ensureTable：按模板字段在目标业务库建表（多方言）
 * - dropTable：删除物化表
 * - writeRows：按主键字段 upsert（有则更新、无则插入），批量、事务
 * - readRows：从物化表读数据（导出用）
 */
@Service
public class MaterializeService {

    private static final Logger log = LoggerFactory.getLogger(MaterializeService.class);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private TargetDataSourceManager dataSourceManager;
    @Autowired
    private RptDatasourceMapper rptDatasourceMapper;
    @Autowired
    private RptTemplateFieldMapper rptTemplateFieldMapper;

    /** 解析模板使用的目标数据源 */
    public RptDatasource resolveDatasource(RptTemplate template) {
        if (template == null) throw new RuntimeException("模板不存在");
        if (template.getDatasourceId() == null) throw new RuntimeException("请先为该模板配置目标数据源");
        RptDatasource ds = rptDatasourceMapper.selectById(template.getDatasourceId());
        if (ds == null) throw new RuntimeException("模板配置的数据源不存在");
        return ds;
    }

    /** 物化表名：优先模板配置，否则由模板编码推导 */
    public String resolveTable(RptTemplate template) {
        if (StrUtil.isNotBlank(template.getTableName())) return safeIdent(template.getTableName());
        String code = StrUtil.isBlank(template.getTemplateCode()) ? ("tpl" + template.getId()) : template.getTemplateCode();
        return safeIdent("t_" + code);
    }

    public List<RptTemplateField> fields(Long templateId) {
        return rptTemplateFieldMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<RptTemplateField>lambdaQuery()
                        .eq(RptTemplateField::getTemplateId, templateId)
                        .orderByAsc(RptTemplateField::getSort));
    }

    /**
     * 物化建表；recreate=true 时先删后建
     */
    public String ensureTable(RptTemplate template, List<RptTemplateField> fields, boolean recreate) {
        if (fields == null || fields.isEmpty()) throw new RuntimeException("模板没有字段，无法物化");
        RptDatasource ds = resolveDatasource(template);
        DialectSupport.Dialect dialect = DialectSupport.of(ds.getDsType());
        String table = resolveTable(template);
        boolean exists = tableExists(template, ds, table);
        try (Connection conn = dataSourceManager.getConnection(ds); Statement st = conn.createStatement()) {
            if (exists && recreate) {
                st.executeUpdate(DialectSupport.dropTable(dialect, ds.getDbName(), table));
                exists = false;
            }
            if (!exists) {
                String ddl = DialectSupport.createTable(dialect, ds.getDbName(), table, fields);
                log.info("物化建表: {}", ddl);
                st.executeUpdate(ddl);
            }
            return table;
        } catch (SQLException e) {
            throw new RuntimeException("物化失败: " + e.getMessage(), e);
        }
    }

    public void dropTable(RptTemplate template) {
        RptDatasource ds = resolveDatasource(template);
        DialectSupport.Dialect dialect = DialectSupport.of(ds.getDsType());
        String table = resolveTable(template);
        if (!tableExists(template, ds, table)) return;
        try (Connection conn = dataSourceManager.getConnection(ds); Statement st = conn.createStatement()) {
            st.executeUpdate(DialectSupport.dropTable(dialect, ds.getDbName(), table));
            log.info("已删除物化表 {}", table);
        } catch (SQLException e) {
            throw new RuntimeException("删除物化表失败: " + e.getMessage(), e);
        }
    }

    public boolean tableExists(RptTemplate template, RptDatasource ds, String table) {
        DialectSupport.Dialect dialect = DialectSupport.of(ds.getDsType());
        try (Connection conn = dataSourceManager.getConnection(ds);
             PreparedStatement ps = conn.prepareStatement(DialectSupport.existsTableSql(dialect))) {
            if (dialect == DialectSupport.Dialect.ORACLE) {
                ps.setString(1, table.toUpperCase());
            } else if (dialect == DialectSupport.Dialect.POSTGRESQL) {
                ps.setString(1, table);
                ps.setString(2, "public");
            } else {
                ps.setString(1, table);
                ps.setString(2, StrUtil.isBlank(ds.getDbName()) ? "public" : ds.getDbName());
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("检查目标表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量写入：rows 的 key 为数据库列名；有主键字段时按主键 upsert
     *
     * @return 影响行数
     */
    public int writeRows(RptTemplate template, List<RptTemplateField> fields, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return 0;
        RptDatasource ds = resolveDatasource(template);
        DialectSupport.Dialect dialect = DialectSupport.of(ds.getDsType());
        String table = resolveTable(template);

        List<String> allColumns = new ArrayList<>();
        List<String> keyColumns = new ArrayList<>();
        for (RptTemplateField field : fields) {
            String column = DialectSupport.columnName(field);
            if (StrUtil.isBlank(column)) continue;
            allColumns.add(column);
            if (field.getIsKey() != null && field.getIsKey() == 1) keyColumns.add(column);
        }
        if (allColumns.isEmpty()) throw new RuntimeException("模板没有可用的字段映射");

        List<String> setColumns = new ArrayList<>(allColumns);
        setColumns.removeAll(keyColumns);

        String insertSql = DialectSupport.insertSql(dialect, ds.getDbName(), table, allColumns);
        String updateSql = (!keyColumns.isEmpty() && !setColumns.isEmpty())
                ? DialectSupport.updateSql(dialect, ds.getDbName(), table, setColumns, keyColumns) : null;

        int affected = 0;
        try (Connection conn = dataSourceManager.getConnection(ds)) {
            conn.setAutoCommit(false);
            try (PreparedStatement insert = conn.prepareStatement(insertSql);
                 PreparedStatement update = updateSql == null ? null : conn.prepareStatement(updateSql)) {
                for (Map<String, Object> row : rows) {
                    if (update != null) {
                        int idx = 1;
                        for (String column : setColumns) update.setObject(idx++, row.get(column));
                        for (String column : keyColumns) update.setObject(idx++, row.get(column));
                        int n = update.executeUpdate();
                        if (n > 0) {
                            affected += n;
                            continue;
                        }
                    }
                    int idx = 1;
                    for (String column : allColumns) insert.setObject(idx++, row.get(column));
                    affected += insert.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
            log.info("写入目标库完成 table={} rows={} affected={}", table, rows.size(), affected);
            return affected;
        } catch (SQLException e) {
            throw new RuntimeException("写入目标库失败: " + e.getMessage(), e);
        }
    }

    /** 从物化表读取数据（导出/查询用） */
    public List<Map<String, Object>> readRows(RptTemplate template, List<RptTemplateField> fields, int limit) {
        RptDatasource ds = resolveDatasource(template);
        DialectSupport.Dialect dialect = DialectSupport.of(ds.getDsType());
        String table = resolveTable(template);
        List<String> columns = new ArrayList<>();
        for (RptTemplateField field : fields) {
            String column = DialectSupport.columnName(field);
            if (StrUtil.isNotBlank(column)) columns.add(column);
        }
        if (columns.isEmpty()) throw new RuntimeException("模板没有可用的字段映射");

        List<Map<String, Object>> result = new ArrayList<>();
        String sql = DialectSupport.selectAllSql(dialect, ds.getDbName(), table, columns);
        if (limit > 0 && dialect != DialectSupport.Dialect.ORACLE) {
            sql = sql + " LIMIT " + limit;
        }
        try (Connection conn = dataSourceManager.getConnection(ds);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData meta = ps.getMetaData() == null ? rs.getMetaData() : ps.getMetaData();
            int count = meta.getColumnCount();
            int n = 0;
            while (rs.next() && (limit <= 0 || n < limit)) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= count; i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                result.add(row);
                n++;
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("读取目标表失败: " + e.getMessage(), e);
        }
    }

    /** 按字段类型把字符串转换成合适的 JDBC 值（供导入/JSON 写回使用） */
    public static Object convertValue(String fieldType, String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty()) return null;
        String type = StrUtil.isBlank(fieldType) ? "VARCHAR" : fieldType.trim().toUpperCase();
        try {
            switch (type) {
                case "INT":
                case "INTEGER":
                    return Integer.valueOf(value);
                case "BIGINT":
                case "LONG":
                    return Long.valueOf(value);
                case "DECIMAL":
                case "NUMBER":
                case "NUMERIC":
                    return new BigDecimal(value);
                case "DATE":
                    return java.sql.Date.valueOf(LocalDate.parse(value, DATE_FMT));
                case "DATETIME":
                case "TIMESTAMP":
                    if (value.length() <= 10) {
                        return Timestamp.valueOf(LocalDate.parse(value, DATE_FMT).atStartOfDay());
                    }
                    return Timestamp.valueOf(LocalDateTime.parse(value, DATETIME_FMT));
                default:
                    return value;
            }
        } catch (Exception e) {
            throw new RuntimeException("字段值格式不正确(" + raw + " → " + type + ")");
        }
    }

    private static String safeIdent(String raw) {
        if (StrUtil.isBlank(raw)) throw new RuntimeException("表名不能为空");
        String cleaned = raw.trim().replaceAll("[^0-9A-Za-z_]", "_");
        if (cleaned.length() > 60) cleaned = cleaned.substring(0, 60);
        if (Character.isDigit(cleaned.charAt(0))) cleaned = "t_" + cleaned;
        return cleaned;
    }

    private static void bindNull(PreparedStatement ps, int index) throws SQLException {
        ps.setNull(index, Types.NULL);
    }
}
