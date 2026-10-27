package com.reportsys.controller;

import com.reportsys.common.Result;
import com.reportsys.service.ReportRankService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** R-12 手册四类统计（任务制定统计 / 部门完成排行 / 填报任务统计 / 部门下发排行）。 */
@RestController
@RequestMapping("/statistics")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN','FILL_USER')")
public class StatisticsController {

    @Autowired
    private ReportRankService reportRankService;

    @GetMapping("/task-make")
    public Result<Map<String, Object>> taskMake() {
        return Result.success(reportRankService.taskMake(currentUserId()));
    }

    @GetMapping("/dept-finish-rank")
    public Result<java.util.List<Map<String, Object>>> deptFinishRank() {
        return Result.success(reportRankService.deptFinishRank());
    }

    @GetMapping("/fill-task")
    public Result<Map<String, Object>> fillTask() {
        return Result.success(reportRankService.fillTask(currentUserId()));
    }

    @GetMapping("/dept-issue-rank")
    public Result<java.util.List<Map<String, Object>>> deptIssueRank() {
        return Result.success(reportRankService.deptIssueRank());
    }

    private Long currentUserId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof Long) return (Long) a.getPrincipal();
        return null;
    }
}
