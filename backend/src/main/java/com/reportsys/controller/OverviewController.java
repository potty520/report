package com.reportsys.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.reportsys.common.Result;
import com.reportsys.dao.RptTaskDataMapper;
import com.reportsys.dao.RptTaskMapper;
import com.reportsys.dao.SysDeptMapper;
import com.reportsys.model.RptTask;
import com.reportsys.model.RptTaskData;
import com.reportsys.model.SysDept;
import com.reportsys.service.RptTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 总览统计。
 *
 * 原实现里"最近7天趋势"与"部门统计"分别用 Math.random() 和写死的部门名伪造，
 * 这里全部改为基于 rpt_task / rpt_task_data 的真实统计。
 */
@RestController
@RequestMapping("/overview")
public class OverviewController {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("MM/dd");

    @Autowired
    private RptTaskService rptTaskService;
    @Autowired
    private RptTaskMapper rptTaskMapper;
    @Autowired
    private RptTaskDataMapper rptTaskDataMapper;
    @Autowired
    private SysDeptMapper sysDeptMapper;

    /**
     * 仪表盘数据
     */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        try {
            Map<String, Object> taskStats = rptTaskService.getTaskStatistics();
            Map<String, Object> dashboard = new HashMap<>();

            dashboard.put("templateCount", taskStats.get("totalTemplate"));
            dashboard.put("taskCount", taskStats.get("totalTask"));

            long fillingCount = toLong(taskStats.get("fillingTask"));
            long completedCount = toLong(taskStats.get("completedTask"));
            long totalActiveTask = fillingCount + completedCount;
            double fillRate = totalActiveTask > 0
                    ? round2(completedCount * 100.0 / totalActiveTask)
                    : 0;
            dashboard.put("fillRate", fillRate);

            // 审核率 = 已审核的填报数据 / 已提交的填报数据
            long submitted = countData(1, null);
            long audited = countData(1, new Integer[]{1, 2});
            dashboard.put("auditRate", submitted > 0 ? round2(audited * 100.0 / submitted) : 0);

            // 逾期任务 = 已过填报截止时间且仍处于 已发布/填报中
            long overdue = rptTaskMapper.selectCount(Wrappers.<RptTask>lambdaQuery()
                    .isNotNull(RptTask::getFillDeadline)
                    .lt(RptTask::getFillDeadline, LocalDateTime.now())
                    .in(RptTask::getStatus, 1, 2));
            dashboard.put("overdueCount", overdue);

            dashboard.put("draftCount", taskStats.get("draftTask"));
            dashboard.put("publishedCount", taskStats.get("publishedTask"));
            dashboard.put("fillingCount", taskStats.get("fillingTask"));
            dashboard.put("completedCount", taskStats.get("completedTask"));
            dashboard.put("closedCount", taskStats.get("closedTask"));
            dashboard.put("userCount", taskStats.get("totalUser"));

            // 最近 7 天真实趋势（按 rpt_task_data 的提交/审核时间聚合）
            dashboard.put("trend", buildTrend(totalActiveTask));

            return Result.success(dashboard);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 部门统计（真实数据）
     */
    @GetMapping("/deptStats")
    public Result<List<Map<String, Object>>> deptStats() {
        try {
            List<Map<String, Object>> stats = new ArrayList<>();
            List<SysDept> depts = sysDeptMapper.selectList(Wrappers.<SysDept>lambdaQuery().orderByAsc(SysDept::getId));
            for (SysDept dept : depts) {
                long totalTask = rptTaskMapper.selectCount(Wrappers.<RptTask>lambdaQuery()
                        .eq(RptTask::getDeptId, dept.getId()));
                long completedTask = rptTaskMapper.selectCount(Wrappers.<RptTask>lambdaQuery()
                        .eq(RptTask::getDeptId, dept.getId())
                        .eq(RptTask::getStatus, 3));
                long overdueCount = rptTaskMapper.selectCount(Wrappers.<RptTask>lambdaQuery()
                        .eq(RptTask::getDeptId, dept.getId())
                        .isNotNull(RptTask::getFillDeadline)
                        .lt(RptTask::getFillDeadline, LocalDateTime.now())
                        .in(RptTask::getStatus, 1, 2));
                long submitted = rptTaskDataMapper.selectCount(Wrappers.<RptTaskData>lambdaQuery()
                        .eq(RptTaskData::getDeptId, dept.getId())
                        .eq(RptTaskData::getStatus, 1));
                long audited = rptTaskDataMapper.selectCount(Wrappers.<RptTaskData>lambdaQuery()
                        .eq(RptTaskData::getDeptId, dept.getId())
                        .eq(RptTaskData::getStatus, 1)
                        .in(RptTaskData::getAuditStatus, 1, 2));

                Map<String, Object> item = new HashMap<>();
                item.put("deptId", dept.getId());
                item.put("deptName", dept.getDeptName());
                item.put("totalTask", totalTask);
                item.put("completedTask", completedTask);
                item.put("fillRate", totalTask > 0 ? round2(completedTask * 100.0 / totalTask) : 0);
                item.put("overdueCount", overdueCount);
                item.put("submitCount", submitted);
                item.put("auditRate", submitted > 0 ? round2(audited * 100.0 / submitted) : 0);
                stats.add(item);
            }
            return Result.success(stats);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    private List<Map<String, Object>> buildTrend(long activeTaskCount) {
        LocalDate today = LocalDate.now();
        LocalDateTime from = today.minusDays(6).atStartOfDay();

        List<RptTaskData> recent = rptTaskDataMapper.selectList(Wrappers.<RptTaskData>lambdaQuery()
                .ge(RptTaskData::getCreateTime, from));

        Map<LocalDate, long[]> byDay = new HashMap<>();
        for (RptTaskData data : recent) {
            if (data.getSubmitTime() != null) {
                byDay.computeIfAbsent(data.getSubmitTime().toLocalDate(), k -> new long[2])[0]++;
            }
            if (data.getAuditTime() != null) {
                byDay.computeIfAbsent(data.getAuditTime().toLocalDate(), k -> new long[2])[1]++;
            }
        }

        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            long[] counts = byDay.getOrDefault(day, new long[2]);
            long submitCount = counts[0];
            long auditCount = counts[1];

            Map<String, Object> item = new HashMap<>();
            item.put("day", day.format(DAY_FORMAT));
            item.put("date", day.toString());
            item.put("submitCount", submitCount);
            item.put("auditCount", auditCount);
            item.put("fillRate", activeTaskCount > 0
                    ? Math.min(100, round2(submitCount * 100.0 / activeTaskCount))
                    : 0);
            item.put("auditRate", submitCount > 0
                    ? Math.min(100, round2(auditCount * 100.0 / submitCount))
                    : 0);
            trend.add(item);
        }
        return trend;
    }

    private long countData(int status, Integer[] auditStatuses) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RptTaskData> wrapper =
                Wrappers.<RptTaskData>lambdaQuery().eq(RptTaskData::getStatus, status);
        if (auditStatuses != null && auditStatuses.length > 0) {
            wrapper.in(RptTaskData::getAuditStatus, (Object[]) auditStatuses);
        }
        return rptTaskDataMapper.selectCount(wrapper);
    }

    private long toLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
