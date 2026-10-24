-- ============================================================
-- 填报系统 - 完整数据库建表脚本 (MySQL 8.0)
-- 包含: 授权系统 + 组织架构 + 填报业务
-- ============================================================

CREATE DATABASE IF NOT EXISTS report_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE report_system;

-- ============================================================
-- 一、授权系统
-- ============================================================

-- 部门表 (组织架构与授权共用)
DROP TABLE IF EXISTS sys_dept;
CREATE TABLE sys_dept (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '部门ID',
    parent_id   BIGINT        DEFAULT 0  COMMENT '父部门ID (0=根节点)',
    dept_name   VARCHAR(64)   NOT NULL COMMENT '部门名称',
    dept_code   VARCHAR(32)   NOT NULL COMMENT '部门编码',
    leader      VARCHAR(32)   DEFAULT '' COMMENT '负责人',
    phone       VARCHAR(20)   DEFAULT '' COMMENT '联系电话',
    sort        INT           DEFAULT 0  COMMENT '排序',
    status      TINYINT       DEFAULT 1  COMMENT '状态 (1=启用 0=停用)',
    create_time DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dept_code (dept_code)
) ENGINE=InnoDB COMMENT='部门表';

-- 用户表
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username    VARCHAR(32)   NOT NULL COMMENT '用户名',
    password    VARCHAR(128)  NOT NULL COMMENT '密码 (BCrypt加密)',
    real_name   VARCHAR(32)   DEFAULT '' COMMENT '真实姓名',
    email       VARCHAR(64)   DEFAULT '' COMMENT '邮箱',
    phone       VARCHAR(20)   DEFAULT '' COMMENT '手机号',
    dept_id     BIGINT        DEFAULT NULL COMMENT '所属部门ID',
    status      TINYINT       DEFAULT 1  COMMENT '状态 (1=启用 0=停用)',
    user_type   TINYINT       DEFAULT 2  COMMENT '用户类型 (1=超级管理员 2=普通用户)',
    create_time DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_dept_id (dept_id)
) ENGINE=InnoDB COMMENT='用户表';

-- 角色表
DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    role_name   VARCHAR(32)   NOT NULL COMMENT '角色名称',
    role_code   VARCHAR(32)   NOT NULL COMMENT '角色编码',
    description VARCHAR(128)  DEFAULT '' COMMENT '描述',
    status      TINYINT       DEFAULT 1  COMMENT '状态 (1=启用 0=停用)',
    create_time DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB COMMENT='角色表';

-- 菜单表
DROP TABLE IF EXISTS sys_menu;
CREATE TABLE sys_menu (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
    parent_id   BIGINT        DEFAULT 0  COMMENT '父菜单ID (0=根节点)',
    menu_name   VARCHAR(32)   NOT NULL COMMENT '菜单名称',
    menu_code   VARCHAR(64)   DEFAULT '' COMMENT '菜单编码(权限标识)',
    menu_type   CHAR(1)       DEFAULT 'M' COMMENT '类型 (M=目录 C=菜单 B=按钮)',
    path        VARCHAR(128)  DEFAULT '' COMMENT '前端路由路径',
    component   VARCHAR(200)  DEFAULT NULL COMMENT '组件路径',
    permission  VARCHAR(100)  DEFAULT NULL COMMENT '权限标识',
    icon        VARCHAR(64)   DEFAULT '' COMMENT '图标',
    sort        INT           DEFAULT 0  COMMENT '排序',
    status      TINYINT       DEFAULT 1  COMMENT '状态 (1=启用 0=停用)',
    visible     TINYINT       DEFAULT 1  COMMENT '是否可见 (1=是 0=否)',
    create_time DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_menu_code (menu_code)
) ENGINE=InnoDB COMMENT='菜单表';

-- 用户-角色关联表
DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (user_id, role_id),
    KEY idx_role_id (role_id)
) ENGINE=InnoDB COMMENT='用户角色关联表';

-- 角色-菜单关联表
DROP TABLE IF EXISTS sys_role_menu;
CREATE TABLE sys_role_menu (
    role_id BIGINT NOT NULL COMMENT '角色ID',
    menu_id BIGINT NOT NULL COMMENT '菜单ID',
    PRIMARY KEY (role_id, menu_id),
    KEY idx_menu_id (menu_id)
) ENGINE=InnoDB COMMENT='角色菜单关联表';

-- 登录日志表
DROP TABLE IF EXISTS sys_login_log;
CREATE TABLE sys_login_log (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    user_id     BIGINT        DEFAULT NULL COMMENT '用户ID',
    username    VARCHAR(32)   DEFAULT '' COMMENT '用户名',
    ip          VARCHAR(64)   DEFAULT '' COMMENT '登录IP',
    location    VARCHAR(100)  DEFAULT NULL COMMENT '登录地点',
    browser     VARCHAR(200)  DEFAULT NULL COMMENT '浏览器',
    os          VARCHAR(100)  DEFAULT NULL COMMENT '操作系统',
    login_time  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    status      TINYINT       DEFAULT 1  COMMENT '状态 (1=成功 0=失败)',
    msg         VARCHAR(256)  DEFAULT '' COMMENT '消息',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_login_time (login_time)
) ENGINE=InnoDB COMMENT='登录日志表';

-- ============================================================
-- 二、组织架构
-- ============================================================

-- 项目表
DROP TABLE IF EXISTS sys_project;
CREATE TABLE sys_project (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '项目ID',
    dept_id     BIGINT        DEFAULT NULL COMMENT '所属部门ID',
    parent_id   BIGINT        DEFAULT 0  COMMENT '父项目ID (支持树形)',
    project_name VARCHAR(64)  NOT NULL COMMENT '项目名称',
    project_code VARCHAR(32)  NOT NULL COMMENT '项目编码',
    description VARCHAR(256)  DEFAULT '' COMMENT '描述',
    status      TINYINT       DEFAULT 1  COMMENT '状态 (1=启用 0=停用)',
    sort        INT           DEFAULT 0  COMMENT '排序',
    create_time DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_code (project_code),
    KEY idx_dept_id (dept_id)
) ENGINE=InnoDB COMMENT='项目表';

-- ============================================================
-- 三、填报业务
-- ============================================================

-- 数据源表
DROP TABLE IF EXISTS rpt_datasource;
CREATE TABLE rpt_datasource (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '数据源ID',
    ds_name     VARCHAR(64)   NOT NULL COMMENT '数据源名称',
    ds_type     VARCHAR(16)   DEFAULT 'MYSQL' COMMENT '数据库类型 (MYSQL/ORACLE/POSTGRESQL)',
    ip          VARCHAR(64)   NOT NULL COMMENT 'IP地址',
    port        INT           DEFAULT 3306 COMMENT '端口',
    db_name     VARCHAR(64)   NOT NULL COMMENT '数据库名',
    username    VARCHAR(64)   NOT NULL COMMENT '用户名',
    password    VARCHAR(128)  NOT NULL COMMENT '密码(AES加密)',
    status      TINYINT       DEFAULT 1  COMMENT '状态 (1=启用 0=停用)',
    create_time DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB COMMENT='数据源表';

-- 模板表
DROP TABLE IF EXISTS rpt_template;
CREATE TABLE rpt_template (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '模板ID',
    template_name   VARCHAR(64)   NOT NULL COMMENT '模板名称',
    template_code   VARCHAR(32)   NOT NULL COMMENT '模板编码',
    filling_cycle   TINYINT       NOT NULL COMMENT '填报周期 (1日报 2周报 3月报 4季报 5年报 6一次性)',
    dept_id         BIGINT        DEFAULT NULL COMMENT '所属部门ID',
    project_id      BIGINT        DEFAULT NULL COMMENT '所属项目ID',
    file_url        VARCHAR(256)  DEFAULT '' COMMENT '模板Excel文件路径',
    file_name       VARCHAR(128)  DEFAULT '' COMMENT '模板文件名',
    table_name      VARCHAR(64)   DEFAULT '' COMMENT '目标表名',
    datasource_id   BIGINT        DEFAULT NULL COMMENT '目标数据源ID',
    description     VARCHAR(256)  DEFAULT '' COMMENT '描述',
    status          TINYINT       DEFAULT 0  COMMENT '模板状态 (0=草稿 1=启用 2=停用)',
    audit_status    TINYINT       DEFAULT 0  COMMENT '审核状态 (0=待审 1=通过 2=驳回)',
    audit_comment   VARCHAR(256)  DEFAULT '' COMMENT '审核意见',
    create_by       BIGINT        DEFAULT NULL COMMENT '创建人ID',
    create_time     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_template_code (template_code),
    KEY idx_dept_id (dept_id),
    KEY idx_project_id (project_id)
) ENGINE=InnoDB COMMENT='填报模板表';

-- 模板字段映射表
DROP TABLE IF EXISTS rpt_template_field;
CREATE TABLE rpt_template_field (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '字段ID',
    template_id     BIGINT        NOT NULL COMMENT '模板ID',
    field_name      VARCHAR(64)   NOT NULL COMMENT '字段名称(中文)',
    field_code      VARCHAR(64)   NOT NULL COMMENT '字段编码',
    field_type      VARCHAR(16)   DEFAULT 'VARCHAR' COMMENT '字段类型',
    field_length    INT           DEFAULT 255 COMMENT '字段长度',
    is_required     TINYINT       DEFAULT 0  COMMENT '是否必填 (1=是 0=否)',
    is_key          TINYINT       DEFAULT 0  COMMENT '是否主键 (1=是 0=否)',
    excel_column    VARCHAR(8)    DEFAULT '' COMMENT 'Excel列号 (A/B/C...)',
    db_column       VARCHAR(64)   DEFAULT '' COMMENT '数据库列名',
    sort            INT           DEFAULT 0  COMMENT '排序',
    PRIMARY KEY (id),
    KEY idx_template_id (template_id)
) ENGINE=InnoDB COMMENT='模板字段映射表';

-- 填报任务表
DROP TABLE IF EXISTS rpt_task;
CREATE TABLE rpt_task (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    task_name       VARCHAR(128)  NOT NULL COMMENT '任务名称',
    template_id     BIGINT        NOT NULL COMMENT '模板ID',
    dept_id         BIGINT        DEFAULT NULL COMMENT '下发部门ID',
    project_id      BIGINT        DEFAULT NULL COMMENT '所属项目ID',
    belong_time     VARCHAR(32)   DEFAULT '' COMMENT '数据所属时间 (如2026-06)',
    filling_cycle   TINYINT       NOT NULL COMMENT '填报周期',
    status          TINYINT       DEFAULT 0  COMMENT '任务状态 (0=草稿 1=已发布 2=填报中 3=已完成 4=已关闭)',
    fill_deadline   DATETIME      DEFAULT NULL COMMENT '填报截止时间',
    audit_deadline  DATETIME      DEFAULT NULL COMMENT '审核截止时间',
    create_by       BIGINT        DEFAULT NULL COMMENT '创建人ID',
    create_time     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_template_id (template_id),
    KEY idx_dept_id (dept_id),
    KEY idx_status (status)
) ENGINE=InnoDB COMMENT='填报任务表';

-- 任务分派表
DROP TABLE IF EXISTS rpt_task_assign;
CREATE TABLE rpt_task_assign (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '分派ID',
    task_id     BIGINT        NOT NULL COMMENT '任务ID',
    dept_id     BIGINT        NOT NULL COMMENT '被分配部门ID',
    user_id     BIGINT        DEFAULT NULL COMMENT '被分配用户ID',
    assign_type VARCHAR(8)    DEFAULT 'FILL' COMMENT '分配类型 (FILL=填报 AUDIT=审核)',
    status      TINYINT       DEFAULT 0  COMMENT '状态 (0=待填报 1=已填报 2=逾期)',
    create_time DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_task_id (task_id),
    KEY idx_dept_id (dept_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB COMMENT='任务分派表';

-- 填报数据表
DROP TABLE IF EXISTS rpt_task_data;
CREATE TABLE rpt_task_data (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '数据ID',
    task_id         BIGINT        NOT NULL COMMENT '任务ID',
    template_id     BIGINT        NOT NULL COMMENT '模板ID',
    dept_id         BIGINT        DEFAULT NULL COMMENT '填报部门ID',
    fill_user_id    BIGINT        DEFAULT NULL COMMENT '填报人ID',
    data_json       TEXT          COMMENT '填报数据(JSON格式)',
    file_url        VARCHAR(256)  DEFAULT '' COMMENT '附件路径',
    file_name       VARCHAR(128)  DEFAULT '' COMMENT '附件名',
    status          TINYINT       DEFAULT 0  COMMENT '状态 (0=草稿 1=已提交)',
    submit_time     DATETIME      DEFAULT NULL COMMENT '提交时间',
    audit_status    TINYINT       DEFAULT 0  COMMENT '审核状态 (0=待审 1=通过 2=驳回)',
    audit_comment   VARCHAR(256)  DEFAULT '' COMMENT '审核意见',
    audit_user_id   BIGINT        DEFAULT NULL COMMENT '审核人ID',
    audit_time      DATETIME      DEFAULT NULL COMMENT '审核时间',
    create_time     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_task_id (task_id),
    KEY idx_dept_id (dept_id),
    KEY idx_fill_user_id (fill_user_id),
    KEY idx_audit_status (audit_status)
) ENGINE=InnoDB COMMENT='填报数据表';

-- 数据申请表
DROP TABLE IF EXISTS rpt_data_apply;
CREATE TABLE rpt_data_apply (
    id                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '申请ID',
    task_id             BIGINT        NOT NULL COMMENT '任务ID',
    applicant_id        BIGINT        NOT NULL COMMENT '申请人ID',
    applicant_dept_id   BIGINT        DEFAULT NULL COMMENT '申请部门ID',
    reason              VARCHAR(256)  DEFAULT '' COMMENT '申请原因',
    status              TINYINT       DEFAULT 0  COMMENT '状态 (0=待审 1=通过 2=驳回)',
    auditor_id          BIGINT        DEFAULT NULL COMMENT '审批人ID',
    audit_comment       VARCHAR(256)  DEFAULT '' COMMENT '审批意见',
    create_time         DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    audit_time          DATETIME      DEFAULT NULL COMMENT '审批时间',
    PRIMARY KEY (id),
    KEY idx_task_id (task_id),
    KEY idx_applicant_id (applicant_id),
    KEY idx_status (status)
) ENGINE=InnoDB COMMENT='数据申请表';

-- ============================================================
-- 四、初始化数据
-- ============================================================

-- 初始化部门
INSERT INTO sys_dept (id, parent_id, dept_name, dept_code, sort) VALUES
(1, 0, '总公司', 'ROOT', 0),
(2, 1, '技术部', 'TECH', 1),
(3, 1, '财务部', 'FINANCE', 2),
(4, 1, '市场部', 'MARKET', 3);

-- 初始化用户 (密码: ***REMOVED_DEFAULT_ADMIN_PASSWORD*** / ***REMOVED_DEFAULT_USER_PASSWORD***, BCrypt加密)
-- ***REMOVED_DEFAULT_ADMIN_PASSWORD*** -> ***REMOVED_BCRYPT_HASH***
INSERT INTO sys_user (id, username, password, real_name, dept_id, status, user_type) VALUES
(1, 'admin',  '***REMOVED_BCRYPT_HASH***', '超级管理员', 1, 1, 1),
(2, 'user1',  '***REMOVED_BCRYPT_HASH***', '张三', 2, 1, 2),
(3, 'user2',  '***REMOVED_BCRYPT_HASH***', '李四', 3, 1, 2),
(4, 'user3',  '***REMOVED_BCRYPT_HASH***', '王五', 4, 1, 2);

-- 初始化角色
INSERT INTO sys_role (id, role_name, role_code, description) VALUES
(1, '超级管理员', 'SUPER_ADMIN', '系统超级管理员'),
(2, '部门管理员', 'DEPT_ADMIN', '部门级管理员'),
(3, '填报人', 'FILL_USER', '数据填报人员'),
(4, '审核人', 'AUDIT_USER', '数据审核人员');

-- 初始化用户-角色关联
INSERT INTO sys_user_role (user_id, role_id) VALUES
(1, 1),
(2, 2), (2, 3),
(3, 3),
(4, 4);

-- 初始化菜单 (完整的系统菜单树)
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, permission, icon, sort) VALUES
-- 一级菜单
(1,  0,  '填报总览',   'overview',        'C', '/overview',        'pages/Overview',      'overview:view',       'DashboardOutlined',    1),
(2,  0,  '系统管理',   'system',          'M', '/system',          NULL,                   NULL,                  'SettingOutlined',      9),
(3,  0,  '组织架构',   'org',             'M', '/org',             NULL,                   NULL,                  'ApartmentOutlined',    8),
(4,  0,  '模板管理',   'template',        'C', '/template/list',   'pages/Template/List', 'template:view',       'FileExcelOutlined',    2),
(5,  0,  '数据源管理', 'datasource',      'C', '/datasource/list', 'pages/Datasource/List','datasource:view',     'DatabaseOutlined',     3),
(6,  0,  '任务管理',   'task',            'M', '/task',            NULL,                   NULL,                  'ScheduleOutlined',     4),
(7,  0,  '填报管理',   'fill',            'M', '/fill',            NULL,                   NULL,                  'FormOutlined',         5),
(8,  0,  '数据申请',   'dataApply',       'M', '/dataApply',       NULL,                   NULL,                  'ImportOutlined',       6),
-- 系统管理子菜单
(21, 2,  '用户管理',   'system:user',     'C', '/system/user',     'pages/System/User',   'system:user:view',    'UserOutlined',         1),
(22, 2,  '角色管理',   'system:role',     'C', '/system/role',     'pages/System/Role',   'system:role:view',    'TeamOutlined',         2),
(23, 2,  '菜单管理',   'system:menu',     'C', '/system/menu',     'pages/System/Menu',   'system:menu:view',    'MenuOutlined',         3),
(24, 2,  '登录日志',   'system:log',      'C', '/system/log',      'pages/System/Log',    'system:log:view',     'FileTextOutlined',     4),
-- 组织架构子菜单
(31, 3,  '部门管理',   'org:dept',        'C', '/org/dept',        'pages/Org/Dept',      'org:dept:view',       'ClusterOutlined',      1),
(32, 3,  '项目管理',   'org:project',     'C', '/org/project',     'pages/Org/Project',   'org:project:view',    'ProjectOutlined',      2),
-- 任务管理子菜单
(61, 6,  '任务制定',   'task:formulation','C', '/task/formulation','pages/Task/Formulation','task:formulation:view','EditOutlined',       1),
(62, 6,  '任务分派',   'task:assign',     'C', '/task/assign',     'pages/Task/Assign',   'task:assign:view',    'SendOutlined',         2),
(63, 6,  '任务台账',   'task:ledger',     'C', '/task/ledger',     'pages/Task/Ledger',   'task:ledger:view',    'BookOutlined',         3),
-- 填报管理子菜单
(71, 7,  '数据填报',   'fill:task',       'C', '/fill/task',       'pages/Fill/Task',     'fill:task:view',      'FormOutlined',         1),
(72, 7,  '填报审核',   'fill:audit',      'C', '/fill/audit',      'pages/Fill/Audit',    'fill:audit:view',     'CheckSquareOutlined',  2),
-- 数据申请子菜单
(81, 8,  '数据申请',   'dataApply:apply', 'C', '/dataApply/apply', 'pages/DataApply/Apply','dataApply:apply:view','FileAddOutlined',     1),
(82, 8,  '申请审批',   'dataApply:approve','C','/dataApply/approve','pages/DataApply/Approve','dataApply:approve:view','AuditOutlined',      2);

-- 初始化角色-菜单关联 (超级管理员拥有全部权限)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu;

-- 给填报人角色分配填报相关权限
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(3, 1), (3, 7), (3, 71), (3, 72), (3, 8), (3, 81);

-- 给审核人角色分配审核相关权限
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(4, 1), (4, 7), (4, 71), (4, 72), (4, 8), (4, 82);

-- 初始化默认数据源
INSERT INTO rpt_datasource (id, ds_name, ds_type, ip, port, db_name, username, password, status) VALUES
(1, '本地MySQL', 'MYSQL', '127.0.0.1', 3306, 'report_target', 'root', 'enc:***REMOVED_DATASOURCE_PASSWORD***', 1);
