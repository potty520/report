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
    public Result<Void> logout() {
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
            sysUserService.updatePwd(userId, oldPassword, newPassword);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty() && !"unknown".equalsIgnoreCase(xff)) {
            return xff.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
