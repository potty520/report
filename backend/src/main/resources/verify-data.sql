-- ============================================================
-- 验证用测试数据（仅用于本地 H2 内存库 / 验证脚本，勿用于生产）
--
-- 说明：DataInitializer 的 @Component 已被注释（生产库另有初始化脚本），
-- 这里补一套最小可用的账号/角色/菜单/部门数据，供 verify_fixes.py 验证缺陷修复。
-- ============================================================

-- 部门
INSERT INTO sys_dept (id, parent_id, dept_name, dept_code, sort, status) VALUES (1, 0, '根部门', 'ROOT', 0, 1);
INSERT INTO sys_dept (id, parent_id, dept_name, dept_code, sort, status) VALUES (2, 1, '测试部门', 'DEPT_TEST', 1, 1);

-- 用户：admin / ***REMOVED_DEFAULT_ADMIN_PASSWORD***，user / ***REMOVED_DEFAULT_USER_PASSWORD***
INSERT INTO sys_user (id, username, password, real_name, email, phone, dept_id, status, user_type)
VALUES (1, 'admin', '***REMOVED_BCRYPT_HASH***', '系统管理员', 'admin@example.com', '13800000000', 1, 1, 1);
INSERT INTO sys_user (id, username, password, real_name, email, phone, dept_id, status, user_type)
VALUES (2, 'user', '***REMOVED_BCRYPT_HASH***', '部门管理员', 'user@example.com', '13800000001', 2, 1, 2);

-- 角色：SUPER_ADMIN 超管、DEPT_ADMIN 部门管理员
INSERT INTO sys_role (id, role_name, role_code, description, status) VALUES (1, '超级管理员', 'SUPER_ADMIN', '超级管理员', 1);
INSERT INTO sys_role (id, role_name, role_code, description, status) VALUES (2, '部门管理员', 'DEPT_ADMIN', '部门管理员', 1);

-- 用户-角色
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);
INSERT INTO sys_user_role (user_id, role_id) VALUES (2, 2);

-- 菜单（最小集）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (1, 0, '首页概览', 'C', '/overview', 'Overview', 'dashboard', 1, 1, 'overview:dashboard');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (2, 0, '系统管理', 'M', '/system', NULL, 'setting', 2, 1, NULL);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (12, 0, '模板管理', 'M', '/template', NULL, 'file', 3, 1, NULL);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission) VALUES (13, 12, '模板列表', 'C', '/template/list', 'Template/List', 'unordered-list', 1, 1, 'template:list');
