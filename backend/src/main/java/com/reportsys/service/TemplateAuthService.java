package com.reportsys.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.reportsys.dao.RptTemplateAuthMapper;
import com.reportsys.dao.SysUserMapper;
import com.reportsys.model.RptTemplateAuth;
import com.reportsys.model.SysUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 模板两级授权（部门 / 用户）——补齐原版 rel_model_user 的能力。
 *
 * 设计要点：
 * 1. 向后兼容：模板没有授权记录时不做任何限制，避免把现有流程卡死；
 * 2. 容错：新表尚未迁移时（表不存在）所有查询按「无授权」处理，只记录告警，不影响模板列表；
 * 3. 超级管理员不受授权限制（由调用方判断角色）。
 */
@Service
public class TemplateAuthService {

    private static final Logger log = LoggerFactory.getLogger(TemplateAuthService.class);

    @Resource
    private RptTemplateAuthMapper rptTemplateAuthMapper;
    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private JdbcTemplate jdbcTemplate;

    /** 某模板的授权列表（含授权对象名称） */
    public List<Map<String, Object>> list(Long templateId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (RptTemplateAuth row : selectByTemplate(templateId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.getId());
            item.put("templateId", row.getTemplateId());
            item.put("authType", row.getAuthType());
            item.put("targetId", row.getTargetId());
            item.put("targetName", targetName(row.getAuthType(), row.getTargetId()));
            item.put("createBy", row.getCreateBy());
            item.put("createTime", row.getCreateTime());
            out.add(item);
        }
        return out;
    }

    /** 批量授权（幂等：已存在的记录跳过） */
    @Transactional
    public int grant(Long templateId, String authType, List<Long> targetIds, Long userId) {
        if (templateId == null) throw new RuntimeException("模板ID不能为空");
        String type = StrUtil.isBlank(authType) ? "DEPT" : authType.trim().toUpperCase();
        if (!"DEPT".equals(type) && !"USER".equals(type)) {
            throw new RuntimeException("授权类型只能是 DEPT（部门）或 USER（用户）");
        }
        if (targetIds == null || targetIds.isEmpty()) throw new RuntimeException("请至少选择一个授权对象");

        int added = 0;
        for (Long targetId : targetIds) {
            if (targetId == null) continue;
            Long exists = rptTemplateAuthMapper.selectCount(Wrappers.<RptTemplateAuth>lambdaQuery()
                    .eq(RptTemplateAuth::getTemplateId, templateId)
                    .eq(RptTemplateAuth::getAuthType, type)
                    .eq(RptTemplateAuth::getTargetId, targetId));
            if (exists != null && exists > 0) continue;

            RptTemplateAuth row = new RptTemplateAuth();
            row.setTemplateId(templateId);
            row.setAuthType(type);
            row.setTargetId(targetId);
            row.setCreateBy(userId);
            row.setCreateTime(LocalDateTime.now());
            rptTemplateAuthMapper.insert(row);
            added++;
        }
        return added;
    }

    @Transactional
    public void revoke(Long templateId, Long authId) {
        RptTemplateAuth row = rptTemplateAuthMapper.selectById(authId);
        if (row == null || !Objects.equals(row.getTemplateId(), templateId)) {
            throw new RuntimeException("授权记录不存在");
        }
        rptTemplateAuthMapper.deleteById(authId);
    }

    /** 模板是否已被限制（存在授权记录） */
    public boolean isRestricted(Long templateId) {
        try {
            Long n = rptTemplateAuthMapper.selectCount(Wrappers.<RptTemplateAuth>lambdaQuery()
                    .eq(RptTemplateAuth::getTemplateId, templateId));
            return n != null && n > 0;
        } catch (Exception e) {
            log.warn("模板授权表不可用，按未限制处理: {}", e.getMessage());
            return false;
        }
    }

    /** 某用户可访问的模板ID集合（含所有未设置授权的模板） */
    public Set<Long> accessibleTemplateIds(Long userId) {
        Set<Long> allowed = new HashSet<>();
        Set<Long> restricted = new HashSet<>();
        Long deptId = deptIdOf(userId);
        try {
            for (RptTemplateAuth row : rptTemplateAuthMapper.selectList(Wrappers.<RptTemplateAuth>lambdaQuery())) {
                restricted.add(row.getTemplateId());
                if ("USER".equalsIgnoreCase(row.getAuthType()) && Objects.equals(row.getTargetId(), userId)) {
                    allowed.add(row.getTemplateId());
                }
                if ("DEPT".equalsIgnoreCase(row.getAuthType()) && deptId != null
                        && Objects.equals(row.getTargetId(), deptId)) {
                    allowed.add(row.getTemplateId());
                }
            }
            for (Long id : jdbcTemplate.queryForList("SELECT id FROM rpt_template", Long.class)) {
                if (!restricted.contains(id)) allowed.add(id);
            }
        } catch (Exception e) {
            log.warn("读取模板授权失败，按未限制处理: {}", e.getMessage());
            try {
                allowed.addAll(jdbcTemplate.queryForList("SELECT id FROM rpt_template", Long.class));
            } catch (Exception ignored) {
                // 连模板表都读不到时返回空集合，由调用方决定行为
            }
        }
        return allowed;
    }

    /** 单个模板是否对该用户可见 */
    public boolean canAccess(Long templateId, Long userId) {
        if (templateId == null) return false;
        List<RptTemplateAuth> rows = selectByTemplate(templateId);
        if (rows.isEmpty()) return true;
        Long deptId = deptIdOf(userId);
        for (RptTemplateAuth row : rows) {
            if ("USER".equalsIgnoreCase(row.getAuthType()) && Objects.equals(row.getTargetId(), userId)) return true;
            if ("DEPT".equalsIgnoreCase(row.getAuthType()) && deptId != null
                    && Objects.equals(row.getTargetId(), deptId)) return true;
        }
        return false;
    }

    public Long deptIdOf(Long userId) {
        if (userId == null) return null;
        try {
            SysUser user = sysUserMapper.selectById(userId);
            return user == null ? null : user.getDeptId();
        } catch (Exception e) {
            return null;
        }
    }

    private List<RptTemplateAuth> selectByTemplate(Long templateId) {
        try {
            return rptTemplateAuthMapper.selectList(Wrappers.<RptTemplateAuth>lambdaQuery()
                    .eq(RptTemplateAuth::getTemplateId, templateId)
                    .orderByAsc(RptTemplateAuth::getAuthType)
                    .orderByAsc(RptTemplateAuth::getId));
        } catch (Exception e) {
            log.warn("查询模板授权失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String targetName(String authType, Long targetId) {
        if (targetId == null) return null;
        try {
            if ("DEPT".equalsIgnoreCase(authType)) {
                List<String> names = jdbcTemplate.queryForList(
                        "SELECT dept_name FROM sys_dept WHERE id = ?", String.class, targetId);
                return names.isEmpty() ? null : names.get(0);
            }
            List<String> names = jdbcTemplate.queryForList(
                    "SELECT COALESCE(real_name, username) FROM sys_user WHERE id = ?", String.class, targetId);
            return names.isEmpty() ? null : names.get(0);
        } catch (Exception e) {
            return null;
        }
    }
}
