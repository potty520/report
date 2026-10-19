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
    /** 更新任务（原 PUT /task 误用 createTask，导致主键冲突、无法改任务） */
    public void updateTask(RptTask task) {
        if (task == null || task.getId() == null) {
            throw new RuntimeException("任务ID不能为空");
        }
        rptTaskMapper.updateById(task);
    }

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
        boolean hasFill = fillUserIds != null && !fillUserIds.isEmpty();
        boolean hasAudit = auditUserIds != null && !auditUserIds.isEmpty();
        if (!hasFill && !hasAudit) {
            // 避免"参数名传错导致静默分派失败还改状态"
            throw new RuntimeException("请至少选择一个分派对象（userIds/deptIds 不能都为空）");
        }

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
        // P1-F：给被分派人发待办消息（替代原版短信通知）
        try {
            todoService.notifyAssignees(taskId, "【待办】" + task.getTaskName(),
                    "任务已分派给你，请在 " + (task.getFillDeadline() == null ? "截止时间前" : task.getFillDeadline())
                            + " 完成填报并提交。", "TODO", task.getCreateBy());
        } catch (Exception ignored) {
            // 通知失败不影响分派
        }
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
     * 按部门分派：把部门下的在职用户展开成分派人（原版支持按部门下发）
     */
    @Transactional
    public void assignTaskByDepts(Long taskId, List<Long> deptIds, boolean audit) {
        if (deptIds == null || deptIds.isEmpty()) {
            throw new RuntimeException("请至少选择一个分派对象");
        }
        List<SysUser> users = sysUserMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .in(SysUser::getDeptId, deptIds)
                .eq(SysUser::getStatus, 1));
        List<Long> userIds = users.stream().map(SysUser::getId).collect(Collectors.toList());
        if (userIds.isEmpty()) {
            throw new RuntimeException("所选部门下没有可用用户");
        }
        if (audit) {
            assignTask(taskId, null, userIds);
        } else {
            assignTask(taskId, userIds, null);
        }
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

    // ========================================================================
    // P0-C：周期任务自动生成（替代原版 TaskJob + SaveTaskService）
    // ========================================================================

    @Autowired
    private PeriodService periodService;
    @Autowired
    private TodoService todoService;

    /**
     * 为周期主任务生成当前属期的部门子任务。
     *
     * @param parentTaskId 指定主任务；为 null 时处理所有已发布状态（1/2）的主任务
     */
    @Transactional
    public Map<String, Object> generatePeriodicTasks(Long parentTaskId) {
        List<RptTask> parents;
        if (parentTaskId != null) {
            RptTask parent = rptTaskMapper.selectById(parentTaskId);
            if (parent == null) throw new RuntimeException("主任务不存在");
            parents = Collections.singletonList(parent);
        } else {
            parents = rptTaskMapper.selectList(Wrappers.<RptTask>lambdaQuery()
                    .isNull(RptTask::getParentTaskId)
                    .in(RptTask::getStatus, 1, 2));
        }

        LocalDateTime now = LocalDateTime.now();
        int createdCount = 0;
        String currentBatch = null;
        List<String> details = new ArrayList<>();

        for (RptTask parent : parents) {
            Integer cycle = parent.getFillingCycle() == null ? PeriodService.MONTHLY : parent.getFillingCycle();
            String batch = periodService.currentPeriod(cycle, java.time.LocalDate.now());
            currentBatch = batch;

            Long exists = rptTaskMapper.selectCount(Wrappers.<RptTask>lambdaQuery()
                    .eq(RptTask::getParentTaskId, parent.getId())
                    .eq(RptTask::getBelongTime, batch));
            if (exists != null && exists > 0) {
                details.add(parent.getTaskName() + ": 属期 " + batch + " 已生成，跳过");
                continue;
            }

            // 涉及部门：优先取分派记录里的部门；没有分派则用主任务自身部门
            List<RptTaskAssign> assigns = rptTaskAssignMapper.selectList(
                    Wrappers.<RptTaskAssign>lambdaQuery()
                            .eq(RptTaskAssign::getTaskId, parent.getId())
                            .eq(RptTaskAssign::getAssignType, "FILL"));
            Map<Long, List<RptTaskAssign>> byDept = new LinkedHashMap<>();
            for (RptTaskAssign assign : assigns) {
                Long deptId = assign.getDeptId() != null ? assign.getDeptId() : parent.getDeptId();
                byDept.computeIfAbsent(deptId, k -> new ArrayList<>()).add(assign);
            }
            if (byDept.isEmpty()) {
                byDept.put(parent.getDeptId(), new ArrayList<>());
            }

            int seq = 1;
            for (Map.Entry<Long, List<RptTaskAssign>> entry : byDept.entrySet()) {
                Long deptId = entry.getKey();
                RptTask child = new RptTask();
                child.setTaskName(parent.getTaskName() + "-" + periodService.deptCode(deptId) + "-" + seq);
                child.setTemplateId(parent.getTemplateId());
                child.setDeptId(deptId);
                child.setProjectId(parent.getProjectId());
                child.setBelongTime(batch);
                child.setGenerateBatch(batch);
                child.setFillingCycle(cycle);
                child.setStatus(2); // 填报中
                child.setParentTaskId(parent.getId());
                child.setFillDeadline(periodService.fillDeadline(cycle, java.time.LocalDate.now()));
                child.setAuditDeadline(periodService.auditDeadline(cycle, java.time.LocalDate.now(), 2));
                child.setCreateBy(parent.getCreateBy());
                child.setCreateTime(now);
                child.setUpdateTime(now);
                rptTaskMapper.insert(child);

                for (RptTaskAssign assign : entry.getValue()) {
                    RptTaskAssign copy = new RptTaskAssign();
                    copy.setTaskId(child.getId());
                    copy.setDeptId(assign.getDeptId());
                    copy.setUserId(assign.getUserId());
                    copy.setAssignType(assign.getAssignType());
                    copy.setStatus(0);
                    copy.setCreateTime(now);
                    rptTaskAssignMapper.insert(copy);
                }
                createdCount++;
                seq++;
            }
            details.add(parent.getTaskName() + ": 生成 " + (seq - 1) + " 个子任务（属期 " + batch + "）");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batch", currentBatch);
        result.put("createdCount", createdCount);
        result.put("detail", String.join("; ", details));
        return result;
    }

    /** 子任务列表 */
    public List<RptTask> listChildren(Long parentTaskId) {
        return rptTaskMapper.selectList(Wrappers.<RptTask>lambdaQuery()
                .eq(RptTask::getParentTaskId, parentTaskId)
                .orderByDesc(RptTask::getCreateTime));
    }
}
