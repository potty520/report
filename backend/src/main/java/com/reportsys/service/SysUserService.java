package com.reportsys.service;

import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.reportsys.common.PageResult;
import com.reportsys.common.Result;
import com.reportsys.config.CaptchaStore;
import com.reportsys.config.JwtTokenUtil;
import com.reportsys.config.LoginAttemptLimiter;
import com.reportsys.dao.*;
import com.reportsys.model.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SysUserService extends ServiceImpl<SysUserMapper, SysUser> {

    @Resource private SysUserMapper userMapper;
    @Resource private SysRoleMapper roleMapper;
    @Resource private SysMenuMapper menuMapper;
    @Resource private SysLoginLogMapper loginLogMapper;
    @Resource private SysUserRoleMapper sysUserRoleMapper;
    @Resource private PasswordEncoder passwordEncoder;
    @Resource private JwtTokenUtil jwtTokenUtil;
    @Resource private CaptchaStore captchaStore;
    @Resource private LoginAttemptLimiter loginAttemptLimiter;
    @Resource private RptTaskMapper rptTaskMapper;
    @Resource private RptTaskDataMapper rptTaskDataMapper;
    @Resource private RptTaskAssignMapper rptTaskAssignMapper;
    @Resource private RptDataApplyMapper rptDataApplyMapper;

    public Result<Map<String, Object>> login(String username, String password, String uuid, String captcha, HttpServletRequest request) {
        // 真实校验验证码：由 CaptchaStore 统一存取（原实现分两套存储且未命中即放行）
        CaptchaStore.Status captchaStatus = captchaStore.verify(uuid, captcha);
        if (captchaStatus != CaptchaStore.Status.OK) {
            String msg;
            switch (captchaStatus) {
                case EXPIRED:
                    msg = "验证码已过期，请重新获取";
                    break;
                case MISMATCH:
                    msg = "验证码错误";
                    break;
                default:
                    msg = "请先获取验证码";
                    break;
            }
            logLogin(null, username, request, 0, "登录失败：" + msg);
            return Result.error(msg);
        }
        // 登录失败限流：连续失败 5 次锁定 5 分钟
        String clientIp = clientIp(request);
        String blocked = loginAttemptLimiter.blockedMessage(username, clientIp);
        if (blocked != null) {
            logLogin(null, username, request, 0, "登录失败：账号已锁定");
            return Result.error(blocked);
        }
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null || user.getStatus() == 0 || !passwordEncoder.matches(password, user.getPassword())) {
            loginAttemptLimiter.onFailure(username, clientIp);
            logLogin(user != null ? user.getId() : null, username, request, 0, "登录失败");
            return Result.error("用户名或密码错误");
        }
        loginAttemptLimiter.onSuccess(username, clientIp);
        logLogin(user.getId(), username, request, 1, "登录成功");
        String token = jwtTokenUtil.generateToken(user.getId(), user.getUsername());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("userId", user.getId());
        user.setPassword(null);
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .inSql(SysRole::getId, "SELECT role_id FROM sys_user_role WHERE user_id = " + user.getId()));
        List<String> roleCodes = roles.stream().map(SysRole::getRoleCode).collect(Collectors.toList());
        // 用户可访问菜单上的权限标识（供前端按钮级控制使用）
        List<String> permissions = getUserMenuTree(user.getId()).stream()
                .map(SysMenu::getPermission)
                .filter(p -> p != null && !p.trim().isEmpty())
                .distinct()
                .collect(Collectors.toList());
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId()); userMap.put("username", user.getUsername());
        userMap.put("realName", user.getRealName()); userMap.put("email", user.getEmail());
        userMap.put("deptId", user.getDeptId());
        userMap.put("userType", user.getUserType()); userMap.put("roles", roleCodes);
        data.put("user", userMap);
        // 顶层字段保持兼容（旧版 AuthController 直接读这些 key）
        data.put("realName", user.getRealName());
        data.put("email", user.getEmail());
        data.put("roles", roleCodes);
        data.put("permissions", permissions);
        return Result.success("登录成功", data);
    }

    private void logLogin(Long userId, String username, HttpServletRequest request, int status, String msg) {
        SysLoginLog log = new SysLoginLog();
        log.setUserId(userId); log.setUsername(username);
        String ip = normalizeIp(clientIp(request));
        log.setIp(ip); log.setLoginTime(LocalDateTime.now());
        log.setStatus(status); log.setMsg(msg);

        String uaStr = request.getHeader("User-Agent");
        if (uaStr != null && !uaStr.isEmpty()) {
            UserAgent ua = UserAgentUtil.parse(uaStr);
            String browser = ua.getBrowser().toString();
            String os = ua.getOs().toString();
            if (!"Unknown".equals(browser)) log.setBrowser(browser);
            if (!"Unknown".equals(os)) log.setOs(os);
        }
        log.setLocation(getLocationByIp(ip));

        loginLogMapper.insert(log);
    }

    private String normalizeIp(String ip) {
        if (ip == null) return "";
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) return "127.0.0.1";
        return ip;
    }

    /** 取真实客户端 IP（安全修复：统一走 IpUtils，仅信任受信代理写入的转发头） */
    private String clientIp(HttpServletRequest request) {
        return com.reportsys.common.IpUtils.clientIp(request);
    }

    private String getLocationByIp(String ip) {
        if (ip == null || ip.isEmpty()) return "";
        if (ip.equals("127.0.0.1") || ip.startsWith("192.168.") || ip.startsWith("10.")) return "内网";
        if (ip.startsWith("172.")) {
            try {
                int second = Integer.parseInt(ip.split("\\.")[1]);
                if (second >= 16 && second <= 31) return "内网";
            } catch (Exception ignored) {}
        }
        return "";
    }

    public Result<?> updatePwd(Long userId, String oldPassword, String newPassword) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) return Result.error("用户不存在");
        // 缺陷修复(U10)：原判断只要求长度 >= 6，123456 也能通过；改为统一密码策略
        String pwdError = com.reportsys.common.PasswordPolicy.check(newPassword);
        if (pwdError != null) return Result.error(pwdError);
        if (oldPassword == null || !passwordEncoder.matches(oldPassword, user.getPassword())) return Result.error("原密码错误");
        // 新密码不能与旧密码相同，避免"改了个寂寞"
        if (newPassword.equals(oldPassword)) return Result.error("新密码不能与原密码相同");
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        return Result.success("密码修改成功");
    }

    public List<SysMenu> getUserMenuTree(Long userId) {
        List<SysMenu> menus = menuMapper.selectMenusByUserId(userId);
        return buildMenuTree(menus, 0L);
    }

    private List<SysMenu> buildMenuTree(List<SysMenu> menus, Long parentId) {
        return menus.stream().filter(m -> m.getParentId().equals(parentId) && m.getStatus() == 1 && m.getVisible() == 1)
                .peek(m -> m.setChildren(buildMenuTree(menus, m.getId())))
                .sorted(Comparator.comparingInt(SysMenu::getSort)).collect(Collectors.toList());
    }

    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        sysUserRoleMapper.deleteByUserId(userId);
        for (Long roleId : roleIds) { SysUserRole ur = new SysUserRole(); ur.setUserId(userId); ur.setRoleId(roleId); sysUserRoleMapper.insert(ur); }
    }

    public Result<?> addUser(SysUser user) {
        // 缺陷修复(U06)：必填校验前置，避免缺 username 时穿透到 DAO 抛 SQLException 原文
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            return Result.error("用户名不能为空");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, user.getUsername())) > 0)
            return Result.error("用户名已存在");
        // 缺陷修复(U10)：不再使用 123456 兜底默认密码，必须显式给出符合策略的密码
        String pwdError = com.reportsys.common.PasswordPolicy.check(user.getPassword());
        if (pwdError != null) return Result.error(pwdError);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);
        return Result.success("新增成功");
    }

    public Result<?> updateUser(SysUser user) {
        SysUser exist = userMapper.selectById(user.getId());
        if (exist == null) return Result.error("用户不存在");
        if (user.getPassword() != null && !user.getPassword().isEmpty()) exist.setPassword(passwordEncoder.encode(user.getPassword()));
        if (user.getRealName() != null) exist.setRealName(user.getRealName());
        if (user.getDeptId() != null) exist.setDeptId(user.getDeptId());
        if (user.getStatus() != null) exist.setStatus(user.getStatus());
        if (user.getEmail() != null) exist.setEmail(user.getEmail());
        if (user.getPhone() != null) exist.setPhone(user.getPhone());
        userMapper.updateById(exist);
        return Result.success("更新成功");
    }

    // Controller compatibility aliases
    public void add(SysUser user) { addUser(user); }
    public void update(SysUser user) { updateUser(user); }

    /**
     * 删除用户。
     *
     * 一致性修复（审计：严重级）：原实现直接物理删除，<b>不检查任何下游引用</b>，
     * 删除后 sys_user_role、rpt_task.create_by、rpt_task_data.fill_user_id、
     * rpt_task_assign.user_id、rpt_data_apply.applicant_id 全部悬空；
     * 尤其删掉 admin 后 currentUserId() 拿到的 id 在 sys_user 里查不到，全站信息显示为空。
     *
     * 处理策略：有业务数据引用的用户不允许物理删除（改为停用），避免数据链断裂。
     */
    @Transactional
    public void deleteById(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) throw new RuntimeException("用户不存在");
        // 禁止删除最后一个超管
        if ("admin".equalsIgnoreCase(user.getUsername())) {
            throw new RuntimeException("内置管理员账号不允许删除");
        }
        Long taskCount = rptTaskMapper.selectCount(
                Wrappers.<RptTask>lambdaQuery().eq(RptTask::getCreateBy, id));
        Long dataCount = rptTaskDataMapper.selectCount(
                Wrappers.<RptTaskData>lambdaQuery().eq(RptTaskData::getFillUserId, id));
        Long assignCount = rptTaskAssignMapper.selectCount(
                Wrappers.<RptTaskAssign>lambdaQuery().eq(RptTaskAssign::getUserId, id));
        long refs = (taskCount == null ? 0 : taskCount)
                + (dataCount == null ? 0 : dataCount)
                + (assignCount == null ? 0 : assignCount);
        if (refs > 0) {
            throw new RuntimeException("该用户已有 " + refs
                    + " 条业务数据关联（任务/填报/分派），不能删除，请改为「停用」");
        }
        // 清理角色关联后再删
        sysUserRoleMapper.deleteByUserId(id);
        userMapper.deleteById(id);
    }
    public void updateStatus(Long id, Integer status) { SysUser u = userMapper.selectById(id); if (u != null) { u.setStatus(status); userMapper.updateById(u); } }
    public PageResult<SysUser> listPage(int pageNum, int pageSize, String username, Long deptId, Integer status) {
        Page<SysUser> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (username != null && !username.isEmpty()) wrapper.like(SysUser::getUsername, username);
        if (deptId != null) wrapper.eq(SysUser::getDeptId, deptId);
        if (status != null) wrapper.eq(SysUser::getStatus, status);
        wrapper.orderByDesc(SysUser::getCreateTime);
        PageResult<SysUser> result = PageResult.of(userMapper.selectPage(page, wrapper));
        // 安全加固：用户列表不回传密码哈希
        result.getList().forEach(u -> u.setPassword(null));
        return result;
    }
}
