-- ============================================================================
-- 2.2 角色拆分：对齐原版手册的 5 类角色
--   数据管理员      -> DATA_ADMIN     （沿用 DEPT_ADMIN 的菜单权限）
--   模板审核人员    -> TEMPLATE_AUDITOR
--   任务制定人员    -> TASK_MAKER
--   任务分派人员    -> TASK_ASSIGNER
--   填报负责人      -> FILL_OWNER     （沿用 FILL_USER 的菜单权限）
-- 说明：保留原有 4 个角色不动，新增 3 个细分角色，菜单权限从同类角色复制。
-- ============================================================================

INSERT INTO sys_role (role_code, role_name, description, status, create_time)
SELECT 'TEMPLATE_AUDITOR', '模板审核人员', '负责模板审核（通过/不通过）', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'TEMPLATE_AUDITOR');

INSERT INTO sys_role (role_code, role_name, description, status, create_time)
SELECT 'TASK_MAKER', '任务制定人员', '负责制定填报任务、处理被退回任务', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'TASK_MAKER');

INSERT INTO sys_role (role_code, role_name, description, status, create_time)
SELECT 'TASK_ASSIGNER', '任务分派人员', '负责把任务分派给填报负责人、执行分派退回', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'TASK_ASSIGNER');

-- 菜单权限：任务类角色复制 DEPT_ADMIN，模板审核复制 SUPER_ADMIN 的模板相关菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT nr.id, rm.menu_id
FROM sys_role nr
JOIN sys_role pr ON pr.role_code = 'DEPT_ADMIN'
JOIN sys_role_menu rm ON rm.role_id = pr.id
WHERE nr.role_code IN ('TASK_MAKER', 'TASK_ASSIGNER')
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = nr.id AND x.menu_id = rm.menu_id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT nr.id, rm.menu_id
FROM sys_role nr
JOIN sys_role pr ON pr.role_code = 'SUPER_ADMIN'
JOIN sys_role_menu rm ON rm.role_id = pr.id
WHERE nr.role_code = 'TEMPLATE_AUDITOR'
  AND rm.menu_id IN (SELECT id FROM sys_menu WHERE path LIKE '%template%' OR path LIKE '%audit%' OR menu_name LIKE '%模板%' OR menu_name LIKE '%审核%')
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = nr.id AND x.menu_id = rm.menu_id);
