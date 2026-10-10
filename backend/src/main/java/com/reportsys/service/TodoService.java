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
            send(userId, title, content, msgType, taskId, null, createBy);
        }
        return recipients.size();
    }

    /** 待办汇总 */
    public Map<String, Object> summary(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        Long unread = sysMessageMapper.selectCount(Wrappers.<SysMessage>lambdaQuery()
                .eq(SysMessage::getUserId, userId).eq(SysMessage::getReadFlag, 0));
        Long read = sysMessageMapper.selectCount(Wrappers.<SysMessage>lambdaQuery()
                .eq(SysMessage::getUserId, userId).eq(SysMessage::getReadFlag, 1));
        Long today = sysMessageMapper.selectCount(Wrappers.<SysMessage>lambdaQuery()
                .eq(SysMessage::getUserId, userId).apply("DATE(create_time) = CURDATE()"));
        Long overdue = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rpt_task t WHERE t.status IN (1,2) AND t.fill_deadline IS NOT NULL "
                        + "AND t.fill_deadline < NOW() AND NOT EXISTS (SELECT 1 FROM rpt_task_data d "
                        + "WHERE d.task_id = t.id AND d.status = 1) AND (t.create_by = ? OR EXISTS ("
                        + "SELECT 1 FROM rpt_task_assign a WHERE a.task_id = t.id AND a.user_id = ?))",
                Long.class, userId, userId);
        result.put("todoCount", unread == null ? 0 : unread);
        result.put("doneCount", read == null ? 0 : read);
        result.put("todayCount", today == null ? 0 : today);
        result.put("overdueCount", overdue == null ? 0 : overdue);
        return result;
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

    /** 催办：给任务填报人发催办消息 */
    @Transactional
    public Map<String, Object> remind(Long taskId, String comment, Long operatorId) {
        RptTask task = rptTaskMapper.selectById(taskId);
        if (task == null) throw new RuntimeException("任务不存在");
        String title = "【催办】" + task.getTaskName();
        String content = StrUtil.isBlank(comment) ? "请尽快完成填报并提交。" : comment;
        int sent = notifyAssignees(taskId, title, content, "REMIND", operatorId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("sent", sent);
        return result;
    }

    /**
     * 扫描超期未提交的任务并通知（每天由定时任务调用；同一天同一任务同一人只发一次）
     */
    @Transactional
    public Map<String, Object> scanOverdue(Long operatorId) {
        List<Map<String, Object>> tasks = jdbcTemplate.queryForList(
                "SELECT t.id, t.task_name FROM rpt_task t WHERE t.status IN (1,2) "
                        + "AND t.fill_deadline IS NOT NULL AND t.fill_deadline < NOW() "
                        + "AND NOT EXISTS (SELECT 1 FROM rpt_task_data d WHERE d.task_id = t.id AND d.status = 1)");
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
