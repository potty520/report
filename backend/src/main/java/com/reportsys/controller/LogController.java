package com.reportsys.controller;

import com.reportsys.common.Result;
import com.reportsys.model.SysLoginLog;
import com.reportsys.service.SysLoginLogService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/system/log")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class LogController {

    @Resource
    private SysLoginLogService logService;

    @GetMapping("/page")
    public Result<?> page(@RequestParam(defaultValue = "1") int pageNum,
                          @RequestParam(defaultValue = "10") int pageSize,
                          @RequestParam(required = false) String username,
                          @RequestParam(required = false) String ip,
                          @RequestParam(required = false) Integer status) {
        return Result.success(logService.listPage(pageNum, pageSize, username, ip, status));
    }

    @GetMapping("/{id}")
    public Result<SysLoginLog> detail(@PathVariable Long id) {
        SysLoginLog log = logService.getById(id);
        if (log == null) return Result.error("日志不存在");
        return Result.success(log);
    }
}
