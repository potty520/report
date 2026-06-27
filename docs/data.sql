-- H2 初始化数据
-- 部门
INSERT INTO sys_dept (id, parent_id, dept_name, dept_code, sort) VALUES (1, 0, '总公司', 'ROOT', 0);
INSERT INTO sys_dept (id, parent_id, dept_name, dept_code, sort) VALUES (2, 1, '技术部', 'TECH', 1);
INSERT INTO sys_dept (id, parent_id, dept_name, dept_code, sort) VALUES (3, 1, '财务部', 'FINANCE', 2);
INSERT INTO sys_dept (id, parent_id, dept_name, dept_code, sort) VALUES (4, 1, '市场部', 'MARKET', 3);

-- 用户 (密码: admin123 / user123, BCrypt加密)
INSERT INTO sys_user (id, username, password, real_name, dept_id, status, user_type) VALUES (1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7i5uQZ2', '超级管理员', 1, 1, 1);
INSERT INTO sys_user (id, username, password, real_name, dept_id, status, user_type) VALUES (2, 'user1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7i5uQZ2', '张三', 2, 1, 2);
INSERT INTO sys_user (id, username, password, real_name, dept_id, status, user_type) VALUES (3, 'user2', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7i5uQZ2', '李四', 3, 1, 2);
INSERT INTO sys_user (id, username, password, real_name, dept_id, status, user_type) VALUES (4, 'user3', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7i5uQZ2', '王五', 4, 1, 2);

-- 角色
INSERT INTO sys_role (id, role_name, role_code, description) VALUES (1, '超级管理员', 'SUPER_ADMIN', '系统超级管理员');
INSERT INTO sys_role (id, role_name, role_code, description) VALUES (2, '部门管理员', 'DEPT_ADMIN', '部门级管理员');
INSERT INTO sys_role (id, role_name, role_code, description) VALUES (3, '填报人', 'FILL_USER', '数据填报人员');
INSERT INTO sys_role (id, role_name, role_code, description) VALUES (4, '审核人', 'AUDIT_USER', '数据审核人员');

-- 用户-角色关联
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);
INSERT INTO sys_user_role (user_id, role_id) VALUES (2, 2);
INSERT INTO sys_user_role (user_id, role_id) VALUES (2, 3);
INSERT INTO sys_user_role (user_id, role_id) VALUES (3, 3);
INSERT INTO sys_user_role (user_id, role_id) VALUES (4, 4);

-- 菜单
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, permission, icon, sort) VALUES (1, 0, '填报总览', 'overview', 'C', '/overview', 'pages/Overview', 'overview:view', 'DashboardOutlined', 1);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, permission, icon, sort) VALUES (2, 0, '系统管理', 'system', 'M', '/system', NULL, NULL, 'SettingOutlined', 9);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, permission, icon, sort) VALUES (3, 0, '组织架构', 'org', 'M', '/org', NULL, NULL, 'ApartmentOutlined', 8);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, permission, icon, sort) VALUES (4, 0, '模板管理', 'template', 'C', '/template/list', 'pages/Template/List', 'template:view', 'FileExcelOutlined', 2);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, permission, icon, sort) VALUES (5, 0, '数据源管理', 'datasource', 'C', '/datasource/list', 'pages/Datasource/List', 'datasource:view', 'DatabaseOutlined', 3);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, permission, icon, sort) VALUES (6, 0, '任务管理', 'task', 'M', '/task', NULL, NULL, 'ScheduleOutlined', 4);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, permission, icon, sort) VALUES (7, 0, '填报管理', 'fill', 'M', '/fill', NULL, NULL, 'FormOutlined', 5);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, permission, icon, sort) VALUES (8, 0, '数据申请', 'dataApply', 'M', '/dataApply', NULL, NULL, 'ImportOutlined', 6);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, permission, icon, sort) VALUES (21, 2, '用户管理', 'system:user', 'C', '/system/user', 'pages/System/User', 'system:user:view', 'UserOutlined', 1);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, permission, icon, sort) VALUES (22, 2, '角色管理', 'system:role', 'C', '/system/role', 'pages/System/Role', 'system:role:view', 'TeamOutlined', 2);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, permission, icon, sort) VALUES (23, 2, '菜单管理', 'system:menu', 'C', '/system/menu', 'pages/System/Menu', 'system:menu:view', 'MenuOutlined', 3);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, permission, icon, sort) VALUES (24, 2, '登录日志', 'system:log', 'C', '/system/log', 'pages/System/Log', 'system:log:view', 'FileTextOutlined', 4);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, permission, icon, sort) VALUES (31, 3, '部门管理', 'org:dept', 'C', '/org/dept', 'pages/Org/Dept', 'org:dept:view', 'ClusterOutlined', 1);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, permission, icon, sort) VALUES (32, 3, '项目管理', 'org:project', 'C', '/org/project', 'pages/Org/Project', 'org:project:view', 'ProjectOutlined', 2);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, permission, icon, sort) VALUES (61, 6, '任务制定', 'task:formulation', 'C', '/task/formulation', 'pages/Task/Formulation', 'task:formulation:view', 'EditOutlined', 1);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, permission, icon, sort) VALUES (62, 6, '任务分派', 'task:assign', 'C', '/task/assign', 'pages/Task/Assign', 'task:assign:view', 'SendOutlined', 2);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, permission, icon, sort) VALUES (63, 6, '任务台账', 'task:ledger', 'C', '/task/ledger', 'pages/Task/Ledger', 'task:ledger:view', 'BookOutlined', 3);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, permission, icon, sort) VALUES (71, 7, '数据填报', 'fill:task', 'C', '/fill/task', 'pages/Fill/Task', 'fill:task:view', 'FormOutlined', 1);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, permission, icon, sort) VALUES (72, 7, '填报审核', 'fill:audit', 'C', '/fill/audit', 'pages/Fill/Audit', 'fill:audit:view', 'CheckSquareOutlined', 2);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, permission, icon, sort) VALUES (81, 8, '数据申请', 'dataApply:apply', 'C', '/dataApply/apply', 'pages/DataApply/Apply', 'dataApply:apply:view', 'FileAddOutlined', 1);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, permission, icon, sort) VALUES (82, 8, '申请审批', 'dataApply:approve', 'C', '/dataApply/approve', 'pages/DataApply/Approve', 'dataApply:approve:view', 'AuditOutlined', 2);

-- 超级管理员拥有全部菜单
INSERT INTO sys_role_menu (role_id, menu_id) SELECT 1, id FROM sys_menu;
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (3, 1), (3, 7), (3, 71), (3, 72), (3, 8), (3, 81);
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (4, 1), (4, 7), (4, 71), (4, 72), (4, 8), (4, 82);
