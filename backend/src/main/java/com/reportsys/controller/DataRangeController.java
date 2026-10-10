package com.reportsys.controller;

import com.reportsys.annotation.OperLog;
import com.reportsys.common.Result;
import com.reportsys.model.RptDataRangeConfig;
import com.reportsys.service.DataRangeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据范围 SQL 配置（P2-G）。
 */
@RestController
@RequestMapping("/dataRange")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")
public class DataRangeController {

    @Autowired
    private DataRangeService dataRangeService;

    @GetMapping("/list")
    public Result<List<RptDataRangeConfig>> list() {
        return Result.success(dataRangeService.list());
    }

    @GetMapping("/{id}")
    public Result<RptDataRangeConfig> get(@PathVariable Long id) {
        return Result.success(dataRangeService.getById(id));
    }

    @PostMapping
    public Result<Long> save(@RequestBody RptDataRangeConfig config) {
        try {
            return Result.success(dataRangeService.save(config, currentUserId()));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping
    public Result<Long> update(@RequestBody RptDataRangeConfig config) {
        try {
            return Result.success(dataRangeService.save(config, currentUserId()));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            dataRangeService.delete(id);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 预览：执行配置里的 SQL（支持 Para_DepID / Para_UserId 变量） */
    @GetMapping("/{id}/preview")
    public Result<Map<String, Object>> preview(@PathVariable Long id,
                                                @RequestParam(required = false) Long deptId) {
        try {
            return Result.success(dataRangeService.preview(id, deptId, currentUserId()));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** SQL 试跑（不落库） */
    @PostMapping("/run")
    @OperLog(module = "数据范围", action = "SQL试跑")
    public Result<Map<String, Object>> run(@RequestBody Map<String, Object> body) {
        try {
            Long datasourceId = body.get("datasourceId") == null ? null
                    : Long.valueOf(String.valueOf(body.get("datasourceId")));
            Long deptId = body.get("deptId") == null ? null : Long.valueOf(String.valueOf(body.get("deptId")));
            int limit = body.get("limit") == null ? 0 : Integer.parseInt(String.valueOf(body.get("limit")));
            String sql = body.get("sql") == null ? "" : String.valueOf(body.get("sql"));
            return Result.success(dataRangeService.runSql(datasourceId, sql, deptId, currentUserId(), limit));
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
