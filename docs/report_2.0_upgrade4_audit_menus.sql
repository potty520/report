-- P0-C 前端界面所需的菜单与角色授权（幂等，可重复执行）
-- 对应新增页面：/audit/dept-audit、/audit/task-audit、/audit/trajectory、/audit/correct
-- 与后端 DataInitializer.java 中的 INSERT IGNORE 保持一致。

-- 1) 一级目录：审核管理
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission)
VALUES (25, 0, '审核管理', 'M', '/audit', NULL, 'AuditOutlined', 9, 1, NULL);

-- 2) 子菜单
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission)
VALUES (26, 25, '部门审核', 'C', '/audit/dept-audit', 'Audit/DeptAudit', 'AuditOutlined', 1, 1, 'audit:dept');
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission)
VALUES (27, 25, '任务方审核', 'C', '/audit/task-audit', 'Audit/TaskAudit', 'SafetyCertificateOutlined', 2, 1, 'audit:task');
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission)
VALUES (28, 25, '流转轨迹', 'C', '/audit/trajectory', 'Audit/Trajectory', 'NodeIndexOutlined', 3, 1, 'audit:trajectory');
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission)
VALUES (29, 25, '补正管理', 'C', '/audit/correct', 'Audit/Correct', 'ToolOutlined', 4, 1, 'audit:correct');

-- 3) 授权：超级管理员
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
  (1, 25), (1, 26), (1, 27), (1, 28), (1, 29);

-- 4) 授权：部门管理员（原先该角色无任何菜单，登录后侧边栏为空）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
  (2, 1), (2, 8), (2, 9), (2, 10), (2, 11), (2, 12), (2, 13), (2, 14),
  (2, 15), (2, 16), (2, 17), (2, 18), (2, 19), (2, 20), (2, 21),
  (2, 22), (2, 23), (2, 24), (2, 25), (2, 26), (2, 27), (2, 28), (2, 29);

-- 5) 授权：审核人
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
  (4, 22), (4, 23), (4, 24), (4, 25), (4, 26), (4, 27), (4, 28), (4, 29);

-- 校验
-- SELECT id, parent_id, menu_name, menu_type, path FROM sys_menu WHERE id BETWEEN 25 AND 29;
-- SELECT role_id, COUNT(*) FROM sys_role_menu GROUP BY role_id;
