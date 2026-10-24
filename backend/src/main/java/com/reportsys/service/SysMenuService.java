package com.reportsys.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.reportsys.dao.SysMenuMapper;
import com.reportsys.dao.SysRoleMenuMapper;
import com.reportsys.model.SysMenu;
import com.reportsys.model.SysRoleMenu;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.Resource;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

@Service
public class SysMenuService extends ServiceImpl<SysMenuMapper, SysMenu> {

    @Resource private SysMenuMapper menuMapper;
    @Resource private SysRoleMenuMapper sysRoleMenuMapper;

    public List<SysMenu> getAllMenuTree() {
        List<SysMenu> allMenus = menuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getStatus, 1).orderByAsc(SysMenu::getSort));
        return buildTree(allMenus, 0L);
    }

    private List<SysMenu> buildTree(List<SysMenu> menus, Long parentId) {
        return menus.stream()
                .filter(m -> m.getParentId().equals(parentId))
                .peek(m -> m.setChildren(buildTree(menus, m.getId())))
                .sorted(Comparator.comparingInt(SysMenu::getSort))
                .collect(Collectors.toList());
    }

    @Transactional
    public void assignMenusToRole(Long roleId, List<Long> menuIds) {
        sysRoleMenuMapper.deleteByRoleId(roleId);
        for (Long menuId : menuIds) {
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(roleId);
            rm.setMenuId(menuId);
            sysRoleMenuMapper.insert(rm);
        }
    }

    @Transactional
    public void removeMenusByRoleId(Long roleId) {
        sysRoleMenuMapper.deleteByRoleId(roleId);
    }

    public void addMenu(SysMenu menu) {
        menuMapper.insert(menu);
    }

    public void updateMenu(SysMenu menu) {
        menuMapper.updateById(menu);
    }

    public List<Long> getRoleMenuIds(Long roleId) {
        List<SysRoleMenu> rms = sysRoleMenuMapper.selectList(
                new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        return rms.stream().map(SysRoleMenu::getMenuId).collect(java.util.stream.Collectors.toList());
    }

    public void deleteMenu(Long id) {
        List<SysMenu> children = menuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id));
        for (SysMenu child : children) {
            deleteMenu(child.getId());
        }
        menuMapper.deleteById(id);
    }

    // Controller compatibility aliases
    public void add(SysMenu menu) { addMenu(menu); }
    public void update(SysMenu menu) { updateMenu(menu); }
    public void deleteById(Long id) { deleteMenu(id); }
    public List<SysMenu> listAll() { return menuMapper.selectList(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getStatus, 1).orderByAsc(SysMenu::getSort)); }
    @Cacheable(value = "menus", key = "'all'", unless = "#result == null || #result.size() == 0")
    public List<SysMenu> getMenuTree() { return getAllMenuTree(); }
}
