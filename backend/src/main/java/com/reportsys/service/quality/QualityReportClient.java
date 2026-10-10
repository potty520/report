package com.reportsys.service.quality;

/**
 * 外部质检报告服务通道（可插拔）。
 *
 * 原版是异步线程池 + HTTP 调用外部质检服务（data_report/audit），并把结果落中间表，
 * 审核时以「报告未生成不可审核」拦截。本版抽象为接口：
 * - 默认 {@link NoopQualityReportClient}：不连外部服务，仅返回 PENDING 并说明未配置；
 * - 配置 report.quality.provider=http 并给出 url 后使用 {@link HttpQualityReportClient}。
 */
public interface QualityReportClient {

    String provider();

    /** 是否具备调用条件（配置是否齐备） */
    boolean ready();

    /** 提交质检请求 */
    QualityResult submit(Long taskDataId, Long taskId, Long templateId, String templateCode, String dataJson);

    /** 查询质检结果 */
    QualityResult query(Long taskDataId, String reportId);

    class QualityResult {
        private final boolean success;
        private final String status;
        private final String reportId;
        private final String reportUrl;
        private final String message;

        public QualityResult(boolean success, String status, String reportId, String reportUrl, String message) {
            this.success = success;
            this.status = status;
            this.reportId = reportId;
            this.reportUrl = reportUrl;
            this.message = message;
        }

        public static QualityResult of(String status, String reportId, String reportUrl, String message) {
            return new QualityResult(true, status, reportId, reportUrl, message);
        }

        public static QualityResult fail(String message) {
            return new QualityResult(false, "FAIL", null, null, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getStatus() {
            return status;
        }

        public String getReportId() {
            return reportId;
        }

        public String getReportUrl() {
            return reportUrl;
        }

        public String getMessage() {
            return message;
        }
    }

    /** 质检状态常量 */
    String STATUS_NONE = "NONE";
    String STATUS_PENDING = "PENDING";
    String STATUS_PASS = "PASS";
    String STATUS_FAIL = "FAIL";
}
