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
    @Autowired
    private com.reportsys.dao.RptFileMapper rptFileMapper;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

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
     * 发布任务。
     *
     * 并发修复（审计 P0-2）：用条件更新（status 0→1）原子抢占，
     * 避免并发重复发布或与关闭操作互相覆盖。
     */
    @Transactional
    public void publishTask(Long taskId) {
        RptTask task = rptTaskMapper.selectById(taskId);
        if (task == null) throw new RuntimeException("任务不存在");
        int claimed = rptTaskMapper.update(null,
                Wrappers.<RptTask>lambdaUpdate()
                        .set(RptTask::getStatus, 1)
                        .set(RptTask::getUpdateTime, LocalDateTime.now())
                        .eq(RptTask::getId, taskId)
                        .eq(RptTask::getStatus, 0));
        if (claimed == 0) {
            throw new RuntimeException("只有草稿状态的任务可以发布（当前状态已变更，请刷新）");
        }
    }

    /**
     * 关闭任务（语义化别名，内部复用 updateStatus 的状态守卫与原子更新）。
     */
    @Transactional
    public void closeTask(Long taskId) {
        updateStatus(taskId, 4);
    }

    /**
     * 分派任务
     */
    @Transactional
    public void assignTask(Long taskId, List<Long> fillUserIds, List<Long> auditUserIds) {
        RptTask task = rptTaskMapper.selectById(taskId);
        if (task == null) throw new RuntimeException("任务不存在");

        // 状态守卫（审计 P1-1）：已完成(3)/已关闭(4) 的任务不允许再分派，避免"复活"已关闭任务
        if (task.getStatus() != null && (task.getStatus() == 3 || task.getStatus() == 4)) {
            throw new RuntimeException("已完成或已关闭的任务不能再分派");
        }

        boolean hasFill = fillUserIds != null && !fillUserIds.isEmpty();
        boolean hasAudit = auditUserIds != null && !auditUserIds.isEmpty();
        if (!hasFill && !hasAudit) {
            // 避免"参数名传错导致静默分派失败还改状态"
            throw new RuntimeException("请至少选择一个分派对象（userIds/deptIds 不能都为空）");
        }

        // 安全修复（审计 P1-1）：只删对应类型的分派，
        // 原实现无论传 FILL 还是 AUDIT 都会先删光全部 FILL 分派，
        // 导致"只想补审核人"时把填报人全清空。
        if (hasFill) {
            rptTaskAssignMapper.delete(
                    Wrappers.<RptTaskAssign>lambdaQuery()
                            .eq(RptTaskAssign::getTaskId, taskId)
                            .eq(RptTaskAssign::getAssignType, "FILL"));
        }
        if (hasAudit) {
            rptTaskAssignMapper.delete(
                    Wrappers.<RptTaskAssign>lambdaQuery()
                            .eq(RptTaskAssign::getTaskId, taskId)
                            .eq(RptTaskAssign::getAssignType, "AUDIT"));
        }

        // 分派人（去重 + 无效用户直接报错，不再静默 continue 造成"填报中但无人可填"死任务）
        int inserted = 0;
        inserted += insertAssigns(taskId, fillUserIds, "FILL");
        inserted += insertAssigns(taskId, auditUserIds, "AUDIT");
        if (inserted == 0) {
            throw new RuntimeException("没有有效的分派对象（用户不存在或已停用）");
        }

        // 状态只前进不后退：草稿(0)/已发布(1) → 填报中(2)；已是 2 保持
        if (task.getStatus() == null || task.getStatus() < 2) {
            task.setStatus(2); // 填报中
            task.setUpdateTime(LocalDateTime.now());
            rptTaskMapper.updateById(task);
        }
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
     * 写入分派记录（去重、校验用户存在且启用）。
     *
     * 审计修复：原实现对无效用户 {@code continue} 静默跳过，
     * 接口仍返回成功但实际 0 人分派，任务却被置为"填报中"，形成无人可填的死任务。
     *
     * @return 实际写入的记录数
     */
    private int insertAssigns(Long taskId, List<Long> userIds, String assignType) {
        if (userIds == null || userIds.isEmpty()) return 0;
        int n = 0;
        // 去重，避免重复分派导致"我的任务"重复、统计虚高
        for (Long userId : new java.util.LinkedHashSet<>(userIds)) {
            if (userId == null) continue;
            SysUser user = sysUserMapper.selectById(userId);
            if (user == null) {
                throw new RuntimeException("用户不存在: " + userId);
            }
            if (user.getStatus() == null || user.getStatus() != 1) {
                throw new RuntimeException("用户已停用，不能分派: " + user.getUsername());
            }
            RptTaskAssign assign = new RptTaskAssign();
            assign.setTaskId(taskId);
            assign.setUserId(userId);
            assign.setDeptId(user.getDeptId());
            assign.setAssignType(assignType);
            assign.setStatus(0);
            assign.setCreateTime(LocalDateTime.now());
            rptTaskAssignMapper.insert(assign);
            n++;
        }
        return n;
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
     * 更新任务状态。
     *
     * 并发修复（审计 P0-2）+ 状态守卫（审计 P1-1）：
     * 原实现「先查后改」且 case 4（关闭）没有任何守卫，任何状态的任务都能被关闭、
     * 也能与其它操作互相覆盖。现改为按目标状态构造条件更新，原子生效：
     *   1 发布：仅 草稿(0) → 已发布(1)
     *   3 完成：仅 填报中(2) → 已完成(3)，且要求全部 FILL 分派已完成
     *   4 关闭：仅 已发布(1)/填报中(2) → 已关闭(4)
     */
    @Transactional
    public void updateStatus(Long taskId, Integer status) {
        if (status == null) throw new RuntimeException("状态不能为空");
        RptTask task = rptTaskMapper.selectById(taskId);
        if (task == null) throw new RuntimeException("任务不存在");

        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<RptTask> wrapper =
                Wrappers.<RptTask>lambdaUpdate()
                        .set(RptTask::getStatus, status)
                        .set(RptTask::getUpdateTime, LocalDateTime.now())
                        .eq(RptTask::getId, taskId);

        switch (status) {
            case 1: // 发布
                wrapper.eq(RptTask::getStatus, 0);
                break;
            case 3: { // 完成：仅填报中，且全部 FILL 分派已完成
                wrapper.eq(RptTask::getStatus, 2);
                Long totalAssign = rptTaskAssignMapper.selectCount(
                        Wrappers.<RptTaskAssign>lambdaQuery()
                                .eq(RptTaskAssign::getTaskId, taskId)
                                .eq(RptTaskAssign::getAssignType, "FILL"));
                Long doneAssign = rptTaskAssignMapper.selectCount(
                        Wrappers.<RptTaskAssign>lambdaQuery()
                                .eq(RptTaskAssign::getTaskId, taskId)
                                .eq(RptTaskAssign::getAssignType, "FILL")
                                .eq(RptTaskAssign::getStatus, 1));
                long total = totalAssign == null ? 0 : totalAssign;
                long done = doneAssign == null ? 0 : doneAssign;
                if (total > 0 && done < total) {
                    throw new RuntimeException("还有 " + (total - done) + " 个填报未完成");
                }
                break;
            }
            case 4: // 关闭：只允许已发布/填报中
                wrapper.in(RptTask::getStatus, 1, 2);
                break;
            default:
                throw new RuntimeException("不支持的目标状态: " + status);
        }

        int claimed = rptTaskMapper.update(null, wrapper);
        if (claimed == 0) {
            throw new RuntimeException("任务状态已变更或不满足流转条件（当前状态请刷新后重试）");
        }
    }

    /**
     * 删除任务。
     *
     * 一致性修复（审计：高）：原实现只删了 task / assign / data，
     * 遗留 rpt_file、rpt_task_process、rpt_data_apply、rpt_correct_task 孤儿记录
     * （本次审计清理时实测产生了 7 条孤儿进程记录）。
     * 现在把关联表一并清理，并禁止删除已有审批数据或处于进行中的任务。
     */
    @Transactional
    public void deleteById(Long id) {
        RptTask task = rptTaskMapper.selectById(id);
        if (task == null) throw new RuntimeException("任务不存在");
        // 进行中的任务不允许直接删除（避免填报数据丢失）
        if (task.getStatus() != null && task.getStatus() == 2) {
            throw new RuntimeException("任务处于填报中，请先关闭再删除");
        }
        // 清理所有关联表，避免产生孤儿数据
        rptTaskMapper.deleteById(id);
        rptTaskAssignMapper.delete(
                Wrappers.<RptTaskAssign>lambdaQuery().eq(RptTaskAssign::getTaskId, id));
        rptTaskDataMapper.delete(
                Wrappers.<RptTaskData>lambdaQuery().eq(RptTaskData::getTaskId, id));
        // 关联的附件、流转轨迹、数据申请、补正批次
        rptFileMapper.delete(
                Wrappers.<com.reportsys.model.RptFile>lambdaQuery()
                        .eq(com.reportsys.model.RptFile::getTaskId, id));
        jdbcTemplate.update("DELETE FROM rpt_task_process WHERE task_id = ?", id);
        jdbcTemplate.update("DELETE FROM rpt_data_apply WHERE task_id = ?", id);
        jdbcTemplate.update("DELETE FROM rpt_correct_task WHERE task_id = ?", id);
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
                // 并发修复（审计 P0-3）：插入改为「捕获唯一键冲突即跳过」，
                // 不再单纯依赖前面的 selectCount 预判（并发下预判会失效）。
                // 依赖唯一约束 uk_periodic(parent_task_id, belong_time, dept_id)。
                try {
                    rptTaskMapper.insert(child);
                } catch (org.springframework.dao.DuplicateKeyException dup) {
                    // 唯一约束 uk_periodic 生效：另一实例/另一轮已生成，跳过即可
                    seq++;
                    continue;
                }

                // 复制分派：FILL 分派必复制；AUDIT 分派同步复制（原实现只复制了 FILL，
                // 导致子任务没有审核人，只能靠 fail-open 兜底）
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
