-- H2 Schema for Report System

CREATE TABLE IF NOT EXISTS sys_dept (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT DEFAULT 0,
    dept_name VARCHAR(64) NOT NULL,
    dept_code VARCHAR(32) NOT NULL,
    leader VARCHAR(32) DEFAULT '',
    phone VARCHAR(20) DEFAULT '',
    sort INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(32) NOT NULL,
    password VARCHAR(128) NOT NULL,
    real_name VARCHAR(32) DEFAULT '',
    email VARCHAR(64) DEFAULT '',
    phone VARCHAR(20) DEFAULT '',
    dept_id BIGINT,
    status TINYINT DEFAULT 1,
    user_type TINYINT DEFAULT 2,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_username ON sys_user(username);

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_name VARCHAR(32) NOT NULL,
    role_code VARCHAR(32) NOT NULL,
    description VARCHAR(128) DEFAULT '',
    status TINYINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT DEFAULT 0,
    menu_name VARCHAR(32) NOT NULL,
    menu_code VARCHAR(64) DEFAULT '',
    menu_type CHAR(1) DEFAULT 'M',
    path VARCHAR(128) DEFAULT '',
    component VARCHAR(128) DEFAULT '',
    icon VARCHAR(64) DEFAULT '',
    sort INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    visible TINYINT DEFAULT 1,
    permission VARCHAR(128) DEFAULT '',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id)
);

CREATE TABLE IF NOT EXISTS sys_login_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(32) DEFAULT '',
    ip VARCHAR(64) DEFAULT '',
    -- 登录地：SysLoginLog 实体与 SysLoginLogMapper 都在写这一列，
    -- 原 schema 漏了它，导致 H2/MODE=MySQL 下登录直接报 Column "location" not found
    location VARCHAR(64) DEFAULT '',
    -- 浏览器 / 操作系统：SysLoginLog 实体同样有这两个字段，原 schema 一并缺失
    browser VARCHAR(64) DEFAULT '',
    os VARCHAR(64) DEFAULT '',
    login_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status TINYINT DEFAULT 1,
    msg VARCHAR(256) DEFAULT ''
);

CREATE TABLE IF NOT EXISTS sys_project (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dept_id BIGINT,
    parent_id BIGINT DEFAULT 0,
    project_name VARCHAR(64) NOT NULL,
    project_code VARCHAR(32) NOT NULL,
    description VARCHAR(256) DEFAULT '',
    status TINYINT DEFAULT 1,
    sort INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rpt_datasource (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ds_name VARCHAR(64) NOT NULL,
    ds_type VARCHAR(16) DEFAULT 'MYSQL',
    ip VARCHAR(64) NOT NULL,
    port INT DEFAULT 3306,
    db_name VARCHAR(64) NOT NULL,
    username VARCHAR(64) NOT NULL,
    password VARCHAR(128) NOT NULL,
    status TINYINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rpt_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_name VARCHAR(64) NOT NULL,
    template_code VARCHAR(32) NOT NULL,
    filling_cycle TINYINT NOT NULL,
    dept_id BIGINT,
    project_id BIGINT,
    file_url VARCHAR(256) DEFAULT '',
    file_name VARCHAR(128) DEFAULT '',
    table_name VARCHAR(64) DEFAULT '',
    datasource_id BIGINT,
    description VARCHAR(256) DEFAULT '',
    status TINYINT DEFAULT 0,
    audit_status TINYINT DEFAULT 0,
    audit_comment VARCHAR(256) DEFAULT '',
    create_by BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rpt_template_field (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL,
    field_name VARCHAR(64) NOT NULL,
    field_code VARCHAR(64) NOT NULL,
    field_type VARCHAR(16) DEFAULT 'VARCHAR',
    field_length INT DEFAULT 255,
    is_required TINYINT DEFAULT 0,
    is_key TINYINT DEFAULT 0,
    excel_column VARCHAR(8) DEFAULT '',
    db_column VARCHAR(64) DEFAULT '',
    sort INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS rpt_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_name VARCHAR(128) NOT NULL,
    template_id BIGINT NOT NULL,
    dept_id BIGINT,
    project_id BIGINT,
    belong_time VARCHAR(32) DEFAULT '',
    filling_cycle TINYINT NOT NULL,
    status TINYINT DEFAULT 0,
    fill_deadline TIMESTAMP,
    audit_deadline TIMESTAMP,
    create_by BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rpt_task_assign (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    dept_id BIGINT NOT NULL,
    user_id BIGINT,
    assign_type VARCHAR(8) DEFAULT 'FILL',
    status TINYINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rpt_task_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    dept_id BIGINT,
    fill_user_id BIGINT,
    data_json TEXT,
    file_url VARCHAR(256) DEFAULT '',
    file_name VARCHAR(128) DEFAULT '',
    status TINYINT DEFAULT 0,
    submit_time TIMESTAMP,
    audit_status TINYINT DEFAULT 0,
    audit_comment VARCHAR(256) DEFAULT '',
    audit_user_id BIGINT,
    audit_time TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rpt_data_apply (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    applicant_id BIGINT NOT NULL,
    applicant_dept_id BIGINT,
    reason VARCHAR(256) DEFAULT '',
    status TINYINT DEFAULT 0,
    auditor_id BIGINT,
    audit_comment VARCHAR(256) DEFAULT '',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    audit_time TIMESTAMP
);

-- ============================================================
-- 附件表
--
-- 修复：RptFile 实体（@TableName("rpt_file")）与 FileStorageService 全链路都在读写这张表，
-- 但原 schema 里没有建表语句，导致附件上传/列表/下载在纯净库上直接报 Table "rpt_file" not found。
-- ============================================================
CREATE TABLE IF NOT EXISTS rpt_file (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT,
    data_id BIGINT,
    template_id BIGINT,
    file_name VARCHAR(255) NOT NULL,
    store_path VARCHAR(512) NOT NULL,
    file_size BIGINT DEFAULT 0,
    file_type VARCHAR(32) DEFAULT 'ATTACHMENT',
    content_type VARCHAR(128) DEFAULT '',
    md5 VARCHAR(64) DEFAULT '',
    upload_by BIGINT,
    deleted TINYINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 性能索引（缺陷修复 V01）
--
-- 背景：评测中「部门审核」接口实测 25,139ms 超时，而同批次其他接口均值仅 617ms。
-- 根因：rpt_task_assign / rpt_task_data / rpt_task_process 三张表只有 task_id 普通列，
--       没有建索引。审核链路里 AuthzService.assertCanAudit() 每次都要按 task_id 查分派记录，
--       数据量上来后是全表扫描，多个这样的查询叠加就把单次审核拖到几十秒。
--
-- 注：H2 / MySQL 8 均支持 CREATE INDEX IF NOT EXISTS 语义（H2 原生支持；
--     MySQL 8.0 不支持该关键字时会报错，生产库请改用 script/ 下的迁移脚本执行）。
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_task_assign_task_id ON rpt_task_assign(task_id);
CREATE INDEX IF NOT EXISTS idx_task_assign_dept_id ON rpt_task_assign(dept_id);
CREATE INDEX IF NOT EXISTS idx_task_assign_user_id ON rpt_task_assign(user_id);
CREATE INDEX IF NOT EXISTS idx_task_data_task_id ON rpt_task_data(task_id);
CREATE INDEX IF NOT EXISTS idx_task_data_task_status ON rpt_task_data(task_id, status);
CREATE INDEX IF NOT EXISTS idx_task_data_fill_user ON rpt_task_data(fill_user_id);
CREATE INDEX IF NOT EXISTS idx_task_data_dept_id ON rpt_task_data(dept_id);
CREATE INDEX IF NOT EXISTS idx_data_apply_task_id ON rpt_data_apply(task_id);
CREATE INDEX IF NOT EXISTS idx_data_apply_applicant ON rpt_data_apply(applicant_id);
CREATE INDEX IF NOT EXISTS idx_file_task_id ON rpt_file(task_id);
CREATE INDEX IF NOT EXISTS idx_file_md5 ON rpt_file(md5);

-- 注意：rpt_task_process（流转轨迹表）由业务侧首次写入时创建，不在本 schema 内，
-- 因此这里不在 schema.sql 里为它建索引；生产库请用下面的语句手动补：
--   CREATE INDEX idx_task_process_task_id ON rpt_task_process(task_id);
