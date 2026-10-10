-- ============================================================================
-- 填报系统 2.0 升级脚本（补齐原版能力：模板物化 / 填报入库 / 附件 / 轨迹 / 审计）
-- 目标：MySQL 8.0（系统库 report_system）
-- 执行前请确认：本脚本假定基础表已由 docs/database.sql 建立
-- ============================================================================

-- 一、模板：记录是否已物化到目标库
ALTER TABLE rpt_template
  ADD COLUMN materialized TINYINT DEFAULT 0 COMMENT '是否已物化到目标数据源',
  ADD COLUMN materialized_time DATETIME NULL COMMENT '物化时间';

-- 二、填报数据：记录落库状态
ALTER TABLE rpt_task_data
  ADD COLUMN inbound_status TINYINT DEFAULT 0 COMMENT '入库状态 0未入库 1成功 2失败',
  ADD COLUMN inbound_time DATETIME NULL COMMENT '入库时间',
  ADD COLUMN inbound_rows INT DEFAULT 0 COMMENT '入库行数',
  ADD COLUMN error_msg VARCHAR(512) DEFAULT '' COMMENT '执行/校验错误信息';

-- 三、任务：双通道审核与补正（P0-C 用，先建结构）
ALTER TABLE rpt_task
  ADD COLUMN dept_audit_status TINYINT DEFAULT 0 COMMENT '部门审核 0待审 1通过 2驳回',
  ADD COLUMN dept_audit_user_id BIGINT NULL,
  ADD COLUMN dept_audit_time DATETIME NULL,
  ADD COLUMN task_audit_status TINYINT DEFAULT 0 COMMENT '任务方审核 0待审 1通过 2驳回',
  ADD COLUMN task_audit_user_id BIGINT NULL,
  ADD COLUMN task_audit_time DATETIME NULL,
  ADD COLUMN correct_status TINYINT DEFAULT 0 COMMENT '补正 0无 1进行中 2已完成',
  ADD COLUMN correct_way TINYINT NULL COMMENT '补正方式 1在线 2导入';

-- 四、附件/文件（替代原版 FastDFS + 文档系统，落地本地磁盘）
CREATE TABLE IF NOT EXISTS rpt_file (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_id BIGINT NULL,
  data_id BIGINT NULL,
  template_id BIGINT NULL,
  file_name VARCHAR(255) NOT NULL,
  store_path VARCHAR(512) NOT NULL COMMENT '本地存储相对路径',
  file_size BIGINT DEFAULT 0,
  file_type VARCHAR(32) DEFAULT '' COMMENT 'REPORT/ATTACHMENT/PIC',
  content_type VARCHAR(128) DEFAULT '',
  md5 VARCHAR(64) DEFAULT '' COMMENT '秒传用',
  upload_by BIGINT NULL,
  deleted TINYINT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_file_task (task_id),
  KEY idx_file_md5 (md5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='附件与填报文件';

-- 五、分片上传（秒传/断点续传）
CREATE TABLE IF NOT EXISTS rpt_chunk_info (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  file_key VARCHAR(128) NOT NULL COMMENT '文件唯一标识（md5 或 业务key）',
  file_name VARCHAR(255) DEFAULT '',
  chunk_index INT NOT NULL,
  chunk_total INT NOT NULL,
  chunk_size BIGINT DEFAULT 0,
  store_path VARCHAR(512) DEFAULT '',
  upload_by BIGINT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_file_chunk (file_key, chunk_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分片上传记录';

-- 六、流程轨迹（替代原版 Activiti 的可见部分：状态流转记录）
CREATE TABLE IF NOT EXISTS rpt_task_process (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_id BIGINT NOT NULL,
  data_id BIGINT NULL,
  node_code VARCHAR(64) NOT NULL COMMENT '节点编码 SUBMIT/DEPT_AUDIT/TASK_AUDIT/CORRECT/INBOUND',
  node_name VARCHAR(64) DEFAULT '',
  action VARCHAR(32) DEFAULT '' COMMENT 'PASS/REJECT/SUBMIT/BACK/CORRECT_START/CORRECT_CLOSE/INBOUND',
  operator_id BIGINT NULL,
  operator_name VARCHAR(32) DEFAULT '',
  comment VARCHAR(512) DEFAULT '',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_process_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务/数据流转轨迹';

-- 七、操作审计日志（替代原版三套 AOP 日志的落库部分）
CREATE TABLE IF NOT EXISTS rpt_oper_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NULL,
  username VARCHAR(32) DEFAULT '',
  module VARCHAR(64) DEFAULT '',
  action VARCHAR(64) DEFAULT '',
  target VARCHAR(128) DEFAULT '',
  detail VARCHAR(2000) DEFAULT '',
  ip VARCHAR(64) DEFAULT '',
  cost_ms BIGINT DEFAULT 0,
  status TINYINT DEFAULT 1 COMMENT '1成功 0失败',
  error_msg VARCHAR(1000) DEFAULT '',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_log_user (user_id),
  KEY idx_log_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计日志';

-- 八、数据范围配置（原版 data_range_config：输入 SQL + 部门变量）
CREATE TABLE IF NOT EXISTS rpt_data_range_config (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  config_name VARCHAR(64) NOT NULL,
  template_id BIGINT NULL,
  sql_text TEXT COMMENT '数据范围 SQL，支持 = Para_DepID 变量',
  datasource_id BIGINT NULL,
  remark VARCHAR(256) DEFAULT '',
  status TINYINT DEFAULT 1,
  create_by BIGINT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据范围配置';

-- 九、字典（原版 sys_dict/sys_dictionary 的精简版）
CREATE TABLE IF NOT EXISTS sys_dict (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  dict_type VARCHAR(64) NOT NULL,
  dict_code VARCHAR(64) NOT NULL,
  dict_label VARCHAR(128) NOT NULL,
  sort INT DEFAULT 0,
  status TINYINT DEFAULT 1,
  remark VARCHAR(256) DEFAULT '',
  UNIQUE KEY uk_dict (dict_type, dict_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据字典';

INSERT IGNORE INTO sys_dict (dict_type, dict_code, dict_label, sort) VALUES
  ('filling_cycle', '1', '日报', 1),
  ('filling_cycle', '2', '周报', 2),
  ('filling_cycle', '3', '月报', 3),
  ('filling_cycle', '4', '季报', 4),
  ('filling_cycle', '5', '年报', 5),
  ('task_status', '0', '草稿', 1),
  ('task_status', '1', '已发布', 2),
  ('task_status', '2', '填报中', 3),
  ('task_status', '3', '已完成', 4),
  ('task_status', '4', '已关闭', 5),
  ('audit_status', '0', '待审核', 1),
  ('audit_status', '1', '已通过', 2),
  ('audit_status', '2', '已驳回', 3),
  ('data_source_type', 'MYSQL', 'MySQL', 1),
  ('data_source_type', 'ORACLE', 'Oracle', 2),
  ('data_source_type', 'POSTGRESQL', 'PostgreSQL', 3),
  ('data_source_type', 'CIRRODATA', 'CirroData', 4);
