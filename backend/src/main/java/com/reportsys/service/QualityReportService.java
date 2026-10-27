package com.reportsys.service;

import cn.hutool.core.util.StrUtil;
import com.reportsys.dao.RptTaskDataMapper;
import com.reportsys.model.RptTaskData;
import com.reportsys.service.quality.QualityReportClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 质检报告服务：把「外部质检」抽象成可插拔通道，并提供审核拦截能力。
 *
 * - report.quality.required=false（默认）时，审核不检查质检报告，行为与之前完全一致；
 * - report.quality.required=true 时，未通过质检（或报告不存在）的填报数据不允许审核，
 *   对应原版「报告未生成不可审核」的拦截语义。
 */
@Service
public class QualityReportService {

    private static final Logger log = LoggerFactory.getLogger(QualityReportService.class);

    @Resource
    private List<QualityReportClient> clients;
    @Resource
    private RptTaskDataMapper rptTaskDataMapper;
    @Resource
    private JdbcTemplate jdbcTemplate;

    @Value("${report.quality.provider:none}")
    private String provider;

    @Value("${report.quality.required:false}")
    private boolean required;

    public boolean isRequired() {
        return required;
    }

    public String currentProvider() {
        return StrUtil.isBlank(provider) ? "none" : provider;
    }

    /** 提交质检请求并落库（同一填报数据重复提交则更新） */
    @Transactional
    public Map<String, Object> submit(Long taskDataId, Long userId) {
        RptTaskData data = rptTaskDataMapper.selectById(taskDataId);
        if (data == null) throw new RuntimeException("填报数据不存在");
        QualityReportClient client = pickClient();
        QualityReportClient.QualityResult result = client.submit(taskDataId, data.getTaskId(),
                data.getTemplateId(), null, data.getDataJson());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("provider", client.provider());
        out.put("status", result.getStatus());
        out.put("reportId", result.getReportId());
        out.put("reportUrl", result.getReportUrl());
        out.put("message", result.getMessage());
        persist(taskDataId, data, client.provider(), result, userId);
        return out;
    }

    /** 查询某填报数据的质检状态 */
    public Map<String, Object> get(Long taskDataId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("required", required);
        out.put("provider", currentProvider());
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, task_data_id AS taskDataId, status, provider, report_id AS reportId, "
                            + "report_url AS reportUrl, message, create_time AS createTime, update_time AS updateTime "
                            + "FROM rpt_quality_report WHERE task_data_id = ?", taskDataId);
            out.put("record", rows.isEmpty() ? null : rows.get(0));
        } catch (Exception e) {
            out.put("record", null);
            out.put("message", "质检记录表不可用: " + e.getMessage());
        }
        return out;
    }

    /** 质量报告结论：PASS / FAIL / null（R-06） */
    public String checkResult(Long taskDataId) {
        try {
            List<String> rows = jdbcTemplate.queryForList(
                    "SELECT check_result FROM rpt_quality_report WHERE task_data_id = ?", String.class, taskDataId);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    /** 结论为不合格时，只允许驳回（R-06，不受 required 开关影响） */
    public void assertCanApprove(Long taskDataId) {
        String result = checkResult(taskDataId);
        if ("FAIL".equalsIgnoreCase(result)) {
            throw new RuntimeException("质量报告结论为不合格，只能驳回，不能审核通过");
        }
    }

    /** 审核前校验（required=false 时直接放行） */
    public void assertCanAudit(Long taskDataId) {
        if (!required) return;
        String status = null;
        try {
            List<String> rows = jdbcTemplate.queryForList(
                    "SELECT status FROM rpt_quality_report WHERE task_data_id = ?", String.class, taskDataId);
            if (!rows.isEmpty()) status = rows.get(0);
        } catch (Exception e) {
            log.warn("读取质检状态失败: {}", e.getMessage());
        }
        if (status == null) {
            throw new RuntimeException("质检报告未生成，不能进行审核");
        }
        if (!QualityReportClient.STATUS_PASS.equalsIgnoreCase(status)) {
            throw new RuntimeException("质检报告未通过（当前状态: " + status + "），不能进行审核");
        }
    }

    private QualityReportClient pickClient() {
        if (clients == null || clients.isEmpty()) throw new RuntimeException("没有可用的质检通道");
        String want = currentProvider();
        for (QualityReportClient client : clients) {
            if (want.equalsIgnoreCase(client.provider())) return client;
        }
        for (QualityReportClient client : clients) {
            if ("none".equalsIgnoreCase(client.provider())) return client;
        }
        return clients.get(0);
    }

    private void persist(Long taskDataId, RptTaskData data, String providerName,
                         QualityReportClient.QualityResult result, Long userId) {
        try {
            List<Long> exists = jdbcTemplate.queryForList(
                    "SELECT id FROM rpt_quality_report WHERE task_data_id = ?", Long.class, taskDataId);
            String checkResult = QualityReportClient.STATUS_PASS.equalsIgnoreCase(result.getStatus()) ? "PASS" : "FAIL";
            if (exists.isEmpty()) {
                jdbcTemplate.update("INSERT INTO rpt_quality_report (task_data_id, task_id, template_id, provider, "
                                + "status, check_result, report_id, report_url, message, create_by, create_time, update_time) "
                                + "VALUES (?,?,?,?,?,?,?,?,?,?,NOW(),NOW())",
                        taskDataId, data.getTaskId(), data.getTemplateId(), providerName,
                        result.getStatus(), checkResult, result.getReportId(), result.getReportUrl(),
                        StrUtil.maxLength(StrUtil.nullToEmpty(result.getMessage()), 500), userId);
            } else {
                jdbcTemplate.update("UPDATE rpt_quality_report SET provider=?, status=?, check_result=?, report_id=?, "
                                + "report_url=?, message=?, update_time=NOW() WHERE task_data_id=?",
                        providerName, result.getStatus(), checkResult, result.getReportId(), result.getReportUrl(),
                        StrUtil.maxLength(StrUtil.nullToEmpty(result.getMessage()), 500), taskDataId);
            }
        } catch (Exception e) {
            log.warn("写入质检记录失败（不影响提交）: {}", e.getMessage());
        }
    }
}
