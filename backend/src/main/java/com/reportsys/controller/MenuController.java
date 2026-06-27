package com.reportsys.controller;

import com.reportsys.common.Result;
import com.reportsys.model.SysMenu;
import com.reportsys.service.SysMenuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/menu")
public class MenuController {

    @Autowired
    private SysMenuService sysMenuService;

    /**
     * 菜单树
     */
    @GetMapping("/tree")
    public Result<List<SysMenu>> getMenuTree() {
        try {
            List<SysMenu> tree = sysMenuService.getMenuTree();
            return Result.success(tree);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 全部菜单列表(平铺)
     */
    @GetMapping("/list")
    public Result<List<SysMenu>> listAll() {
        try {
            List<SysMenu> list = sysMenuService.listAll();
            return Result.success(list);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 新增菜单
     */
    @PostMapping
    public Result<Void> add(@RequestBody SysMenu menu) {
        try {
            sysMenuService.add(menu);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新菜单
     */
    @PutMapping
    public Result<Void> update(@RequestBody SysMenu menu) {
        try {
            sysMenuService.update(menu);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除菜单
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            sysMenuService.deleteById(id);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
