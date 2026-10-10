package com.reportsys.controller;

import com.reportsys.common.PageResult;
import com.reportsys.common.Result;
import com.reportsys.model.RptTaskData;
import com.reportsys.service.RptTaskDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.reportsys.annotation.OperLog;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/fill")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN','FILL_USER')")
public class FillController {

    @Autowired
    private RptTaskDataService rptTaskDataService;
    @Autowired
    private com.reportsys.service.FileStorageService fileStorageService;

    /**
     * 我的填报任务列表(分页)
     */
    @GetMapping({"/myTasks", "/page"})
    public Result<PageResult<RptTaskData>> myTasks(@RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "10") int pageSize,
                                                     @RequestParam(required = false) Integer status) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return Result.error("用户未登录");
            }
            PageResult<RptTaskData> result = rptTaskDataService.getFillList(page, pageSize, userId, status);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 填报详情(通过taskId)
     */
    @GetMapping("/task/{taskId}")
    public Result<RptTaskData> getFillDetail(@PathVariable Long taskId) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return Result.error("用户未登录");
            }
            // 查询当前用户在该任务下的填报数据
            PageResult<RptTaskData> pageResult = rptTaskDataService.getFillList(1, 100, userId, null);
            RptTaskData target = null;
            if (pageResult.getList() != null) {
                for (RptTaskData data : pageResult.getList()) {
                    if (data.getTaskId().equals(taskId)) {
                        target = data;
                        break;
                    }
                }
            }
            if (target == null) {
                return Result.error("未找到填报数据");
            }
            return Result.success(target);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 提交填报数据
     */
    @PostMapping("/submit")
    public Result<Void> submit(@RequestParam Long taskId,
                                @RequestParam String dataJson,
                                @RequestParam(required = false) String fileUrl) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return Result.error("用户未登录");
            }
            // 先保存数据
            RptTaskData taskData = new RptTaskData();
            taskData.setTaskId(taskId);
            taskData.setFillUserId(userId);
            taskData.setDataJson(dataJson);
            taskData.setFileUrl(fileUrl);
            rptTaskDataService.fillData(taskData);

            // 查找刚才保存的ID并提交
            PageResult<RptTaskData> pageResult = rptTaskDataService.getFillList(1, 100, userId, 0);
            if (pageResult.getList() != null) {
                for (RptTaskData d : pageResult.getList()) {
                    if (d.getTaskId().equals(taskId)) {
                        rptTaskDataService.submitData(d.getId());
                        break;
                    }
                }
            }
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 保存草稿
     */
    @PostMapping({"/save", "/draft"})
    public Result<Void> saveDraft(@RequestParam Long taskId,
                                   @RequestParam String dataJson,
                                   @RequestParam(required = false) String fileUrl) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return Result.error("用户未登录");
            }
            RptTaskData taskData = new RptTaskData();
            taskData.setTaskId(taskId);
            taskData.setFillUserId(userId);
            taskData.setDataJson(dataJson);
            taskData.setFileUrl(fileUrl);
            rptTaskDataService.fillData(taskData);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 上传附件
     */
    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file,
                                              @RequestParam(required = false) Long taskId,
                                              @RequestParam(required = false) Long dataId) {
        try {
            com.reportsys.model.RptFile saved = fileStorageService.save(file, "ATTACHMENT", taskId, dataId,
                    null, getCurrentUserId());
            Map<String, String> data = new java.util.HashMap<>();
            data.put("fileId", String.valueOf(saved.getId()));
            data.put("fileName", saved.getFileName());
            data.put("fileUrl", "/file/" + saved.getId() + "/download");
            data.put("fileSize", String.valueOf(saved.getFileSize()));
            data.put("md5", saved.getMd5());
            return Result.success(data);
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * Excel 导入并写回目标业务库（P0-B）
     */
    @PostMapping("/import")
    @OperLog(module = "数据填报", action = "Excel导入")
    public Result<Map<String, Object>> importExcel(@RequestParam Long taskId,
                                                    @RequestParam("file") MultipartFile file,
                                                    @RequestParam(defaultValue = "0") int headerRowIndex) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) return Result.error("用户未登录");
            if (file == null || file.isEmpty()) return Result.error("请选择要导入的 Excel 文件");
            return Result.success(rptTaskDataService.importExcel(taskId, userId, file.getBytes(), headerRowIndex));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 把已提交的填报数据写回目标业务库
     */
    @PostMapping("/{taskId}/commit")
    @OperLog(module = "数据填报", action = "提交入库")
    public Result<Map<String, Object>> commitToTarget(@PathVariable Long taskId,
                                                       @RequestParam(required = false) Long dataId) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) return Result.error("用户未登录");
            return Result.success(rptTaskDataService.commitToTarget(taskId, dataId, userId));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 预览目标表已入库数据
     */
    @GetMapping("/{taskId}/target-rows")
    public Result<List<Map<String, Object>>> targetRows(@PathVariable Long taskId,
                                                         @RequestParam(defaultValue = "50") int limit) {
        try {
            return Result.success(rptTaskDataService.queryTargetRows(taskId, limit));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 导出任务数据（从目标物化表读取，xlsx 下载）
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam Long taskId) {
        Map<String, Object> result = rptTaskDataService.exportTaskData(taskId);
        byte[] content = (byte[]) result.get("content");
        String fileName = URLEncoder.encode(String.valueOf(result.get("fileName")), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content);
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
