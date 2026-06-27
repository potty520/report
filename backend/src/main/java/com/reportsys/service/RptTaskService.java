package com.reportsys.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.reportsys.common.PageResult;
import com.reportsys.dao.*;
import com.reportsys.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RptTaskService {

    @Autowired
    private RptTaskMapper rptTaskMapper;
    @Autowired
    private RptTaskAssignMapper rptTaskAssignMapper;
    @Autowired
    private RptTaskDataMapper rptTaskDataMapper;
    @Autowired
    private RptTemplateMapper rptTemplateMapper;
    @Autowired
    private SysDeptMapper sysDeptMapper;
    @Autowired
    private SysProjectMapper sysProjectMapper;
    @Autowired
    private SysUserMapper sysUserMapper;

    /**
     * 创建任务
     */
    @Transactional
    public void createTask(RptTask task) {
        RptTemplate template = rptTemplateMapper.selectById(task.getTemplateId());
        if (template == null) throw new RuntimeException("模板不存在");
        if (template.getStatus() == null || template.getStatus() != 1) throw new RuntimeException("模板未启用");
        task.setFillingCycle(template.getFillingCycle());
        task.setStatus(0); // 草稿
        task.setAuditStatus(0); // 待审
        task.setCreateTime(LocalDateTime.now());
        rptTaskMapper.insert(task);
    }

    /**
     * 发布任务
     */
    @Transactional
    public void publishTask(Long taskId) {
        RptTask task = rptTaskMapper.selectById(taskId);
        if (task == null) throw new RuntimeException("任务不存在");
        if (task.getStatus() != 0) throw new RuntimeException("只有草稿状态的任务可以发布");
        task.setStatus(1); // 已发布
        task.setUpdateTime(LocalDateTime.now());
        rptTaskMapper.updateById(task);
    }

    /**
     * 分派任务
     */
    @Transactional
    public void assignTask(Long taskId, List<Long> fillUserIds, List<Long> auditUserIds) {
        RptTask task = rptTaskMapper.selectById(taskId);
        if (task == null) throw new RuntimeException("任务不存在");

        // 删除原有分派
        rptTaskAssignMapper.delete(
                Wrappers.<RptTaskAssign>lambdaQuery().eq(RptTaskAssign::getTaskId, taskId));

        // 分派填报人员
        if (fillUserIds != null) {
            for (Long userId : fillUserIds) {
                SysUser user = sysUserMapper.selectById(userId);
                if (user == null) continue;
                RptTaskAssign assign = new RptTaskAssign();
                assign.setTaskId(taskId);
                assign.setUserId(userId);
                assign.setDeptId(user.getDeptId());
                assign.setAssignType("FILL");
                assign.setStatus(0);
                assign.setCreateTime(LocalDateTime.now());
                rptTaskAssignMapper.insert(assign);
            }
        }

        // 分派审核人员
        if (auditUserIds != null) {
            for (Long userId : auditUserIds) {
                SysUser user = sysUserMapper.selectById(userId);
                if (user == null) continue;
                RptTaskAssign assign = new RptTaskAssign();
                assign.setTaskId(taskId);
                assign.setUserId(userId);
                assign.setDeptId(user.getDeptId());
                assign.setAssignType("AUDIT");
                assign.setStatus(0);
                assign.setCreateTime(LocalDateTime.now());
                rptTaskAssignMapper.insert(assign);
            }
        }

        task.setStatus(2); // 填报中
        task.setUpdateTime(LocalDateTime.now());
        rptTaskMapper.updateById(task);
    }

    /**
     * 获取任务列表
     */
    public PageResult<RptTask> getTaskList(int pageNum, int pageSize, String taskName, Integer status,
                                           String belongTime, Long deptId, Long projectId) {
        Page<RptTask> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<RptTask> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(taskName)) wrapper.like(RptTask::getTaskName, taskName);
        if (status != null) wrapper.eq(RptTask::getStatus, status);
        if (StrUtil.isNotBlank(belongTime)) wrapper.eq(RptTask::getBelongTime, belongTime);
        if (deptId != null) wrapper.eq(RptTask::getDeptId, deptId);
        if (projectId != null) wrapper.eq(RptTask::getProjectId, projectId);
        wrapper.orderByDesc(RptTask::getCreateTime);
        PageResult<RptTask> result = PageResult.of(rptTaskMapper.selectPage(page, wrapper));
        fillTaskExtInfo(result.getList());
        return result;
    }

    /**
     * 获取我的任务(填报)
     */
    public PageResult<RptTask> getMyTaskList(int pageNum, int pageSize, Long userId, Integer status) {
        // 查询用户被分派的填报任务
        List<RptTaskAssign> assigns = rptTaskAssignMapper.selectList(
                Wrappers.<RptTaskAssign>lambdaQuery()
                        .eq(RptTaskAssign::getUserId, userId)
                        .eq(RptTaskAssign::getAssignType, "FILL"));

        if (assigns.isEmpty()) {
            return PageResult.of(new ArrayList<>(), pageNum, pageSize, 0);
        }

        List<Long> taskIds = assigns.stream().map(RptTaskAssign::getTaskId).distinct().collect(Collectors.toList());
        if (taskIds.isEmpty()) return PageResult.of(new Page<>());
        Page<RptTask> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<RptTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(RptTask::getId, taskIds);
        if (status != null) wrapper.eq(RptTask::getStatus, status);
        wrapper.orderByDesc(RptTask::getCreateTime);
        PageResult<RptTask> result = PageResult.of(rptTaskMapper.selectPage(page, wrapper));
        fillTaskExtInfo(result.getList());
        return result;
    }

    /**
     * 获取我的审核任务
     */
    public PageResult<RptTask> getMyAuditTaskList(int pageNum, int pageSize, Long userId, Integer status) {
        List<RptTaskAssign> assigns = rptTaskAssignMapper.selectList(
                Wrappers.<RptTaskAssign>lambdaQuery()
                        .eq(RptTaskAssign::getUserId, userId)
                        .eq(RptTaskAssign::getAssignType, "AUDIT"));

        if (assigns.isEmpty()) {
            return PageResult.of(new ArrayList<>(), pageNum, pageSize, 0);
        }

        List<Long> taskIds = assigns.stream().map(RptTaskAssign::getTaskId).distinct().collect(Collectors.toList());
        if (taskIds.isEmpty()) return PageResult.of(new Page<>());
        Page<RptTask> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<RptTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(RptTask::getId, taskIds);
        if (status != null) wrapper.eq(RptTask::getStatus, status);
        wrapper.orderByDesc(RptTask::getCreateTime);
        PageResult<RptTask> result = PageResult.of(rptTaskMapper.selectPage(page, wrapper));
        fillTaskExtInfo(result.getList());
        return result;
    }

    private void fillTaskExtInfo(List<RptTask> list) {
        for (RptTask task : list) {
            if (task.getTemplateId() != null) {
                RptTemplate t = rptTemplateMapper.selectById(task.getTemplateId());
                if (t != null) task.setTemplateName(t.getTemplateName());
            }
            if (task.getDeptId() != null) {
                SysDept d = sysDeptMapper.selectById(task.getDeptId());
                if (d != null) task.setDeptName(d.getDeptName());
            }
            if (task.getProjectId() != null) {
                SysProject p = sysProjectMapper.selectById(task.getProjectId());
                if (p != null) task.setProjectName(p.getProjectName());
            }
            if (task.getCreateBy() != null) {
                SysUser u = sysUserMapper.selectById(task.getCreateBy());
                if (u != null) task.setCreateByName(u.getRealName() != null ? u.getRealName() : u.getUsername());
            }
            // 统计填报进度
            Long total = rptTaskAssignMapper.selectCount(
                    Wrappers.<RptTaskAssign>lambdaQuery()
                            .eq(RptTaskAssign::getTaskId, task.getId())
                            .eq(RptTaskAssign::getAssignType, "FILL"));
            Long fillCount = rptTaskAssignMapper.selectCount(
                    Wrappers.<RptTaskAssign>lambdaQuery()
                            .eq(RptTaskAssign::getTaskId, task.getId())
                            .eq(RptTaskAssign::getAssignType, "FILL")
                            .eq(RptTaskAssign::getStatus, 1));
            task.setTotalCount(total != null ? total.intValue() : 0);
            task.setFillCount(fillCount != null ? fillCount.intValue() : 0);
        }
    }

    /**
     * 获取任务统计
     */
    public Map<String, Object> getTaskStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalTask", rptTaskMapper.selectCount(null));
        stats.put("draftTask", rptTaskMapper.selectCount(Wrappers.<RptTask>lambdaQuery().eq(RptTask::getStatus, 0)));
        stats.put("publishedTask", rptTaskMapper.selectCount(Wrappers.<RptTask>lambdaQuery().eq(RptTask::getStatus, 1)));
        stats.put("fillingTask", rptTaskMapper.selectCount(Wrappers.<RptTask>lambdaQuery().eq(RptTask::getStatus, 2)));
        stats.put("completedTask", rptTaskMapper.selectCount(Wrappers.<RptTask>lambdaQuery().eq(RptTask::getStatus, 3)));
        stats.put("closedTask", rptTaskMapper.selectCount(Wrappers.<RptTask>lambdaQuery().eq(RptTask::getStatus, 4)));
        stats.put("totalTemplate", rptTemplateMapper.selectCount(null));
        stats.put("totalUser", sysUserMapper.selectCount(null));
        return stats;
    }

    /**
     * 更新任务状态
     */
    @Transactional
    public void updateStatus(Long taskId, Integer status) {
        RptTask task = rptTaskMapper.selectById(taskId);
        if (task == null) throw new RuntimeException("任务不存在");

        switch (status) {
            case 1: // 发布
                if (task.getStatus() != 0) throw new RuntimeException("只能发布草稿状态的任务");
                break;
            case 3: // 完成
                if (task.getStatus() != 2) throw new RuntimeException("只能完成填报中的任务");
                // 检查是否全部填报完成
                Long totalAssign = rptTaskAssignMapper.selectCount(
                        Wrappers.<RptTaskAssign>lambdaQuery()
                                .eq(RptTaskAssign::getTaskId, taskId)
                                .eq(RptTaskAssign::getAssignType, "FILL"));
                Long doneAssign = rptTaskAssignMapper.selectCount(
                        Wrappers.<RptTaskAssign>lambdaQuery()
                                .eq(RptTaskAssign::getTaskId, taskId)
                                .eq(RptTaskAssign::getAssignType, "FILL")
                                .eq(RptTaskAssign::getStatus, 1));
                if (totalAssign > 0 && !doneAssign.equals(totalAssign)) {
                    throw new RuntimeException("还有" + (totalAssign - doneAssign) + "个填报未完成");
                }
                break;
            case 4: // 关闭
                break;
            default:
                break;
        }

        task.setStatus(status);
        task.setUpdateTime(LocalDateTime.now());
        rptTaskMapper.updateById(task);
    }

    @Transactional
    public void deleteById(Long id) {
        rptTaskMapper.deleteById(id);
        rptTaskAssignMapper.delete(
                Wrappers.<RptTaskAssign>lambdaQuery().eq(RptTaskAssign::getTaskId, id));
        rptTaskDataMapper.delete(
                Wrappers.<RptTaskData>lambdaQuery().eq(RptTaskData::getTaskId, id));
    }

    public RptTask getById(Long id) {
        return rptTaskMapper.selectById(id);
    }

    /**
     * 获取任务已分派的用户
     */
    public List<RptTaskAssign> getAssigns(Long taskId, String assignType) {
        return rptTaskAssignMapper.selectList(
                Wrappers.<RptTaskAssign>lambdaQuery()
                        .eq(RptTaskAssign::getTaskId, taskId)
                        .eq(StrUtil.isNotBlank(assignType), RptTaskAssign::getAssignType, assignType));
    }
}
