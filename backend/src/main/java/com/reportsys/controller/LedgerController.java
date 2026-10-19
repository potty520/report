package com.reportsys.controller;

import com.reportsys.common.Result;
import com.reportsys.service.ExcelService;
import com.reportsys.service.ReportStatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务台账（P1-E）：6 张视图 + 导出 + 完成率/按时率/超期率。
 */
@RestController
@RequestMapping("/ledger")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN','AUDIT_USER')")
public class LedgerController {

    @Autowired
    private ReportStatsService reportStatsService;
    @Autowired
    private ExcelService excelService;

    /** 支持的台账视图类型 */
    @GetMapping("/types")
    public Result<List<Map<String, Object>>> types() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String type : ReportStatsService.TYPES) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", type);
            item.put("label", reportStatsService.typeLabel(type));
            list.add(item);
        }
        return Result.success(list);
    }

    /** 三率统计（可按部门/任务过滤） */
    @GetMapping("/rates")
    public Result<Map<String, Object>> rates(@RequestParam(required = false) Long deptId,
                                             @RequestParam(required = false) Long taskId) {
        return Result.success(reportStatsService.rates(deptId, taskId));
    }

    /** 台账视图（分页） */
    @GetMapping("/{type}")
    public Result<Map<String, Object>> view(@PathVariable String type,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int pageSize) {
        try {
            if (!reportStatsService.isSupported(type)) return Result.error("不支持的台账类型: " + type);
            return Result.success(reportStatsService.view(type, page, Math.min(pageSize, 200)));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 导出台账（xlsx） */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam String type,
                                          @RequestParam(defaultValue = "20000") int limit) {
        if (!reportStatsService.isSupported(type)) {
            throw new RuntimeException("不支持的台账类型: " + type);
        }
        Map<String, String> headerMap = reportStatsService.headers(type);
        List<Map<String, Object>> rows = reportStatsService.all(type, Math.min(limit, 50000));
        List<String> headers = new ArrayList<>(headerMap.values());
        List<Map<String, Object>> exportRows = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : headerMap.entrySet()) {
                item.put(entry.getValue(), row.get(entry.getKey()));
            }
            exportRows.add(item);
        }
        byte[] content = excelService.export(headers, exportRows);
        String fileName = com.reportsys.common.WebUtils.encodeFileName("台账-" + reportStatsService.typeLabel(type) + "-"
                + System.currentTimeMillis() + ".xlsx");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content);
    }
}
