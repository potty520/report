package com.reportsys.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.reportsys.dao.RptStatisticsRuleMapper;
import com.reportsys.model.RptStatisticsRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 统计规则服务：把「超期宽限天数 / 提前提醒天数 / 是否启用」做成可配置项。
 *
 * 兼容策略：读不到规则（缺行、缺表、被禁用）时返回安全默认值，
 * 使调用方行为与配置前完全一致（宽限 0 天、不提前提醒、不启用）。
 */
@Service
public class StatisticsRuleService {

    private static final Logger log = LoggerFactory.getLogger(StatisticsRuleService.class);

    public static final String OVERDUE_REMIND = "OVERDUE_REMIND";
    public static final String TODO_REMIND = "TODO_REMIND";
    public static final String CORRECT_REMIND = "CORRECT_REMIND";

    @Resource
    private RptStatisticsRuleMapper rptStatisticsRuleMapper;
    @Resource
    private JdbcTemplate jdbcTemplate;

    /** 全部规则 */
    public List<RptStatisticsRule> list() {
        try {
            return rptStatisticsRuleMapper.selectList(Wrappers.<RptStatisticsRule>lambdaQuery()
                    .orderByAsc(RptStatisticsRule::getRuleCode));
        } catch (Exception e) {
            log.warn("统计规则表不可用: {}", e.getMessage());
            return new java.util.ArrayList<>();
        }
    }

    @Transactional
    public Long save(RptStatisticsRule rule) {
        if (rule == null || StrUtil.isBlank(rule.getRuleCode())) {
            throw new RuntimeException("规则编码不能为空");
        }
        rule.setRuleCode(rule.getRuleCode().trim().toUpperCase());
        if (rule.getGraceDays() == null || rule.getGraceDays() < 0) rule.setGraceDays(0);
        if (rule.getRemindDays() == null || rule.getRemindDays() < 0) rule.setRemindDays(0);
        if (rule.getEnabled() == null) rule.setEnabled(1);
        rule.setUpdateTime(LocalDateTime.now());

        RptStatisticsRule exist = getByCode(rule.getRuleCode());
        if (exist != null && exist.getId() != null) {
            rule.setId(exist.getId());
            rule.setCreateTime(exist.getCreateTime());
            rptStatisticsRuleMapper.updateById(rule);
            return rule.getId();
        }
        rule.setCreateTime(LocalDateTime.now());
        rptStatisticsRuleMapper.insert(rule);
        return rule.getId();
    }

    @Transactional
    public void delete(Long id) {
        rptStatisticsRuleMapper.deleteById(id);
    }

    public RptStatisticsRule getByCode(String ruleCode) {
        if (StrUtil.isBlank(ruleCode)) return null;
        try {
            return rptStatisticsRuleMapper.selectOne(Wrappers.<RptStatisticsRule>lambdaQuery()
                    .eq(RptStatisticsRule::getRuleCode, ruleCode.trim().toUpperCase())
                    .last("limit 1"));
        } catch (Exception e) {
            log.warn("读取统计规则失败({}): {}", ruleCode, e.getMessage());
            return null;
        }
    }

    /**
     * 超期宽限天数（默认 0 = 现行为）。规则未启用时同样返回 0。
     */
    public int overdueGraceDays() {
        RptStatisticsRule rule = getByCode(OVERDUE_REMIND);
        if (rule == null || rule.getEnabled() == null || rule.getEnabled() != 1) return 0;
        return rule.getGraceDays() == null ? 0 : Math.max(0, rule.getGraceDays());
    }

    /**
     * 提前提醒天数（默认 0 = 不提前提醒）。
     */
    public int remindDaysBefore(String ruleCode) {
        RptStatisticsRule rule = getByCode(ruleCode);
        if (rule == null || rule.getEnabled() == null || rule.getEnabled() != 1) return 0;
        return rule.getRemindDays() == null ? 0 : Math.max(0, rule.getRemindDays());
    }

    /** 供接口展示：规则 + 当前生效值 */
    public Map<String, Object> effective() {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("overdueGraceDays", overdueGraceDays());
        out.put("overdueRemindDaysBefore", remindDaysBefore(OVERDUE_REMIND));
        out.put("todoRemindDaysBefore", remindDaysBefore(TODO_REMIND));
        out.put("correctRemindDaysBefore", remindDaysBefore(CORRECT_REMIND));
        try {
            java.util.List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT COUNT(*) AS cnt FROM rpt_task WHERE status IN (1,2) AND fill_deadline IS NOT NULL");
            out.put("taskCount", rows.isEmpty() ? 0 : rows.get(0).get("cnt"));
        } catch (Exception e) {
            out.put("taskCount", null);
        }
        return out;
    }
}
