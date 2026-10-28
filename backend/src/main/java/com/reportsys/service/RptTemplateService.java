package com.reportsys.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.reportsys.common.PageResult;
import com.reportsys.dao.*;
import com.reportsys.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RptTemplateService {

    @Autowired
    private RptTemplateMapper rptTemplateMapper;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired
    private RptTemplateFieldMapper rptTemplateFieldMapper;
    @Autowired
    private SysDeptMapper sysDeptMapper;
    @Autowired
    private SysProjectMapper sysProjectMapper;
    @Autowired
    private RptDatasourceMapper rptDatasourceMapper;
    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private MaterializeService materializeService;
    @Autowired
    private ExcelService excelService;
    @Autowired
    private RptTaskMapper rptTaskMapper;
    @Autowired
    private RptTaskDataMapper rptTaskDataMapper;
    @Autowired
    private RptTemplateAuthMapper rptTemplateAuthMapper;

    /**
     * 解析上传的 Excel 模板表头，返回可用的字段建议（不入库）
     */
    public Map<String, Object> parseTemplateExcel(InputStream in, int headerRowIndex) {
        List<String> headers = excelService.parseHeader(in, headerRowIndex);
        List<Map<String, Object>> suggestions = new ArrayList<>();
        int index = 1;
        for (String header : headers) {
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("fieldName", header);
            field.put("fieldCode", "f" + index);
            field.put("dbColumn", "f" + index);
            field.put("fieldType", guessFieldType(header));
            field.put("fieldLength", 255);
            field.put("isRequired", 0);
            field.put("isKey", index == 1 ? 1 : 0);
            field.put("excelColumn", excelColumnName(index - 1));
            suggestions.add(field);
            index++;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("headers", headers);
        result.put("fieldCount", headers.size());
        result.put("fields", suggestions);
        return result;
    }

    /**
     * 物化：按模板字段在目标业务库建表
     */
    @Transactional
    public Map<String, Object> materialize(Long id, boolean recreate) {
        RptTemplate template = rptTemplateMapper.selectById(id);
        if (template == null) throw new RuntimeException("模板不存在");
        List<RptTemplateField> fields = getAllFields(id);
        if (fields.isEmpty()) throw new RuntimeException("模板没有字段，无法物化");
        RptDatasource ds = materializeService.resolveDatasource(template);
        String table = materializeService.ensureTable(template, fields, recreate);

        template.setTableName(table);
        template.setMaterialized(1);
        template.setMaterializedTime(LocalDateTime.now());
        template.setUpdateTime(LocalDateTime.now());
        rptTemplateMapper.updateById(template);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("templateId", id);
        result.put("tableName", table);
        result.put("datasourceId", ds.getId());
        result.put("dialect", ds.getDsType());
        result.put("fieldCount", fields.size());
        result.put("recreated", recreate);
        return result;
    }

    /**
     * 删除物化表
     */
    @Transactional
    public void dropMaterialized(Long id) {
        RptTemplate template = rptTemplateMapper.selectById(id);
        if (template == null) throw new RuntimeException("模板不存在");
        materializeService.dropTable(template);
        template.setMaterialized(0);
        template.setMaterializedTime(null);
        template.setUpdateTime(LocalDateTime.now());
        rptTemplateMapper.updateById(template);
    }

    /** 模板审核结果通知（原版「模板定义通知」：审核通过后才通知） */
    private void notifyTemplateAudit(RptTemplate template, Integer auditStatus) {
        if (template.getCreateBy() == null) return;
        String title = auditStatus != null && auditStatus == 1 ? "模板审核通过" : "模板审核未通过";
        String content = "模板「" + template.getTemplateName() + "」" +
                (auditStatus != null && auditStatus == 1 ? "已审核通过，可用于制定任务" : "未通过审核，请修改后重新提交");
        try {
            jdbcTemplate.update("INSERT INTO sys_message (user_id, title, content, msg_type, read_flag, create_time) "
                    + "VALUES (?,?,?,'SYSTEM',0,NOW())", template.getCreateBy(), title, content);
        } catch (Exception ignored) {
        }
    }

    /**
     * 生成模板下载文件（表头 = 字段名，示例行 = 字段类型说明）
     */
    public byte[] downloadTemplate(Long id) {
        RptTemplate template = rptTemplateMapper.selectById(id);
        if (template == null) throw new RuntimeException("模板不存在");
        List<RptTemplateField> fields = getAllFields(id);
        if (fields.isEmpty()) throw new RuntimeException("模板没有字段");
        List<String> headers = fields.stream().map(RptTemplateField::getFieldName).collect(Collectors.toList());
        List<String> examples = fields.stream()
                .map(f -> "(" + f.getFieldType() + (f.getIsKey() != null && f.getIsKey() == 1 ? ",主键" : "") + ")")
                .collect(Collectors.toList());
        return excelService.buildTemplate(headers, examples);
    }

    private String guessFieldType(String header) {
        if (header == null) return "VARCHAR";
        String h = header.trim();
        if (h.contains("日期") || h.contains("时间")) return "DATETIME";
        if (h.contains("数") || h.contains("金额") || h.contains("数量")) return "DECIMAL";
        return "VARCHAR";
    }

    private String excelColumnName(int index) {
        StringBuilder sb = new StringBuilder();
        int i = index;
        while (i >= 0) {
            sb.insert(0, (char) ('A' + i % 26));
            i = i / 26 - 1;
        }
        return sb.toString();
    }

    /**
     * 分页查询模板
     */
    public PageResult<RptTemplate> listPage(int pageNum, int pageSize, String templateName, Integer status,
                                            Long deptId, Long projectId) {
        return listPage(pageNum, pageSize, templateName, status, deptId, projectId, null, null, null);
    }

    /**
     * 分页查询模板（筛选条件全部下推到 SQL）。
     *
     * 缺陷修复：原先 fillingCycle / auditStatus / 模板授权是在 Controller 里对**已分页的 list**
     * 做 removeIf，一是 total/pages 与 list 不一致（翻页会丢数据），二是
     * {@code String.equals(Integer)} 恒为 false —— 只要传周期筛选，列表必空。
     *
     * @param allowedIds 可见模板范围；null=不限制（超管），空集合=无可见模板
     */
    public PageResult<RptTemplate> listPage(int pageNum, int pageSize, String templateName, Integer status,
                                            Long deptId, Long projectId,
                                            Integer fillingCycle, Integer auditStatus,
                                            java.util.Collection<Long> allowedIds) {
        Page<RptTemplate> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<RptTemplate> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(templateName)) wrapper.like(RptTemplate::getTemplateName, templateName);
        if (status != null) wrapper.eq(RptTemplate::getStatus, status);
        if (deptId != null) wrapper.eq(RptTemplate::getDeptId, deptId);
        if (projectId != null) wrapper.eq(RptTemplate::getProjectId, projectId);
        if (fillingCycle != null) wrapper.eq(RptTemplate::getFillingCycle, fillingCycle);
        if (auditStatus != null) wrapper.eq(RptTemplate::getAuditStatus, auditStatus);
        if (allowedIds != null) {
            if (allowedIds.isEmpty()) {
                return PageResult.of(new java.util.ArrayList<>(), pageNum, pageSize, 0);
            }
            wrapper.in(RptTemplate::getId, allowedIds);
        }
        wrapper.orderByDesc(RptTemplate::getCreateTime);
        PageResult<RptTemplate> result = PageResult.of(rptTemplateMapper.selectPage(page, wrapper));
        fillExtInfo(result.getList());
        return result;
    }

    private void fillExtInfo(List<RptTemplate> list) {
        for (RptTemplate t : list) {
            if (t.getDeptId() != null) {
                SysDept d = sysDeptMapper.selectById(t.getDeptId());
                if (d != null) t.setDeptName(d.getDeptName());
            }
            if (t.getProjectId() != null) {
                SysProject p = sysProjectMapper.selectById(t.getProjectId());
                if (p != null) t.setProjectName(p.getProjectName());
            }
            if (t.getDatasourceId() != null) {
                RptDatasource ds = rptDatasourceMapper.selectById(t.getDatasourceId());
                if (ds != null) t.setDatasourceName(ds.getDsName());
            }
            if (t.getCreateBy() != null) {
                SysUser u = sysUserMapper.selectById(t.getCreateBy());
                if (u != null) t.setCreateByName(u.getRealName() != null ? u.getRealName() : u.getUsername());
            }
        }
    }

    /**
     * 获取所有字段
     */
    public List<RptTemplateField> getAllFields(Long templateId) {
        return rptTemplateFieldMapper.selectList(
                Wrappers.<RptTemplateField>lambdaQuery()
                        .eq(RptTemplateField::getTemplateId, templateId)
                        .orderByAsc(RptTemplateField::getSort));
    }

    /**
     * 创建模板
     *
     * @return 新建模板 ID（前端保存后需要立即物化/下载，必须回传主键）
     */
    @Transactional
    public Long add(RptTemplate template, List<RptTemplateField> fields) {
        RptTemplate exist = rptTemplateMapper.selectOne(
                Wrappers.<RptTemplate>lambdaQuery().eq(RptTemplate::getTemplateCode, template.getTemplateCode()));
        if (exist != null) throw new RuntimeException("模板编码已存在");
        // 周期取值统一校验（原先前端传什么就落什么，导致「月度」被当成「日报」执行）
        PeriodService.validate(template.getFillingCycle());
        template.setCreateTime(LocalDateTime.now());
        template.setStatus(0); // 草稿
        template.setAuditStatus(0); // 待审
        rptTemplateMapper.insert(template);

        // 保存字段（缺陷修复：后端也要校验编码唯一，不能只靠前端拦）
        if (fields != null && !fields.isEmpty()) {
            Set<String> codes = new HashSet<>();
            int sort = 0;
            for (RptTemplateField field : fields) {
                String code = field.getFieldCode();
                if (code == null || code.trim().isEmpty()) {
                    throw new RuntimeException("第 " + (sort + 1) + " 个字段缺少字段编码");
                }
                if (!codes.add(code.trim().toLowerCase())) {
                    throw new RuntimeException("字段编码重复：" + code + "（字段编码必须唯一）");
                }
                // R-07 附件字段：类型锁定为 VARCHAR(255)，长度不可改
                if (field.getFieldKind() != null && field.getFieldKind() == 1) {
                    field.setFieldType("VARCHAR");
                    field.setFieldLength(255);
                }
                field.setTemplateId(template.getId());
                field.setSort(sort++);
                rptTemplateFieldMapper.insert(field);
            }
        }
        return template.getId();
    }

    /** 按模板编码查询（前端保存后按编码回查） */
    public RptTemplate getByCode(String templateCode) {
        if (StrUtil.isBlank(templateCode)) return null;
        return rptTemplateMapper.selectOne(
                Wrappers.<RptTemplate>lambdaQuery().eq(RptTemplate::getTemplateCode, templateCode));
    }

    /**
     * 更新模板
     */
    @Transactional
    public void update(RptTemplate template, List<RptTemplateField> fields) {
        if (template.getId() == null) throw new RuntimeException("模板ID不能为空");
        // 周期取值统一校验；仅在前端显式传了周期时校验（PUT 局部更新场景）
        if (template.getFillingCycle() != null) {
            PeriodService.validate(template.getFillingCycle());
        }
        template.setUpdateTime(LocalDateTime.now());
        rptTemplateMapper.updateById(template);

        if (fields != null) {
            // 删除原有字段
            rptTemplateFieldMapper.delete(
                    Wrappers.<RptTemplateField>lambdaQuery().eq(RptTemplateField::getTemplateId, template.getId()));
            // 重新保存
            int sort = 0;
            for (RptTemplateField field : fields) {
                field.setId(null);
                field.setTemplateId(template.getId());
                field.setSort(sort++);
                if (field.getFieldKind() != null && field.getFieldKind() == 1) {
                    field.setFieldType("VARCHAR");
                    field.setFieldLength(255);
                }
                rptTemplateFieldMapper.insert(field);
            }
        }
    }

    /**
     * 删除模板。
     *
     * 安全/一致性修复（审计：严重级）：原实现直接物理删除，<b>完全不检查引用</b>，
     * 导致生产库 55 条任务引用已删除模板 template_id=2，这些任务的填报功能彻底不可用
     * （commitToTarget / exportTaskData / queryTargetRows 均报"模板不存在"）。
     *
     * 现在删除前检查下游引用：任务、填报数据、模板授权，任一存在则拒绝。
     */
    @Transactional
    public void deleteById(Long id) {
        Long taskCount = rptTaskMapper.selectCount(
                Wrappers.<RptTask>lambdaQuery().eq(RptTask::getTemplateId, id));
        if (taskCount != null && taskCount > 0) {
            throw new RuntimeException("该模板下存在 " + taskCount + " 个任务，无法删除，请先删除或迁移任务");
        }
        Long dataCount = rptTaskDataMapper.selectCount(
                Wrappers.<RptTaskData>lambdaQuery().eq(RptTaskData::getTemplateId, id));
        if (dataCount != null && dataCount > 0) {
            throw new RuntimeException("该模板下存在 " + dataCount + " 条填报数据，无法删除");
        }
        Long authCount = rptTemplateAuthMapper.selectCount(
                Wrappers.<RptTemplateAuth>lambdaQuery().eq(RptTemplateAuth::getTemplateId, id));
        if (authCount != null && authCount > 0) {
            throw new RuntimeException("该模板存在 " + authCount + " 条授权记录，无法删除，请先解除授权");
        }
        rptTemplateMapper.deleteById(id);
        rptTemplateFieldMapper.delete(
                Wrappers.<RptTemplateField>lambdaQuery().eq(RptTemplateField::getTemplateId, id));
    }

    /**
     * 审核模板
     */
    @Transactional
    public void audit(Long id, Integer auditStatus) {
        RptTemplate template = rptTemplateMapper.selectById(id);
        if (template == null) throw new RuntimeException("模板不存在");
        if (template.getAuditStatus() != 0) throw new RuntimeException("模板已审核，请勿重复操作");
        template.setAuditStatus(auditStatus);
        if (auditStatus == 1) { // 通过
            template.setStatus(1); // 启用
        }
        template.setUpdateTime(LocalDateTime.now());
        rptTemplateMapper.updateById(template);
        notifyTemplateAudit(template, auditStatus);
    }

    /**
     * 启用/停用
     */
    @Transactional
    public void deploy(Long id, Integer status) {
        RptTemplate template = rptTemplateMapper.selectById(id);
        if (template == null) throw new RuntimeException("模板不存在");
        template.setStatus(status);
        template.setUpdateTime(LocalDateTime.now());
        rptTemplateMapper.updateById(template);
    }

    public RptTemplate getById(Long id) {
        RptTemplate template = rptTemplateMapper.selectById(id);
        if (template != null) {
            List<RptTemplate> list = new java.util.ArrayList<>();
            list.add(template);
            fillExtInfo(list);
            template.setFields(getAllFields(id));
        }
        return template;
    }

    public List<RptTemplate> listAll() {
        return rptTemplateMapper.selectList(
                Wrappers.<RptTemplate>lambdaQuery().eq(RptTemplate::getStatus, 1));
    }
    /**
     * R-08 关联已有表：把模板字段绑定到目标库的列。
     * binds: [{fieldId, dbColumn}]；存在未映射/重复映射时拒绝。
     */
    @org.springframework.transaction.annotation.Transactional
    public void bindColumns(Long templateId, java.util.List<java.util.Map<String, Object>> binds) {
        if (templateId == null) throw new IllegalArgumentException("缺少模板ID");
        java.util.List<com.reportsys.model.RptTemplateField> fields =
                rptTemplateFieldMapper.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers
                        .<com.reportsys.model.RptTemplateField>lambdaQuery()
                        .eq(com.reportsys.model.RptTemplateField::getTemplateId, templateId));
        if (fields.isEmpty()) throw new IllegalStateException("模板没有字段");
        if (binds == null || binds.isEmpty()) throw new IllegalStateException("请至少映射一个字段");
        java.util.Set<String> used = new java.util.HashSet<>();
        java.util.Set<Long> mapped = new java.util.HashSet<>();
        for (java.util.Map<String, Object> b : binds) {
            Object fid = b.get("fieldId");
            Object col = b.get("dbColumn");
            if (fid == null || col == null || String.valueOf(col).trim().isEmpty()) continue;
            Long fieldId = Long.valueOf(String.valueOf(fid));
            String column = String.valueOf(col).trim();
            if (!used.add(column.toLowerCase())) {
                throw new IllegalStateException("数据库列被重复映射: " + column);
            }
            mapped.add(fieldId);
            com.reportsys.model.RptTemplateField f = new com.reportsys.model.RptTemplateField();
            f.setId(fieldId);
            f.setDbColumn(column);
            rptTemplateFieldMapper.updateById(f);
        }
        if (mapped.size() != fields.size()) {
            throw new IllegalStateException("存在未映射字段，请完成全部字段映射（" + mapped.size() + "/" + fields.size() + "）");
        }
        com.reportsys.model.RptTemplate tpl = new com.reportsys.model.RptTemplate();
        tpl.setId(templateId);
        tpl.setMaterializeType(2);
        rptTemplateMapper.updateById(tpl);
    }

}
