package com.reportsys.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.reportsys.dao.RptFileMapper;
import com.reportsys.model.RptFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 本地文件存储（替代原版 FastDFS / 外部文档系统）。
 *
 * 目录结构：
 *   ${report.file.root}/yyyyMMdd/{uuid}_{fileName}    正式文件
 *   ${report.file.root}/chunks/{fileKey}/{index}.part 分片
 *
 * 支持：普通上传、分片上传、断点续传（查询已上传分片）、秒传（同 md5 直接复用）、下载、软删除。
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private RptFileMapper rptFileMapper;

    @Value("${report.file.root:/opt/report/files}")
    private String fileRoot;

    @Value("${report.file.max-size-mb:200}")
    private int maxSizeMb;

    private Path root;

    @PostConstruct
    public void init() {
        root = Paths.get(fileRoot).toAbsolutePath();
        try {
            Files.createDirectories(root);
            Files.createDirectories(root.resolve("chunks"));
            log.info("文件存储根目录: {}", root);
        } catch (IOException e) {
            log.error("创建文件存储目录失败: {}", e.getMessage());
        }
    }

    public Path getRoot() {
        return root;
    }

    /**
     * 允许上传的文件后缀白名单。
     *
     * 缺陷修复(G07)：原实现只做文件名非法字符清洗，没有后缀限制，
     * .sh / .exe / .jsp 等可执行或可被解释的文件也能上传成功（评测实测 .sh 被接受=True）。
     * 改为白名单：不在名单内一律拒绝。白名单比黑名单可靠，黑名单永远漏得掉新后缀。
     */
    private static final java.util.Set<String> ALLOWED_EXTENSIONS = new java.util.HashSet<>(java.util.Arrays.asList(
            // 办公文档
            "doc", "docx", "xls", "xlsx", "xlsm", "csv", "ppt", "pptx", "pdf", "txt", "rtf", "md",
            // 图片
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg",
            // 压缩包
            "zip", "rar", "7z", "tar", "gz",
            // 音视频
            "mp3", "mp4", "wav", "avi", "mov",
            // 结构化数据
            "json", "xml", "yaml", "yml"
    ));

    /** 校验文件后缀是否在白名单内；无后缀或不在名单内一律拒绝 */
    private void assertExtensionAllowed(String originalFilename) {
        if (StrUtil.isBlank(originalFilename)) return;
        String name = originalFilename.trim();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            throw new RuntimeException("文件缺少扩展名，拒绝上传");
        }
        String ext = name.substring(dot + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new RuntimeException("不支持的文件类型: ." + ext + "，仅允许上传文档、图片、压缩包等常规格式");
        }
    }

    /** 普通上传 */
    @Transactional
    public RptFile save(MultipartFile file, String fileType, Long taskId, Long dataId, Long templateId, Long uploadBy) {
        if (file == null || file.isEmpty()) throw new RuntimeException("上传文件为空");
        // 缺陷修复(G07)：先做后缀白名单校验，再做大小/落盘
        assertExtensionAllowed(file.getOriginalFilename());
        long maxBytes = (long) maxSizeMb * 1024 * 1024;
        if (file.getSize() > maxBytes) throw new RuntimeException("文件超过 " + maxSizeMb + "MB 限制");
        try {
            byte[] content = file.getBytes();
            String md5 = md5(content);
            return store(content, safeName(file.getOriginalFilename()), file.getContentType(), md5,
                    fileType, taskId, dataId, templateId, uploadBy);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 秒传/断点续传检查：返回已上传分片与是否可秒传。
     *
     * 安全修复（审计 H6）：原实现按 md5 <b>全局</b>查重并直接返回他人文件的 fileId，
     * 攻击者只要知道某个 md5 就能"秒传"拿到别人附件的 id，再走下载接口取内容。
     *
     * 现在秒传只认<b>同一上传人</b>的已有文件；其他用户的同 md5 文件由调用方自己去重
     * （store 内部也会按 uploadBy 隔离），不会泄露 fileId。
     */
    public Map<String, Object> checkChunks(String fileKey, String fileName, String md5, Long uploadBy) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileKey", fileKey);
        result.put("uploadedChunks", uploadedChunks(fileKey));
        if (StrUtil.isNotBlank(md5) && uploadBy != null) {
            RptFile exist = rptFileMapper.selectOne(Wrappers.<RptFile>lambdaQuery()
                    .eq(RptFile::getMd5, md5)
                    .eq(RptFile::getUploadBy, uploadBy)
                    .eq(RptFile::getDeleted, 0).last("limit 1"));
            if (exist != null) {
                result.put("skipUpload", true);
                result.put("fileId", exist.getId());
                result.put("fileName", exist.getFileName());
                return result;
            }
        }
        result.put("skipUpload", false);
        return result;
    }

    public List<Integer> uploadedChunks(String fileKey) {
        List<Integer> list = new ArrayList<>();
        if (StrUtil.isBlank(fileKey)) return list;
        Path dir = root.resolve("chunks").resolve(sanitize(fileKey));
        if (!Files.isDirectory(dir)) return list;
        try (java.util.stream.Stream<Path> stream = Files.list(dir)) {
            stream.forEach(p -> {
                String name = p.getFileName().toString();
                if (name.endsWith(".part")) {
                    try {
                        list.add(Integer.parseInt(name.substring(0, name.length() - 5)));
                    } catch (NumberFormatException ignored) {
                    }
                }
            });
        } catch (IOException e) {
            log.warn("读取分片目录失败: {}", e.getMessage());
        }
        list.sort(Integer::compareTo);
        return list;
    }

    /** 保存单个分片 */
    public int saveChunk(String fileKey, int chunkIndex, MultipartFile chunk) {
        if (StrUtil.isBlank(fileKey)) throw new RuntimeException("fileKey 不能为空");
        if (chunk == null || chunk.isEmpty()) throw new RuntimeException("分片内容为空");
        try {
            Path dir = root.resolve("chunks").resolve(sanitize(fileKey));
            Files.createDirectories(dir);
            Path target = dir.resolve(chunkIndex + ".part");
            try (InputStream in = chunk.getInputStream()) {
                Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return uploadedChunks(fileKey).size();
        } catch (IOException e) {
            throw new RuntimeException("分片保存失败: " + e.getMessage(), e);
        }
    }

    /** 合并分片为正式文件并登记 */
    @Transactional
    public RptFile mergeChunks(String fileKey, String fileName, int chunkTotal, String fileType,
                               Long taskId, Long dataId, Long templateId, Long uploadBy) {
        Path dir = root.resolve("chunks").resolve(sanitize(fileKey));
        if (!Files.isDirectory(dir)) throw new RuntimeException("没有找到分片目录，请先上传分片");
        try {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            for (int i = 0; i < chunkTotal; i++) {
                Path part = dir.resolve(i + ".part");
                if (!Files.exists(part)) throw new RuntimeException("缺少分片 " + i + "，无法合并");
                Files.copy(part, buffer);
            }
            byte[] content = buffer.toByteArray();
            String md5 = md5(content);
            RptFile file = store(content, safeName(fileName), null, md5, fileType, taskId, dataId, templateId, uploadBy);
            // 清理分片
            try (java.util.stream.Stream<Path> stream = Files.list(dir)) {
                stream.forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) { } });
            }
            Files.deleteIfExists(dir);
            return file;
        } catch (IOException e) {
            throw new RuntimeException("分片合并失败: " + e.getMessage(), e);
        }
    }

    private RptFile store(byte[] content, String fileName, String contentType, String md5, String fileType,
                          Long taskId, Long dataId, Long templateId, Long uploadBy) throws IOException {
        // 缺陷修复(G07)：store() 是所有落盘路径的唯一入口（普通上传 + 分片合并都走这里），
        // 在入口统一做后缀白名单，避免某条路径漏校验。
        assertExtensionAllowed(fileName);
        // 秒传：同 md5 已存在则直接复用。
        // 安全修复（审计 H6）：只复用<b>同一上传人</b>的文件，
        // 否则 A 上传后 B 传同内容会拿到 A 的文件记录（越权下载/删除他人附件）。
        RptFile exist = null;
        if (uploadBy != null) {
            exist = rptFileMapper.selectOne(Wrappers.<RptFile>lambdaQuery()
                    .eq(RptFile::getMd5, md5)
                    .eq(RptFile::getUploadBy, uploadBy)
                    .eq(RptFile::getDeleted, 0).last("limit 1"));
        }
        if (exist != null) {
            log.info("命中秒传: {} -> fileId={}", fileName, exist.getId());
            return exist;
        }
        String day = LocalDate.now().format(DAY);
        Path dir = root.resolve(day);
        Files.createDirectories(dir);
        String stored = UUID.randomUUID().toString().replace("-", "") + "_" + fileName;
        Path target = dir.resolve(stored);
        Files.write(target, content);

        RptFile file = new RptFile();
        file.setTaskId(taskId);
        file.setDataId(dataId);
        file.setTemplateId(templateId);
        file.setFileName(fileName);
        file.setStorePath(root.relativize(target).toString().replace('\\', '/'));
        file.setFileSize((long) content.length);
        file.setFileType(StrUtil.isBlank(fileType) ? "ATTACHMENT" : fileType);
        file.setContentType(contentType == null ? "" : contentType);
        file.setMd5(md5);
        file.setUploadBy(uploadBy);
        file.setDeleted(0);
        file.setCreateTime(LocalDateTime.now());
        rptFileMapper.insert(file);
        return file;
    }

    public List<RptFile> listByTask(Long taskId) {
        return rptFileMapper.selectList(Wrappers.<RptFile>lambdaQuery()
                .eq(RptFile::getTaskId, taskId).eq(RptFile::getDeleted, 0)
                .orderByDesc(RptFile::getCreateTime));
    }

    public RptFile getById(Long id) {
        RptFile file = rptFileMapper.selectById(id);
        if (file == null || (file.getDeleted() != null && file.getDeleted() == 1)) {
            throw new RuntimeException("文件不存在或已删除");
        }
        return file;
    }

    public byte[] readBytes(Long id) {
        RptFile file = getById(id);
        try {
            return Files.readAllBytes(root.resolve(file.getStorePath()));
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败: " + e.getMessage(), e);
        }
    }

    public void writeTo(Long id, OutputStream out) {
        byte[] bytes = readBytes(id);
        try {
            out.write(bytes);
        } catch (IOException e) {
            throw new RuntimeException("写出文件失败: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void delete(Long id) {
        RptFile file = getById(id);
        file.setDeleted(1);
        rptFileMapper.updateById(file);
    }

    private String safeName(String name) {
        if (StrUtil.isBlank(name)) return "unnamed";
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return cleaned.length() > 120 ? cleaned.substring(cleaned.length() - 120) : cleaned;
    }

    private String sanitize(String key) {
        return key.replaceAll("[^0-9A-Za-z_\\-]", "_");
    }

    private String md5(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(content);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
