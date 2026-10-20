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
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 物化状态：0未物化 1已建表
    materialized TINYINT DEFAULT 0,
    materialized_time TIMESTAMP
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
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 双通道审核与补正相关（原先只在生产库有，H2/纯净库缺列会导致代码报错）
    dept_audit_status TINYINT DEFAULT 0,
    dept_audit_user_id BIGINT,
    dept_audit_time TIMESTAMP,
    task_audit_status TINYINT DEFAULT 0,
    task_audit_user_id BIGINT,
    task_audit_time TIMESTAMP,
    correct_status TINYINT DEFAULT 0,
    correct_way TINYINT,
    -- 周期任务：子任务指向父任务，generate_batch 记录生成批次
    parent_task_id BIGINT,
    generate_batch VARCHAR(32) DEFAULT ''
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
    -- 入库（物化表写入）状态：0未入库 1成功 2失败
    inbound_status TINYINT DEFAULT 0,
    inbound_time TIMESTAMP,
    inbound_rows INT DEFAULT 0,
    error_msg VARCHAR(1000) DEFAULT '',
    -- 双通道审核（部门 / 任务方）
    dept_audit_status TINYINT DEFAULT 0,
    dept_audit_user_id BIGINT,
    dept_audit_time TIMESTAMP,
    dept_audit_comment VARCHAR(512) DEFAULT '',
    task_audit_status TINYINT DEFAULT 0,
    task_audit_user_id BIGINT,
    task_audit_time TIMESTAMP,
    task_audit_comment VARCHAR(512) DEFAULT '',
    -- 补正
    correct_status TINYINT DEFAULT 0,
    correct_way TINYINT,
    correct_comment VARCHAR(512) DEFAULT '',
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

-- ============================================================
-- 业务唯一约束（审计：严重级）
--
-- 背景：这三处缺失导致
--   1. 同一人同一任务可产生多条填报数据 → 后续 selectOne 抛 TooManyResults，
--      该用户该任务从此无法保存/提交/审核（永久锁死）；
--   2. 可重复分派同一人 → "我的任务"重复、统计虚高；
--   3. 周期任务可重复生成 → 多实例部署时每实例各生成一遍。
--
-- 落地顺序：必须先确认存量无重复数据，再加唯一约束，否则 DDL 直接失败。
--   体检 SQL：
--     SELECT task_id, fill_user_id, COUNT(*) FROM rpt_task_data GROUP BY 1,2 HAVING COUNT(*)>1;
--     SELECT task_id, assign_type, user_id, COUNT(*) FROM rpt_task_assign GROUP BY 1,2,3 HAVING COUNT(*)>1;
--     SELECT parent_task_id, belong_time, dept_id, COUNT(*) FROM rpt_task
--       WHERE parent_task_id IS NOT NULL GROUP BY 1,2,3 HAVING COUNT(*)>1;
-- ============================================================
CREATE UNIQUE INDEX IF NOT EXISTS uk_task_data_user ON rpt_task_data(task_id, fill_user_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_task_assign ON rpt_task_assign(task_id, assign_type, user_id);
-- 注意：MySQL 唯一索引允许多个 NULL，普通任务（parent_task_id 为 NULL）不受影响
CREATE UNIQUE INDEX IF NOT EXISTS uk_periodic ON rpt_task(parent_task_id, belong_time, dept_id);

-- ============================================================
-- >>> AUTO-GENERATED: MISSING TABLES
-- 来源：生产库 SHOW CREATE TABLE（MySQL），已转成 H2/MySQL 双兼容语法
-- 解决：原 schema.sql 只建 13 张表，代码读写的下列表未定义，
--       缺失时写操作被 catch(Exception ignored) 吞掉 → 功能静默失效
-- ============================================================
CREATE TABLE IF NOT EXISTS rpt_task_process (
  id bigint NOT NULL AUTO_INCREMENT,
  task_id bigint NOT NULL,
  data_id bigint DEFAULT NULL,
  node_code varchar(64) NOT NULL,
  node_name varchar(64) DEFAULT '',
  action varchar(32) DEFAULT '',
  operator_id bigint DEFAULT NULL,
  operator_name varchar(32) DEFAULT '',
  comment varchar(512) DEFAULT '',
  create_time datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_process_task ON rpt_task_process(task_id);

CREATE TABLE IF NOT EXISTS rpt_correct_task (
  id bigint NOT NULL AUTO_INCREMENT,
  task_id bigint NOT NULL,
  data_ids varchar(1000) DEFAULT '',
  start_user_id bigint DEFAULT NULL,
  start_time datetime DEFAULT CURRENT_TIMESTAMP,
  close_user_id bigint DEFAULT NULL,
  close_time datetime DEFAULT NULL,
  status tinyint DEFAULT '1',
  comment varchar(512) DEFAULT '',
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_correct_task ON rpt_correct_task(task_id);

CREATE TABLE IF NOT EXISTS rpt_schedule_log (
  id bigint NOT NULL AUTO_INCREMENT,
  job_name varchar(64) NOT NULL,
  batch varchar(32) DEFAULT '',
  created_count int DEFAULT '0',
  detail varchar(2000) DEFAULT '',
  status tinyint DEFAULT '1',
  cost_ms bigint DEFAULT '0',
  create_time datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS rpt_oper_log (
  id bigint NOT NULL AUTO_INCREMENT,
  user_id bigint DEFAULT NULL,
  username varchar(32) DEFAULT '',
  module varchar(64) DEFAULT '',
  action varchar(64) DEFAULT '',
  target varchar(128) DEFAULT '',
  detail varchar(2000) DEFAULT '',
  ip varchar(64) DEFAULT '',
  cost_ms bigint DEFAULT '0',
  status tinyint DEFAULT '1',
  error_msg varchar(1000) DEFAULT '',
  create_time datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_log_user ON rpt_oper_log(user_id);
CREATE INDEX IF NOT EXISTS idx_log_time ON rpt_oper_log(create_time);
CREATE INDEX IF NOT EXISTS idx_log_module ON rpt_oper_log(module);
CREATE INDEX IF NOT EXISTS idx_log_action ON rpt_oper_log(action);

CREATE TABLE IF NOT EXISTS rpt_quality_report (
  id bigint NOT NULL AUTO_INCREMENT,
  task_data_id bigint NOT NULL,
  task_id bigint DEFAULT NULL,
  template_id bigint DEFAULT NULL,
  provider varchar(32) DEFAULT NULL,
  status varchar(16) DEFAULT 'PENDING',
  report_id varchar(128) DEFAULT NULL,
  report_url varchar(500) DEFAULT NULL,
  message varchar(500) DEFAULT NULL,
  create_by bigint DEFAULT NULL,
  create_time datetime DEFAULT CURRENT_TIMESTAMP,
  update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS uk_quality_task_data ON rpt_quality_report(task_data_id);

CREATE TABLE IF NOT EXISTS rpt_sms_log (
  id bigint NOT NULL AUTO_INCREMENT,
  user_id bigint DEFAULT NULL,
  task_id bigint DEFAULT NULL,
  biz_type varchar(16) DEFAULT NULL,
  phone varchar(32) DEFAULT NULL,
  template_code varchar(64) DEFAULT NULL,
  provider varchar(32) DEFAULT NULL,
  success tinyint DEFAULT '0',
  message_id varchar(64) DEFAULT NULL,
  message varchar(500) DEFAULT NULL,
  content varchar(500) DEFAULT NULL,
  create_time datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_sms_log_task ON rpt_sms_log(task_id);
CREATE INDEX IF NOT EXISTS idx_sms_log_user ON rpt_sms_log(user_id);

CREATE TABLE IF NOT EXISTS rpt_volume_file (
  id bigint NOT NULL AUTO_INCREMENT,
  group_key varchar(128) NOT NULL,
  volume_index int NOT NULL DEFAULT '0',
  file_id bigint NOT NULL,
  task_id bigint DEFAULT NULL,
  create_by bigint DEFAULT NULL,
  create_time datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS uk_volume_group_index ON rpt_volume_file(group_key,volume_index);
CREATE INDEX IF NOT EXISTS idx_volume_task ON rpt_volume_file(task_id);

CREATE TABLE IF NOT EXISTS rpt_statistics_rule (
  id bigint NOT NULL AUTO_INCREMENT,
  rule_code varchar(64) NOT NULL,
  rule_name varchar(128) DEFAULT NULL,
  grace_days int DEFAULT '0',
  remind_days int DEFAULT '0',
  enabled tinyint DEFAULT '1',
  remark varchar(500) DEFAULT NULL,
  create_time datetime DEFAULT CURRENT_TIMESTAMP,
  update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS uk_statistics_rule_code ON rpt_statistics_rule(rule_code);

CREATE TABLE IF NOT EXISTS rpt_template_auth (
  id bigint NOT NULL AUTO_INCREMENT,
  template_id bigint NOT NULL,
  auth_type varchar(16) NOT NULL,
  target_id bigint NOT NULL,
  create_by bigint DEFAULT NULL,
  create_time datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS uk_template_auth ON rpt_template_auth(template_id,auth_type,target_id);
CREATE INDEX IF NOT EXISTS idx_template_auth_target ON rpt_template_auth(auth_type,target_id);

CREATE TABLE IF NOT EXISTS rpt_online_user (
  id bigint NOT NULL AUTO_INCREMENT,
  user_id bigint NOT NULL,
  username varchar(64) DEFAULT NULL,
  session_id varchar(64) DEFAULT NULL,
  ip varchar(64) DEFAULT NULL,
  user_agent varchar(300) DEFAULT NULL,
  login_time datetime DEFAULT CURRENT_TIMESTAMP,
  last_active_time datetime DEFAULT CURRENT_TIMESTAMP,
  status tinyint DEFAULT '1',
  offline_reason varchar(200) DEFAULT NULL,
  offline_time datetime DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_online_user ON rpt_online_user(user_id,status);
CREATE INDEX IF NOT EXISTS idx_online_session ON rpt_online_user(session_id);

-- 定时任务跨实例互斥锁（ScheduleLockService 使用）
-- 唯一键 job_name 用于 INSERT 抢占式加锁；locked_at 用于判定崩溃残留锁（默认 30 分钟超时）
CREATE TABLE IF NOT EXISTS rpt_schedule_lock (
  job_name  VARCHAR(64) NOT NULL,
  instance  VARCHAR(128) DEFAULT '',
  locked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (job_name)
);
