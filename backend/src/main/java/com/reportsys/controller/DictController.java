package com.reportsys.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.reportsys.common.Result;
import com.reportsys.annotation.OperLog;
import com.reportsys.model.SysDict;
import com.reportsys.dao.SysDictMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据字典（P2-G）。
 */
@RestController
@RequestMapping("/dict")
@PreAuthorize("isAuthenticated()")
public class DictController {

    @Autowired
    private SysDictMapper sysDictMapper;

    /** 全部字典（按类型分组，前端一次拉取） */
    @GetMapping("/all")
    public Result<Map<String, List<SysDict>>> all() {
        List<SysDict> list = sysDictMapper.selectList(Wrappers.<SysDict>lambdaQuery()
                .eq(SysDict::getStatus, 1).orderByAsc(SysDict::getDictType).orderByAsc(SysDict::getSort));
        Map<String, List<SysDict>> grouped = new LinkedHashMap<>();
        for (SysDict dict : list) {
            grouped.computeIfAbsent(dict.getDictType(), k -> new ArrayList<>()).add(dict);
        }
        return Result.success(grouped);
    }

    /** 按类型查询 */
    @GetMapping("/{dictType}")
    public Result<List<SysDict>> byType(@PathVariable String dictType) {
        return Result.success(sysDictMapper.selectList(Wrappers.<SysDict>lambdaQuery()
                .eq(SysDict::getDictType, dictType)
                .eq(SysDict::getStatus, 1)
                .orderByAsc(SysDict::getSort)));
    }

    /** 按类型查询（含停用项，管理用） */
    @GetMapping("/manage/{dictType}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<List<SysDict>> manage(@PathVariable String dictType) {
        return Result.success(sysDictMapper.selectList(Wrappers.<SysDict>lambdaQuery()
                .eq(StrUtil.isNotBlank(dictType), SysDict::getDictType, dictType)
                .orderByAsc(SysDict::getDictType).orderByAsc(SysDict::getSort)));
    }

    @PostMapping
    @OperLog(module = "字典管理", action = "新增字典项")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Long> add(@RequestBody SysDict dict) {
        try {
            if (StrUtil.isBlank(dict.getDictType()) || StrUtil.isBlank(dict.getDictCode())) {
                return Result.error("字典类型与编码不能为空");
            }
            Long exists = sysDictMapper.selectCount(Wrappers.<SysDict>lambdaQuery()
                    .eq(SysDict::getDictType, dict.getDictType())
                    .eq(SysDict::getDictCode, dict.getDictCode()));
            if (exists != null && exists > 0) return Result.error("该字典项已存在");
            sysDictMapper.insert(dict);
            return Result.success(dict.getId());
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping
    @OperLog(module = "字典管理", action = "修改字典项")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Void> update(@RequestBody SysDict dict) {
        try {
            sysDictMapper.updateById(dict);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @OperLog(module = "字典管理", action = "删除字典项")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            sysDictMapper.deleteById(id);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}
