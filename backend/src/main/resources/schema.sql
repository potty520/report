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
