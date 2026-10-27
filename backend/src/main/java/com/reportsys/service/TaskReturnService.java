package com.reportsys.service;

import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务退回与处理（R-03 分派退回 / R-04 填报退回 / R-05 撤销·变更部门·重新提交）。
 *
 * 「退回」作用在任务上（提交/审核之前），与审核环节的「驳回」（作用在数据上）区分。
 */
@Service
public class TaskReturnService {

    public static final int TYPE_ASSIGN = 1;
    public static final int TYPE_FILL = 2;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ProcessLogService processLog;

    /** R-03：分派人对「尚未分派填报人」的任务退回给制定人 */
    @Transactional
    public void returnByAssigner(Long taskId, Long operatorId, String reason) {
        Map<String, Object> task = loadTask(taskId);
        Long fillAssigns = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rpt_task_assign WHERE task_id = ? AND assign_type = 'FILL'",
                Long.class, taskId);
        if (fillAssigns != null && fillAssigns > 0) {
            throw new IllegalStateException("任务已分派给填报人，不可退回");
        }
        doReturn(taskId, TYPE_ASSIGN, operatorId, toLong(task.get("create_by")),
                toLong(task.get("dept_id")), reason);
        notify(toLong(task.get("create_by")), "任务被退回", "任务「" + task.get("task_name") + "」被退回，请处理");
        processLog.log(taskId, null, "TASK_RETURN", "任务分派退回", "RETURN_BY_ASSIGN", operatorId, reason);
    }

    /** R-04：填报人对「尚未提交」的任务退回给分派人 */
    @Transactional
    public void returnByFiller(Long taskId, Long operatorId, String reason) {
        Map<String, Object> task = loadTask(taskId);
        Long submitted = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rpt_task_data WHERE task_id = ? AND fill_user_id = ? AND status >= 1",
                Long.class, taskId, operatorId);
        if (submitted != null && submitted > 0) {
            throw new IllegalStateException("该任务已提交数据，不能退回");
        }
        Long deptId = toLong(task.get("dept_id"));
        doReturn(taskId, TYPE_FILL, operatorId, null, deptId, reason);
        // 通知该部门的部门管理员（分派角色）
        List<Long> managers = jdbc.queryForList(
                "SELECT DISTINCT u.id FROM sys_user u JOIN sys_user_role ur ON ur.user_id = u.id "
                        + "JOIN sys_role r ON r.id = ur.role_id WHERE r.role_code = 'DEPT_ADMIN' AND u.dept_id = ?",
                Long.class, deptId);
        for (Long uid : managers) {
            notify(uid, "填报任务被退回", "任务「" + task.get("task_name") + "」被填报人退回，请重新分派");
        }
        processLog.log(taskId, null, "TASK_RETURN", "数据填报退回", "RETURN_BY_FILLER", operatorId, reason);
    }

    /** 制定人的「被退回任务」列表 */
    public Map<String, Object> listReturned(Long userId, int page, int pageSize) {
        int offset = Math.max(0, (page - 1) * pageSize);
        String base = "FROM rpt_task t LEFT JOIN sys_dept d ON d.id = t.dept_id WHERE t.return_status = 1 AND t.create_by = ?";
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + base, Long.class, userId);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT t.id, t.task_name AS taskName, t.filling_cycle AS fillingCycle, t.dept_id AS deptId, "
                        + "t.return_reason AS returnReason, t.return_time AS returnTime, d.dept_name AS deptName "
                        + base + " ORDER BY t.return_time DESC LIMIT ? OFFSET ?", userId, pageSize, offset);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total == null ? 0 : total);
        out.put("list", rows);
        return out;
    }

    /** R-05：被退回任务的三种处理动作 */
    @Transactional
    public void handle(Long taskId, String action, Long deptId, Long operatorId, String reason) {
        Map<String, Object> task = loadTask(taskId);
        Integer returnStatus = task.get("return_status") == null ? 0 : ((Number) task.get("return_status")).intValue();
        if (returnStatus != 1) {
            throw new IllegalStateException("仅被退回的任务可以执行该操作");
        }
        int handleType;
        switch (StrUtil.nullToEmpty(action)) {
            case "revoke":
                // 撤销：任务关闭，不再下发
                jdbc.update("UPDATE rpt_task SET status = 4, return_status = 0, update_time = NOW() WHERE id = ?", taskId);
                handleType = 1;
                processLog.log(taskId, null, "TASK_RETURN", "任务撤销", "REVOKE", operatorId, reason);
                break;
            case "changeDept":
                if (deptId == null) throw new IllegalStateException("请选择新的填报部门");
                jdbc.update("UPDATE rpt_task SET dept_id = ?, status = 1, return_status = 0, update_time = NOW() WHERE id = ?",
                        deptId, taskId);
                jdbc.update("DELETE FROM rpt_task_assign WHERE task_id = ? AND assign_type = 'FILL'", taskId);
                handleType = 2;
                processLog.log(taskId, null, "TASK_RETURN", "变更填报部门", "CHANGE_DEPT", operatorId, reason);
                break;
            case "resubmit":
                jdbc.update("UPDATE rpt_task SET status = 1, return_status = 0, update_time = NOW() WHERE id = ?", taskId);
                handleType = 3;
                processLog.log(taskId, null, "TASK_RETURN", "重新提交", "RESUBMIT", operatorId, reason);
                break;
            default:
                throw new IllegalStateException("不支持的操作: " + action);
        }
        jdbc.update("UPDATE rpt_task_return SET status = 1, handle_type = ?, handle_by = ?, handle_time = NOW() "
                + "WHERE task_id = ? AND status = 0", handleType, operatorId, taskId);
    }

    public Map<String, Object> detail(Long taskId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, task_id AS taskId, return_type AS returnType, reason, status, handle_type AS handleType, "
                        + "handle_by AS handleBy, handle_time AS handleTime, create_time AS createTime "
                        + "FROM rpt_task_return WHERE task_id = ? ORDER BY id DESC", taskId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", rows);
        return out;
    }

    // ---------------------------------------------------------------- 内部方法
    private void doReturn(Long taskId, int type, Long fromUser, Long toUser, Long deptId, String reason) {
        jdbc.update("UPDATE rpt_task SET return_status = ?, return_reason = ?, return_by = ?, return_time = NOW(), "
                        + "update_time = NOW() WHERE id = ?",
                type == TYPE_ASSIGN ? 1 : 2, StrUtil.maxLength(StrUtil.nullToEmpty(reason), 500), fromUser, taskId);
        jdbc.update("INSERT INTO rpt_task_return (task_id, return_type, from_user_id, to_user_id, from_dept_id, "
                        + "reason, status, create_time) VALUES (?,?,?,?,?,?,0,NOW())",
                taskId, type, fromUser, toUser, deptId, StrUtil.maxLength(StrUtil.nullToEmpty(reason), 500));
    }

    private Map<String, Object> loadTask(Long taskId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, task_name, status, create_by, dept_id, return_status FROM rpt_task WHERE id = ?", taskId);
        if (rows.isEmpty()) throw new IllegalStateException("任务不存在");
        Map<String, Object> task = rows.get(0);
        int status = ((Number) task.get("status")).intValue();
        if (status >= 3) throw new IllegalStateException("任务已完成或已关闭，不能退回");
        return task;
    }

    private void notify(Long userId, String title, String content) {
        if (userId == null) return;
        try {
            jdbc.update("INSERT INTO sys_message (user_id, title, content, msg_type, read_flag, create_time) "
                    + "VALUES (?,?,?,'RETURN',0,NOW())", userId, title, StrUtil.maxLength(content, 500));
        } catch (Exception ignored) {
        }
    }

    private Long toLong(Object o) {
        return o == null ? null : ((Number) o).longValue();
    }
}
