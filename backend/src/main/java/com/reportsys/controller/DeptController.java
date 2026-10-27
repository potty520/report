package com.reportsys.controller;

import com.reportsys.common.Result;
import com.reportsys.model.SysDept;
import com.reportsys.service.SysDeptService;
import com.reportsys.service.OrgFeatureService;
import java.util.Map;
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
    @Autowired
    private OrgFeatureService orgFeatureService;

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
     *
     * 安全修复(X04)：组织架构属于平台级配置，仅超管可写。
     * 原来类级别只有 hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')，导致部门管理员可新建部门（垂直越权）。
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
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
     *
     * 安全修复：写操作收紧为超管（与 X04 同源问题）。
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
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
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/{id}")
    public Result<Void> updateById(@PathVariable Long id, @RequestBody SysDept dept) {
        dept.setId(id);
        return update(dept);
    }

    /** 安全修复：删除部门属高危操作，收紧为超管。 */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
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
    /** R-10 批量设置填报部门 */
    @PutMapping("/fill-dept")
    public Result<Void> setFillDept(@RequestBody Map<String, Object> body) {
        try {
            java.util.List<Long> ids = new java.util.ArrayList<>();
            Object raw = body.get("deptIds");
            if (raw instanceof java.util.List) {
                for (Object o : (java.util.List<?>) raw) ids.add(Long.valueOf(String.valueOf(o)));
            }
            orgFeatureService.setFillDept(ids);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /** R-10 仅返回填报部门 */
    @GetMapping("/fill-list")
    public Result<java.util.List<Map<String, Object>>> fillList() {
        try {
            return Result.success(orgFeatureService.listFillDept());
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

}
