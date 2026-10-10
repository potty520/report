package com.reportsys.controller;

import com.reportsys.annotation.OperLog;
import com.reportsys.common.Result;
import com.reportsys.service.QualityReportService;
import com.reportsys.service.SmsService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 外部质检报告通道（可插拔）与通道状态查询。
 *
 * POST /quality/{taskDataId}/submit   提交质检请求（落 rpt_quality_report）
 * GET  /quality/{taskDataId}          查询质检状态
 * GET  /quality/channel/status        查看短信/质检通道配置（仅超级管理员）
 *
 * 说明：默认 report.quality.provider=none（不连外部服务）且 required=false，
 * 因此本接口存在但不会影响既有审核流程；配置真实服务后即可启用「未质检不可审核」拦截。
 */
@RestController
@RequestMapping("/quality")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN','AUDIT_USER')")
public class QualityController {

    @Resource
    private QualityReportService qualityReportService;
    @Resource
    private SmsService smsService;

    @PostMapping("/{taskDataId}/submit")
    @OperLog(module = "质检", action = "提交质检")
    public Result<Map<String, Object>> submit(@PathVariable Long taskDataId) {
        try {
            Long userId = null;
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Long) userId = (Long) auth.getPrincipal();
            return Result.success(qualityReportService.submit(taskDataId, userId));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{taskDataId}")
    public Result<Map<String, Object>> get(@PathVariable Long taskDataId) {
        try {
            return Result.success(qualityReportService.get(taskDataId));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/channel/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Map<String, Object>> channelStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("smsEnabled", smsService.isEnabled());
        status.put("smsProvider", smsService.currentProvider());
        status.put("qualityProvider", qualityReportService.currentProvider());
        status.put("qualityRequired", qualityReportService.isRequired());
        return Result.success(status);
    }
}
