package com.reportsys.service;

import java.util.List;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.reportsys.common.PageResult;
import com.reportsys.dao.SysRoleMapper;
import com.reportsys.model.SysRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class SysRoleService extends ServiceImpl<SysRoleMapper, SysRole> {

    @Resource private SysRoleMapper roleMapper;
    @Resource private SysMenuService menuService;

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

    @Transactional
    public void deleteRole(Long id) {
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
