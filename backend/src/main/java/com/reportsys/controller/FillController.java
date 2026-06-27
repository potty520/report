package com.reportsys.controller;

import com.reportsys.common.PageResult;
import com.reportsys.common.Result;
import com.reportsys.model.RptTaskData;
import com.reportsys.service.RptTaskDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/fill")
public class FillController {

    @Autowired
    private RptTaskDataService rptTaskDataService;

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
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 保存草稿
     */
    @PostMapping("/save")
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
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 上传附件
     */
    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        try {
            // 简单文件上传处理，返回模拟URL
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isEmpty()) {
                return Result.error("文件名不能为空");
            }
            // 实际项目应上传到OSS/本地存储
            String fileUrl = "/uploads/" + System.currentTimeMillis() + "_" + originalFilename;
            java.util.Map<String, String> data = new java.util.HashMap<>();
            data.put("fileName", originalFilename);
            data.put("fileUrl", fileUrl);
            data.put("fileSize", String.valueOf(file.getSize()));
            return Result.success(data);
        } catch (Exception e) {
            return Result.error("文件上传失败: " + e.getMessage());
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
