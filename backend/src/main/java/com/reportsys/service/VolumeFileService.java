package com.reportsys.service;

import cn.hutool.core.util.StrUtil;
import com.reportsys.model.RptFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 分卷附件（对应原版 report_volume_file / 分卷与非分卷附件）。
 *
 * 设计：不修改既有的 rpt_file 表结构，另建 rpt_volume_file 记录「卷号 → 文件」的归属，
 * 每个卷本身仍由 FileStorageService 正常存储（含秒传/去重），成组下载时按卷号顺序打包为 zip。
 */
@Service
public class VolumeFileService {

    private static final Logger log = LoggerFactory.getLogger(VolumeFileService.class);

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private FileStorageService fileStorageService;

    /** 上传一个分卷 */
    @Transactional
    public Map<String, Object> upload(MultipartFile file, String groupKey, int volumeIndex,
                                      Long taskId, Long dataId, Long userId) {
        if (StrUtil.isBlank(groupKey)) throw new RuntimeException("分卷组标识（groupKey）不能为空");
        if (volumeIndex < 0) throw new RuntimeException("卷号不能为负数");

        RptFile stored = fileStorageService.save(file, "VOLUME", taskId, dataId, null, userId);
        List<Long> exists = jdbcTemplate.queryForList(
                "SELECT id FROM rpt_volume_file WHERE group_key = ? AND volume_index = ?",
                Long.class, groupKey, volumeIndex);
        if (exists.isEmpty()) {
            jdbcTemplate.update("INSERT INTO rpt_volume_file (group_key, volume_index, file_id, task_id, create_by, create_time) "
                    + "VALUES (?,?,?,?,?,NOW())", groupKey, volumeIndex, stored.getId(), taskId, userId);
        } else {
            jdbcTemplate.update("UPDATE rpt_volume_file SET file_id = ?, task_id = ? WHERE id = ?",
                    stored.getId(), taskId, exists.get(0));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("groupKey", groupKey);
        out.put("volumeIndex", volumeIndex);
        out.put("fileId", stored.getId());
        out.put("fileName", stored.getFileName());
        out.put("fileSize", stored.getFileSize());
        return out;
    }

    /** 按任务列出分卷组（含每个卷的文件信息） */
    public List<Map<String, Object>> listGroups(Long taskId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT v.group_key, v.volume_index, v.file_id, f.file_name, f.file_size, f.md5, f.create_time "
                        + "FROM rpt_volume_file v JOIN rpt_file f ON f.id = v.file_id AND f.deleted = 0 "
                        + "WHERE (? IS NULL OR v.task_id = ?) ORDER BY v.group_key, v.volume_index",
                taskId, taskId);
        Map<String, Map<String, Object>> groups = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String key = String.valueOf(row.get("group_key"));
            Map<String, Object> group = groups.computeIfAbsent(key, k -> {
                Map<String, Object> g = new LinkedHashMap<>();
                g.put("groupKey", k);
                g.put("parts", new ArrayList<Map<String, Object>>());
                g.put("totalSize", 0L);
                return g;
            });
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> parts = (List<Map<String, Object>>) group.get("parts");
            Map<String, Object> part = new LinkedHashMap<>();
            part.put("volumeIndex", row.get("volume_index"));
            part.put("fileId", row.get("file_id"));
            part.put("fileName", row.get("file_name"));
            part.put("fileSize", row.get("file_size"));
            part.put("md5", row.get("md5"));
            part.put("createTime", row.get("create_time"));
            parts.add(part);
            Object size = row.get("file_size");
            long current = (Long) group.get("totalSize");
            group.put("totalSize", current + (size instanceof Number ? ((Number) size).longValue() : 0L));
        }
        for (Map<String, Object> group : groups.values()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> parts = (List<Map<String, Object>>) group.get("parts");
            group.put("partCount", parts.size());
        }
        return new ArrayList<>(groups.values());
    }

    /** 成组打包下载（按卷号顺序打成 zip） */
    public byte[] zipGroup(String groupKey) {
        List<Map<String, Object>> parts = jdbcTemplate.queryForList(
                "SELECT v.volume_index, v.file_id, f.file_name FROM rpt_volume_file v "
                        + "JOIN rpt_file f ON f.id = v.file_id AND f.deleted = 0 "
                        + "WHERE v.group_key = ? ORDER BY v.volume_index", groupKey);
        if (parts.isEmpty()) throw new RuntimeException("该分卷组不存在或所有分卷已删除");

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
                java.util.Set<String> used = new java.util.HashSet<>();
                for (Map<String, Object> part : parts) {
                    Long fileId = ((Number) part.get("file_id")).longValue();
                    String name = String.valueOf(part.get("file_name"));
                    if (!used.add(name)) {
                        name = part.get("volume_index") + "_" + name;
                        used.add(name);
                    }
                    zip.putNextEntry(new ZipEntry(name));
                    zip.write(fileStorageService.readBytes(fileId));
                    zip.closeEntry();
                }
            }
            return buffer.toByteArray();
        } catch (Exception e) {
            log.warn("分卷打包失败: {}", e.getMessage());
            throw new RuntimeException("分卷打包失败: " + e.getMessage());
        }
    }

    /** 删除整个分卷组（软删除各卷文件并清理分组记录） */
    @Transactional
    public int deleteGroup(String groupKey) {
        List<Long> fileIds = jdbcTemplate.queryForList(
                "SELECT file_id FROM rpt_volume_file WHERE group_key = ?", Long.class, groupKey);
        for (Long fileId : fileIds) {
            try {
                fileStorageService.delete(fileId);
            } catch (Exception e) {
                log.warn("删除分卷文件失败 fileId={}: {}", fileId, e.getMessage());
            }
        }
        int removed = jdbcTemplate.update("DELETE FROM rpt_volume_file WHERE group_key = ?", groupKey);
        return removed;
    }
}
