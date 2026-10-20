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
    @Autowired
    private com.reportsys.service.AuthzService authzService;

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

    /** 断点续传/秒传检查（安全修复：秒传只认本人文件，不再全局复用他人 fileId） */
    @GetMapping("/chunk/check")
    public Result<Map<String, Object>> checkChunk(@RequestParam String fileKey,
                                                   @RequestParam(required = false) String fileName,
                                                   @RequestParam(required = false) String md5) {
        try {
            return Result.success(fileStorageService.checkChunks(fileKey, fileName, md5, currentUserId()));
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
            // 安全修复：补任务归属校验，避免任意用户按 taskId 列出他人任务的全部附件
            authzService.assertCanFill(taskId, currentUserId());
            return Result.success(fileStorageService.listByTask(taskId));
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(com.reportsys.common.Result.sanitizeMessage(e.getMessage()));
        }
    }

    /**
     * 下载。
     *
     * 安全修复（审计：/file/{id}/download 无归属校验）：原先遍历 id 即可下载全公司附件。
     * 现要求：超管 / 文件上传者本人 / 该文件所属任务的被分派人，三者之一。
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        RptFile file = fileStorageService.getById(id);
        assertFileAccessible(file);
        byte[] content = fileStorageService.readBytes(id);
        String fileName = com.reportsys.common.WebUtils.encodeFileName(file.getFileName());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content);
    }

    /** 删除（软删除）。安全修复：同下载，补归属校验。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            RptFile file = fileStorageService.getById(id);
            assertFileAccessible(file);
            fileStorageService.delete(id);
            return Result.success();
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(com.reportsys.common.Result.sanitizeMessage(e.getMessage()));
        }
    }

    /**
     * 附件访问控制：超管 / 上传者本人 / 所属任务的被分派人。
     *
     * 说明：文件若未挂任务（taskId 为空），仅上传者本人与超管可访问，
     * 避免「无主文件」成为越权通道。
     */
    private void assertFileAccessible(RptFile file) {
        if (file == null) throw new RuntimeException("文件不存在或已删除");
        if (authzService.isAdmin()) return;
        Long me = authzService.requireUserId();
        if (file.getUploadBy() != null && file.getUploadBy().equals(me)) return;
        if (file.getTaskId() != null) {
            // 被分派到该任务的用户可以访问该任务的附件（内部会做超管放行）
            authzService.assertCanFill(file.getTaskId(), me);
            return;
        }
        throw new org.springframework.security.access.AccessDeniedException("无权访问该附件");
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long) return (Long) auth.getPrincipal();
        return null;
    }
}
