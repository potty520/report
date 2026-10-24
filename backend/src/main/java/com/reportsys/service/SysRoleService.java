package com.reportsys.service;

import java.util.List;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.reportsys.common.PageResult;
import com.reportsys.dao.SysRoleMapper;
import com.reportsys.model.SysRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class SysRoleService extends ServiceImpl<SysRoleMapper, SysRole> {

    @Resource private SysRoleMapper roleMapper;
    @Resource private SysMenuService menuService;
    @Resource private com.reportsys.dao.SysUserRoleMapper userRoleMapper;

    public PageResult<SysRole> pageList(int pageNum, int pageSize, String keyword) {
        Page<SysRole> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(SysRole::getRoleName, keyword).or().like(SysRole::getRoleCode, keyword);
        }
        wrapper.orderByDesc(SysRole::getCreateTime);
        return PageResult.of(roleMapper.selectPage(page, wrapper));
    }

    @Transactional
    public void addRole(SysRole role) {
        role.setCreateTime(LocalDateTime.now());
        roleMapper.insert(role);
        if (role.getMenuIds() != null && !role.getMenuIds().isEmpty()) {
            menuService.assignMenusToRole(role.getId(), role.getMenuIds());
        }
    }

    @Transactional
    public void updateRole(SysRole role) {
        roleMapper.updateById(role);
        if (role.getMenuIds() != null && !role.getMenuIds().isEmpty()) {
            menuService.assignMenusToRole(role.getId(), role.getMenuIds());
        }
    }

    /**
     * 删除角色。
     *
     * 缺陷修复(R08)：原来不检查角色是否仍被用户占用，直接物理删除，
     * 导致 sys_user_role 里留下悬空 role_id（用户"有角色 ID 但角色不存在"），权限判断会出问题。
     * 现在先统计关联用户数，>0 时拒绝删除并提示，让调用方先解绑。
     */
    @Transactional
    public void deleteRole(Long id) {
        long inUse = userRoleMapper.selectCount(
                new LambdaQueryWrapper<com.reportsys.model.SysUserRole>()
                        .eq(com.reportsys.model.SysUserRole::getRoleId, id));
        if (inUse > 0) {
            throw new IllegalStateException("该角色下存在 " + inUse + " 个用户，无法删除，请先解除关联");
        }
        roleMapper.deleteById(id);
        menuService.removeMenusByRoleId(id);
    }

    // Controller compatibility aliases
    public void add(SysRole role) { addRole(role); }
    public void update(SysRole role) { updateRole(role); }
    public void deleteById(Long id) { deleteRole(id); }
    public List<SysRole> listAll() { return roleMapper.selectList(new LambdaQueryWrapper<>()); }
    public PageResult<SysRole> listPage(int pageNum, int pageSize, String keyword) { return pageList(pageNum, pageSize, keyword); }
    public void assignMenus(Long roleId, List<Long> menuIds) { menuService.assignMenusToRole(roleId, menuIds); }
    public List<Long> getRoleMenuIds(Long roleId) {
        return menuService.getRoleMenuIds(roleId);
    }
}
