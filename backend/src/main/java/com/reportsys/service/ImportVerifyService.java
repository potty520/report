package com.reportsys.service;

import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * R-09 报表核验（stage=1）与数据导入预校验（stage=2）。
 *
 * - stage1：表头字段齐全、必填项非空、基本类型（数字/日期）可解析；
 * - stage2：长度超限、主键/业务键重复（文件内）；
 * 每次校验写入 rpt_import_check，返回逐行错误明细（行号 + 列 + 原因）。
 */
@Service
public class ImportVerifyService {

    @Autowired
    private JdbcTemplate jdbc;

    public Map<String, Object> verify(Long taskId, List<Map<String, Object>> rows, Long userId) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> fields = jdbc.queryForList(
                "SELECT f.field_code AS fieldCode, f.field_name AS fieldName, f.field_type AS fieldType, "
                        + "f.field_length AS fieldLength, f.is_required AS isRequired, f.is_key AS isKey "
                        + "FROM rpt_template_field f JOIN rpt_task t ON t.template_id = f.template_id "
                        + "WHERE t.id = ? ORDER BY f.sort", taskId);
        if (fields.isEmpty()) throw new IllegalStateException("任务模板没有字段");

        List<Map<String, Object>> stage1 = new ArrayList<>();
        List<Map<String, Object>> stage2 = new ArrayList<>();

        // ---------- stage 1
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            for (Map<String, Object> f : fields) {
                String code = str(f.get("fieldCode"));
                String name = str(f.get("fieldName"));
                Object v = row.get(code) != null ? row.get(code) : row.get(name);
                String text = v == null ? "" : String.valueOf(v).trim();
                if (intOf(f.get("isRequired")) == 1 && text.isEmpty()) {
                    stage1.add(err(i + 1, name, "必填项为空"));
                    continue;
                }
                if (text.isEmpty()) continue;
                String type = str(f.get("fieldType")).toUpperCase();
                if (isNumeric(type) && !text.matches("-?\\d+(\\.\\d+)?")) {
                    stage1.add(err(i + 1, name, "应为数值，实际为「" + trunc(text) + "」"));
                }
                if ("DATE".equals(type) || "DATETIME".equals(type)) {
                    if (!text.matches("\\d{4}-\\d{2}-\\d{2}([ T]\\d{2}:\\d{2}(:\\d{2})?)?")) {
                        stage1.add(err(i + 1, name, "日期格式应为 yyyy-MM-dd"));
                    }
                }
            }
        }

        // ---------- stage 2
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            StringBuilder key = new StringBuilder();
            for (Map<String, Object> f : fields) {
                String code = str(f.get("fieldCode"));
                String name = str(f.get("fieldName"));
                Object v = row.get(code) != null ? row.get(code) : row.get(name);
                String text = v == null ? "" : String.valueOf(v).trim();
                if (intOf(f.get("isKey")) == 1) key.append(text).append('|');
                Integer len = f.get("fieldLength") == null ? null : ((Number) f.get("fieldLength")).intValue();
                if (len != null && len > 0 && text.length() > len && !isNumeric(str(f.get("fieldType")))) {
                    stage2.add(err(i + 1, name, "长度 " + text.length() + " 超过限制 " + len));
                }
            }
            if (key.length() > 0 && !keys.add(key.toString())) {
                stage2.add(err(i + 1, "主键", "与前面的行重复：" + key));
            }
        }

        Map<String, Object> s1 = stage(stage1, rows.size());
        Map<String, Object> s2 = stage(stage2, rows.size());
        out.put("stage1", s1);
        out.put("stage2", s2);
        out.put("passed", Boolean.TRUE.equals(s1.get("passed")) && Boolean.TRUE.equals(s2.get("passed")));

        record(taskId, 1, rows.size(), s1, userId);
        record(taskId, 2, rows.size(), s2, userId);
        return out;
    }

    private void record(Long taskId, int stage, int total, Map<String, Object> result, Long userId) {
        try {
            int fail = ((List<?>) result.get("errors")).size();
            jdbc.update("INSERT INTO rpt_import_check (task_id, check_stage, total_rows, pass_rows, fail_rows, "
                            + "passed, detail_json, create_by, create_time) VALUES (?,?,?,?,?,?,?,?,NOW())",
                    taskId, stage, total, Math.max(0, total - fail), fail,
                    Boolean.TRUE.equals(result.get("passed")) ? 1 : 0,
                    StrUtil.maxLength(String.valueOf(result.get("errors")), 60000), userId);
        } catch (Exception ignored) {
        }
    }

    private Map<String, Object> stage(List<Map<String, Object>> errors, int total) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("passed", errors.isEmpty());
        m.put("totalRows", total);
        m.put("failCount", errors.size());
        m.put("errors", errors);
        return m;
    }

    private Map<String, Object> err(int rowNo, String column, String reason) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("row", rowNo);
        e.put("column", column);
        e.put("reason", reason);
        return e;
    }

    private boolean isNumeric(String type) {
        return "INT".equals(type) || "BIGINT".equals(type) || "DECIMAL".equals(type)
                || "NUMBER".equals(type) || "DOUBLE".equals(type) || "FLOAT".equals(type);
    }

    private int intOf(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private String trunc(String s) {
        return s.length() > 20 ? s.substring(0, 20) + "…" : s;
    }
}
