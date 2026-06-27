package com.reportsys.controller;

import com.reportsys.common.PageResult;
import com.reportsys.common.Result;
import com.reportsys.model.SysRole;
import com.reportsys.service.SysRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/role")
public class RoleController {

    @Autowired
    private SysRoleService sysRoleService;

    /**
     * 全部角色列表
     */
    @GetMapping("/list")
    public Result<List<SysRole>> listAll() {
        try {
            List<SysRole> list = sysRoleService.listAll();
            return Result.success(list);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 分页列表
     */
    @GetMapping("/page")
    public Result<PageResult<SysRole>> listPage(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int pageSize,
                                                 @RequestParam(required = false) String roleName) {
        try {
            PageResult<SysRole> result = sysRoleService.listPage(page, pageSize, roleName);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 新增角色
     */
    @PostMapping
    public Result<Void> add(@RequestBody SysRole role) {
        try {
            sysRoleService.add(role);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新角色
     */
    @PutMapping
    public Result<Void> update(@RequestBody SysRole role) {
        try {
            sysRoleService.update(role);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除角色
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            sysRoleService.deleteById(id);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 分配菜单权限
     */
    @PutMapping("/{id}/menus")
    public Result<Void> assignMenus(@PathVariable Long id, @RequestBody List<Long> menuIds) {
        try {
            sysRoleService.assignMenus(id, menuIds);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取角色的菜单ID列表
     */
    @GetMapping("/{id}/menus")
    public Result<List<Long>> getRoleMenuIds(@PathVariable Long id) {
        try {
            List<Long> menuIds = sysRoleService.getRoleMenuIds(id);
            return Result.success(menuIds);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
