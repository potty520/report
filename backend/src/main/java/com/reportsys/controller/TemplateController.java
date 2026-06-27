package com.reportsys.controller;

import com.reportsys.common.PageResult;
import com.reportsys.common.Result;
import com.reportsys.model.RptTemplate;
import com.reportsys.model.RptTemplateField;
import com.reportsys.service.RptTemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/template")
public class TemplateController {

    @Autowired
    private RptTemplateService rptTemplateService;

    /**
     * 分页列表
     */
    @GetMapping("/page")
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
            }
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 详情(包含字段列表)
     */
    @GetMapping("/{id}")
    public Result<RptTemplate> getById(@PathVariable Long id) {
        try {
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
    @PostMapping
    public Result<Void> add(@RequestBody RptTemplate template) {
        try {
            Long userId = getCurrentUserId();
            if (userId != null) {
                template.setCreateBy(userId);
            }
            List<RptTemplateField> fields = template.getFields();
            template.setFields(null);
            rptTemplateService.add(template, fields);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新模板
     */
    @PutMapping
    public Result<Void> update(@RequestBody RptTemplate template) {
        try {
            List<RptTemplateField> fields = template.getFields();
            template.setFields(null);
            rptTemplateService.update(template, fields);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除模板
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            rptTemplateService.deleteById(id);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 切换状态(启用/停用)
     */
    @PutMapping("/{id}/status")
    public Result<Void> toggleStatus(@PathVariable Long id, @RequestParam Integer status) {
        try {
            rptTemplateService.deploy(id, status);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 审核
     */
    @PutMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id,
                               @RequestParam Integer auditStatus,
                               @RequestParam(required = false) String auditComment) {
        try {
            rptTemplateService.audit(id, auditStatus);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取模板字段列表
     */
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
    @PostMapping("/{id}/fields")
    public Result<Void> saveFields(@PathVariable Long id, @RequestBody List<RptTemplateField> fields) {
        try {
            RptTemplate template = rptTemplateService.getById(id);
            if (template == null) {
                return Result.error("模板不存在");
            }
            rptTemplateService.update(template, fields);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 部署(关联数据源+表)
     */
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
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
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
