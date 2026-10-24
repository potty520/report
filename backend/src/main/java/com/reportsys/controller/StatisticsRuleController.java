package com.reportsys.controller;

import com.reportsys.annotation.OperLog;
import com.reportsys.common.Result;
import com.reportsys.model.RptStatisticsRule;
import com.reportsys.service.StatisticsRuleService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 统计规则配置（对应原版 statistics_rule_config）。
 *
 * GET    /statistics-rule/list       规则列表（超管）
 * GET    /statistics-rule/effective  当前生效的规则值（任意登录用户可看）
 * POST   /statistics-rule            新增/更新（按 rule_code 幂等，超管）
 * DELETE /statistics-rule/{id}       删除（超管）
 */
@RestController
@RequestMapping("/statistics-rule")
public class StatisticsRuleController {

    @Resource
    private StatisticsRuleService statisticsRuleService;

    @GetMapping("/list")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<List<RptStatisticsRule>> list() {
        return Result.success(statisticsRuleService.list());
    }

    @GetMapping("/effective")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> effective() {
        return Result.success(statisticsRuleService.effective());
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @OperLog(module = "统计规则", action = "保存规则")
    public Result<Long> save(@RequestBody RptStatisticsRule rule) {
        try {
            return Result.success(statisticsRuleService.save(rule));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @OperLog(module = "统计规则", action = "删除规则")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            statisticsRuleService.delete(id);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
