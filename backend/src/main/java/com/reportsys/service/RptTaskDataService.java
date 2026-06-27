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
import java.util.ArrayList;
import java.util.List;

@Service
public class RptTaskDataService {

    @Autowired
    private RptTaskDataMapper rptTaskDataMapper;
    @Autowired
    private RptTaskMapper rptTaskMapper;
    @Autowired
    private RptTaskAssignMapper rptTaskAssignMapper;
    @Autowired
    private RptTemplateMapper rptTemplateMapper;
    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private SysDeptMapper sysDeptMapper;

    /**
     * 填报数据(草稿)
     */
    @Transactional
    public void fillData(RptTaskData taskData) {
        if (taskData.getTaskId() == null) throw new RuntimeException("任务ID不能为空");
        if (taskData.getFillUserId() == null) throw new RuntimeException("填报人不能为空");

        // 检查任务状态
        RptTask task = rptTaskMapper.selectById(taskData.getTaskId());
        if (task == null) throw new RuntimeException("任务不存在");
        if (task.getStatus() != 2) throw new RuntimeException("任务非填报中状态，无法填报");

        // 检查是否超过截止时间
        if (task.getFillDeadline() != null && LocalDateTime.now().isAfter(task.getFillDeadline())) {
            throw new RuntimeException("已超过填报截止时间");
        }

        // 查找已有填报数据
        RptTaskData existData = rptTaskDataMapper.selectOne(
                Wrappers.<RptTaskData>lambdaQuery()
                        .eq(RptTaskData::getTaskId, taskData.getTaskId())
                        .eq(RptTaskData::getFillUserId, taskData.getFillUserId()));

        if (existData != null) {
            // 更新已有数据
            existData.setDataJson(taskData.getDataJson());
            existData.setFileUrl(taskData.getFileUrl());
            existData.setStatus(0); // 草稿
            existData.setUpdateTime(LocalDateTime.now());
            rptTaskDataMapper.updateById(existData);
            taskData.setId(existData.getId());
        } else {
            // 新增填报数据
            taskData.setTemplateId(task.getTemplateId());
            taskData.setStatus(0); // 草稿
            taskData.setAuditStatus(0); // 待审
            taskData.setCreateTime(LocalDateTime.now());
            rptTaskDataMapper.insert(taskData);
        }
    }

    /**
     * 提交数据
     */
    @Transactional
    public void submitData(Long dataId) {
        RptTaskData taskData = rptTaskDataMapper.selectById(dataId);
        if (taskData == null) throw new RuntimeException("填报数据不存在");
        if (taskData.getStatus() != null && taskData.getStatus() == 1) throw new RuntimeException("数据已提交，请勿重复提交");
        if (StrUtil.isBlank(taskData.getDataJson())) throw new RuntimeException("填报数据不能为空");

        taskData.setStatus(1); // 已提交
        taskData.setSubmitTime(LocalDateTime.now());
        taskData.setAuditStatus(0); // 待审
        taskData.setUpdateTime(LocalDateTime.now());
        rptTaskDataMapper.updateById(taskData);

        // 更新任务分派状态
        RptTaskAssign assign = rptTaskAssignMapper.selectOne(
                Wrappers.<RptTaskAssign>lambdaQuery()
                        .eq(RptTaskAssign::getTaskId, taskData.getTaskId())
                        .eq(RptTaskAssign::getUserId, taskData.getFillUserId())
                        .eq(RptTaskAssign::getAssignType, "FILL"));
        if (assign != null) {
            assign.setStatus(1); // 已填报
            rptTaskAssignMapper.updateById(assign);
        }
    }

    /**
     * 获取填报列表
     */
    public PageResult<RptTaskData> getFillList(int pageNum, int pageSize, Long fillUserId, Integer status) {
        Page<RptTaskData> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<RptTaskData> wrapper = new LambdaQueryWrapper<>();
        if (fillUserId != null) wrapper.eq(RptTaskData::getFillUserId, fillUserId);
        if (status != null) wrapper.eq(RptTaskData::getStatus, status);
        wrapper.orderByDesc(RptTaskData::getCreateTime);
        PageResult<RptTaskData> result = PageResult.of(rptTaskDataMapper.selectPage(page, wrapper));
        fillExtInfo(result.getList());
        return result;
    }

    /**
     * 获取审核列表
     */
    public PageResult<RptTaskData> getAuditList(int pageNum, int pageSize, Long auditUserId, Integer auditStatus,
                                                 Long taskId) {
        // 查询审核人员被分派的任务
        List<RptTaskAssign> assigns = rptTaskAssignMapper.selectList(
                Wrappers.<RptTaskAssign>lambdaQuery()
                        .eq(auditUserId != null, RptTaskAssign::getUserId, auditUserId)
                        .eq(RptTaskAssign::getAssignType, "AUDIT"));

        if (assigns.isEmpty() && auditUserId != null) {
            return PageResult.of(new ArrayList<>(), pageNum, pageSize, 0);
        }

        List<Long> taskIds = null;
        if (auditUserId != null) {
            taskIds = new java.util.ArrayList<>();
            java.util.Set<Long> tidSet = new java.util.HashSet<>();
            for (RptTaskAssign a : assigns) tidSet.add(a.getTaskId());
            taskIds.addAll(tidSet);
        }

        Page<RptTaskData> pageObj = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<RptTaskData> wrapper = new LambdaQueryWrapper<>();
        if (taskIds != null && !taskIds.isEmpty()) wrapper.in(RptTaskData::getTaskId, taskIds);
        if (auditStatus != null) wrapper.eq(RptTaskData::getAuditStatus, auditStatus);
        if (taskId != null) wrapper.eq(RptTaskData::getTaskId, taskId);
        wrapper.eq(RptTaskData::getStatus, 1);
        wrapper.orderByDesc(RptTaskData::getSubmitTime);
        PageResult<RptTaskData> result = PageResult.of(rptTaskDataMapper.selectPage(pageObj, wrapper));
        fillExtInfo(result.getList());
        return result;
    }

    /**
     * 审核操作
     */
    @Transactional
    public void audit(Long dataId, Integer auditStatus, String auditComment, Long auditUserId) {
        RptTaskData taskData = rptTaskDataMapper.selectById(dataId);
        if (taskData == null) throw new RuntimeException("填报数据不存在");
        if (taskData.getAuditStatus() != null && taskData.getAuditStatus() != 0) {
            throw new RuntimeException("数据已审核，请勿重复操作");
        }
        if (taskData.getStatus() == null || taskData.getStatus() != 1) {
            throw new RuntimeException("数据未提交，无法审核");
        }

        taskData.setAuditStatus(auditStatus);
        taskData.setAuditComment(auditComment);
        taskData.setAuditUserId(auditUserId);
        taskData.setAuditTime(LocalDateTime.now());
        taskData.setUpdateTime(LocalDateTime.now());
        rptTaskDataMapper.updateById(taskData);

        // 如果驳回，将提交状态重置
        if (auditStatus == 2) {
            taskData.setStatus(0);
            taskData.setSubmitTime(null);
            rptTaskDataMapper.updateById(taskData);

            // 重置分派状态
            RptTaskAssign assign = rptTaskAssignMapper.selectOne(
                    Wrappers.<RptTaskAssign>lambdaQuery()
                            .eq(RptTaskAssign::getTaskId, taskData.getTaskId())
                            .eq(RptTaskAssign::getUserId, taskData.getFillUserId())
                            .eq(RptTaskAssign::getAssignType, "FILL"));
            if (assign != null) {
                assign.setStatus(0);
                rptTaskAssignMapper.updateById(assign);
            }
        }

        // 更新任务审核状态
        RptTask task = rptTaskMapper.selectById(taskData.getTaskId());
        if (task != null && task.getAuditStatus() == 0) {
            task.setAuditStatus(1); // 审核中
            task.setUpdateTime(LocalDateTime.now());
            rptTaskMapper.updateById(task);
        }
    }

    private void fillExtInfo(List<RptTaskData> list) {
        for (RptTaskData data : list) {
            if (data.getTaskId() != null) {
                RptTask t = rptTaskMapper.selectById(data.getTaskId());
                if (t != null) {
                    data.setTaskName(t.getTaskName());
                    data.setFillingCycle(t.getFillingCycle());
                    data.setBelongTime(t.getBelongTime());
                    if (t.getTemplateId() != null) {
                        RptTemplate tp = rptTemplateMapper.selectById(t.getTemplateId());
                        if (tp != null) data.setTemplateName(tp.getTemplateName());
                    }
                }
            }
            if (data.getFillUserId() != null) {
                SysUser u = sysUserMapper.selectById(data.getFillUserId());
                if (u != null) {
                    data.setFillUserName(u.getRealName() != null ? u.getRealName() : u.getUsername());
                    if (u.getDeptId() != null) {
                        SysDept d = sysDeptMapper.selectById(u.getDeptId());
                        if (d != null) data.setDeptName(d.getDeptName());
                    }
                }
            }
            if (data.getAuditUserId() != null) {
                SysUser u = sysUserMapper.selectById(data.getAuditUserId());
                if (u != null) data.setAuditUserName(u.getRealName() != null ? u.getRealName() : u.getUsername());
            }
        }
    }

    public RptTaskData getById(Long id) {
        return rptTaskDataMapper.selectById(id);
    }
}
