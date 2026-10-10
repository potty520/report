package com.reportsys.controller;

import com.reportsys.common.Result;
import com.reportsys.model.SysDept;
import com.reportsys.service.SysDeptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/dept", "/org/dept"})
@PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")
public class DeptController {

    @Autowired
    private SysDeptService sysDeptService;

    /**
     * 部门树
     */
    @GetMapping("/tree")
    public Result<List<SysDept>> getDeptTree() {
        try {
            List<SysDept> tree = sysDeptService.getDeptTree();
            return Result.success(tree);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 部门列表
     */
    @GetMapping("/list")
    public Result<List<SysDept>> listAll() {
        try {
            List<SysDept> list = sysDeptService.listAll();
            return Result.success(list);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 新增部门
     */
    @PostMapping
    public Result<Void> add(@RequestBody SysDept dept) {
        try {
            sysDeptService.add(dept);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新部门
     */
    @PutMapping
    public Result<Void> update(@RequestBody SysDept dept) {
        try {
            sysDeptService.update(dept);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除部门
     */
    /** 兼容旧前端：PUT /xxx/{id}（id 在路径里）；委托同类 update，保留权限与校验 */
    @PutMapping("/{id}")
    public Result<Void> updateById(@PathVariable Long id, @RequestBody SysDept dept) {
        dept.setId(id);
        return update(dept);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            sysDeptService.deleteById(id);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
