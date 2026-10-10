package com.reportsys.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.reportsys.common.PageResult;
import com.reportsys.dao.SysDeptMapper;
import com.reportsys.model.SysDept;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class SysDeptService {

    @Autowired
    private SysDeptMapper sysDeptMapper;

    /**
     * 获取部门树
     */
    public List<SysDept> getDeptTree() {
        List<SysDept> allDepts = sysDeptMapper.selectList(
                Wrappers.<SysDept>lambdaQuery()
                        .eq(SysDept::getStatus, 1)
                        .orderByAsc(SysDept::getSort));
        return buildTree(allDepts, 0L);
    }

    private List<SysDept> buildTree(List<SysDept> allDepts, Long parentId) {
        List<SysDept> tree = new ArrayList<>();
        for (SysDept dept : allDepts) {
            if (dept.getParentId().equals(parentId)) {
                List<SysDept> children = buildTree(allDepts, dept.getId());
                if (!children.isEmpty()) {
                    dept.setChildren(children);
                }
                tree.add(dept);
            }
        }
        return tree;
    }

    /**
     * 列表查询
     */
    public PageResult<SysDept> listPage(int pageNum, int pageSize, String deptName) {
        Page<SysDept> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysDept> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(deptName)) wrapper.like(SysDept::getDeptName, deptName);
        wrapper.orderByAsc(SysDept::getSort);
        return PageResult.of(sysDeptMapper.selectPage(page, wrapper));
    }

    /**
     * 全部部门列表(树状)
     */
    public List<SysDept> listTree() {
        List<SysDept> allDepts = sysDeptMapper.selectList(
                Wrappers.<SysDept>lambdaQuery().orderByAsc(SysDept::getSort));
        return buildTree(allDepts, 0L);
    }

    /**
     * 全部部门列表(平铺)
     */
    public List<SysDept> listAll() {
        return sysDeptMapper.selectList(
                Wrappers.<SysDept>lambdaQuery().eq(SysDept::getStatus, 1).orderByAsc(SysDept::getSort));
    }


    /** 生成唯一部门编码：D + 6 位序号（跳过已占用） */
    private String generateDeptCode() {
        for (int i = 1; i <= 9999; i++) {
            String code = String.format("D%06d", i);
            Long n = sysDeptMapper.selectCount(
                    com.baomidou.mybatisplus.core.toolkit.Wrappers.<SysDept>lambdaQuery()
                            .eq(SysDept::getDeptCode, code));
            if (n == null || n == 0) {
                return code;
            }
        }
        return "D" + (System.currentTimeMillis() % 1000000);
    }

    @Transactional
    public void add(SysDept dept) {
        // 缺陷修复：sys_dept.dept_code 为 NOT NULL 且无默认值，而新增弹窗原本没有"部门编码"项，
        // 导致新增部门必然报 Field 'dept_code' doesn't have a default value。
        // 这里做兜底：为空则自动生成唯一编码；同时给前端保留显式指定的能力。
        if (dept.getDeptCode() == null || dept.getDeptCode().trim().isEmpty()) {
            dept.setDeptCode(generateDeptCode());
        }
        dept.setDeptCode(dept.getDeptCode().trim());
        SysDept exist = sysDeptMapper.selectOne(
                Wrappers.<SysDept>lambdaQuery().eq(SysDept::getDeptCode, dept.getDeptCode()));
        if (exist != null) throw new RuntimeException("部门编码已存在");
        dept.setCreateTime(LocalDateTime.now());
        sysDeptMapper.insert(dept);
    }

    @Transactional
    public void update(SysDept dept) {
        if (dept.getId() == null) throw new RuntimeException("部门ID不能为空");
        dept.setUpdateTime(LocalDateTime.now());
        sysDeptMapper.updateById(dept);
    }

    @Transactional
    public void deleteById(Long id) {
        // 检查是否有子部门
        Long count = sysDeptMapper.selectCount(
                Wrappers.<SysDept>lambdaQuery().eq(SysDept::getParentId, id));
        if (count > 0) throw new RuntimeException("该部门下存在子部门，无法删除");
        sysDeptMapper.deleteById(id);
    }

    public SysDept getById(Long id) {
        return sysDeptMapper.selectById(id);
    }
}
