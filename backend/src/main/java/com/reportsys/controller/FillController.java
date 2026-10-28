package com.reportsys.controller;

import com.reportsys.common.PageResult;
import com.reportsys.common.Result;
import com.reportsys.model.RptTaskData;
import com.reportsys.service.RptTaskDataService;
import com.reportsys.service.FillActionService;
import com.reportsys.service.TaskReturnService;
import com.reportsys.service.TemplateDisplayService;
import com.reportsys.service.ImportCheckService;
import com.reportsys.service.ImportVerifyService;
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
    private FillActionService fillActionService;
    @Autowired
    private TaskReturnService taskReturnService;
    @Autowired
    private TemplateDisplayService templateDisplayService;
    @Autowired
    private ImportCheckService importCheckService;
    @Autowired
    private ImportVerifyService importVerifyService;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired
    private com.reportsys.service.FileStorageService fileStorageService;
    @Autowired
    private com.reportsys.service.AuthzService authzService;
    @Autowired
    private com.reportsys.service.RptTaskService rptTaskService;

    /**
     * 我被分派的填报任务列表（含"尚未填报"的任务）。
     *
     * 功能补全（前端审计发现）：{@code /fill/task/page} 查的是 rpt_task_data，
     * 只有"存过草稿或提交过"的任务才会出现；被分派但从未保存的任务看不到，
     * 导致首次填报没有入口。本接口直接返回分派任务（含未填报），供填报页展示待办。
     *
     * 实现说明：复用 RptTaskService.getMyTaskList（原先已有实现但没有暴露成接口）。
     */
    @GetMapping("/my-assigned")
    public Result<PageResult<com.reportsys.model.RptTask>> myAssigned(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Integer status) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) return Result.error("用户未登录");
            return Result.success(rptTaskService.getMyTaskList(page, pageSize, userId, status));
        } catch (RuntimeException e) {
            return Result.error(Result.sanitizeMessage(e.getMessage()));
        }
    }

    /**
     * 我的填报任务列表(分页)
     *
     * 路由修复(F02)：前端(frontend2)实际调用 /fill/task/page，
     * 但后端只有 /fill/task/{taskId}，导致 "page" 被当成 taskId 解析成 Long 失败并返回 500。
     * 这里显式注册精确路径 /task/page，让 Spring 优先匹配字面路径而不是通配路径。
     */
    @GetMapping({"/myTasks", "/page", "/task/page"})
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
     *
     * 路由修复(F02 配套)：加 {taskId:\d+} 正则约束，避免再被 /task/page 之类的字面路径误捕获。
     */
    @GetMapping("/task/{taskId:\\d+}")
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
                                                       @RequestParam(required = false) Long dataId,
                                                       @RequestParam(defaultValue = "false") boolean force) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) return Result.error("用户未登录");
            return Result.success(rptTaskDataService.commitToTarget(taskId, dataId, userId, force));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 预览目标表已入库数据。
     *
     * 安全修复（审计 H2）：原方法<b>没有任何归属校验</b>，
     * 任意 FILL_USER 传任意 taskId 就能读取该任务的全量物化表数据。
     * 现要求：必须被分派到该任务（assertCanFill），或为超管。
     */
    @GetMapping("/{taskId}/target-rows")
    public Result<List<Map<String, Object>>> targetRows(@PathVariable Long taskId,
                                                         @RequestParam(defaultValue = "50") int limit) {
        try {
            authzService.assertCanFill(taskId, getCurrentUserId());
            return Result.success(rptTaskDataService.queryTargetRows(taskId, limit));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(Result.sanitizeMessage(e.getMessage()));
        }
    }

    /**
     * 导出任务数据（从目标物化表读取，xlsx 下载）。
     *
     * 安全修复（审计 H2）：同 targetRows，原先无任何归属校验，
     * 任一填报员可导出任意任务的全量数据（审计报告标注为"最大数据泄露面"）。
     * 现在导出前统一做分派归属校验。
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam Long taskId) {
        authzService.assertCanFill(taskId, getCurrentUserId());
        Map<String, Object> result = rptTaskDataService.exportTaskData(taskId);
        byte[] content = (byte[]) result.get("content");
        String fileName = com.reportsys.common.WebUtils.encodeFileName(String.valueOf(result.get("fileName")));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content);
    }

    /**
     * 获取当前用户ID
     */

    // ==================== R-02 无更新提交 / R-04 填报退回 / R-01 参考数据 ====================

    /** R-02 无更新提交 */
    @PostMapping("/no-update")
    public Result<Void> noUpdate(@RequestBody Map<String, Object> body) {
        try {
            Long taskId = body.get("taskId") == null ? null : Long.valueOf(String.valueOf(body.get("taskId")));
            Object reason = body.get("reason");
            fillActionService.noUpdate(taskId, reason == null ? null : String.valueOf(reason), getCurrentUserId());
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** R-04 填报人退回未提交任务 */
    @PostMapping("/{taskId}/return")
    public Result<Void> returnTask(@PathVariable Long taskId, @RequestBody(required = false) Map<String, Object> body) {
        try {
            Object reason = body == null ? null : body.get("reason");
            taskReturnService.returnByFiller(taskId, getCurrentUserId(), reason == null ? null : String.valueOf(reason));
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** R-01 填报页参考数据（只读） */
    @GetMapping("/{taskId}/reference")
    public Result<Map<String, Object>> reference(@PathVariable Long taskId,
                                                 @RequestParam(required = false) Long deptId) {
        try {
            return Result.success(templateDisplayService.reference(taskId, deptId));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** R-09 导入校验记录 */
    @GetMapping("/{taskId}/import-checks")
    public Result<Map<String, Object>> importChecks(@PathVariable Long taskId) {
        try {
            return Result.success(importCheckService.list(taskId));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }


    /** R-09 报表核验 + 导入预校验（返回逐行错误明细） */
    @PostMapping("/{taskId}/verify")
    public Result<Map<String, Object>> verify(@PathVariable Long taskId,
                                              @RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) body.getOrDefault("rows", java.util.Collections.emptyList());
            return Result.success(importVerifyService.verify(taskId, rows, getCurrentUserId()));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long) {
                return (Long) authentication.getPrincipal();
        }
        return null;
    }
}
