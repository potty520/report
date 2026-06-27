-- H2 初始化数据
-- 部门
INSERT INTO sys_dept (id, parent_id, dept_name, dept_code, sort) VALUES (1, 0, '总公司', 'ROOT', 0);
INSERT INTO sys_dept (id, parent_id, dept_name, dept_code, sort) VALUES (2, 1, '技术部', 'TECH', 1);
INSERT INTO sys_dept (id, parent_id, dept_name, dept_code, sort) VALUES (3, 1, '财务部', 'FINANCE', 2);
INSERT INTO sys_dept (id, parent_id, dept_name, dept_code, sort) VALUES (4, 1, '市场部', 'MARKET', 3);

-- 用户 (密码: ***REMOVED_DEFAULT_ADMIN_PASSWORD*** / ***REMOVED_DEFAULT_USER_PASSWORD***, BCrypt加密)
INSERT INTO sys_user (id, username, password, real_name, dept_id, status, user_type) VALUES (1, 'admin', '***REMOVED_BCRYPT_HASH***', '超级管理员', 1, 1, 1);
INSERT INTO sys_user (id, username, password, real_name, dept_id, status, user_type) VALUES (2, 'user1', '***REMOVED_BCRYPT_HASH***', '张三', 2, 1, 2);
INSERT INTO sys_user (id, username, password, real_name, dept_id, status, user_type) VALUES (3, 'user2', '***REMOVED_BCRYPT_HASH***', '李四', 3, 1, 2);
INSERT INTO sys_user (id, username, password, real_name, dept_id, status, user_type) VALUES (4, 'user3', '***REMOVED_BCRYPT_HASH***', '王五', 4, 1, 2);

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
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, icon, sort) VALUES (1, 0, '填报总览', 'overview', 'C', '/overview', 'DashboardOutlined', 1);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, icon, sort) VALUES (2, 0, '系统管理', 'system', 'M', '/system', 'SettingOutlined', 9);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, icon, sort) VALUES (3, 0, '组织架构', 'org', 'M', '/org', 'ApartmentOutlined', 8);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, icon, sort) VALUES (4, 0, '模板管理', 'template', 'C', '/template/list', 'FileExcelOutlined', 2);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, icon, sort) VALUES (5, 0, '数据源管理', 'datasource', 'C', '/datasource/list', 'DatabaseOutlined', 3);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, icon, sort) VALUES (6, 0, '任务管理', 'task', 'M', '/task', 'ScheduleOutlined', 4);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, icon, sort) VALUES (7, 0, '填报管理', 'fill', 'M', '/fill', 'FormOutlined', 5);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, icon, sort) VALUES (8, 0, '数据申请', 'dataApply', 'M', '/dataApply', 'ImportOutlined', 6);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, icon, sort) VALUES (21, 2, '用户管理', 'system:user', 'C', '/system/user', 'UserOutlined', 1);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, icon, sort) VALUES (22, 2, '角色管理', 'system:role', 'C', '/system/role', 'TeamOutlined', 2);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, icon, sort) VALUES (23, 2, '菜单管理', 'system:menu', 'C', '/system/menu', 'MenuOutlined', 3);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, icon, sort) VALUES (24, 2, '登录日志', 'system:log', 'C', '/system/log', 'FileTextOutlined', 4);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, icon, sort) VALUES (31, 3, '部门管理', 'org:dept', 'C', '/org/dept', 'ClusterOutlined', 1);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, icon, sort) VALUES (32, 3, '项目管理', 'org:project', 'C', '/org/project', 'ProjectOutlined', 2);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, icon, sort) VALUES (61, 6, '任务制定', 'task:formulation', 'C', '/task/formulation', 'EditOutlined', 1);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, icon, sort) VALUES (62, 6, '任务分派', 'task:assign', 'C', '/task/assign', 'SendOutlined', 2);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, icon, sort) VALUES (63, 6, '任务台账', 'task:ledger', 'C', '/task/ledger', 'BookOutlined', 3);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, icon, sort) VALUES (71, 7, '数据填报', 'fill:task', 'C', '/fill/task', 'FormOutlined', 1);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, icon, sort) VALUES (72, 7, '填报审核', 'fill:audit', 'C', '/fill/audit', 'CheckSquareOutlined', 2);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, icon, sort) VALUES (81, 8, '数据申请', 'dataApply:apply', 'C', '/dataApply/apply', 'FileAddOutlined', 1);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, icon, sort) VALUES (82, 8, '申请审批', 'dataApply:approve', 'C', '/dataApply/approve', 'AuditOutlined', 2);

-- 超级管理员拥有全部菜单
INSERT INTO sys_role_menu (role_id, menu_id) SELECT 1, id FROM sys_menu;
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (3, 1), (3, 7), (3, 71), (3, 72), (3, 8), (3, 81);
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (4, 1), (4, 7), (4, 71), (4, 72), (4, 8), (4, 82);
