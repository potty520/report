package com.reportsys.aop;

import cn.hutool.core.util.StrUtil;
import com.reportsys.annotation.OperLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;

/**
 * 操作审计切面（P2-G，替代原版三套注解+切面的落库部分）。
 *
 * 记录：操作人、模块、动作、目标、IP、耗时、成功/失败与错误信息 → rpt_oper_log。
 */
@Aspect
@Component
public class OperLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperLogAspect.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        Long userId = currentUserId();
        String username = currentUsername();
        String ip = currentIp();
        String target = describe(joinPoint);
        try {
            Object result = joinPoint.proceed();
            record(userId, username, operLog, target, ip, System.currentTimeMillis() - start, 1, null);
            return result;
        } catch (Throwable throwable) {
            record(userId, username, operLog, target, ip, System.currentTimeMillis() - start, 0,
                    throwable.getMessage());
            throw throwable;
        }
    }

    private void record(Long userId, String username, OperLog operLog, String target, String ip,
                        long cost, int status, String error) {
        try {
            jdbcTemplate.update("INSERT INTO rpt_oper_log (user_id, username, module, action, target, detail, ip, "
                            + "cost_ms, status, error_msg, create_time) VALUES (?,?,?,?,?,'',?,?,?,?,NOW())",
                    userId, StrUtil.nullToEmpty(username), operLog.module(), operLog.action(),
                    StrUtil.maxLength(target, 120), StrUtil.nullToEmpty(ip), cost, status,
                    StrUtil.maxLength(StrUtil.nullToEmpty(error), 900));
        } catch (Exception e) {
            log.warn("写入操作审计日志失败: {}", e.getMessage());
        }
    }

    private String describe(ProceedingJoinPoint joinPoint) {
        String method = joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) return method;
        String argText = Arrays.stream(args)
                .filter(a -> a != null && !(a instanceof java.io.InputStream))
                .map(String::valueOf)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        return method + "(" + StrUtil.maxLength(argText, 80) + ")";
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long) return (Long) auth.getPrincipal();
        return null;
    }

    private String currentUsername() {
        // 缺陷修复：principal 里放的是 userId，直接取值会把"操作人"记成 "1"；
        // 这里按 userId 反查用户名，查不到再回落。
        Long userId = currentUserId();
        if (userId != null) {
            try {
                List<String> names = jdbcTemplate.queryForList(
                        "SELECT username FROM sys_user WHERE id = ?", String.class, userId);
                if (!names.isEmpty() && StrUtil.isNotBlank(names.get(0))) {
                    return names.get(0);
                }
            } catch (Exception ignored) {
                // 查不到就回落，不影响业务
            }
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "" : StrUtil.nullToEmpty(String.valueOf(auth.getPrincipal()));
    }

    /** 安全修复：统一走 IpUtils，避免客户端伪造 X-Forwarded-For 污染审计日志 */
    private String currentIp() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) return "";
            return com.reportsys.common.IpUtils.clientIp(attributes.getRequest());
        } catch (Exception e) {
            return "";
        }
    }
}
