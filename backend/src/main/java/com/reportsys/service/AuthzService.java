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
     * 填报归属校验：必须是分派给本人或本人所在部门；超管除外。
     *
     * 安全修复（审计 H1）：原实现「没有任何分派记录时放行」，属 fail-open——
     * 一旦任务发布后忘了分派（或分派被误删），<b>全站任何填报用户都能填这个任务</b>。
     * 现改为 fail-closed：没有分派记录一律拒绝，只有超管可绕过。
     *
     * 兼容性说明：生产存量任务若确实存在「无分派但需要填报」的历史数据，
     * 应由管理员补分派，而不是靠放行兜底。
     */
    public void assertCanFill(Long taskId, Long userId) {
        if (taskId == null) throw new AccessDeniedException("任务不存在");
        // 超管可操作任意任务
        if (hasAnyRole(ADMIN)) return;
        if (userId == null) throw new AccessDeniedException("未登录");
        List<RptTaskAssign> assigns = rptTaskAssignMapper.selectByTaskId(taskId);
        if (assigns == null || assigns.isEmpty()) {
            // fail-closed：无分派记录不再放行
            throw new AccessDeniedException("该任务尚未分派，无法填报");
        }
        SysUser user = sysUserMapper.selectById(userId);
        Long deptId = user == null ? null : user.getDeptId();
        for (RptTaskAssign assign : assigns) {
            if (assign.getAssignType() != null && "AUDIT".equalsIgnoreCase(assign.getAssignType())) continue;
            if (assign.getUserId() != null && assign.getUserId().equals(userId)) return;
            if (assign.getDeptId() != null && deptId != null && assign.getDeptId().equals(deptId)) return;
        }
        throw new AccessDeniedException("当前用户未被分派该填报任务");
    }

    /**
     * 审核归属校验：① 禁止自审；② 必须是该任务的审核人（按人或按部门）；超管除外。
     *
     * 安全修复（审计 H1）：原实现的「无 AUDIT 分派即放行」同样是 fail-open——
     * 任务只要有 FILL 分派而没配审核人，<b>任何审核用户都能审</b>。现改为 fail-closed。
     */
    public void assertCanAudit(Long taskId, Long dataOwnerId, Long auditorId) {
        if (dataOwnerId != null && dataOwnerId.equals(auditorId)) {
            throw new AccessDeniedException("不能审核自己提交的数据");
        }
        if (taskId == null) throw new AccessDeniedException("任务不存在");
        if (hasAnyRole(ADMIN)) return;
        if (auditorId == null) throw new AccessDeniedException("未登录");
        List<RptTaskAssign> assigns = rptTaskAssignMapper.selectByTaskId(taskId);
        if (assigns == null || assigns.isEmpty()) {
            throw new AccessDeniedException("该任务尚未分派，无法审核");
        }

        boolean hasAuditAssign = false;
        for (RptTaskAssign assign : assigns) {
            if (assign.getAssignType() == null || !"AUDIT".equalsIgnoreCase(assign.getAssignType())) continue;
            hasAuditAssign = true;
            if (assign.getUserId() != null && assign.getUserId().equals(auditorId)) return;
        }
        if (!hasAuditAssign) {
            // fail-closed：没有配置审核人，不再默认放行
            throw new AccessDeniedException("该任务未配置审核人，无法审核");
        }

        SysUser auditor = sysUserMapper.selectById(auditorId);
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

    /**
     * 数据归属校验：当前用户必须是该填报数据的填报人本人，或超管。
     *
     * 新增（审计 H2/H3）：供 /fill/export、/fill/{taskId}/target-rows、
     * /file 下载等「按数据 id / taskId 直接取数」的接口做统一归属判断。
     *
     * 说明：这里只做「本人或超管」的严格判断。若业务上需要部门内共享，
     * 应通过 assertCanFill（按分派部门）而不是放开到全部登录用户。
     */
    public void assertDataOwnerOrAdmin(Long dataOwnerId) {
        if (hasAnyRole(ADMIN)) return;
        Long me = requireUserId();
        if (dataOwnerId == null || !dataOwnerId.equals(me)) {
            throw new AccessDeniedException("无权访问他人的填报数据");
        }
    }

    /** 是否超管（供 Controller 做查询范围收敛） */
    public boolean isAdmin() {
        return hasAnyRole(ADMIN);
    }
}
