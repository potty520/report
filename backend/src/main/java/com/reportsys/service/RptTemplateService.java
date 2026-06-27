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

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RptTemplateService {

    @Autowired
    private RptTemplateMapper rptTemplateMapper;
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

    /**
     * 分页查询模板
     */
    public PageResult<RptTemplate> listPage(int pageNum, int pageSize, String templateName, Integer status,
                                            Long deptId, Long projectId) {
        Page<RptTemplate> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<RptTemplate> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(templateName)) wrapper.like(RptTemplate::getTemplateName, templateName);
        if (status != null) wrapper.eq(RptTemplate::getStatus, status);
        if (deptId != null) wrapper.eq(RptTemplate::getDeptId, deptId);
        if (projectId != null) wrapper.eq(RptTemplate::getProjectId, projectId);
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
     */
    @Transactional
    public void add(RptTemplate template, List<RptTemplateField> fields) {
        RptTemplate exist = rptTemplateMapper.selectOne(
                Wrappers.<RptTemplate>lambdaQuery().eq(RptTemplate::getTemplateCode, template.getTemplateCode()));
        if (exist != null) throw new RuntimeException("模板编码已存在");
        template.setCreateTime(LocalDateTime.now());
        template.setStatus(0); // 草稿
        template.setAuditStatus(0); // 待审
        rptTemplateMapper.insert(template);

        // 保存字段
        if (fields != null && !fields.isEmpty()) {
            int sort = 0;
            for (RptTemplateField field : fields) {
                field.setTemplateId(template.getId());
                field.setSort(sort++);
                rptTemplateFieldMapper.insert(field);
            }
        }
    }

    /**
     * 更新模板
     */
    @Transactional
    public void update(RptTemplate template, List<RptTemplateField> fields) {
        if (template.getId() == null) throw new RuntimeException("模板ID不能为空");
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
                rptTemplateFieldMapper.insert(field);
            }
        }
    }

    /**
     * 删除模板
     */
    @Transactional
    public void deleteById(Long id) {
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
}
