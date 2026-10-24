package com.reportsys.controller;

import com.reportsys.common.PageResult;
import com.reportsys.common.Result;
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
     * 提交申请。
     *
     * 缺陷修复：前端提交 {taskId,title,reason,dataScope} 的 JSON，后端原来只接受
     * @RequestParam taskId+reason，导致界面提交必然 400/500（「系统异常」）。
     * 现在同时兼容 JSON body 与 query 参数。
     */
    @PostMapping
    public Result<Void> apply(@RequestBody(required = false) RptDataApply body,
                               @RequestParam(required = false) Long taskId,
                               @RequestParam(required = false) String reason) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return Result.error("用户未登录");
            }
            RptDataApply apply = body != null ? body : new RptDataApply();
            if (apply.getTaskId() == null) apply.setTaskId(taskId);
            if (apply.getReason() == null || apply.getReason().trim().isEmpty()) apply.setReason(reason);
            if (apply.getTaskId() == null) {
                return Result.error("请选择关联任务");
            }
            apply.setApplicantId(userId);
            rptDataApplyService.apply(apply);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 修改本人待审批的申请 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody RptDataApply body) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) return Result.error("用户未登录");
            rptDataApplyService.updateOwn(id, body, userId);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 删除本人待审批的申请 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) return Result.error("用户未登录");
            rptDataApplyService.deleteOwn(id, userId);
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
