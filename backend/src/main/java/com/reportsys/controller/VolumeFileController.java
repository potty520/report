package com.reportsys.controller;

import com.reportsys.annotation.OperLog;
import com.reportsys.common.Result;
import com.reportsys.service.AuthzService;
import com.reportsys.service.VolumeFileService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 分卷附件（P1-D 补全）：多卷成组上传 + 成组打包下载。
 *
 * POST   /file/volume/upload            上传一个分卷（file, groupKey, volumeIndex, taskId?）
 * GET    /file/volume/list?taskId=      按任务列出分卷组
 * GET    /file/volume/{groupKey}/download  成组打包为 zip 下载
 * DELETE /file/volume/{groupKey}        删除整个分卷组
 */
@RestController
@RequestMapping("/file/volume")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN','FILL_USER','AUDIT_USER')")
public class VolumeFileController {

    @Resource
    private VolumeFileService volumeFileService;
    @Resource
    private AuthzService authzService;

    @PostMapping("/upload")
    @OperLog(module = "附件管理", action = "上传分卷")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                              @RequestParam String groupKey,
                                              @RequestParam int volumeIndex,
                                              @RequestParam(required = false) Long taskId,
                                              @RequestParam(required = false) Long dataId) {
        try {
            return Result.success(volumeFileService.upload(file, groupKey, volumeIndex, taskId, dataId,
                    authzService.currentUserId()));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) Long taskId) {
        try {
            return Result.success(volumeFileService.listGroups(taskId));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{groupKey}/download")
    public ResponseEntity<byte[]> download(@PathVariable String groupKey) {
        byte[] content = volumeFileService.zipGroup(groupKey);
        String fileName = com.reportsys.common.WebUtils.encodeFileName("分卷附件-" + groupKey + ".zip");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content);
    }

    @DeleteMapping("/{groupKey}")
    @OperLog(module = "附件管理", action = "删除分卷组")
    public Result<Integer> delete(@PathVariable String groupKey) {
        try {
            return Result.success(volumeFileService.deleteGroup(groupKey));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
