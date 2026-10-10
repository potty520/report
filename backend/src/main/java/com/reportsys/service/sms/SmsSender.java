package com.reportsys.service.sms;

import java.util.Map;

/**
 * 短信发送通道（可插拔）。
 *
 * 原版通过创蓝短信网关按「待办 / 退回 / 超期」三段模板群发；本版把它抽象成接口：
 * - 默认实现 {@link LoggingSmsSender}：只落库/打日志，不真正发短信（无需任何凭据）；
 * - 配置 report.sms.provider=chunlan 且提供账号凭据后，切换到 {@link ChunlanSmsSender} 真正发送。
 */
public interface SmsSender {

    /** 通道名，用于按配置选择实现 */
    String provider();

    /** 是否具备发送条件（凭据是否齐备） */
    boolean ready();

    /**
     * 发送模板短信。
     *
     * @param phone        手机号
     * @param templateCode 通道侧模板编号
     * @param params       模板参数
     * @return 发送结果
     */
    SmsResult send(String phone, String templateCode, Map<String, String> params);

    class SmsResult {
        private final boolean success;
        private final String messageId;
        private final String message;

        public SmsResult(boolean success, String messageId, String message) {
            this.success = success;
            this.messageId = messageId;
            this.message = message;
        }

        public static SmsResult ok(String messageId) {
            return new SmsResult(true, messageId, "发送成功");
        }

        public static SmsResult fail(String message) {
            return new SmsResult(false, null, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessageId() {
            return messageId;
        }

        public String getMessage() {
            return message;
        }
    }
}
