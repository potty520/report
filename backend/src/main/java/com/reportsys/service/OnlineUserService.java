package com.reportsys.service;

import cn.hutool.core.util.StrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在线用户与异地登录检测（补齐原版 online_user_info_t / 异地登录提示 + 踢下线）。
 *
 * 会话标识取自 JWT 的 jti（见 JwtTokenUtil.generateToken 的 setId），
 * 因此「踢下线」可以在 {@link com.reportsys.config.JwtAuthFilter} 里真正生效。
 *
 * 容错：rpt_online_user 表未迁移时所有方法静默降级（不影响登录与鉴权）。
 */
@Service
public class OnlineUserService {

    private static final Logger log = LoggerFactory.getLogger(OnlineUserService.class);

    @Resource
    private JdbcTemplate jdbcTemplate;

    /**
     * 登录登记 + 异地登录检测。
     * 返回 otherSessions（同账号其他 IP 的在线会话）与 warning 文案。
     */
    @Transactional
    public Map<String, Object> onLogin(Long userId, String username, String sessionId, String ip, String userAgent) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> others = new ArrayList<>();
        try {
            List<Map<String, Object>> actives = jdbcTemplate.queryForList(
                    "SELECT id, ip, login_time FROM rpt_online_user WHERE user_id = ? AND status = 1", userId);
            for (Map<String, Object> row : actives) {
                String oldIp = row.get("ip") == null ? "" : String.valueOf(row.get("ip"));
                if (StrUtil.isNotBlank(oldIp) && StrUtil.isNotBlank(ip) && !oldIp.equals(ip)) {
                    others.add(row);
                }
            }
            // 每次登录一行（每个 token 一个会话）。配合 JwtAuthFilter 的会话白名单：
            // 登出/踢下线把该行置 0 → 对应 token 立即 401；旧 token 因自己那行仍在，不受影响。
            // "在线数只增不减"由 30 分钟活跃窗口 + 心跳 + 按用户去重解决。
            jdbcTemplate.update(
                    "INSERT INTO rpt_online_user (user_id, username, session_id, ip, user_agent, "
                            + "login_time, last_active_time, status) VALUES (?,?,?,?,?,NOW(),NOW(),1)",
                    userId, username, sessionId, ip,
                    StrUtil.maxLength(StrUtil.nullToEmpty(userAgent), 300));
            // 保留 7 天，避免历史会话无限增长
            jdbcTemplate.update("DELETE FROM rpt_online_user WHERE user_id = ? "
                    + "AND login_time < DATE_SUB(NOW(), INTERVAL 7 DAY)", userId);
        } catch (Exception e) {
            log.warn("在线用户登记失败（不影响登录）: {}", e.getMessage());
        }
        out.put("otherSessions", others);
        out.put("warning", others.isEmpty() ? null
                : "该账号已在其他 IP 登录（" + others.size() + " 个会话），如非本人操作请立即修改密码");
        return out;
    }

    /**
     * 登出：把该会话置为离线。
     * 之后 JwtAuthFilter.isKicked(sessionId) 即为 true，登出后的 token 立刻返回 401
     * （修复"登出后 token 仍可用"的安全问题）。
     */
    public int logout(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return 0;
        }
        try {
            return jdbcTemplate.update(
                    "UPDATE rpt_online_user SET status = 0, offline_reason = '用户登出', offline_time = NOW() "
                            + "WHERE session_id = ? AND status = 1", sessionId);
        } catch (Exception e) {
            log.warn("登出清理在线会话失败: {}", e.getMessage());
            return 0;
        }
    }

    /** 在线用户列表 */
    public List<Map<String, Object>> list(String keyword) {
        try {
            StringBuilder sql = new StringBuilder(
                    "SELECT o.id, o.user_id AS userId, o.username, o.session_id AS sessionId, o.ip, "
                            + "o.user_agent AS userAgent, o.login_time AS loginTime, o.last_active_time AS lastActiveTime, "
                            + "o.status, u.real_name AS realName, d.dept_name AS deptName "
                            + "FROM rpt_online_user o LEFT JOIN sys_user u ON u.id = o.user_id "
                            + "LEFT JOIN sys_dept d ON d.id = u.dept_id WHERE o.status = 1 "
                    + "AND o.last_active_time > DATE_SUB(NOW(), INTERVAL 30 MINUTE) "
                    // 按用户去重：每个用户只展示最近一次会话，口径与 onlineCount(DISTINCT user_id) 一致
                    + "AND o.id = (SELECT MAX(x.id) FROM rpt_online_user x "
                    + "WHERE x.user_id = o.user_id AND x.status = 1) ");
            List<Object> args = new ArrayList<>();
            if (StrUtil.isNotBlank(keyword)) {
                sql.append(" AND (o.username LIKE ? OR o.ip LIKE ?) ");
                args.add("%" + keyword + "%");
                args.add("%" + keyword + "%");
            }
            sql.append(" ORDER BY o.login_time DESC LIMIT 500");
            return jdbcTemplate.queryForList(sql.toString(), args.toArray());
        } catch (Exception e) {
            log.warn("查询在线用户失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public int onlineCount() {
        try {
            Integer n = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT user_id) FROM rpt_online_user WHERE status = 1 "
                        + "AND last_active_time > DATE_SUB(NOW(), INTERVAL 30 MINUTE)", Integer.class);
            return n == null ? 0 : n;
        } catch (Exception e) {
            return 0;
        }
    }

    /** 按会话踢下线（返回影响行数） */
    @Transactional
    public int kickBySession(String sessionId, String reason) {
        if (StrUtil.isBlank(sessionId)) throw new RuntimeException("sessionId 不能为空");
        return jdbcTemplate.update("UPDATE rpt_online_user SET status = 0, offline_reason = ?, offline_time = NOW() "
                + "WHERE session_id = ? AND status = 1", StrUtil.maxLength(StrUtil.nullToEmpty(reason), 200), sessionId);
    }

    /** 按用户踢下线（该用户所有在线会话） */
    @Transactional
    public int kickByUser(Long userId, String reason) {
        if (userId == null) throw new RuntimeException("userId 不能为空");
        return jdbcTemplate.update("UPDATE rpt_online_user SET status = 0, offline_reason = ?, offline_time = NOW() "
                + "WHERE user_id = ? AND status = 1", StrUtil.maxLength(StrUtil.nullToEmpty(reason), 200), userId);
    }

    /**
     * 该会话是否已被踢下线。
     * 表不存在或没有该会话记录时返回 false（对历史 token 保持兼容）。
     */
    public boolean isKicked(String sessionId) {
        if (StrUtil.isBlank(sessionId)) return false;
        try {
            List<Integer> status = jdbcTemplate.queryForList(
                    "SELECT status FROM rpt_online_user WHERE session_id = ? ORDER BY id DESC LIMIT 1",
                    Integer.class, sessionId);
            return !status.isEmpty() && status.get(0) != null && status.get(0) == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** 刷新最后活跃时间（静默失败） */
    public void touch(String sessionId) {
        if (StrUtil.isBlank(sessionId)) return;
        try {
            jdbcTemplate.update("UPDATE rpt_online_user SET last_active_time = NOW() "
                    + "WHERE session_id = ? AND status = 1", sessionId);
        } catch (Exception ignored) {
            // 忽略
        }
    }
}
