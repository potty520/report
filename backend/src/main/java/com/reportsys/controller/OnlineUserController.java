package com.reportsys.controller;

import com.reportsys.annotation.OperLog;
import com.reportsys.common.Result;
import com.reportsys.service.OnlineUserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在线用户管理（补齐原版在线用户/异地登录能力）。
 *
 * GET  /online/list?keyword=        在线会话列表（超管）
 * GET  /online/count               在线数
 * POST /online/kick?sessionId=&reason=   按会话踢下线（超管）
 * POST /online/kick-user?userId=&reason= 按用户踢下线（超管）
 *
 * 踢下线后该 token 会在 JwtAuthFilter 中被拒绝（HTTP 401）。
 */
@RestController
@RequestMapping("/online")
public class OnlineUserController {

    @Resource
    private OnlineUserService onlineUserService;

    @GetMapping("/list")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword) {
        return Result.success(onlineUserService.list(keyword));
    }

    @GetMapping("/count")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> count() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("online", onlineUserService.onlineCount());
        return Result.success(out);
    }

    @PostMapping("/kick")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @OperLog(module = "在线用户", action = "踢下线")
    public Result<Integer> kick(@RequestParam String sessionId,
                                @RequestParam(required = false) String reason) {
        try {
            return Result.success(onlineUserService.kickBySession(sessionId, reason));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/kick-user")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @OperLog(module = "在线用户", action = "踢下线（按用户）")
    public Result<Integer> kickUser(@RequestParam Long userId,
                                    @RequestParam(required = false) String reason) {
        try {
            return Result.success(onlineUserService.kickByUser(userId, reason));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
