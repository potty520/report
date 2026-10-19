package com.reportsys.controller;

import com.reportsys.common.Result;
import com.reportsys.model.SysDept;
import com.reportsys.model.SysProject;
import com.reportsys.service.SysProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/project", "/org/project"})
@PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")
public class ProjectController {

    @Autowired
    private SysProjectService sysProjectService;

    /**
     * 项目树(按部门分组)
     */
    @GetMapping("/tree")
    public Result<List<SysDept>> getProjectTree() {
        try {
            List<SysDept> tree = sysProjectService.getProjectTree();
            return Result.success(tree);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 项目列表
     */
    @GetMapping("/list")
    public Result<List<SysProject>> listAll() {
        try {
            List<SysProject> list = sysProjectService.listAll();
            return Result.success(list);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 新增项目
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping
    public Result<Void> add(@RequestBody SysProject project) {
        try {
            sysProjectService.add(project);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新项目
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping
    public Result<Void> update(@RequestBody SysProject project) {
        try {
            sysProjectService.update(project);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除项目
     */
    /** 兼容旧前端：PUT /xxx/{id}（id 在路径里）；委托同类 update，保留权限与校验 */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/{id}")
    public Result<Void> updateById(@PathVariable Long id, @RequestBody SysProject project) {
        project.setId(id);
        return update(project);
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            sysProjectService.deleteById(id);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
