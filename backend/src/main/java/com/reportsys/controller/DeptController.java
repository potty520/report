package com.reportsys.controller;

import com.reportsys.common.Result;
import com.reportsys.model.SysDept;
import com.reportsys.service.SysDeptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/dept")
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
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除部门
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            sysDeptService.deleteById(id);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
