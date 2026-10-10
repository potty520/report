-- ---------------------------------------------------------------------------
-- 界面菜单与角色授权（修正版 v2）
--
-- ⚠️ 踩坑记录（务必按本文件执行，不要用历史版本的 id）：
--   1) sys_menu.menu_code 有唯一键且默认值为 ''，插入时必须显式给唯一 menu_code，
--      否则从第二条起会因唯一键冲突被 INSERT IGNORE 静默跳过；
--   2) 线上已存在 id 1-8 / 21-24 / 31-32 / 61-63 / 71-72 / 81-82，
--      因此本版统一使用 id 83-94（AUTO_INCREMENT 亦为 83）。
--
-- 幂等，可重复执行。
-- ---------------------------------------------------------------------------

-- 1) 审核管理（P0-C）
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, icon, sort, visible, permission)
VALUES (83, 0, '审核管理', 'audit', 'M', '/audit', NULL, 'AuditOutlined', 9, 1, NULL);
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, icon, sort, visible, permission)
VALUES (84, 83, '部门审核', 'audit:dept', 'C', '/audit/dept-audit', 'Audit/DeptAudit', 'AuditOutlined', 1, 1, 'audit:dept');
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, icon, sort, visible, permission)
VALUES (85, 83, '任务方审核', 'audit:task', 'C', '/audit/task-audit', 'Audit/TaskAudit', 'SafetyCertificateOutlined', 2, 1, 'audit:task');
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, icon, sort, visible, permission)
VALUES (86, 83, '流转轨迹', 'audit:trajectory', 'C', '/audit/trajectory', 'Audit/Trajectory', 'NodeIndexOutlined', 3, 1, 'audit:trajectory');
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, icon, sort, visible, permission)
VALUES (87, 83, '补正管理', 'audit:correct', 'C', '/audit/correct', 'Audit/Correct', 'ToolOutlined', 4, 1, 'audit:correct');

-- 2) 附件管理（P1-D，挂在 **线上 id 7 = 填报管理** 下）
--    注意：线上 sys_menu 的 id 空间与 backend/.../DataInitializer.java 的种子 id 不同
--    （线上 7=填报管理、22=角色管理；种子里 22=填报审核）。历史上曾因此把附件管理挂错到角色管理下。
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, icon, sort, visible, permission)
VALUES (88, 7, '附件管理', 'file:list', 'C', '/file/list', 'File/List', 'PaperClipOutlined', 3, 1, 'file:list');

-- 3) 消息中心（P1-F，一级）
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, icon, sort, visible, permission)
VALUES (89, 0, '消息中心', 'todo', 'C', '/todo', 'Todo', 'BellOutlined', 10, 1, 'todo:list');

-- 4) 系统管理下属（P2-G）
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, icon, sort, visible, permission)
VALUES (90, 2, '字典管理', 'system:dict', 'C', '/system/dict', 'System/Dict', 'BookOutlined', 4, 1, 'system:dict:view');
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, icon, sort, visible, permission)
VALUES (91, 2, '审计日志', 'system:operlog', 'C', '/system/operLog', 'System/OperLog', 'FileSearchOutlined', 5, 1, 'system:operlog:view');
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, icon, sort, visible, permission)
VALUES (93, 2, '在线用户', 'system:online', 'C', '/system/online', 'System/OnlineUser', 'WifiOutlined', 6, 1, 'system:online:view');
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, icon, sort, visible, permission)
VALUES (94, 2, '统计规则', 'system:rule', 'C', '/system/statisticsRule', 'System/StatisticsRule', 'SlidersOutlined', 7, 1, 'system:rule:view');

-- 5) 数据范围（挂在 6=任务管理 下）
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, icon, sort, visible, permission)
VALUES (92, 6, '数据范围', 'dataRange', 'C', '/dataRange', 'DataRange', 'FilterOutlined', 4, 1, 'dataRange:view');

-- ---------------------------------------------------------------------------
-- 角色授权（按接口真实权限：字典/审计/在线用户/统计规则仅 SUPER_ADMIN）
--   注意：role 2（DEPT_ADMIN）在线上原本一条菜单授权都没有，登录后侧边栏是空的，
--   这里按"部门管理员的业务范围（不含系统管理）"补齐。
-- ---------------------------------------------------------------------------

-- 超级管理员：新增菜单全部
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
  (1, 83), (1, 84), (1, 85), (1, 86), (1, 87), (1, 88), (1, 89),
  (1, 90), (1, 91), (1, 92), (1, 93), (1, 94);

-- 部门管理员：业务菜单（首页/模板/数据源/组织/任务/填报/数据申请/审核/附件/消息/数据范围）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
  (2, 1), (2, 4), (2, 5), (2, 31), (2, 32),
  (2, 61), (2, 62), (2, 63), (2, 71), (2, 72), (2, 81), (2, 82),
  (2, 83), (2, 84), (2, 85), (2, 86), (2, 87), (2, 88), (2, 89), (2, 92);

-- 审核人：审核管理 + 消息中心 + 附件
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
  (4, 83), (4, 84), (4, 85), (4, 86), (4, 87), (4, 88), (4, 89);

-- 填报人：附件 + 消息中心
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (3, 88), (3, 89);

-- 校验
-- SELECT id, parent_id, menu_name, menu_code, menu_type, path FROM sys_menu WHERE id BETWEEN 83 AND 94 ORDER BY id;
-- SELECT role_id, COUNT(*) FROM sys_role_menu GROUP BY role_id;
