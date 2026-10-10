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

-- ---------------------------------------------------------------------------
-- P1-D 附件管理（分片上传 / 秒传 / 断点续传 / 下载）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission)
VALUES (30, 22, '附件管理', 'C', '/file/list', 'File/List', 'PaperClipOutlined', 3, 1, 'file:list');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
  (1, 30), (2, 30), (3, 30);

-- ---------------------------------------------------------------------------
-- P1-F / P2-G 消息中心 / 字典管理 / 审计日志 / 数据范围
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission)
VALUES (31, 0, '消息中心', 'C', '/todo', 'Todo', 'BellOutlined', 10, 1, 'todo:list');
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission)
VALUES (32, 2, '字典管理', 'C', '/system/dict', 'System/Dict', 'BookOutlined', 4, 1, 'system:dict:view');
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission)
VALUES (33, 2, '审计日志', 'C', '/system/operLog', 'System/OperLog', 'FileSearchOutlined', 5, 1, 'system:operlog:view');
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission)
VALUES (34, 6, '数据范围', 'C', '/dataRange', 'DataRange', 'FilterOutlined', 4, 1, 'dataRange:view');

-- 字典管理与审计日志接口仅 SUPER_ADMIN 可用；数据范围需 SUPER_ADMIN/DEPT_ADMIN
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
  (1, 31), (1, 32), (1, 33), (1, 34),
  (2, 31), (2, 34),
  (3, 31),
  (4, 31);

-- ---------------------------------------------------------------------------
-- 在线用户 / 统计规则（接口均仅 SUPER_ADMIN 可用）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission)
VALUES (35, 2, '在线用户', 'C', '/system/online', 'System/OnlineUser', 'WifiOutlined', 6, 1, 'system:online:view');
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, permission)
VALUES (36, 2, '统计规则', 'C', '/system/statisticsRule', 'System/StatisticsRule', 'SlidersOutlined', 7, 1, 'system:rule:view');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 35), (1, 36);

-- ---------------------------------------------------------------------------
-- 附件上传上限（必须先放开，否则 >1MB 的附件一律失败）
--   1) Spring：spring.servlet.multipart.max-file-size / max-request-size
--      —— 已在 backend/src/main/resources/application.yml 提供（默认 50MB/60MB）
--      线上还需同步 /opt/report/application-prod.yml（该文件是 --spring.config.location 指定的唯一配置）
--   2) nginx：client_max_body_size（默认 1MB，实测 1.5MB 上传直接 413）
--      在 listen 8091 的 server 块内加：client_max_body_size 60m;
--
-- 校验
-- SELECT id, parent_id, menu_name, menu_type, path FROM sys_menu WHERE id BETWEEN 25 AND 30;
-- SELECT role_id, COUNT(*) FROM sys_role_menu GROUP BY role_id;
