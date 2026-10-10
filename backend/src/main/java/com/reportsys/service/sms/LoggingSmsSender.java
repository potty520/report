package com.reportsys.service.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * 默认短信通道：只记录日志，不真正发送。
 *
 * 这样「催办」功能在任何环境都能工作（站内消息为主），
 * 需要真正发短信时再切到 chunlan 通道并配置凭据。
 */
@Component
public class LoggingSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

    @Override
    public String provider() {
        return "log";
    }

    @Override
    public boolean ready() {
        return true;
    }

    @Override
    public SmsResult send(String phone, String templateCode, Map<String, String> params) {
        String messageId = "log-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        log.info("[SMS-仿真] phone={} template={} params={} messageId={}", phone, templateCode, params, messageId);
        return SmsResult.ok(messageId);
    }
}
