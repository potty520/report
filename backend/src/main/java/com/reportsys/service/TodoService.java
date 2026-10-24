package com.reportsys.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reportsys.dao.RptTaskAssignMapper;
import com.reportsys.dao.RptTaskMapper;
import com.reportsys.dao.SysMessageMapper;
import com.reportsys.model.RptTask;
import com.reportsys.model.RptTaskAssign;
import com.reportsys.model.SysMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 待办与站内消息（P1-F，替代原版短信催办 + 待办统计）。
 *
 * 消息类型：TODO 待办 / RETURN 退回 / OVERDUE 超期 / REMIND 催办 / SYSTEM 系统
 */
@Service
public class TodoService {

    private static final Logger log = LoggerFactory.getLogger(TodoService.class);

    @Autowired
    private SysMessageMapper sysMessageMapper;
    @Autowired
    private RptTaskAssignMapper rptTaskAssignMapper;
    @Autowired
    private RptTaskMapper rptTaskMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SmsService smsService;
    @Autowired
    private StatisticsRuleService statisticsRuleService;

    /** 发一条站内消息 */
    @Transactional
    public void send(Long userId, String title, String content, String msgType,
                     Long taskId, Long dataId, Long createBy) {
        if (userId == null || StrUtil.isBlank(title)) return;
        SysMessage message = new SysMessage();
        message.setUserId(userId);
        message.setTitle(StrUtil.maxLength(title, 120));
        message.setContent(StrUtil.maxLength(StrUtil.nullToEmpty(content), 900));
        message.setMsgType(StrUtil.isBlank(msgType) ? "SYSTEM" : msgType);
        message.setBizTaskId(taskId);
        message.setDataId(dataId);
        message.setReadFlag(0);
        message.setCreateBy(createBy);
        message.setCreateTime(LocalDateTime.now());
        sysMessageMapper.insert(message);
    }

    /** 通知任务的所有填报分派人（无人分派时通知创建人） */
    @Transactional
    public int notifyAssignees(Long taskId, String title, String content, String msgType, Long createBy) {
        Set<Long> recipients = new LinkedHashSet<>();
        List<RptTaskAssign> assigns = rptTaskAssignMapper.selectList(Wrappers.<RptTaskAssign>lambdaQuery()
                .eq(RptTaskAssign::getTaskId, taskId)
                .eq(RptTaskAssign::getAssignType, "FILL"));
        for (RptTaskAssign assign : assigns) {
            if (assign.getUserId() != null) recipients.add(assign.getUserId());
        }
        if (recipients.isEmpty()) {
            RptTask task = rptTaskMapper.selectById(taskId);
            if (task != null && task.getCreateBy() != null) recipients.add(task.getCreateBy());
        }
        for (Long userId : recipients) {
            // 同一任务同一类型只保留一条未读：原实现每次分派/催办都插一条，
            // 反复分派会把「未读消息」堆到几百条（与真实待办数完全脱节）。
            if (hasUnread(userId, taskId, msgType)) continue;
            send(userId, title, content, msgType, taskId, null, createBy);
        }
        return recipients.size();
    }

    /** 是否已有同任务、同类型的未读消息 */
    private boolean hasUnread(Long userId, Long taskId, String msgType) {
        Long n = sysMessageMapper.selectCount(Wrappers.<SysMessage>lambdaQuery()
                .eq(SysMessage::getUserId, userId)
                .eq(SysMessage::getBizTaskId, taskId)
                .eq(SysMessage::getMsgType, StrUtil.isBlank(msgType) ? "SYSTEM" : msgType)
                .eq(SysMessage::getReadFlag, 0));
        return n != null && n > 0;
    }

    /**
     * 待办汇总。
     *
     * 缺陷修复：「待办」原先取的是<b>未读站内消息数</b>、doneCount 取已读消息数，
     * 而分派/催办/超期扫描每次都会发消息且没有按任务去重，数字只增不减——
     * 实测一个只有 52 个分派任务、1 条填报数据的用户，接口返回「待办 158」。
     *
     * 现在 todoCount 按真实待办计算（待填 + 待审 + 待批），消息未读单独放在 unreadCount，
     * 前端顶栏红点用 unreadCount，待办卡片用 todoCount。
     */
    public Map<String, Object> summary(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 待填报：分派给我、任务未结束、且我还没有已提交的数据
        long fillTodo = count(
                "SELECT COUNT(*) FROM rpt_task t WHERE t.status IN (1,2) "
                        + "AND EXISTS (SELECT 1 FROM rpt_task_assign a WHERE a.task_id = t.id "
                        + "AND a.assign_type = 'FILL' AND a.user_id = ?) "
                        + "AND NOT EXISTS (SELECT 1 FROM rpt_task_data d WHERE d.task_id = t.id "
                        + "AND d.fill_user_id = ? AND d.status = 1)", userId, userId);

        // 待审核：分派我为审核人、已提交且还没有一级审核结论的数据（排除自己提交的）
        long auditTodo = count(
                "SELECT COUNT(DISTINCT d.id) FROM rpt_task_data d "
                        + "JOIN rpt_task_assign a ON a.task_id = d.task_id "
                        + "AND a.assign_type = 'AUDIT' AND a.user_id = ? "
                        + "WHERE d.status = 1 AND COALESCE(d.audit_status, 0) = 0 "
                        + "AND (d.fill_user_id IS NULL OR d.fill_user_id <> ?)", userId, userId);

        // 待审批：只有具备审批权限的角色才统计数据申请
        long applyTodo = 0L;
        if (isApprover(userId)) {
            applyTodo = count("SELECT COUNT(*) FROM rpt_data_apply ap WHERE ap.status = 0 "
                    + "AND (ap.applicant_id IS NULL OR ap.applicant_id <> ?)", userId);
        }

        long unread = count("SELECT COUNT(*) FROM sys_message WHERE user_id = ? AND read_flag = 0", userId);
        long read = count("SELECT COUNT(*) FROM sys_message WHERE user_id = ? AND read_flag = 1", userId);
        long today = count("SELECT COUNT(*) FROM sys_message WHERE user_id = ? AND DATE(create_time) = CURDATE()", userId);
        // 已办：我提交且审核通过的填报 + 我审过的数据
        long done = count("SELECT COUNT(*) FROM rpt_task_data d WHERE "
                + "(d.fill_user_id = ? AND d.status = 1 AND d.audit_status = 1) "
                + "OR (d.audit_user_id = ? AND d.audit_status IN (1,2))", userId, userId);
        long overdue = count(
                "SELECT COUNT(*) FROM rpt_task t WHERE t.status IN (1,2) AND t.fill_deadline IS NOT NULL "
                        + "AND t.fill_deadline < NOW() AND NOT EXISTS (SELECT 1 FROM rpt_task_data d "
                        + "WHERE d.task_id = t.id AND d.status = 1) AND (t.create_by = ? OR EXISTS ("
                        + "SELECT 1 FROM rpt_task_assign a WHERE a.task_id = t.id AND a.user_id = ?))",
                userId, userId);

        result.put("todoCount", fillTodo + auditTodo + applyTodo);
        result.put("fillTodo", fillTodo);
        result.put("auditTodo", auditTodo);
        result.put("applyTodo", applyTodo);
        result.put("doneCount", done);
        result.put("unreadCount", unread);
        result.put("readCount", read);
        result.put("todayCount", today);
        result.put("overdueCount", overdue);
        return result;
    }

    /** 是否具备数据申请审批权限（审批列表 /dataApply/approveList 的角色口径） */
    private boolean isApprover(Long userId) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id "
                        + "WHERE ur.user_id = ? AND r.role_code IN ('SUPER_ADMIN','DEPT_ADMIN','AUDIT_USER')",
                Long.class, userId);
        return n != null && n > 0;
    }

    /** 计数查询（单值） */
    private long count(String sql, Object... args) {
        try {
            Long n = jdbcTemplate.queryForObject(sql, Long.class, args);
            return n == null ? 0L : n;
        } catch (Exception e) {
            log.warn("待办统计失败: {} ({})", e.getMessage(), sql);
            return 0L;
        }
    }

    /** 消息列表（分页） */
    public Map<String, Object> list(Long userId, int page, int pageSize, Integer readFlag, String msgType) {
        Page<SysMessage> pageObj = new Page<>(page, pageSize);
        Map<String, Object> result = new LinkedHashMap<>();
        LambdaQueryWrapper<SysMessage> wrapper = Wrappers.<SysMessage>lambdaQuery()
                .eq(SysMessage::getUserId, userId)
                .eq(readFlag != null, SysMessage::getReadFlag, readFlag)
                .eq(StrUtil.isNotBlank(msgType), SysMessage::getMsgType, msgType)
                .orderByDesc(SysMessage::getCreateTime);
        Page<SysMessage> data = sysMessageMapper.selectPage(pageObj, wrapper);
        result.put("total", data.getTotal());
        result.put("list", data.getRecords());
        return result;
    }

    @Transactional
    public void markRead(Long id, Long userId) {
        SysMessage message = sysMessageMapper.selectById(id);
        if (message == null || !message.getUserId().equals(userId)) {
            throw new RuntimeException("消息不存在");
        }
        message.setReadFlag(1);
        message.setReadTime(LocalDateTime.now());
        sysMessageMapper.updateById(message);
    }

    @Transactional
    public int markAllRead(Long userId) {
        List<SysMessage> unread = sysMessageMapper.selectList(Wrappers.<SysMessage>lambdaQuery()
                .eq(SysMessage::getUserId, userId).eq(SysMessage::getReadFlag, 0));
        LocalDateTime now = LocalDateTime.now();
        for (SysMessage message : unread) {
            message.setReadFlag(1);
            message.setReadTime(now);
            sysMessageMapper.updateById(message);
        }
        return unread.size();
    }

    /** 催办：给任务填报人发催办消息（站内消息 + 可插拔短信通道） */
    @Transactional
    public Map<String, Object> remind(Long taskId, String comment, Long operatorId) {
        RptTask task = rptTaskMapper.selectById(taskId);
        if (task == null) throw new RuntimeException("任务不存在");
        String title = "【催办】" + task.getTaskName();
        String content = StrUtil.isBlank(comment) ? "请尽快完成填报并提交。" : comment;
        int sent = notifyAssignees(taskId, title, content, "REMIND", operatorId);

        // 短信催办：走可插拔通道（REPORT_SMS_ENABLED=false 时只记 rpt_sms_log，不真正发送）。
        // 短信失败不影响站内消息与主流程。
        List<Map<String, Object>> smsResults = new ArrayList<>();
        try {
            List<Map<String, Object>> phones = jdbcTemplate.queryForList(
                    "SELECT DISTINCT u.id AS userId, u.phone AS phone FROM rpt_task_assign a "
                            + "JOIN sys_user u ON u.id = a.user_id "
                            + "WHERE a.task_id = ? AND a.assign_type = 'FILL' "
                            + "AND u.phone IS NOT NULL AND u.phone <> ''", taskId);
            for (Map<String, Object> row : phones) {
                Object uid = row.get("userId");
                Long recipient = uid instanceof Number ? ((Number) uid).longValue() : null;
                smsResults.add(smsService.sendRemind(String.valueOf(row.get("phone")),
                        task.getTaskName(), content, recipient, taskId));
            }
        } catch (Exception e) {
            log.warn("短信催办失败（不影响站内消息）: {}", e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("sent", sent);
        result.put("smsSent", smsResults.size());
        result.put("smsProvider", smsService.currentProvider());
        result.put("smsEnabled", smsService.isEnabled());
        result.put("sms", smsResults);
        return result;
    }

    /**
     * 扫描超期未提交的任务并通知（每天由定时任务调用；同一天同一任务同一人只发一次）
     */
    @Transactional
    public Map<String, Object> scanOverdue(Long operatorId) {
        // 超期判定可配置（rpt_statistics_rule.OVERDUE_REMIND.grace_days）：
        // 默认宽限 0 天，SQL 与配置前完全等价。
        int graceDays = statisticsRuleService.overdueGraceDays();
        List<Map<String, Object>> tasks = jdbcTemplate.queryForList(
                "SELECT t.id, t.task_name FROM rpt_task t WHERE t.status IN (1,2) "
                        + "AND t.fill_deadline IS NOT NULL "
                        + "AND t.fill_deadline < DATE_SUB(NOW(), INTERVAL ? DAY) "
                        + "AND NOT EXISTS (SELECT 1 FROM rpt_task_data d WHERE d.task_id = t.id AND d.status = 1)",
                graceDays);
        int notified = 0;
        List<String> details = new ArrayList<>();
        for (Map<String, Object> row : tasks) {
            Long taskId = ((Number) row.get("id")).longValue();
            String taskName = String.valueOf(row.get("task_name"));
            Set<Long> recipients = new LinkedHashSet<>();
            for (RptTaskAssign assign : rptTaskAssignMapper.selectList(Wrappers.<RptTaskAssign>lambdaQuery()
                    .eq(RptTaskAssign::getTaskId, taskId)
                    .eq(RptTaskAssign::getAssignType, "FILL"))) {
                if (assign.getUserId() != null) recipients.add(assign.getUserId());
            }
            if (recipients.isEmpty()) {
                RptTask task = rptTaskMapper.selectById(taskId);
                if (task != null && task.getCreateBy() != null) recipients.add(task.getCreateBy());
            }
            for (Long userId : recipients) {
                Long exists = sysMessageMapper.selectCount(Wrappers.<SysMessage>lambdaQuery()
                        .eq(SysMessage::getUserId, userId)
                        .eq(SysMessage::getBizTaskId, taskId)
                        .eq(SysMessage::getMsgType, "OVERDUE")
                        .apply("DATE(create_time) = CURDATE()"));
                if (exists != null && exists > 0) continue;
                send(userId, "【超期】" + taskName, "该任务已超过填报截止时间，请尽快处理。", "OVERDUE", taskId, null, operatorId);
                notified++;
            }
            details.add(taskName);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overdueTasks", tasks.size());
        result.put("notified", notified);
        result.put("tasks", details);
        log.info("超期扫描完成: 任务 {} 个，通知 {} 人", tasks.size(), notified);
        return result;
    }

    /** 未读消息数（给前端红点用） */
    public long unreadCount(Long userId) {
        Long count = sysMessageMapper.selectCount(Wrappers.<SysMessage>lambdaQuery()
                .eq(SysMessage::getUserId, userId).eq(SysMessage::getReadFlag, 0));
        return count == null ? 0L : count;
    }

    /** 今日已发送的某类消息数（幂等校验辅助） */
    public long todayCount(Long userId, String msgType) {
        Long count = sysMessageMapper.selectCount(Wrappers.<SysMessage>lambdaQuery()
                .eq(SysMessage::getUserId, userId)
                .eq(SysMessage::getMsgType, msgType)
                .apply("DATE(create_time) = ?", LocalDate.now()));
        return count == null ? 0L : count;
    }
}
