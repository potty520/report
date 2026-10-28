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
        notifyByAction(taskId, dataId, nodeCode, action, operatorId, comment);
    }

    /** 按轨迹动作发送站内消息（模板审核通过、分派、审核通过、入库、补正发起） */
    private void notifyByAction(Long taskId, Long dataId, String nodeCode, String action,
                                Long operatorId, String comment) {
        try {
            Long target = null;
            String title = null;
            if ("CORRECT".equals(nodeCode) && "CORRECT_START".equals(action)) {
                // 补正发起：通知该任务的填报人
                java.util.List<Long> users = jdbcTemplate.queryForList(
                        "SELECT DISTINCT user_id FROM rpt_task_assign WHERE task_id = ? AND assign_type = 'FILL'",
                        Long.class, taskId);
                for (Long u : users) {
                    insertMessage(u, "补正通知", "任务「" + taskName(taskId) + "」发起补正，请重新填报", "TODO", taskId, dataId);
                }
                return;
            }
            if ("PASS".equals(action)) {
                if ("DEPT_AUDIT".equals(nodeCode)) title = "部门审核通过";
                else if ("TASK_AUDIT".equals(nodeCode)) title = "任务方审核通过";
                else if ("INBOUND".equals(nodeCode)) title = "数据已入库";
                else if ("AUDIT".equals(nodeCode)) title = "审核通过";
            }
            if (title == null || dataId == null) return;
            java.util.List<Long> fillUsers = jdbcTemplate.queryForList(
                    "SELECT fill_user_id FROM rpt_task_data WHERE id = ? AND fill_user_id IS NOT NULL",
                    Long.class, dataId);
            target = fillUsers.isEmpty() ? null : fillUsers.get(0);
            if (target != null) {
                insertMessage(target, title, "任务「" + taskName(taskId) + "」：" + title, "TODO", taskId, dataId);
            }
        } catch (Exception ignored) {
        }
    }

    private String taskName(Long taskId) {
        try {
            return jdbcTemplate.queryForObject("SELECT task_name FROM rpt_task WHERE id = ?", String.class, taskId);
        } catch (Exception e) {
            return String.valueOf(taskId);
        }
    }

    private void insertMessage(Long userId, String title, String content, String type, Long taskId, Long dataId) {
        if (userId == null) return;
        try {
            jdbcTemplate.update("INSERT INTO sys_message (user_id, title, content, msg_type, biz_task_id, data_id, "
                            + "read_flag, create_time) VALUES (?,?,?,?,?,?,0,NOW())",
                    userId, title, StrUtil.maxLength(content, 500), type, taskId, dataId);
        } catch (Exception ignored) {
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
