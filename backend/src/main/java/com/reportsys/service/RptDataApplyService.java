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
public class RptDataApplyService {

    @Autowired
    private RptDataApplyMapper rptDataApplyMapper;
    @Autowired
    private RptTaskMapper rptTaskMapper;
    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private SysDeptMapper sysDeptMapper;
    @Autowired
    private RptTaskDataMapper rptTaskDataMapper;

    /**
     * 提交数据申请
     */
    @Transactional
    public void apply(RptDataApply apply) {
        if (apply.getTaskId() == null) throw new RuntimeException("任务ID不能为空");
        if (apply.getApplicantId() == null) throw new RuntimeException("申请人不能为空");

        RptTask task = rptTaskMapper.selectById(apply.getTaskId());
        if (task == null) throw new RuntimeException("任务不存在");

        // 检查是否已存在相同申请
        RptDataApply existApply = rptDataApplyMapper.selectOne(
                Wrappers.<RptDataApply>lambdaQuery()
                        .eq(RptDataApply::getTaskId, apply.getTaskId())
                        .eq(RptDataApply::getApplicantId, apply.getApplicantId())
                        .eq(RptDataApply::getStatus, 0));
        if (existApply != null) throw new RuntimeException("已存在待审批的申请，请勿重复提交");

        apply.setStatus(0); // 待审
        apply.setApplicantDeptId(apply.getApplicantDeptId());
        apply.setCreateTime(LocalDateTime.now());
        rptDataApplyMapper.insert(apply);
    }

    /**
     * 获取我的申请列表
     */
    public PageResult<RptDataApply> getMyApplyList(int pageNum, int pageSize, Long applicantId, Integer status) {
        Page<RptDataApply> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<RptDataApply> wrapper = new LambdaQueryWrapper<>();
        if (applicantId != null) wrapper.eq(RptDataApply::getApplicantId, applicantId);
        if (status != null) wrapper.eq(RptDataApply::getStatus, status);
        wrapper.orderByDesc(RptDataApply::getCreateTime);
        PageResult<RptDataApply> result = PageResult.of(rptDataApplyMapper.selectPage(page, wrapper));
        fillExtInfo(result.getList());
        return result;
    }

    /**
     * 获取审批列表
     */
    public PageResult<RptDataApply> getApproveList(int pageNum, int pageSize, Integer status) {
        Page<RptDataApply> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<RptDataApply> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(RptDataApply::getStatus, status);
        wrapper.orderByDesc(RptDataApply::getCreateTime);
        PageResult<RptDataApply> result = PageResult.of(rptDataApplyMapper.selectPage(page, wrapper));
        fillExtInfo(result.getList());
        return result;
    }

    /**
     * 审批操作
     */
    @Transactional
    public void approve(Long applyId, Integer status, String auditComment, Long auditorId) {
        RptDataApply apply = rptDataApplyMapper.selectById(applyId);
        if (apply == null) throw new RuntimeException("申请不存在");
        if (apply.getStatus() != 0) throw new RuntimeException("申请已处理，请勿重复操作");
        // 安全加固：禁止自己审批自己的申请
        if (apply.getApplicantId() != null && apply.getApplicantId().equals(auditorId)) {
            throw new org.springframework.security.access.AccessDeniedException("不能审批自己提交的申请");
        }

        apply.setStatus(status);
        apply.setAuditorId(auditorId);
        apply.setAuditComment(auditComment);
        apply.setAuditTime(LocalDateTime.now());
        rptDataApplyMapper.updateById(apply);

        // 如果通过，创建填报数据记录(空)
        if (status == 1) {
            RptTask task = rptTaskMapper.selectById(apply.getTaskId());
            if (task != null) {
                // 检查是否已有填报数据
                RptTaskData existData = rptTaskDataMapper.selectOne(
                        Wrappers.<RptTaskData>lambdaQuery()
                                .eq(RptTaskData::getTaskId, apply.getTaskId())
                                .eq(RptTaskData::getFillUserId, apply.getApplicantId()));
                if (existData == null) {
                    RptTaskData taskData = new RptTaskData();
                    taskData.setTaskId(apply.getTaskId());
                    taskData.setTemplateId(task.getTemplateId());
                    taskData.setDeptId(apply.getApplicantDeptId());
                    taskData.setFillUserId(apply.getApplicantId());
                    taskData.setStatus(0); // 草稿
                    taskData.setAuditStatus(0);
                    taskData.setCreateTime(LocalDateTime.now());
                    rptTaskDataMapper.insert(taskData);
                }
            }
        }
    }

    private void fillExtInfo(List<RptDataApply> list) {
        for (RptDataApply apply : list) {
            if (apply.getTaskId() != null) {
                RptTask t = rptTaskMapper.selectById(apply.getTaskId());
                if (t != null) apply.setTaskName(t.getTaskName());
            }
            if (apply.getApplicantId() != null) {
                SysUser u = sysUserMapper.selectById(apply.getApplicantId());
                if (u != null) apply.setApplicantName(u.getRealName() != null ? u.getRealName() : u.getUsername());
            }
            if (apply.getApplicantDeptId() != null) {
                SysDept d = sysDeptMapper.selectById(apply.getApplicantDeptId());
                if (d != null) apply.setApplicantDeptName(d.getDeptName());
            }
            if (apply.getAuditorId() != null) {
                SysUser u = sysUserMapper.selectById(apply.getAuditorId());
                if (u != null) apply.setAuditorName(u.getRealName() != null ? u.getRealName() : u.getUsername());
            }
        }
    }

    public RptDataApply getById(Long id) {
        return rptDataApplyMapper.selectById(id);
    }
}
