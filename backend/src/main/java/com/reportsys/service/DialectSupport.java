package com.reportsys.service;

import cn.hutool.core.util.StrUtil;
import com.reportsys.model.RptDatasource;
import com.reportsys.model.RptTemplateField;

import java.util.ArrayList;
import java.util.List;

/**
 * 多方言支持：把"物化建表 / 写入业务库"的 SQL 生成与 JDBC 连接信息按数据库类型隔离。
 *
 * 支持 MYSQL / POSTGRESQL / ORACLE / CIRRODATA（CirroData 默认走 MySQL 协议，
 * 如需专有驱动可用配置 report.datasource.drivers.CIRRODATA 覆盖）。
 */
public final class DialectSupport {

    public enum Dialect { MYSQL, POSTGRESQL, ORACLE, CIRRODATA }

    private DialectSupport() {
    }

    public static Dialect of(String dsType) {
        if (StrUtil.isBlank(dsType)) return Dialect.MYSQL;
        switch (dsType.trim().toUpperCase()) {
            case "ORACLE":
                return Dialect.ORACLE;
            case "POSTGRESQL":
            case "POSTGRES":
            case "PG":
                return Dialect.POSTGRESQL;
            case "CIRRODATA":
                return Dialect.CIRRODATA;
            default:
                return Dialect.MYSQL;
        }
    }

    public static String driverClass(Dialect dialect) {
        switch (dialect) {
            case ORACLE:
                return "oracle.jdbc.OracleDriver";
            case POSTGRESQL:
                return "org.postgresql.Driver";
            default:
                return "com.mysql.cj.jdbc.Driver";
        }
    }

    /** 构造带超时的 JDBC URL */
    public static String jdbcUrl(Dialect dialect, RptDatasource ds, int connectTimeoutMs, int socketTimeoutMs) {
        String host = ds.getIp();
        int port = ds.getPort() == null ? defaultPort(dialect) : ds.getPort();
        String db = ds.getDbName() == null ? "" : ds.getDbName();
        switch (dialect) {
            case ORACLE:
                return "jdbc:oracle:thin:@" + host + ":" + port + "/" + db
                        + "?oracle.net.CONNECT_TIMEOUT=" + connectTimeoutMs
                        + "&oracle.jdbc.ReadTimeout=" + socketTimeoutMs;
            case POSTGRESQL:
                return "jdbc:postgresql://" + host + ":" + port + "/" + db
                        + "?connectTimeout=" + Math.max(1, connectTimeoutMs / 1000)
                        + "&socketTimeout=" + Math.max(1, socketTimeoutMs / 1000)
                        + "&ApplicationName=report-system";
            default:
                return "jdbc:mysql://" + host + ":" + port + "/" + db
                        + "?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai"
                        + "&allowPublicKeyRetrieval=true"
                        + "&connectTimeout=" + connectTimeoutMs + "&socketTimeout=" + socketTimeoutMs;
        }
    }

    public static int defaultPort(Dialect dialect) {
        switch (dialect) {
            case ORACLE:
                return 1521;
            case POSTGRESQL:
                return 5432;
            default:
                return 3306;
        }
    }

    public static String quote(Dialect dialect, String identifier) {
        if (StrUtil.isBlank(identifier)) return identifier;
        return dialect == Dialect.ORACLE || dialect == Dialect.POSTGRESQL
                ? "\"" + identifier.replace("\"", "") + "\""
                : "`" + identifier.replace("`", "") + "`";
    }

    /** 字段类型映射（同时兼容历史/前端的 string、number、date 等别名） */
    public static String columnType(Dialect dialect, String fieldType, Integer length) {
        String type = StrUtil.isBlank(fieldType) ? "VARCHAR" : fieldType.trim().toUpperCase();
        int len = (length == null || length <= 0) ? 255 : length;
        switch (type) {
            case "STRING":
            case "TEXT":
            case "CLOB":
                if ("STRING".equals(type) && len > 0 && len <= 4000) {
                    return dialect == Dialect.ORACLE ? "VARCHAR2(" + len + ")" : "VARCHAR(" + Math.min(len, 8000) + ")";
                }
                return dialect == Dialect.ORACLE ? "CLOB" : "TEXT";
            case "INT":
            case "INTEGER":
                return dialect == Dialect.ORACLE ? "NUMBER(10)" : "INT";
            case "BIGINT":
            case "LONG":
                return dialect == Dialect.ORACLE ? "NUMBER(19)" : "BIGINT";
            case "NUMBER":
            case "NUMERIC":
            case "DECIMAL":
            case "DOUBLE":
            case "FLOAT":
                return dialect == Dialect.ORACLE ? "NUMBER(18,4)" : "DECIMAL(18,4)";
            case "DATE":
                return "DATE";
            case "DATETIME":
            case "TIMESTAMP":
                return dialect == Dialect.ORACLE ? "TIMESTAMP" : "DATETIME";
            default:
                if (dialect == Dialect.ORACLE) {
                    return len > 4000 ? "CLOB" : "VARCHAR2(" + len + ")";
                }
                return "VARCHAR(" + Math.min(len, 8000) + ")";
        }
    }

    /**
     * 表引用：
     * - MySQL/CirroData：库名来自数据源配置（db.table）
     * - PostgreSQL：库名就是连接库，表在 public schema（可用 report.datasource.pg-schema 覆盖）
     * - Oracle：只用表名（schema 由连接用户决定）
     */
    public static String tableRef(Dialect dialect, String dbName, String table) {
        return tableRef(dialect, dbName, table, "public");
    }

    public static String tableRef(Dialect dialect, String dbName, String table, String pgSchema) {
        String t = quote(dialect, table);
        switch (dialect) {
            case MYSQL:
            case CIRRODATA:
                return StrUtil.isBlank(dbName) ? t : quote(dialect, dbName) + "." + t;
            case POSTGRESQL:
                String schema = StrUtil.isBlank(pgSchema) ? "public" : pgSchema;
                return quote(dialect, schema) + "." + t;
            default:
                return t;
        }
    }

    /** 物化建表语句 */
    public static String createTable(Dialect dialect, String dbName, String table, List<RptTemplateField> fields) {
        StringBuilder sb = new StringBuilder("CREATE TABLE ").append(tableRef(dialect, dbName, table)).append(" (");
        List<String> keys = new ArrayList<>();
        boolean first = true;
        for (RptTemplateField field : fields) {
            String column = columnName(field);
            if (StrUtil.isBlank(column)) continue;
            if (!first) sb.append(", ");
            first = false;
            sb.append(quote(dialect, column)).append(" ").append(columnType(dialect, field.getFieldType(), field.getFieldLength()));
            if (field.getIsKey() != null && field.getIsKey() == 1) keys.add(quote(dialect, column));
            if (field.getIsRequired() != null && field.getIsRequired() == 1 && dialect == Dialect.ORACLE) {
                sb.append(" NOT NULL");
            }
        }
        if (keys.isEmpty()) {
            // 没有主键时补一个自增/序列式主键，保证可 update/delete
            sb.append(", ").append(autoIdColumn(dialect));
        } else {
            sb.append(", PRIMARY KEY (").append(String.join(", ", keys)).append(")");
        }
        sb.append(")");
        return sb.toString();
    }

    private static String autoIdColumn(Dialect dialect) {
        switch (dialect) {
            case ORACLE:
                return quote(dialect, "ID") + " NUMBER(19) NOT NULL";
            case POSTGRESQL:
                return quote(dialect, "ID") + " BIGSERIAL";
            default:
                return quote(dialect, "ID") + " BIGINT NOT NULL AUTO_INCREMENT, PRIMARY KEY (" + quote(dialect, "ID") + ")";
        }
    }

    public static String dropTable(Dialect dialect, String dbName, String table) {
        return "DROP TABLE " + tableRef(dialect, dbName, table);
    }

    /** 表是否存在（information_schema 查询） */
    public static String existsTableSql(Dialect dialect) {
        if (dialect == Dialect.ORACLE) {
            return "SELECT COUNT(1) FROM user_tables WHERE table_name = ?";
        }
        return "SELECT COUNT(1) FROM information_schema.tables WHERE table_name = ? AND table_schema = ?";
    }

    public static String insertSql(Dialect dialect, String dbName, String table, List<String> columns) {
        StringBuilder cols = new StringBuilder();
        StringBuilder marks = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                cols.append(", ");
                marks.append(", ");
            }
            cols.append(quote(dialect, columns.get(i)));
            marks.append("?");
        }
        return "INSERT INTO " + tableRef(dialect, dbName, table) + " (" + cols + ") VALUES (" + marks + ")";
    }

    public static String updateSql(Dialect dialect, String dbName, String table,
                                   List<String> setColumns, List<String> keyColumns) {
        StringBuilder sb = new StringBuilder("UPDATE ").append(tableRef(dialect, dbName, table)).append(" SET ");
        for (int i = 0; i < setColumns.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(quote(dialect, setColumns.get(i))).append(" = ?");
        }
        sb.append(" WHERE ");
        for (int i = 0; i < keyColumns.size(); i++) {
            if (i > 0) sb.append(" AND ");
            sb.append(quote(dialect, keyColumns.get(i))).append(" = ?");
        }
        return sb.toString();
    }

    public static String deleteSql(Dialect dialect, String dbName, String table, List<String> keyColumns) {
        StringBuilder sb = new StringBuilder("DELETE FROM ").append(tableRef(dialect, dbName, table)).append(" WHERE ");
        for (int i = 0; i < keyColumns.size(); i++) {
            if (i > 0) sb.append(" AND ");
            sb.append(quote(dialect, keyColumns.get(i))).append(" = ?");
        }
        return sb.toString();
    }

    public static String selectAllSql(Dialect dialect, String dbName, String table, List<String> columns) {
        StringBuilder cols = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) cols.append(", ");
            cols.append(quote(dialect, columns.get(i)));
        }
        return "SELECT " + cols + " FROM " + tableRef(dialect, dbName, table);
    }

    /** 字段最终落库列名：优先 db_column，其次 field_code */
    public static String columnName(RptTemplateField field) {
        if (field == null) return null;
        if (StrUtil.isNotBlank(field.getDbColumn())) return field.getDbColumn().trim();
        return StrUtil.isNotBlank(field.getFieldCode()) ? field.getFieldCode().trim() : null;
    }
}
