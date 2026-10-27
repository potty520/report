package com.reportsys.service;

import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** R-14 线上补正明细：行级新增 / 修改 / 删除，保留原值。 */
@Service
public class CorrectItemService {

    @Autowired
    private JdbcTemplate jdbc;

    public List<Map<String, Object>> list(Long correctId) {
        return jdbc.queryForList(
                "SELECT id, correct_id AS correctId, task_id AS taskId, task_data_id AS taskDataId, "
                        + "op_type AS opType, row_key AS rowKey, before_json AS beforeJson, after_json AS afterJson, "
                        + "create_time AS createTime FROM rpt_correct_item WHERE correct_id = ? ORDER BY id", correctId);
    }

    public void add(Long correctId, Integer opType, String rowKey, String beforeJson, String afterJson, Long userId) {
        if (correctId == null) throw new IllegalStateException("缺少补正单ID");
        if (opType == null || opType < 1 || opType > 3) throw new IllegalStateException("操作类型不合法");
        Long taskId = null;
        try {
            List<Long> t = jdbc.queryForList("SELECT task_id FROM rpt_correct_task WHERE id = ?", Long.class, correctId);
            if (!t.isEmpty()) taskId = t.get(0);
        } catch (Exception ignored) {
        }
        jdbc.update("INSERT INTO rpt_correct_item (correct_id, task_id, op_type, row_key, before_json, after_json, "
                        + "create_by, create_time) VALUES (?,?,?,?,?,?,?,NOW())",
                correctId, taskId, opType, StrUtil.maxLength(StrUtil.nullToEmpty(rowKey), 128),
                beforeJson, afterJson, userId);
    }

    public void delete(Long itemId) {
        jdbc.update("DELETE FROM rpt_correct_item WHERE id = ?", itemId);
    }

    public Map<String, Object> summary(Long correctId) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT op_type, COUNT(*) c FROM rpt_correct_item WHERE correct_id = ? GROUP BY op_type", correctId)) {
            out.put("op" + row.get("op_type"), row.get("c"));
        }
        return out;
    }
}
