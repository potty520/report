package com.reportsys.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.reportsys.common.PageResult;
import com.reportsys.dao.SysDeptMapper;
import com.reportsys.dao.SysProjectMapper;
import com.reportsys.model.SysDept;
import com.reportsys.model.SysProject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class SysProjectService {

    @Autowired
    private SysProjectMapper sysProjectMapper;
    @Autowired
    private SysDeptMapper sysDeptMapper;
    @Autowired
    private com.reportsys.dao.RptTaskMapper rptTaskMapper;
    @Autowired
    private com.reportsys.dao.RptTemplateMapper rptTemplateMapper;

    /**
     * 获取项目树(按部门分组)
     */
    public List<SysDept> getProjectTree() {
        List<SysDept> depts = sysDeptMapper.selectList(
                Wrappers.<SysDept>lambdaQuery().eq(SysDept::getStatus, 1).orderByAsc(SysDept::getSort));
        List<SysProject> projects = sysProjectMapper.selectList(
                Wrappers.<SysProject>lambdaQuery().eq(SysProject::getStatus, 1));

        for (SysDept dept : depts) {
            List<SysProject> deptProjects = new ArrayList<>();
            for (SysProject proj : projects) {
                if (proj.getDeptId().equals(dept.getId())) {
                    deptProjects.add(proj);
                }
            }
            dept.setProjects(deptProjects);
        }
        return depts;
    }

    public PageResult<SysProject> listPage(int pageNum, int pageSize, String projectName) {
        return listPage(pageNum, pageSize, projectName, null);
    }

    public PageResult<SysProject> listPage(int pageNum, int pageSize, String projectName, Long deptId) {
        Page<SysProject> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysProject> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(projectName)) wrapper.like(SysProject::getProjectName, projectName);
        if (deptId != null) wrapper.eq(SysProject::getDeptId, deptId);
        wrapper.orderByDesc(SysProject::getCreateTime);
        PageResult<SysProject> result = PageResult.of(sysProjectMapper.selectPage(page, wrapper));
        for (SysProject p : result.getList()) {
            if (p.getDeptId() != null) {
                SysDept d = sysDeptMapper.selectById(p.getDeptId());
                if (d != null) p.setDeptName(d.getDeptName());
            }
        }
        return result;
    }

    public List<SysProject> listAll() {
        return sysProjectMapper.selectList(
                Wrappers.<SysProject>lambdaQuery().eq(SysProject::getStatus, 1));
    }

    public List<SysProject> listByDeptId(Long deptId) {
        return sysProjectMapper.selectList(
                Wrappers.<SysProject>lambdaQuery()
                        .eq(SysProject::getDeptId, deptId)
                        .eq(SysProject::getStatus, 1));
    }

    @Transactional
    public void add(SysProject project) {
        SysProject exist = sysProjectMapper.selectOne(
                Wrappers.<SysProject>lambdaQuery().eq(SysProject::getProjectCode, project.getProjectCode()));
        if (exist != null) throw new RuntimeException("项目编码已存在");
        project.setCreateTime(LocalDateTime.now());
        sysProjectMapper.insert(project);
    }

    @Transactional
    public void update(SysProject project) {
        if (project.getId() == null) throw new RuntimeException("项目ID不能为空");
        project.setUpdateTime(LocalDateTime.now());
        sysProjectMapper.updateById(project);
    }

    /**
     * 删除项目。
     *
     * 一致性修复（审计：高）：原实现直接物理删除，不检查子项目、
     * rpt_task.project_id、rpt_template.project_id 引用。
     */
    @Transactional
    public void deleteById(Long id) {
        Long childCount = sysProjectMapper.selectCount(
                Wrappers.<SysProject>lambdaQuery().eq(SysProject::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new RuntimeException("该项目下存在 " + childCount + " 个子项目，无法删除");
        }
        Long taskCount = rptTaskMapper.selectCount(
                Wrappers.<com.reportsys.model.RptTask>lambdaQuery()
                        .eq(com.reportsys.model.RptTask::getProjectId, id));
        if (taskCount != null && taskCount > 0) {
            throw new RuntimeException("该项目下存在 " + taskCount + " 个任务，无法删除");
        }
        Long tplCount = rptTemplateMapper.selectCount(
                Wrappers.<com.reportsys.model.RptTemplate>lambdaQuery()
                        .eq(com.reportsys.model.RptTemplate::getProjectId, id));
        if (tplCount != null && tplCount > 0) {
            throw new RuntimeException("该项目下存在 " + tplCount + " 个模板，无法删除");
        }
        sysProjectMapper.deleteById(id);
    }

    public SysProject getById(Long id) {
        SysProject project = sysProjectMapper.selectById(id);
        if (project != null && project.getDeptId() != null) {
            SysDept d = sysDeptMapper.selectById(project.getDeptId());
            if (d != null) project.setDeptName(d.getDeptName());
        }
        return project;
    }
}
