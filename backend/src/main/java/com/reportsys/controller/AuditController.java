package com.reportsys.controller;

import com.reportsys.common.PageResult;
import com.reportsys.common.Result;
import com.reportsys.model.RptTaskData;
import com.reportsys.service.RptTaskDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.reportsys.annotation.OperLog;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/audit", "/fill/audit"})
@PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN','AUDIT_USER')")
public class AuditController {

    @Autowired
    private RptTaskDataService rptTaskDataService;

    /**
     * 我的审核任务列表(分页)
     */
    @GetMapping({"/myTasks", "/page"})
    public Result<PageResult<RptTaskData>> myTasks(@RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "10") int pageSize,
                                                     @RequestParam(required = false) Integer auditStatus) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return Result.error("用户未登录");
            }
            PageResult<RptTaskData> result = rptTaskDataService.getAuditList(page, pageSize, userId,
                    auditStatus, null);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 审核详情(通过taskId获取该任务下的填报数据列表)
     */
    @GetMapping("/task/{taskId}")
    public Result<PageResult<RptTaskData>> getAuditDetail(@PathVariable Long taskId) {
        try {
            PageResult<RptTaskData> result = rptTaskDataService.getAuditList(1, 1000, null,
                    null, taskId);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 审核通过
     */
    @PostMapping("/approve")
    public Result<Void> approve(@RequestParam Long taskDataId,
                                 @RequestParam(required = false) String auditComment) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return Result.error("用户未登录");
            }
            rptTaskDataService.audit(taskDataId, 1, auditComment, userId);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 审核驳回
     */
    @PostMapping("/reject")
    public Result<Void> reject(@RequestParam Long taskDataId,
                                @RequestParam(required = false) String auditComment) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return Result.error("用户未登录");
            }
            rptTaskDataService.audit(taskDataId, 2, auditComment, userId);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 审核统计
     */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return Result.error("用户未登录");
            }
            // 统计待审数和已审数
            PageResult<RptTaskData> pendingResult = rptTaskDataService.getAuditList(1, 1, userId, 0, null);
            PageResult<RptTaskData> approvedResult = rptTaskDataService.getAuditList(1, 1, userId, 1, null);
            PageResult<RptTaskData> rejectedResult = rptTaskDataService.getAuditList(1, 1, userId, 2, null);
            PageResult<RptTaskData> allResult = rptTaskDataService.getAuditList(1, 1, userId, null, null);

            Map<String, Object> stats = new HashMap<>();
            stats.put("pendingCount", pendingResult.getTotal());
            stats.put("approvedCount", approvedResult.getTotal());
            stats.put("rejectedCount", rejectedResult.getTotal());
            stats.put("totalCount", allResult.getTotal());
            return Result.success(stats);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 部门审核（双通道第一级，P0-C）
     */
    @PostMapping({"/dept-audit", "/submit"})
    @OperLog(module = "填报审核", action = "部门审核")
    public Result<Void> deptAudit(@RequestParam Long taskDataId,
                                   @RequestParam boolean pass,
                                   @RequestParam(required = false) String comment) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) return Result.error("用户未登录");
            rptTaskDataService.deptAudit(taskDataId, pass, comment, userId);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 任务方审核（双通道第二级，需部门审核通过，P0-C）
     */
    @PostMapping("/task-audit")
    @OperLog(module = "填报审核", action = "任务方审核")
    public Result<Void> taskAudit(@RequestParam Long taskDataId,
                                   @RequestParam boolean pass,
                                   @RequestParam(required = false) String comment) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) return Result.error("用户未登录");
            rptTaskDataService.taskAudit(taskDataId, pass, comment, userId);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 任务流转轨迹（P0-C）
     */
    @GetMapping("/trajectory")
    public Result<List<Map<String, Object>>> trajectory(@RequestParam Long taskId) {
        try {
            return Result.success(rptTaskDataService.trajectory(taskId));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 发起补正（任务方勾选需要补正的数据）
     */
    @PostMapping("/correct/start")
    public Result<Long> startCorrect(@RequestParam Long taskId,
                                      @RequestParam(required = false) String dataIds,
                                      @RequestParam(required = false) String comment) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) return Result.error("用户未登录");
            return Result.success(rptTaskDataService.startCorrect(taskId, dataIds, comment, userId));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 关闭补正
     */
    @PostMapping("/correct/close")
    public Result<Void> closeCorrect(@RequestParam Long taskId,
                                      @RequestParam(required = false) String comment) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) return Result.error("用户未登录");
            rptTaskDataService.closeCorrect(taskId, userId, comment);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 补正批次列表
     */
    @GetMapping("/correct/list")
    public Result<List<Map<String, Object>>> correctList(@RequestParam Long taskId) {
        try {
            return Result.success(rptTaskDataService.correctList(taskId));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取当前用户ID
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long) {
                return (Long) authentication.getPrincipal();
        }
        return null;
    }
}
