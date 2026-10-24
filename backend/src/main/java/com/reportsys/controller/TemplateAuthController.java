package com.reportsys.controller;

import com.reportsys.annotation.OperLog;
import com.reportsys.common.Result;
import com.reportsys.service.AuthzService;
import com.reportsys.service.TemplateAuthService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 模板两级授权接口（部门 / 用户）——补齐原版 rel_model_user 能力。
 *
 * GET    /template/{templateId}/auths              查询该模板的授权
 * POST   /template/{templateId}/auths              批量授权 {authType: DEPT|USER, targetIds: []}
 * DELETE /template/{templateId}/auths/{authId}     撤销一条授权
 * GET    /template/{templateId}/auths/check        判断某用户是否可访问该模板
 */
@RestController
@RequestMapping("/template/{templateId}/auths")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")
public class TemplateAuthController {

    @Resource
    private TemplateAuthService templateAuthService;
    @Resource
    private AuthzService authzService;

    @GetMapping
    public Result<List<Map<String, Object>>> list(@PathVariable Long templateId) {
        try {
            return Result.success(templateAuthService.list(templateId));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @OperLog(module = "模板管理", action = "模板授权")
    public Result<Integer> grant(@PathVariable Long templateId, @RequestBody Map<String, Object> body) {
        try {
            String authType = body.get("authType") == null ? null : String.valueOf(body.get("authType"));
            List<Long> targetIds = new ArrayList<>();
            Object raw = body.get("targetIds");
            if (raw instanceof List) {
                for (Object item : (List<?>) raw) {
                    if (item == null) continue;
                    String text = String.valueOf(item).trim();
                    if (text.isEmpty()) continue;
                    targetIds.add(Long.valueOf(text));
                }
            }
            int added = templateAuthService.grant(templateId, authType, targetIds, authzService.currentUserId());
            return Result.success(added);
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{authId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @OperLog(module = "模板管理", action = "撤销模板授权")
    public Result<Void> revoke(@PathVariable Long templateId, @PathVariable Long authId) {
        try {
            templateAuthService.revoke(templateId, authId);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/check")
    public Result<Boolean> check(@PathVariable Long templateId,
                                 @RequestParam(required = false) Long userId) {
        Long target = userId == null ? authzService.currentUserId() : userId;
        return Result.success(templateAuthService.canAccess(templateId, target));
    }
}
