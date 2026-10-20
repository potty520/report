package com.reportsys.controller;

import com.reportsys.common.PageResult;
import com.reportsys.common.Result;
import com.reportsys.model.RptTemplate;
import com.reportsys.model.RptTemplateField;
import com.reportsys.service.RptTemplateService;
import com.reportsys.service.TemplateAuthService;
import com.reportsys.service.AuthzService;
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
@RequestMapping("/template")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")
public class TemplateController {

    @Autowired
    private RptTemplateService rptTemplateService;

    @Autowired
    private TemplateAuthService templateAuthService;

    @Autowired
    private AuthzService authzService;

    /**
     * 分页列表
     *
     * 路由修复(P13)：旧前端(frontend)调用 /template/list，会被 /template/{id} 捕获，
     * "list" 解析成 Long 失败返回 500。这里补上 /list 别名，指向同一分页逻辑。
     */
    @GetMapping({"/page", "/list"})
    public Result<PageResult<RptTemplate>> listPage(@RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "10") int pageSize,
                                                     @RequestParam(required = false) String templateName,
                                                     @RequestParam(required = false) String fillingCycle,
                                                     @RequestParam(required = false) Integer status,
                                                     @RequestParam(required = false) Integer auditStatus) {
        try {
            PageResult<RptTemplate> result = rptTemplateService.listPage(page, pageSize, templateName,
                    status, null, null);
            // fillingCycle和auditStatus在Service层未实现，这里做内存过滤
            List<RptTemplate> list = result.getList();
            if (list != null) {
                if (fillingCycle != null) {
                    list.removeIf(t -> !fillingCycle.equals(t.getFillingCycle()));
                }
                if (auditStatus != null) {
                    list.removeIf(t -> !auditStatus.equals(t.getAuditStatus()));
                }
                // 模板两级授权：设置过授权的模板仅授权部门/用户可见（超级管理员不受限）
                if (!authzService.hasAnyRole("SUPER_ADMIN")) {
                    java.util.Set<Long> allowed = templateAuthService.accessibleTemplateIds(getCurrentUserId());
                    list.removeIf(t -> !allowed.contains(t.getId()));
                }
            }
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 详情(包含字段列表)
     *
     * 路由修复(P13 配套)：加 {id:\d+} 约束，防止 /list、/by-code 等字面路径被当成 id。
     */
    @GetMapping("/{id:\\d+}")
    public Result<RptTemplate> getById(@PathVariable Long id) {
        try {
            // 模板两级授权：无权访问的模板按"不存在"处理，避免越权读取字段定义
            if (!authzService.hasAnyRole("SUPER_ADMIN")
                    && !templateAuthService.canAccess(id, getCurrentUserId())) {
                return Result.error("无权访问该模板");
            }
            RptTemplate template = rptTemplateService.getById(id);
            if (template == null) {
                return Result.error("模板不存在");
            }
            return Result.success(template);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 新增模板
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping
    public Result<Long> add(@RequestBody RptTemplate template) {
        try {
            Long userId = getCurrentUserId();
            if (userId != null) {
                template.setCreateBy(userId);
            }
            List<RptTemplateField> fields = template.getFields();
            template.setFields(null);
            Long id = rptTemplateService.add(template, fields);
            // 返回主键：前端保存后可直接物化/下载模板
            return Result.success(id);
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新模板
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping
    public Result<Void> update(@RequestBody RptTemplate template) {
        try {
            List<RptTemplateField> fields = template.getFields();
            template.setFields(null);
            rptTemplateService.update(template, fields);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除模板
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            rptTemplateService.deleteById(id);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 切换状态(启用/停用)
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/{id}/status")
    public Result<Void> toggleStatus(@PathVariable Long id, @RequestParam Integer status) {
        try {
            rptTemplateService.deploy(id, status);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 审核（模板审核属管理动作，仅超级管理员）
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id,
                               @RequestParam Integer auditStatus,
                               @RequestParam(required = false) String auditComment) {
        try {
            rptTemplateService.audit(id, auditStatus);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取模板字段列表（只读）。
     *
     * 安全修复/功能修复：类级 {@code @PreAuthorize} 只允许 SUPER_ADMIN/DEPT_ADMIN，
     * 导致 FILL_USER 填报时取不到表单字段（前端只能降级成手工输入）。
     * 字段定义本身是「表单结构」，不含业务数据，允许所有已登录填报/审核角色读取是合理的；
     * 但写接口（saveFields）仍保持仅超管。
     */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN','FILL_USER','AUDIT_USER')")
    @GetMapping("/{id}/fields")
    public Result<List<RptTemplateField>> getFields(@PathVariable Long id) {
        try {
            List<RptTemplateField> fields = rptTemplateService.getAllFields(id);
            return Result.success(fields);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 批量保存字段映射
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/{id}/fields")
    public Result<Void> saveFields(@PathVariable Long id, @RequestBody List<RptTemplateField> fields) {
        try {
            RptTemplate template = rptTemplateService.getById(id);
            if (template == null) {
                return Result.error("模板不存在");
            }
            rptTemplateService.update(template, fields);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 部署(关联数据源+表)
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/{id}/deploy")
    public Result<Void> deploy(@PathVariable Long id,
                                @RequestParam(required = false) Long datasourceId,
                                @RequestParam(required = false) String tableName) {
        try {
            RptTemplate template = rptTemplateService.getById(id);
            if (template == null) {
                return Result.error("模板不存在");
            }
            if (datasourceId != null) {
                template.setDatasourceId(datasourceId);
            }
            if (tableName != null) {
                template.setTableName(tableName);
            }
            rptTemplateService.update(template, null);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 按模板编码查询（前端保存后按编码回查，避免模糊匹配）
     */
    @GetMapping("/by-code")
    public Result<RptTemplate> getByCode(@RequestParam String templateCode) {
        try {
            RptTemplate template = rptTemplateService.getByCode(templateCode);
            if (template == null) return Result.error("模板不存在");
            return Result.success(template);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 解析上传的 Excel 模板表头，返回字段建议（P0-A）
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/parse-excel")
    public Result<Map<String, Object>> parseExcel(@RequestParam("file") MultipartFile file,
                                                   @RequestParam(defaultValue = "0") int headerRowIndex) {
        try {
            if (file == null || file.isEmpty()) return Result.error("请选择 Excel 模板文件");
            return Result.success(rptTemplateService.parseTemplateExcel(file.getInputStream(), headerRowIndex));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            return Result.error("模板解析失败: " + e.getMessage());
        }
    }

    /**
     * 物化到目标业务库（建表）；recreate=true 先删后建
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/{id}/materialize")
    @OperLog(module = "模板管理", action = "物化建表")
    public Result<Map<String, Object>> materialize(@PathVariable Long id,
                                                    @RequestParam(defaultValue = "false") boolean recreate) {
        try {
            return Result.success(rptTemplateService.materialize(id, recreate));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除物化表
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @DeleteMapping("/{id}/materialize")
    @OperLog(module = "模板管理", action = "删除物化表")
    public Result<Void> dropMaterialize(@PathVariable Long id) {
        try {
            rptTemplateService.dropMaterialized(id);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 下载模板文件（xlsx）
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        byte[] content = rptTemplateService.downloadTemplate(id);
        String fileName = com.reportsys.common.WebUtils.encodeFileName("template-" + id + ".xlsx");
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
