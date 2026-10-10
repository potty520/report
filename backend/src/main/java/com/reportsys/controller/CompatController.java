package com.reportsys.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.reportsys.common.PageResult;
import com.reportsys.common.Result;
import com.reportsys.dao.RptDataApplyMapper;
import com.reportsys.dao.RptDatasourceMapper;
import com.reportsys.dao.RptTaskAssignMapper;
import com.reportsys.dao.RptTaskMapper;
import com.reportsys.dao.SysDeptMapper;
import com.reportsys.dao.SysMenuMapper;
import com.reportsys.dao.SysProjectMapper;
import com.reportsys.dao.SysRoleMapper;
import com.reportsys.dao.SysUserMapper;
import com.reportsys.model.RptDataApply;
import com.reportsys.model.RptDatasource;
import com.reportsys.model.RptTask;
import com.reportsys.model.RptTaskAssign;
import com.reportsys.model.SysDept;
import com.reportsys.model.SysMenu;
import com.reportsys.model.SysProject;
import com.reportsys.model.SysRole;
import com.reportsys.model.SysUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 兼容层：补齐前端既有调用、但后端原本缺失的端点。
 *
 * 背景：前端若干页面调用的路径与后端不一致（见《新增功能测试报告》缺陷 1：
 * 34 处前后端路径不匹配，表现为"编辑/详情/删除/测试连接"点了没反应）。
 * 其中大部分已在前端改走后端正确路径；**真正缺失**的少数端点集中在这里补齐，
 * 避免为了一个 by-id 查询去改动 10 个页面的数据流。
 *
 * 这里只做薄封装（按 id 查询、分页、删除），业务规则仍在各 Service 里。
 */
@RestController
public class CompatController {

    @Autowired
    private SysDeptMapper sysDeptMapper;
    @Autowired
    private SysProjectMapper sysProjectMapper;
    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private RptTaskMapper rptTaskMapper;
    @Autowired
    private RptTaskAssignMapper rptTaskAssignMapper;
    @Autowired
    private RptDataApplyMapper rptDataApplyMapper;
    @Autowired
    private SysRoleMapper sysRoleMapper;
    @Autowired
    private SysMenuMapper sysMenuMapper;
    @Autowired
    private RptDatasourceMapper rptDatasourceMapper;

    /** 当前登录用户（前端 useAuthStore.initialize 会调用；主体是 userId 字符串） */
    @GetMapping("/auth/current-user")
    public Result<SysUser> currentUser(Authentication authentication) {
        Long userId = principalUserId(authentication);
        if (userId == null) {
            return Result.error("未登录或无法识别当前用户");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        user.setPassword(null);
        return Result.success(user);
    }

    /** 部门详情（前端 GET /org/dept/{id} 已改到这里） */
    @GetMapping("/dept/{id}")
    public Result<SysDept> deptById(@PathVariable Long id) {
        SysDept dept = sysDeptMapper.selectById(id);
        if (dept == null) {
            return Result.error("部门不存在");
        }
        return Result.success(dept);
    }

    /** 项目详情 */
    @GetMapping("/project/{id}")
    public Result<SysProject> projectById(@PathVariable Long id) {
        SysProject project = sysProjectMapper.selectById(id);
        if (project == null) {
            return Result.error("项目不存在");
        }
        return Result.success(project);
    }

    /** 任务详情（前端 GET /task/{id}，原来是 PUT /task/{id} 之外的缺口） */
    @GetMapping("/task/{id}")
    public Result<RptTask> taskById(@PathVariable Long id) {
        RptTask task = rptTaskMapper.selectById(id);
        if (task == null) {
            return Result.error("任务不存在");
        }
        return Result.success(task);
    }

    /**
     * 分派记录分页（前端 GET /task/assign/page）。
     * 后端原来只有按任务查的 GET /task/{id}/assigns，这里补一个可分页、可过滤的列表。
     */
    @GetMapping("/task/assign/page")
    public Result<PageResult<Map<String, Object>>> assignPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) String assignType) {
        Page<RptTaskAssign> pager = new Page<>(page, pageSize);
        IPage<RptTaskAssign> result = rptTaskAssignMapper.selectPage(pager,
                Wrappers.<RptTaskAssign>lambdaQuery()
                        .eq(taskId != null, RptTaskAssign::getTaskId, taskId)
                        .eq(assignType != null && !assignType.isEmpty(), RptTaskAssign::getAssignType, assignType)
                        .orderByDesc(RptTaskAssign::getId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RptTaskAssign assign : result.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", assign.getId());
            row.put("taskId", assign.getTaskId());
            row.put("deptId", assign.getDeptId());
            row.put("userId", assign.getUserId());
            row.put("assignType", assign.getAssignType());
            row.put("status", assign.getStatus());
            row.put("createTime", assign.getCreateTime());
            RptTask task = assign.getTaskId() == null ? null : rptTaskMapper.selectById(assign.getTaskId());
            row.put("taskName", task == null ? null : task.getTaskName());
            SysDept dept = assign.getDeptId() == null ? null : sysDeptMapper.selectById(assign.getDeptId());
            row.put("deptName", dept == null ? null : dept.getDeptName());
            String realName = null;
            if (assign.getUserId() != null) {
                SysUser user = sysUserMapper.selectById(assign.getUserId());
                if (user != null) {
                    realName = user.getRealName() != null ? user.getRealName() : user.getUsername();
                }
            }
            row.put("realName", realName);
            rows.add(row);
        }
        return Result.success(PageResult.of(rows, page, pageSize, result.getTotal()));
    }

    /** 取消分派（前端 DELETE /task/assign/{id}） */
    @DeleteMapping("/task/assign/{id}")
    public Result<Void> deleteAssign(@PathVariable Long id) {
        rptTaskAssignMapper.deleteById(id);
        return Result.success();
    }

    /** 数据申请详情 */
    @GetMapping("/dataApply/{id}")
    public Result<RptDataApply> dataApplyById(@PathVariable Long id) {
        RptDataApply apply = rptDataApplyMapper.selectById(id);
        if (apply == null) {
            return Result.error("申请不存在");
        }
        return Result.success(apply);
    }

    /** 修改数据申请（仅允许改原因等描述字段，状态流转仍走审批接口） */
    @PutMapping("/dataApply/{id}")
    public Result<Void> updateDataApply(@PathVariable Long id, @RequestBody RptDataApply apply) {
        RptDataApply exist = rptDataApplyMapper.selectById(id);
        if (exist == null) {
            return Result.error("申请不存在");
        }
        if (apply.getReason() != null) {
            exist.setReason(apply.getReason());
        }
        if (apply.getTaskId() != null) {
            exist.setTaskId(apply.getTaskId());
        }
        rptDataApplyMapper.updateById(exist);
        return Result.success();
    }

    /** 删除数据申请 */
    @DeleteMapping("/dataApply/{id}")
    public Result<Void> deleteDataApply(@PathVariable Long id) {
        rptDataApplyMapper.deleteById(id);
        return Result.success();
    }

    /** 数据源详情（前端 GET /datasource/{id}；DatasourceController 原本只有列表与增删改） */
    @GetMapping("/datasource/{id}")
    public Result<RptDatasource> datasourceById(@PathVariable Long id) {
        RptDatasource ds = rptDatasourceMapper.selectById(id);
        if (ds == null) {
            return Result.error("数据源不存在");
        }
        ds.setPassword(null);
        return Result.success(ds);
    }

    /** 用户详情（前端 GET /user/{id}） */
    @GetMapping("/user/{id}")
    public Result<SysUser> userById(@PathVariable Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            return Result.error("用户不存在");
        }
        user.setPassword(null);
        return Result.success(user);
    }

    /** 角色下拉列表（兼容旧前端的 GET /role/all；否则会被 /role/{id} 吞掉报 NumberFormatException） */
    @GetMapping("/role/all")
    public Result<List<SysRole>> roleAll() {
        return Result.success(sysRoleMapper.selectList(
                Wrappers.<SysRole>lambdaQuery().orderByAsc(SysRole::getId)));
    }

    /** 角色详情（前端 GET /role/{id}） */
    @GetMapping("/role/{id}")
    public Result<SysRole> roleById(@PathVariable Long id) {
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) {
            return Result.error("角色不存在");
        }
        return Result.success(role);
    }

    /** 菜单详情（前端 GET /menu/{id}） */
    @GetMapping("/menu/{id}")
    public Result<SysMenu> menuById(@PathVariable Long id) {
        SysMenu menu = sysMenuMapper.selectById(id);
        if (menu == null) {
            return Result.error("菜单不存在");
        }
        return Result.success(menu);
    }

    /** 从 SecurityContext 取当前用户 ID（JwtAuthFilter 放进去的是 userId 字符串） */
    private Long principalUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(authentication.getPrincipal()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
