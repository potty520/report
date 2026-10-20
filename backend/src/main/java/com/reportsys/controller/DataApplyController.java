package com.reportsys.controller;

import com.reportsys.common.PageResult;
import com.reportsys.common.Result;
import com.reportsys.annotation.OperLog;
import com.reportsys.model.RptDataApply;
import com.reportsys.service.RptDataApplyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/dataApply", "/data-apply"})
@PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN','FILL_USER','AUDIT_USER')")
public class DataApplyController {

    @Autowired
    private RptDataApplyService rptDataApplyService;

    /**
     * 我的申请列表
     */
    @GetMapping({"/myApplies", "/page"})
    public Result<PageResult<RptDataApply>> myApplies(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "10") int pageSize,
                                                       @RequestParam(required = false) Integer status) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return Result.error("用户未登录");
            }
            PageResult<RptDataApply> result = rptDataApplyService.getMyApplyList(page, pageSize, userId, status);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 提交申请
     */
    @PostMapping
    @OperLog(module = "数据申请", action = "提交申请")
    public Result<Void> apply(@RequestParam Long taskId,
                               @RequestParam String reason) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return Result.error("用户未登录");
            }
            RptDataApply apply = new RptDataApply();
            apply.setTaskId(taskId);
            apply.setApplicantId(userId);
            apply.setReason(reason);
            rptDataApplyService.apply(apply);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 待审批列表
     */
    @GetMapping({"/approveList", "/approve/page"})
    public Result<PageResult<RptDataApply>> approveList(@RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "10") int pageSize) {
        try {
            // 查询待审批(status=0)的申请
            PageResult<RptDataApply> result = rptDataApplyService.getApproveList(page, pageSize, 0);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 审批通过
     */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','AUDIT_USER','DEPT_ADMIN')")

    @PostMapping("/{id}/approve")
    @OperLog(module = "数据申请", action = "审批通过")
    public Result<Void> approve(@PathVariable Long id) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return Result.error("用户未登录");
            }
            rptDataApplyService.approve(id, 1, null, userId);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 审批驳回
     */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','AUDIT_USER','DEPT_ADMIN')")

    @PostMapping("/{id}/reject")
    @OperLog(module = "数据申请", action = "审批拒绝")
    public Result<Void> reject(@PathVariable Long id,
                                @RequestParam(required = false) String auditComment) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return Result.error("用户未登录");
            }
            rptDataApplyService.approve(id, 2, auditComment, userId);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
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
