package com.reportsys.service;

import cn.hutool.core.util.StrUtil;
import com.reportsys.service.sms.SmsSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 短信服务门面：按配置选择通道，并把每次发送（含未启用时的仿真记录）写入 rpt_sms_log。
 *
 * 关键点：短信发送失败**绝不影响**主流程（催办/超期提醒先保证站内消息成功）。
 */
@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    @Resource
    private List<SmsSender> senders;
    @Resource
    private JdbcTemplate jdbcTemplate;

    @Value("${report.sms.enabled:false}")
    private boolean enabled;

    @Value("${report.sms.provider:log}")
    private String provider;

    @Value("${report.sms.template.todo:}")
    private String templateTodo;

    @Value("${report.sms.template.overdue:}")
    private String templateOverdue;

    @Value("${report.sms.template.correct:}")
    private String templateCorrect;

    /** 发送催办短信（enabled=false 时只落库，不真正发送） */
    public Map<String, Object> sendRemind(String phone, String taskName, String comment, Long userId, Long taskId) {
        return send(phone, resolveTemplate("TODO"), taskName, comment, userId, taskId, "TODO");
    }

    /** 发送超期提醒短信 */
    public Map<String, Object> sendOverdue(String phone, String taskName, String deadline, Long userId, Long taskId) {
        return send(phone, resolveTemplate("OVERDUE"), taskName, deadline, userId, taskId, "OVERDUE");
    }

    /** 发送补正通知短信 */
    public Map<String, Object> sendCorrect(String phone, String taskName, String comment, Long userId, Long taskId) {
        return send(phone, resolveTemplate("CORRECT"), taskName, comment, userId, taskId, "CORRECT");
    }

    public String currentProvider() {
        return StrUtil.isBlank(provider) ? "log" : provider;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private String resolveTemplate(String type) {
        if ("OVERDUE".equals(type)) return templateOverdue;
        if ("CORRECT".equals(type)) return templateCorrect;
        return templateTodo;
    }

    private Map<String, Object> send(String phone, String templateCode, String taskName, String extra,
                                     Long userId, Long taskId, String bizType) {
        Map<String, Object> result = new LinkedHashMap<>();
        SmsSender sender = pickSender();
        result.put("provider", sender == null ? "none" : sender.provider());
        result.put("enabled", enabled);

        SmsSender.SmsResult sendResult;
        if (!enabled) {
            sendResult = SmsSender.SmsResult.fail("短信未启用（REPORT_SMS_ENABLED=false），仅记录日志");
        } else if (sender == null) {
            sendResult = SmsSender.SmsResult.fail("未找到可用短信通道: " + currentProvider());
        } else {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("taskName", StrUtil.nullToEmpty(taskName));
            params.put("extra", StrUtil.nullToEmpty(extra));
            sendResult = sender.send(phone, StrUtil.nullToEmpty(templateCode), params);
        }

        result.put("success", sendResult.isSuccess());
        result.put("message", sendResult.getMessage());
        result.put("messageId", sendResult.getMessageId());
        record(userId, taskId, bizType, phone, templateCode, sendResult, extra);
        return result;
    }

    private SmsSender pickSender() {
        if (senders == null || senders.isEmpty()) return null;
        String want = currentProvider();
        for (SmsSender sender : senders) {
            if (want.equalsIgnoreCase(sender.provider())) return sender;
        }
        // 配置的通道不存在时退回仿真通道，保证流程可用
        for (SmsSender sender : senders) {
            if ("log".equalsIgnoreCase(sender.provider())) return sender;
        }
        return senders.get(0);
    }

    private void record(Long userId, Long taskId, String bizType, String phone, String templateCode,
                        SmsSender.SmsResult result, String content) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO rpt_sms_log (user_id, task_id, biz_type, phone, template_code, provider, "
                            + "success, message_id, message, content, create_time) VALUES (?,?,?,?,?,?,?,?,?,?,NOW())",
                    userId, taskId, bizType, phone, StrUtil.maxLength(StrUtil.nullToEmpty(templateCode), 64),
                    currentProvider(), result.isSuccess() ? 1 : 0,
                    StrUtil.maxLength(StrUtil.nullToEmpty(result.getMessageId()), 64),
                    StrUtil.maxLength(StrUtil.nullToEmpty(result.getMessage()), 500),
                    StrUtil.maxLength(StrUtil.nullToEmpty(content), 500));
        } catch (Exception e) {
            log.warn("写入短信日志失败（不影响主流程）: {}", e.getMessage());
        }
    }
}
