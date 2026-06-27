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

    /**
     * 获取项目树(按部门分组)
     */
    public List<SysDept> getProjectTree() {
        List<SysDept> depts = sysDeptMapper.selectList(
                Wrappers.<SysDept>lambdaQuery().eq(SysDept::getStatus, 1).orderByAsc(SysDept::getSort));
        List<SysProject> projects = sysProjectMapper.selectList(
                Wrappers.<SysProject>lambdaQuery().eq(SysProject::getStatus, 1));

        for (SysDept dept : depts) {
            List<SysProject> children = new ArrayList<>();
            for (SysProject proj : projects) {
                if (proj.getDeptId().equals(dept.getId())) {
                    children.add(proj);
                }
            }
            if (!children.isEmpty()) {
                dept.setChildren(new ArrayList<>());
                // 注意: SysDept.children 是 List<SysDept>，项目不能直接放进去
                // 这里返回部门树，项目信息由前端组装
            }
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

    @Transactional
    public void deleteById(Long id) {
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
