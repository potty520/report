package com.reportsys.service;

import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 任务/数据流转轨迹（替代原版 Activiti 中"可见的那部分"：谁在什么节点做了什么）。
 * 落表 rpt_task_process，供前端 Timeline 展示。
 */
@Service
public class ProcessLogService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void log(Long taskId, Long dataId, String nodeCode, String nodeName, String action,
                    Long operatorId, String comment) {
        if (taskId == null) return;
        String operatorName = lookupName(operatorId);
        try {
            jdbcTemplate.update(
                    "INSERT INTO rpt_task_process (task_id, data_id, node_code, node_name, action, operator_id, "
                            + "operator_name, comment, create_time) VALUES (?,?,?,?,?,?,?,?,NOW())",
                    taskId, dataId, nodeCode, nodeName, action, operatorId, operatorName,
                    StrUtil.maxLength(StrUtil.nullToEmpty(comment), 500));
        } catch (Exception ignored) {
            // 轨迹记录失败不应影响主流程
        }
    }

    private String lookupName(Long userId) {
        if (userId == null) return "";
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT COALESCE(NULLIF(real_name,''), username) FROM sys_user WHERE id = ?", String.class, userId);
        } catch (Exception e) {
            return "";
        }
    }
}
