package com.reportsys.controller;

import cn.hutool.captcha.CaptchaUtil;
import org.springframework.security.access.AccessDeniedException;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.core.lang.UUID;
import com.reportsys.common.Result;
import com.reportsys.config.CaptchaStore;
import com.reportsys.config.JwtTokenUtil;
import com.reportsys.model.LoginRequest;
import com.reportsys.model.SysMenu;
import com.reportsys.model.SysUser;
import com.reportsys.service.SysUserService;
import com.reportsys.service.OnlineUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private CaptchaStore captchaStore;

    @Autowired
    private OnlineUserService onlineUserService;

    /**
     * 登录
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest loginRequest,
                                              HttpServletRequest request) {
        try {
            String ip = getClientIp(request);
            Result<Map<String, Object>> loginResult = sysUserService.login(
                    loginRequest.getUsername(), loginRequest.getPassword(),
                    loginRequest.getUuid(), loginRequest.getCaptcha(), request);
            if (loginResult.getCode() != 200) return loginResult;
            Map<String, Object> serviceData = loginResult.getData();
            String token = (String) serviceData.get("token");
            Long userId = Long.valueOf(serviceData.get("userId").toString());

            // 组装返回给前端的数据：以 Service 返回的 user 子对象为准，
            // 原先在这里读 serviceData.get("realName"/"email"/"roles"/"permissions")
            // 而 Service 从未放入这些 key，导致前端拿到的用户信息与权限恒为 null。
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", userId);
            userInfo.put("username", loginRequest.getUsername());
            Object serviceUser = serviceData.get("user");
            if (serviceUser instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> u = (Map<String, Object>) serviceUser;
                userInfo.put("realName", u.get("realName"));
                userInfo.put("email", u.get("email"));
                userInfo.put("deptId", u.get("deptId"));
                userInfo.put("userType", u.get("userType"));
                userInfo.put("roles", u.get("roles"));
            }

            List<SysMenu> menuTree = sysUserService.getUserMenuTree(userId);

            Object permissions = serviceData.get("permissions");
            if (!(permissions instanceof List)) {
                permissions = new java.util.ArrayList<String>();
            }

            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("userInfo", userInfo);
            data.put("permissions", permissions);
            data.put("menus", menuTree);

            // 在线用户登记 + 异地登录检测（表未迁移时自动降级，不影响登录）
            Map<String, Object> online = onlineUserService.onLogin(userId, loginRequest.getUsername(),
                    jwtTokenUtil.getSessionId(token), ip, request.getHeader("User-Agent"));
            data.put("otherSessions", online.get("otherSessions"));
            data.put("loginWarning", online.get("warning"));
            return Result.success(data);
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 退出
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        // 安全修复：登出即让该会话失效（JwtAuthFilter 随后拒绝这个 token），
        // 否则登出后的 JWT 仍可继续调用接口。
        Object sessionId = request.getAttribute("sessionId");
        if (sessionId != null) {
            try {
                onlineUserService.logout(String.valueOf(sessionId));
            } catch (Exception ignored) {
                // 不影响登出响应
            }
        }
        return Result.success();
    }

    /**
     * 获取验证码
     *
     * 注意：验证码写入 CaptchaStore（与登录校验共用同一份存储），
     * 不再写入 HttpSession —— 原实现因两处存储不同而完全失效。
     */
    @GetMapping("/captcha")
    public Result<Map<String, Object>> captcha() {
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(120, 40, 4, 10);
        String code = captcha.getCode();
        String uuid = UUID.randomUUID().toString().replace("-", "");
        captchaStore.put(uuid, code);

        String image = "data:image/png;base64," + captcha.getImageBase64();
        Map<String, Object> data = new HashMap<>();
        data.put("uuid", uuid);
        data.put("captchaImage", image);
        // 兼容旧版前端 (frontend/) 读取的字段名
        data.put("img", image);
        return Result.success(data);
    }

    /**
     * 获取当前用户菜单树
     */
    @GetMapping("/menu")
    public Result<List<SysMenu>> getUserMenus() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            Long userId = null;
            if (authentication != null && authentication.getPrincipal() instanceof Long) {
                userId = (Long) authentication.getPrincipal();
            }
            if (userId == null) {
                return Result.error("用户未登录");
            }
            List<SysMenu> menuTree = sysUserService.getUserMenuTree(userId);
            return Result.success(menuTree);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 修改密码
     *
     * 安全修复(A08)：原来忽略 updatePwd() 的返回值，无论旧密码对错都返回"操作成功"，
     * 导致「旧密码校验形同虚设」（用户以为改了其实没改），且把失败信息吞掉。
     * 现在必须把服务层的失败结果原样返回。
     */
    @PutMapping("/password")
    public Result<Void> updatePassword(@RequestParam String oldPassword,
                                        @RequestParam String newPassword) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            Long userId = null;
            if (authentication != null && authentication.getPrincipal() instanceof Long) {
                userId = (Long) authentication.getPrincipal();
            }
            if (userId == null) {
                return Result.error("用户未登录");
            }
            Result<?> r = sysUserService.updatePwd(userId, oldPassword, newPassword);
            // 校验失败（如旧密码错误、新密码过短）必须透传，不能一律 success
            if (r == null || r.getCode() != 200) {
                return Result.error(r == null ? "密码修改失败" : r.getMsg());
            }
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取客户端IP。
     * 安全修复：统一走 IpUtils，只有在直连对端是受信代理时才采信 X-Forwarded-For / X-Real-IP，
     * 避免客户端伪造 IP 绕过登录限流。
     */
    private String getClientIp(HttpServletRequest request) {
        return com.reportsys.common.IpUtils.clientIp(request);
    }
}
