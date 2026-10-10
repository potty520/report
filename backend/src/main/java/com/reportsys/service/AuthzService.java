package com.reportsys.service;

import com.reportsys.dao.RptTaskAssignMapper;
import com.reportsys.dao.SysUserMapper;
import com.reportsys.model.RptTaskAssign;
import com.reportsys.model.SysUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 鉴权与数据归属校验。
 *
 * 安全加固点：把"谁能做什么"从"只要登录就行"收敛为"角色 + 数据归属"：
 * - 角色判断基于 JwtAuthFilter 装载的 ROLE_xxx 权限；
 * - 填报/审核还要求当前用户确实是该任务被分派的人（无分派记录时兼容放行）；
 * - 明确禁止"自己审自己"。
 */
@Service
public class AuthzService {

    /** 超级管理员角色码 */
    public static final String ADMIN = "SUPER_ADMIN";

    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private RptTaskAssignMapper rptTaskAssignMapper;

    public Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long) {
            return (Long) auth.getPrincipal();
        }
        return null;
    }

    public Long requireUserId() {
        Long userId = currentUserId();
        if (userId == null) throw new AccessDeniedException("未登录");
        return userId;
    }

    public boolean hasAnyRole(String... roleCodes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) return false;
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String code = authority.getAuthority();
            if (("ROLE_" + ADMIN).equals(code)) return true;
            for (String role : roleCodes) {
                if (("ROLE_" + role).equals(code)) return true;
            }
        }
        return false;
    }

    public void requireAnyRole(String... roleCodes) {
        if (!hasAnyRole(roleCodes)) throw new AccessDeniedException("无权限执行该操作");
    }

    /**
     * 填报归属校验：任务存在分派记录时，必须是分派给本人或本人所在部门；
     * 没有任何分派记录时放行（兼容历史数据，避免把现有流程卡死）。
     */
    public void assertCanFill(Long taskId, Long userId) {
        List<RptTaskAssign> assigns = rptTaskAssignMapper.selectByTaskId(taskId);
        if (assigns == null || assigns.isEmpty()) return;
        SysUser user = userId == null ? null : sysUserMapper.selectById(userId);
        Long deptId = user == null ? null : user.getDeptId();
        for (RptTaskAssign assign : assigns) {
            if (assign.getAssignType() != null && "AUDIT".equalsIgnoreCase(assign.getAssignType())) continue;
            if (assign.getUserId() != null && assign.getUserId().equals(userId)) return;
            if (assign.getDeptId() != null && deptId != null && assign.getDeptId().equals(deptId)) return;
        }
        throw new AccessDeniedException("当前用户未被分派该填报任务");
    }

    /**
     * 审核归属校验：① 禁止自审；② 存在 AUDIT 分派时必须被分派为审核人（按人，或按部门）。
     */
    public void assertCanAudit(Long taskId, Long dataOwnerId, Long auditorId) {
        if (dataOwnerId != null && dataOwnerId.equals(auditorId)) {
            throw new AccessDeniedException("不能审核自己提交的数据");
        }
        if (taskId == null) return;
        List<RptTaskAssign> assigns = rptTaskAssignMapper.selectByTaskId(taskId);
        if (assigns == null || assigns.isEmpty()) return;

        boolean hasAuditAssign = false;
        for (RptTaskAssign assign : assigns) {
            if (assign.getAssignType() == null || !"AUDIT".equalsIgnoreCase(assign.getAssignType())) continue;
            hasAuditAssign = true;
            if (assign.getUserId() != null && assign.getUserId().equals(auditorId)) return;
        }
        if (!hasAuditAssign) return;

        SysUser auditor = auditorId == null ? null : sysUserMapper.selectById(auditorId);
        Long deptId = auditor == null ? null : auditor.getDeptId();
        if (deptId != null) {
            for (RptTaskAssign assign : assigns) {
                if (assign.getAssignType() != null && "AUDIT".equalsIgnoreCase(assign.getAssignType())
                        && assign.getDeptId() != null && assign.getDeptId().equals(deptId)) {
                    return;
                }
            }
        }
        throw new AccessDeniedException("当前用户未被分派为审核人");
    }
}
