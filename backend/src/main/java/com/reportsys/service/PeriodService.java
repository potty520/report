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
 * fillingCycle 取值：<b>全系统唯一口径</b>，与 sys_dict.filling_cycle、前端下拉保持一致：
 *   1 日报、2 周报、3 月报、4 季报、5 年报、6 一次性
 *
 * 历史缺陷：前端模板向导曾用 1月度/2季度/3半年/4年度/9一次性，而后端按 1日报/2周报/… 计算，
 * 于是界面选「月度」实际按日报算属期和截止日、选「年度」按季报算，「一次性」落进 default 按月报算。
 * 现在枚举只保留本类常量，写入前统一走 {@link #validate(Integer)}，前端/字典/DDL 同步对齐。
 */
@Service
public class PeriodService {

    public static final int DAILY = 1;
    public static final int WEEKLY = 2;
    public static final int MONTHLY = 3;
    public static final int QUARTERLY = 4;
    public static final int YEARLY = 5;
    /** 一次性任务：不参与周期子任务生成 */
    public static final int ONE_TIME = 6;

    private static final java.util.Map<Integer, String> LABELS = new java.util.LinkedHashMap<>();

    static {
        LABELS.put(DAILY, "日报");
        LABELS.put(WEEKLY, "周报");
        LABELS.put(MONTHLY, "月报");
        LABELS.put(QUARTERLY, "季报");
        LABELS.put(YEARLY, "年报");
        LABELS.put(ONE_TIME, "一次性");
    }

    /** 周期的中文名（展示/报错用） */
    public static String label(Integer cycle) {
        return LABELS.getOrDefault(cycle == null ? MONTHLY : cycle, "未知周期");
    }

    /**
     * 校验周期取值。非法值直接拒绝落库，避免字典与代码再次漂移。
     *
     * @throws RuntimeException 取值不在 1-6 之间
     */
    public static void validate(Integer cycle) {
        if (cycle == null) {
            throw new RuntimeException("填报周期不能为空");
        }
        if (!LABELS.containsKey(cycle)) {
            throw new RuntimeException("不支持的填报周期：" + cycle
                    + "（可选 1日报、2周报、3月报、4季报、5年报、6一次性）");
        }
    }

    /** 是否为需要按周期自动生成子任务的主任务（一次性任务不生成） */
    public static boolean isPeriodic(Integer cycle) {
        return cycle != null && cycle != ONE_TIME;
    }

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
            case ONE_TIME:
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
            case ONE_TIME:
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
            case ONE_TIME:
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
