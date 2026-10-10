package com.reportsys.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;

//@Component  // 数据已通过SQL初始化，注释掉避免重复插入
// 注意：本类当前未被 Spring 装配（上面的 @Component 已注释），以下内容仅作为"初始数据/结构"的
// 权威描述与将来重新启用时的种子数据；线上实际以 docs/report_2.0_upgrade*.sql 迁移脚本为准。
public class DataInitializer implements CommandLineRunner {

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        try {
            // 结构保障（幂等）：新增表用 CREATE TABLE IF NOT EXISTS，
            // 避免漏跑迁移脚本时启动即报错。
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS rpt_template_auth ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT,"
                    + "template_id BIGINT NOT NULL COMMENT '模板ID',"
                    + "auth_type VARCHAR(16) NOT NULL COMMENT 'DEPT=部门 USER=用户',"
                    + "target_id BIGINT NOT NULL COMMENT '部门ID或用户ID',"
                    + "create_by BIGINT DEFAULT NULL,"
                    + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                    + "PRIMARY KEY (id),"
                    + "UNIQUE KEY uk_template_auth (template_id, auth_type, target_id),"
                    + "KEY idx_template_auth_target (auth_type, target_id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模板两级授权（部门/用户）'");
        } catch (Exception e) {
            // 表已存在或无权限时忽略
        }
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

        // 2026-10 新增界面（审核管理 / 附件管理 / 消息中心 / 字典管理 / 审计日志 / 数据范围 /
        // 在线用户 / 统计规则）的菜单与角色授权，统一以 docs/report_2.0_upgrade4_audit_menus.sql 为准。
        //
        // ⚠️ 不要在这里补菜单：线上 sys_menu 的 id 空间与本文件的种子 id **不一致**
        //    （例：线上 7=填报管理、22=角色管理；而本文件 22=填报审核）。
        //    历史上曾因此把「附件管理」挂到了「角色管理」下面。
        //    新增菜单请改迁移脚本，并显式给出唯一 menu_code
        //    （sys_menu.menu_code 有唯一键且默认 ''，不给值会从第二条起被 INSERT IGNORE 静默跳过）。

        // Assign all menus to admin role（仅种子内置菜单；新增菜单的授权见迁移脚本）
        jdbcTemplate.update("INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 1),(1, 2),(1, 3),(1, 4),(1, 5),(1, 8),(1, 9),(1, 10),(1, 11),(1, 12),(1, 13),(1, 14),(1, 15),(1, 16),(1, 17),(1, 18),(1, 19),(1, 20),(1, 21),(1, 22),(1, 23),(1, 24)");

        System.out.println("=== Initial data inserted successfully ===");
    }
}
