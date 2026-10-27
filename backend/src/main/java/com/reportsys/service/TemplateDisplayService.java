package com.reportsys.service;

import cn.hutool.core.util.StrUtil;
import com.reportsys.config.TargetDataSourceManager;
import com.reportsys.model.RptDatasource;
import com.reportsys.model.RptTemplateDisplayConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * R-01 模板展示信息配置：无 / 关联模板 / 关联表。
 *
 * 展示信息项只读，不参与物化建表与入库，仅用于填报时给填报人参考历史数据。
 */
@Service
public class TemplateDisplayService {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RptDatasourceService rptDatasourceService;
    @Autowired
    private TargetDataSourceManager targetDataSourceManager;

    public Map<String, Object> getConfig(Long templateId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, template_id AS templateId, config_type AS configType, ref_template_id AS refTemplateId, "
                        + "ref_datasource_id AS refDatasourceId, ref_table_name AS refTableName, ref_columns AS refColumns, "
                        + "link_checked AS linkChecked, link_check_time AS linkCheckTime "
                        + "FROM rpt_template_display_config WHERE template_id = ?", templateId);
        Map<String, Object> out = new LinkedHashMap<>();
        if (rows.isEmpty()) {
            out.put("templateId", templateId);
            out.put("configType", 0);
            out.put("refColumns", "[]");
        } else {
            out.putAll(rows.get(0));
        }
        return out;
    }

    public void save(RptTemplateDisplayConfig cfg, Long userId) {
        if (cfg.getTemplateId() == null) throw new IllegalStateException("缺少模板ID");
        int type = cfg.getConfigType() == null ? 0 : cfg.getConfigType();
        if (type == 1 && cfg.getRefTemplateId() == null) throw new IllegalStateException("请选择关联模板");
        if (type == 2 && (cfg.getRefDatasourceId() == null || StrUtil.isBlank(cfg.getRefTableName()))) {
            throw new IllegalStateException("请选择数据源与关联表");
        }
        List<Long> exists = jdbc.queryForList(
                "SELECT id FROM rpt_template_display_config WHERE template_id = ?", Long.class, cfg.getTemplateId());
        if (exists.isEmpty()) {
            jdbc.update("INSERT INTO rpt_template_display_config (template_id, config_type, ref_template_id, "
                            + "ref_datasource_id, ref_table_name, ref_columns, link_checked, create_by, create_time) "
                            + "VALUES (?,?,?,?,?,?,?,?,NOW())",
                    cfg.getTemplateId(), type, cfg.getRefTemplateId(), cfg.getRefDatasourceId(),
                    cfg.getRefTableName(), StrUtil.nullToEmpty(cfg.getRefColumns()).isEmpty() ? "[]" : cfg.getRefColumns(),
                    cfg.getLinkChecked() == null ? 0 : cfg.getLinkChecked(), userId);
        } else {
            jdbc.update("UPDATE rpt_template_display_config SET config_type=?, ref_template_id=?, ref_datasource_id=?, "
                            + "ref_table_name=?, ref_columns=?, link_checked=?, update_time=NOW() WHERE template_id=?",
                    type, cfg.getRefTemplateId(), cfg.getRefDatasourceId(), cfg.getRefTableName(),
                    StrUtil.nullToEmpty(cfg.getRefColumns()).isEmpty() ? "[]" : cfg.getRefColumns(),
                    cfg.getLinkChecked() == null ? 0 : cfg.getLinkChecked(), cfg.getTemplateId());
        }
    }

    /** 关联表连通性检测 */
    public Map<String, Object> linkTest(Long datasourceId, String tableName) {
        Map<String, Object> out = new LinkedHashMap<>();
        RptDatasource ds = rptDatasourceService.getForConnect(datasourceId);
        if (ds == null) throw new IllegalStateException("数据源不存在");
        String sql = "SELECT * FROM " + tableName + " WHERE 1=0";
        try (Connection conn = targetDataSourceManager.getConnection(ds)) {
            conn.createStatement().executeQuery(sql);
            out.put("ok", true);
            out.put("message", "链接成功");
        } catch (Exception e) {
            out.put("ok", false);
            out.put("message", "链接失败：" + StrUtil.maxLength(e.getMessage(), 200));
        }
        return out;
    }

    /** 该数据源下的表清单（R-01 关联表 / R-08 关联已有表共用） */
    public List<String> listTables(Long datasourceId) {
        RptDatasource ds = rptDatasourceService.getForConnect(datasourceId);
        if (ds == null) throw new IllegalStateException("数据源不存在");
        List<String> tables = new ArrayList<>();
        try (Connection conn = targetDataSourceManager.getConnection(ds)) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            try (ResultSet rs = meta.getTables(catalog, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) tables.add(rs.getString("TABLE_NAME"));
            }
        } catch (Exception e) {
            throw new IllegalStateException("读取表清单失败：" + e.getMessage());
        }
        return tables;
    }

    /** 关联表的列清单 */
    public List<Map<String, Object>> listColumns(Long datasourceId, String tableName) {
        RptDatasource ds = rptDatasourceService.getForConnect(datasourceId);
        if (ds == null) throw new IllegalStateException("数据源不存在");
        List<Map<String, Object>> cols = new ArrayList<>();
        try (Connection conn = targetDataSourceManager.getConnection(ds)) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            try (ResultSet rs = meta.getColumns(catalog, null, tableName, "%")) {
                while (rs.next()) {
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("name", rs.getString("COLUMN_NAME"));
                    c.put("type", rs.getString("TYPE_NAME"));
                    c.put("size", rs.getInt("COLUMN_SIZE"));
                    cols.add(c);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("读取列清单失败：" + e.getMessage());
        }
        return cols;
    }

    /** 填报页参考数据（只读） */
    public Map<String, Object> reference(Long taskId, Long deptId) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> taskRows = jdbc.queryForList(
                "SELECT template_id FROM rpt_task WHERE id = ?", taskId);
        if (taskRows.isEmpty()) return out;
        Long templateId = ((Number) taskRows.get(0).get("template_id")).longValue();
        Map<String, Object> cfg = getConfig(templateId);
        int type = cfg.get("configType") == null ? 0 : ((Number) cfg.get("configType")).intValue();
        out.put("configType", type);
        out.put("columns", cfg.get("refColumns"));
        if (type == 1) {
            Long refTemplateId = ((Number) cfg.get("refTemplateId")).longValue();
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT d.data_json FROM rpt_task_data d JOIN rpt_task t ON t.id = d.task_id "
                            + "WHERE t.template_id = ? AND (? IS NULL OR d.dept_id = ?) AND d.status = 1 "
                            + "ORDER BY d.id DESC LIMIT 100", refTemplateId, deptId, deptId);
            out.put("rows", rows);
        } else if (type == 2) {
            Long dsId = ((Number) cfg.get("refDatasourceId")).longValue();
            String table = String.valueOf(cfg.get("refTableName"));
            RptDatasource ds = rptDatasourceService.getForConnect(dsId);
            List<Map<String, Object>> rows = new ArrayList<>();
            try (Connection conn = targetDataSourceManager.getConnection(ds)) {
                String cols = StrUtil.nullToEmpty((String) cfg.get("refColumns")).replace("[", "").replace("]", "")
                        .replace("\"", "");
                String select = cols.isEmpty() ? "*" : cols;
                try (ResultSet rs = conn.createStatement().executeQuery(
                        "SELECT " + select + " FROM " + table + " LIMIT 100")) {
                    int n = rs.getMetaData().getColumnCount();
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= n; i++) row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
                        rows.add(row);
                    }
                }
            } catch (Exception e) {
                out.put("message", "读取参考数据失败：" + e.getMessage());
            }
            out.put("rows", rows);
        }
        return out;
    }
}
