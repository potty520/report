package com.reportsys.service.quality;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 默认质检通道：不连接任何外部服务。
 *
 * 提交时返回 PENDING 并给出「未配置外部质检服务」的说明，
 * 这样在不具备外部质检环境时，系统仍能记录质检请求，且（默认 required=false）不影响审核流程。
 */
@Component
public class NoopQualityReportClient implements QualityReportClient {

    private static final Logger log = LoggerFactory.getLogger(NoopQualityReportClient.class);

    @Override
    public String provider() {
        return "none";
    }

    @Override
    public boolean ready() {
        return true;
    }

    @Override
    public QualityResult submit(Long taskDataId, Long taskId, Long templateId, String templateCode, String dataJson) {
        log.info("[质检-仿真] 收到质检请求 taskDataId={} taskId={} template={}", taskDataId, taskId, templateCode);
        return QualityResult.of(STATUS_PENDING, null, null, "未配置外部质检服务（report.quality.provider=none），已记录质检请求");
    }

    @Override
    public QualityResult query(Long taskDataId, String reportId) {
        return QualityResult.of(STATUS_PENDING, reportId, null, "未配置外部质检服务，无法查询结果");
    }
}
