package com.reportsys.controller;

import com.reportsys.common.Result;
import com.reportsys.model.RptFile;
import com.reportsys.service.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 附件与文件接口（P1-D）。
 *
 * 普通上传 / 分片上传 / 断点续传检查 / 合并 / 下载 / 列表 / 删除，
 * 存储落地本地磁盘（替代原版 FastDFS + 外部文档系统）。
 */
@RestController
@RequestMapping("/file")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN','FILL_USER','AUDIT_USER')")
public class FileController {

    @Autowired
    private FileStorageService fileStorageService;

    /** 普通上传 */
    @PostMapping("/upload")
    public Result<RptFile> upload(@RequestParam("file") MultipartFile file,
                                   @RequestParam(required = false) Long taskId,
                                   @RequestParam(required = false) Long dataId,
                                   @RequestParam(required = false) Long templateId,
                                   @RequestParam(required = false) String fileType) {
        try {
            return Result.success(fileStorageService.save(file, fileType, taskId, dataId, templateId, currentUserId()));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 保存单个分片 */
    @PostMapping("/chunk")
    public Result<Map<String, Object>> uploadChunk(@RequestParam String fileKey,
                                                    @RequestParam int chunkIndex,
                                                    @RequestParam("file") MultipartFile file) {
        try {
            int received = fileStorageService.saveChunk(fileKey, chunkIndex, file);
            return Result.success(java.util.Collections.singletonMap("received", received));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 断点续传/秒传检查 */
    @GetMapping("/chunk/check")
    public Result<Map<String, Object>> checkChunk(@RequestParam String fileKey,
                                                   @RequestParam(required = false) String fileName,
                                                   @RequestParam(required = false) String md5) {
        try {
            return Result.success(fileStorageService.checkChunks(fileKey, fileName, md5));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 合并分片 */
    @PostMapping("/chunk/merge")
    public Result<RptFile> mergeChunk(@RequestParam String fileKey,
                                       @RequestParam String fileName,
                                       @RequestParam int chunkTotal,
                                       @RequestParam(required = false) Long taskId,
                                       @RequestParam(required = false) Long dataId,
                                       @RequestParam(required = false) String fileType) {
        try {
            return Result.success(fileStorageService.mergeChunks(fileKey, fileName, chunkTotal, fileType,
                    taskId, dataId, null, currentUserId()));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 文件列表（按任务） */
    @GetMapping("/list")
    public Result<List<RptFile>> list(@RequestParam Long taskId) {
        try {
            return Result.success(fileStorageService.listByTask(taskId));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** 下载 */
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        RptFile file = fileStorageService.getById(id);
        byte[] content = fileStorageService.readBytes(id);
        String fileName = com.reportsys.common.WebUtils.encodeFileName(file.getFileName());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content);
    }

    /** 删除（软删除） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            fileStorageService.delete(id);
            return Result.success();
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
