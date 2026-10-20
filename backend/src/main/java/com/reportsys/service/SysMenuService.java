package com.reportsys.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.reportsys.dao.SysMenuMapper;
import com.reportsys.dao.SysRoleMenuMapper;
import com.reportsys.model.SysMenu;
import com.reportsys.model.SysRoleMenu;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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
        if (menuIds == null || menuIds.isEmpty()) return;

        // Ant Design 目录在“半选”时只回传叶子节点。若只保存叶子，登录菜单树
        // 从 parentId=0 递归构建时会找不到父目录，整棵业务分支都会消失。
        // 因此在写库前统一补齐所有祖先菜单。
        Map<Long, SysMenu> menuById = menuMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysMenu::getId, Function.identity(), (a, b) -> a));
        Set<Long> expanded = new LinkedHashSet<>();
        for (Long menuId : menuIds) {
            Long current = menuId;
            while (current != null && current != 0L && menuById.containsKey(current)) {
                if (!expanded.add(current)) break;
                SysMenu menu = menuById.get(current);
                current = menu.getParentId();
            }
        }
        for (Long menuId : expanded) {
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
