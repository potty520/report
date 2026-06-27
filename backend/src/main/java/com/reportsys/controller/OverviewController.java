package com.reportsys.controller;

import com.reportsys.common.Result;
import com.reportsys.service.RptTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/overview")
public class OverviewController {

    @Autowired
    private RptTaskService rptTaskService;

    /**
     * 仪表盘数据
     */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        try {
            Map<String, Object> taskStats = rptTaskService.getTaskStatistics();
            Map<String, Object> dashboard = new HashMap<>();

            // 模板数
            dashboard.put("templateCount", taskStats.get("totalTemplate"));
            // 任务数
            dashboard.put("taskCount", taskStats.get("totalTask"));
            // 填报率 = 已完成填报数 / 填报中的任务数
            Object fillingCountObj = taskStats.get("fillingTask");
            Object completedCountObj = taskStats.get("completedTask");
            long fillingCount = fillingCountObj != null ? ((Number) fillingCountObj).longValue() : 0;
            long completedCount = completedCountObj != null ? ((Number) completedCountObj).longValue() : 0;
            long totalActiveTask = fillingCount + completedCount;
            double fillRate = totalActiveTask > 0
                    ? Math.round((double) completedCount / totalActiveTask * 10000.0) / 100.0
                    : 0;
            dashboard.put("fillRate", fillRate);

            // 审核率
            dashboard.put("auditRate", 0); // 需要审核统计数据，暂设0

            // 逾期数
            dashboard.put("overdueCount", 0); // 需要逾期统计逻辑，暂设0

            // 各状态任务数
            dashboard.put("draftCount", taskStats.get("draftTask"));
            dashboard.put("publishedCount", taskStats.get("publishedTask"));
            dashboard.put("fillingCount", taskStats.get("fillingTask"));
            dashboard.put("completedCount", taskStats.get("completedTask"));
            dashboard.put("closedCount", taskStats.get("closedTask"));
            dashboard.put("userCount", taskStats.get("totalUser"));

            // 完成率趋势(最近7天,模拟数据)
            List<Map<String, Object>> trendList = new ArrayList<>();
            String[] days = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
            for (String day : days) {
                Map<String, Object> item = new HashMap<>();
                item.put("day", day);
                item.put("fillRate", Math.random() * 100);
                item.put("auditRate", Math.random() * 100);
                trendList.add(item);
            }
            dashboard.put("trend", trendList);

            return Result.success(dashboard);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 部门统计
     */
    @GetMapping("/deptStats")
    public Result<List<Map<String, Object>>> deptStats() {
        try {
            // 模拟部门统计数据
            List<Map<String, Object>> stats = new ArrayList<>();
            String[] deptNames = {"技术部", "财务部", "人事部", "市场部", "运营部"};

            for (String deptName : deptNames) {
                Map<String, Object> item = new HashMap<>();
                item.put("deptName", deptName);
                item.put("totalTask", (int) (Math.random() * 50 + 10));
                item.put("completedTask", (int) (Math.random() * 30));
                item.put("fillRate", Math.round(Math.random() * 10000) / 100.0);
                item.put("overdueCount", (int) (Math.random() * 5));
                item.put("auditRate", Math.round(Math.random() * 10000) / 100.0);
                stats.add(item);
            }

            return Result.success(stats);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
