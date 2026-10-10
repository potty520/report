package com.reportsys.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;

//@Component  // 数据已通过SQL初始化，注释掉避免重复插入
public class DataInitializer implements CommandLineRunner {

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        try {
            // Check if admin already exists
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username = 'admin'", Integer.class);
            if (count != null && count > 0) return;
        } catch (Exception e) {
            // table might not exist yet, skip
            return;
        }

        String adminPass = passwordEncoder.encode("admin123");
        String userPass = passwordEncoder.encode("user123");

        // Insert departments
        jdbcTemplate.update("INSERT IGNORE INTO sys_dept (id, parent_id, dept_name, dept_code, sort) VALUES (1, 0, ?, 'ROOT', 0)", "根部门");

        // Insert users
        jdbcTemplate.update("INSERT IGNORE INTO sys_user (id, username, password, real_name, email, phone, dept_id, status) VALUES (1, 'admin', ?, '系统管理员', 'admin@example.com', '13800000000', 1, 1)", adminPass);
        jdbcTemplate.update("INSERT IGNORE INTO sys_user (id, username, password, real_name, email, phone, dept_id, status) VALUES (2, 'user', ?, '普通用户', 'user@example.com', '13800000001', 1, 1)", userPass);

        // Insert roles
        jdbcTemplate.update("INSERT IGNORE INTO sys_role (id, role_name, role_code, description) VALUES (1, '超级管理员', 'ADMIN', '超级管理员')");
        jdbcTemplate.update("INSERT IGNORE INTO sys_role (id, role_name, role_code, description) VALUES (2, '普通用户', 'USER', '普通用户')");

        // Assign roles to users
        jdbcTemplate.update("INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (1, 1)");
        jdbcTemplate.update("INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (2, 2)");

        // Insert menus - 一级菜单
        jdbcTemplate.update("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (1, 0, '首页概览', 'C', '/overview', 'Overview', 'dashboard', 1, 1, 'overview:dashboard')");
        jdbcTemplate.update("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (2, 0, '系统管理', 'M', '/system', null, 'setting', 2, 1, null)");
        jdbcTemplate.update("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (8, 0, '组织架构', 'M', '/org', null, 'apartment', 3, 1, null)");
        jdbcTemplate.update("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (11, 0, '数据源管理', 'C', '/datasource', 'Datasource/List', 'database', 4, 1, 'datasource:list')");
        jdbcTemplate.update("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (12, 0, '模板管理', 'M', '/template', null, 'file-text', 5, 1, null)");
        jdbcTemplate.update("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (15, 0, '填报任务', 'M', '/task', null, 'schedule', 6, 1, null)");
        jdbcTemplate.update("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (19, 0, '数据审批', 'M', '/dataApply', null, 'audit', 7, 1, null)");
        jdbcTemplate.update("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (22, 0, '填报审核', 'M', '/fill', null, 'check-circle', 8, 1, null)");

        // 系统管理子菜单
        jdbcTemplate.update("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (3, 2, '用户管理', 'C', '/system/user', 'System/User', 'user', 1, 1, 'sys:user:list')");
        jdbcTemplate.update("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (4, 2, '角色管理', 'C', '/system/role', 'System/Role', 'team', 2, 1, 'sys:role:list')");
        jdbcTemplate.update("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (5, 2, '菜单管理', 'C', '/system/menu', 'System/Menu', 'menu', 3, 1, 'sys:menu:list')");

        // 组织架构子菜单
        jdbcTemplate.update("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (9, 8, '部门管理', 'C', '/org/dept', 'Org/Dept', 'cluster', 1, 1, 'org:dept:list')");
        jdbcTemplate.update("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (10, 8, '项目管理', 'C', '/org/project', 'Org/Project', 'project', 2, 1, 'org:project:list')");

        // 模板管理子菜单
        jdbcTemplate.update("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (13, 12, '模板列表', 'C', '/template/list', 'Template/List', 'unordered-list', 1, 1, 'template:list')");
        jdbcTemplate.update("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (14, 12, '新增模板', 'C', '/template/add', 'Template/Add', 'file-add', 2, 1, 'template:add')");

        // 填报任务子菜单
        jdbcTemplate.update("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (16, 15, '任务制定', 'C', '/task/formulation', 'Task/Formulation', 'form', 1, 1, 'task:formulate')");
        jdbcTemplate.update("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (17, 15, '任务分配', 'C', '/task/assign', 'Task/Assign', 'send', 2, 1, 'task:assign')");
        jdbcTemplate.update("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (18, 15, '填报台账', 'C', '/task/ledger', 'Task/Ledger', 'table', 3, 1, 'task:ledger')");

        // 数据审批子菜单
        jdbcTemplate.update("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (20, 19, '我的申请', 'C', '/dataApply/apply', 'DataApply/Apply', 'file-protect', 1, 1, 'dataApply:apply')");
        jdbcTemplate.update("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (21, 19, '审批管理', 'C', '/dataApply/approve', 'DataApply/Approve', 'audit', 2, 1, 'dataApply:approve')");

        // 填报审核子菜单
        jdbcTemplate.update("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (23, 22, '我的任务', 'C', '/fill/task', 'Fill/Task', 'carry-out', 1, 1, 'fill:task')");
        jdbcTemplate.update("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (24, 22, '数据审核', 'C', '/fill/audit', 'Fill/Audit', 'safety', 2, 1, 'fill:audit')");

        // 审核管理（P0-C：双通道审核 / 流转轨迹 / 补正）
        jdbcTemplate.update("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (25, 0, '审核管理', 'M', '/audit', null, 'AuditOutlined', 9, 1, null)");
        jdbcTemplate.update("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (26, 25, '部门审核', 'C', '/audit/dept-audit', 'Audit/DeptAudit', 'AuditOutlined', 1, 1, 'audit:dept')");
        jdbcTemplate.update("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (27, 25, '任务方审核', 'C', '/audit/task-audit', 'Audit/TaskAudit', 'SafetyCertificateOutlined', 2, 1, 'audit:task')");
        jdbcTemplate.update("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (28, 25, '流转轨迹', 'C', '/audit/trajectory', 'Audit/Trajectory', 'NodeIndexOutlined', 3, 1, 'audit:trajectory')");
        jdbcTemplate.update("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (29, 25, '补正管理', 'C', '/audit/correct', 'Audit/Correct', 'ToolOutlined', 4, 1, 'audit:correct')");

        // 附件管理（P1-D：分片上传 / 秒传 / 断点续传 / 下载）
        jdbcTemplate.update("INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (30, 22, '附件管理', 'C', '/file/list', 'File/List', 'PaperClipOutlined', 3, 1, 'file:list')");

        // Assign all menus to admin role
        jdbcTemplate.update("INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 1),(1, 2),(1, 3),(1, 4),(1, 5),(1, 8),(1, 9),(1, 10),(1, 11),(1, 12),(1, 13),(1, 14),(1, 15),(1, 16),(1, 17),(1, 18),(1, 19),(1, 20),(1, 21),(1, 22),(1, 23),(1, 24),(1, 25),(1, 26),(1, 27),(1, 28),(1, 29),(1, 30)");

        // 部门管理员（DEPT_ADMIN）：除「系统管理」外的业务菜单。
        // 原先该角色没有任何菜单授权，导致 user/user123 登录后侧边栏为空。
        jdbcTemplate.update("INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES "
                + "(2, 1),(2, 8),(2, 9),(2, 10),(2, 11),(2, 12),(2, 13),(2, 14),"
                + "(2, 15),(2, 16),(2, 17),(2, 18),(2, 19),(2, 20),(2, 21),"
                + "(2, 22),(2, 23),(2, 24),(2, 25),(2, 26),(2, 27),(2, 28),(2, 29),(2, 30)");

        // 审核人（AUDIT_USER）：审核管理全部 + 填报审核
        jdbcTemplate.update("INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES "
                + "(4, 25),(4, 26),(4, 27),(4, 28),(4, 29),(4, 22),(4, 23),(4, 24)");

        // 填报人（FILL_USER）：我的任务 + 附件管理
        jdbcTemplate.update("INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (3, 30)");

        System.out.println("=== Initial data inserted successfully ===");
    }
}
