package com.reportsys.service;

import com.reportsys.dao.SysDeptMapper;
import com.reportsys.model.SysDept;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * 报送周期计算（原版 job/TaskJob + SaveTaskService 里"按日/周/月/季/年算属期与时限"的部分）。
 *
 * fillingCycle：1 日报、2 周报、3 月报、4 季报、5 年报（与 sys_dict.filling_cycle 一致）
 */
@Service
public class PeriodService {

    public static final int DAILY = 1;
    public static final int WEEKLY = 2;
    public static final int MONTHLY = 3;
    public static final int QUARTERLY = 4;
    public static final int YEARLY = 5;

    @Autowired
    private SysDeptMapper sysDeptMapper;

    /** 当前属期标识：2026-10-10 / 2026-W41 / 2026-10 / 2026Q4 / 2026 */
    public String currentPeriod(Integer cycle, LocalDate today) {
        int c = cycle == null ? MONTHLY : cycle;
        switch (c) {
            case DAILY:
                return today.toString();
            case WEEKLY:
                return today.getYear() + "-W" + String.format("%02d", weekOfYear(today));
            case QUARTERLY:
                return today.getYear() + "Q" + ((today.getMonthValue() - 1) / 3 + 1);
            case YEARLY:
                return String.valueOf(today.getYear());
            default:
                return String.format("%04d-%02d", today.getYear(), today.getMonthValue());
        }
    }

    /** 周期开始日 */
    public LocalDate periodStart(Integer cycle, LocalDate today) {
        int c = cycle == null ? MONTHLY : cycle;
        switch (c) {
            case DAILY:
                return today;
            case WEEKLY:
                return today.with(DayOfWeek.MONDAY);
            case QUARTERLY:
                return LocalDate.of(today.getYear(), ((today.getMonthValue() - 1) / 3) * 3 + 1, 1);
            case YEARLY:
                return LocalDate.of(today.getYear(), 1, 1);
            default:
                return today.withDayOfMonth(1);
        }
    }

    /** 周期结束日 */
    public LocalDate periodEnd(Integer cycle, LocalDate today) {
        int c = cycle == null ? MONTHLY : cycle;
        switch (c) {
            case DAILY:
                return today;
            case WEEKLY:
                return today.with(DayOfWeek.SUNDAY);
            case QUARTERLY:
                LocalDate quarterEnd = periodStart(c, today).plusMonths(3).minusDays(1);
                return quarterEnd;
            case YEARLY:
                return LocalDate.of(today.getYear(), 12, 31);
            default:
                return today.with(TemporalAdjusters.lastDayOfMonth());
        }
    }

    /** 填报截止 = 周期结束日 23:59:59；审核截止 = 填报截止 + auditLagDays 天 */
    public LocalDateTime fillDeadline(Integer cycle, LocalDate today) {
        return periodEnd(cycle, today).atTime(23, 59, 59);
    }

    public LocalDateTime auditDeadline(Integer cycle, LocalDate today, int auditLagDays) {
        return periodEnd(cycle, today).plusDays(Math.max(1, auditLagDays)).atTime(23, 59, 59);
    }

    /** 部门编码（用于子任务命名，原版为 任务名-部门编码-序号） */
    public String deptCode(Long deptId) {
        if (deptId == null) return "ALL";
        SysDept dept = sysDeptMapper.selectById(deptId);
        if (dept == null) return String.valueOf(deptId);
        return dept.getDeptCode() != null && !dept.getDeptCode().isEmpty()
                ? dept.getDeptCode() : String.valueOf(deptId);
    }

    private int weekOfYear(LocalDate date) {
        return date.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
    }
}
