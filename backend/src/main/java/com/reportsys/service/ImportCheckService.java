package com.reportsys.service;

import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** R-09 报表核验 / 数据导入预校验：记录每次校验的逐项结果。 */
@Service
public class ImportCheckService {

    @Autowired
    private JdbcTemplate jdbc;

    public void record(Long taskId, Long taskDataId, int stage, int total, int pass, int fail,
                       List<String> errors, Long userId) {
        String detail = errors == null || errors.isEmpty() ? "[]" : "[" + String.join(",", errors) + "]";
        jdbc.update("INSERT INTO rpt_import_check (task_data_id, task_id, check_stage, total_rows, pass_rows, "
                        + "fail_rows, passed, detail_json, create_by, create_time) VALUES (?,?,?,?,?,?,?,?,?,NOW())",
                taskDataId, taskId, stage, total, pass, fail, fail == 0 ? 1 : 0,
                StrUtil.maxLength(detail, 60000), userId);
    }

    public Map<String, Object> list(Long taskId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", jdbc.queryForList(
                "SELECT id, check_stage AS checkStage, total_rows AS totalRows, pass_rows AS passRows, "
                        + "fail_rows AS failRows, passed, detail_json AS detailJson, create_time AS createTime "
                        + "FROM rpt_import_check WHERE task_id = ? ORDER BY id DESC LIMIT 20", taskId));
        return out;
    }
}
