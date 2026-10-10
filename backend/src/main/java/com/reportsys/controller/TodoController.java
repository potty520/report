package com.reportsys.controller;

import com.reportsys.common.Result;
import com.reportsys.service.TodoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 待办与站内消息（P1-F）。
 */
@RestController
@RequestMapping("/todo")
@PreAuthorize("isAuthenticated()")
public class TodoController {

    @Autowired
    private TodoService todoService;

    /** 待办汇总（未读/已办/今日/超期） */
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        Long userId = currentUserId();
        if (userId == null) return Result.error("用户未登录");
        return Result.success(todoService.summary(userId));
    }

    /** 我的消息（分页） */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) Integer readFlag,
                                            @RequestParam(required = false) String msgType) {
        Long userId = currentUserId();
        if (userId == null) return Result.error("用户未登录");
        return Result.success(todoService.list(userId, page, Math.min(pageSize, 100), readFlag, msgType));
    }

    /** 标记已读 */
    @PostMapping("/{id}/read")
    public Result<Void> read(@PathVariable Long id) {
        try {
            Long userId = currentUserId();
            if (userId == null) return Result.error("用户未登录");
            todoService.markRead(id, userId);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 全部已读 */
    @PostMapping("/read-all")
    public Result<Map<String, Object>> readAll() {
        Long userId = currentUserId();
        if (userId == null) return Result.error("用户未登录");
        int count = todoService.markAllRead(userId);
        return Result.success(java.util.Collections.singletonMap("updated", count));
    }

    /** 催办（管理员/部门管理员）：给任务填报人发催办消息 */
    @PostMapping("/remind")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")
    public Result<Map<String, Object>> remind(@RequestParam Long taskId,
                                               @RequestParam(required = false) String comment) {
        try {
            return Result.success(todoService.remind(taskId, comment, currentUserId()));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 手动触发超期扫描通知（定时任务每天也会执行） */
    @PostMapping("/scan")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")
    public Result<Map<String, Object>> scan() {
        try {
            return Result.success(todoService.scanOverdue(currentUserId()));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long) return (Long) auth.getPrincipal();
        return null;
    }
}
