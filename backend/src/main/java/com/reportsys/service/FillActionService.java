package com.reportsys.service;

import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** R-02 无更新提交 与填报侧辅助动作。 */
@Service
public class FillActionService {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ProcessLogService processLog;

    @Transactional
    public void noUpdate(Long taskId, String reason, Long userId) {
        if (taskId == null) throw new IllegalStateException("缺少任务ID");
        if (StrUtil.isBlank(reason)) throw new IllegalStateException("请填写无更新说明");
        if (reason.length() > 200) throw new IllegalStateException("无更新说明不能超过 200 字");
        List<Map<String, Object>> tasks = jdbc.queryForList(
                "SELECT id, filling_cycle, status FROM rpt_task WHERE id = ?", taskId);
        if (tasks.isEmpty()) throw new IllegalStateException("任务不存在");
        int cycle = tasks.get(0).get("filling_cycle") == null ? 0 : ((Number) tasks.get(0).get("filling_cycle")).intValue();
        if (cycle == 6) throw new IllegalStateException("一次性任务不支持无更新提交");
        Long submitted = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rpt_task_data WHERE task_id = ? AND fill_user_id = ? AND status >= 1",
                Long.class, taskId, userId);
        if (submitted != null && submitted > 0) throw new IllegalStateException("本期已提交数据，不能再提交无更新");

        List<Long> exists = jdbc.queryForList(
                "SELECT id FROM rpt_task_data WHERE task_id = ? AND fill_user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, taskId, userId);
        if (exists.isEmpty()) {
            jdbc.update("INSERT INTO rpt_task_data (task_id, template_id, dept_id, fill_user_id, status, no_update, "
                            + "no_update_reason, inbound_status, create_time) "
                            + "SELECT t.id, t.template_id, t.dept_id, ?, 1, 1, ?, 0, NOW() FROM rpt_task t WHERE t.id = ?",
                    userId, StrUtil.maxLength(reason, 200), taskId);
        } else {
            jdbc.update("UPDATE rpt_task_data SET status = 1, no_update = 1, no_update_reason = ?, "
                    + "submit_time = NOW() WHERE id = ?", StrUtil.maxLength(reason, 200), exists.get(0));
        }
        processLog.log(taskId, null, "FILL", "无更新提交", "NO_UPDATE", userId, reason);
    }

    public Map<String, Object> extra(Long dataId) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (dataId == null) return m;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT no_update AS noUpdate, no_update_reason AS noUpdateReason FROM rpt_task_data WHERE id = ?", dataId);
        if (!rows.isEmpty()) m.putAll(rows.get(0));
        return m;
    }
}
