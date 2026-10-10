package com.reportsys.controller;

import com.reportsys.common.PageResult;
import com.reportsys.common.Result;
import com.reportsys.model.SysUser;
import com.reportsys.service.SysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class UserController {

    @Autowired
    private SysUserService sysUserService;

    /**
     * 分页列表
     */
    @GetMapping("/page")
    public Result<PageResult<SysUser>> listPage(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int pageSize,
                                                 @RequestParam(required = false) String username,
                                                 @RequestParam(required = false) Long deptId,
                                                 @RequestParam(required = false) Integer status) {
        try {
            PageResult<SysUser> result = sysUserService.listPage(page, pageSize, username, deptId, status);
            // 如果有status过滤，在内存中过滤(Service层未实现status参数)
            if (status != null && result.getList() != null) {
                result.getList().removeIf(u -> !status.equals(u.getStatus()));
            }
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 新增用户
     */
    @PostMapping
    public Result<Void> add(@RequestBody SysUser user) {
        try {
            sysUserService.add(user);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新用户
     */
    @PutMapping
    public Result<Void> update(@RequestBody SysUser user) {
        try {
            sysUserService.update(user);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除用户
     */
    /** 兼容旧前端：PUT /xxx/{id}（id 在路径里）；委托同类 update，保留权限与校验 */
    @PutMapping("/{id}")
    public Result<Void> updateById(@PathVariable Long id, @RequestBody SysUser user) {
        user.setId(id);
        return update(user);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            sysUserService.deleteById(id);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 切换状态
     */
    @PutMapping("/{id}/status")
    public Result<Void> toggleStatus(@PathVariable Long id, @RequestParam Integer status) {
        try {
            SysUser user = sysUserService.getById(id);
            if (user == null) {
                return Result.error("用户不存在");
            }
            user.setStatus(status);
            sysUserService.update(user);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 分配角色
     */
    @PutMapping("/{id}/roles")
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
        try {
            sysUserService.assignRoles(id, roleIds);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
