
-- ============================================================
-- 补齐缺失表（审计：schema.sql 只建了 13 张，以下表代码在用但未定义）
--
-- 这些表缺失时，相关写操作会被 catch(Exception ignored) 吞掉，
-- 表现为「功能静默失效」（流转轨迹丢失、定时任务无记录、在线会话不生效）。
-- 以下 DDL 由生产库 SHOW CREATE TABLE 导出，已去除 AUTO_INCREMENT 当前值。
-- ============================================================

-- rpt_task_process
CREATE TABLE `rpt_task_process` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` bigint NOT NULL,
  `data_id` bigint DEFAULT NULL,
  `node_code` varchar(64) NOT NULL COMMENT '节点编码 SUBMIT/DEPT_AUDIT/TASK_AUDIT/CORRECT/INBOUND',
  `node_name` varchar(64) DEFAULT '',
  `action` varchar(32) DEFAULT '' COMMENT 'PASS/REJECT/SUBMIT/BACK/CORRECT_START/CORRECT_CLOSE/INBOUND',
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(32) DEFAULT '',
  `comment` varchar(512) DEFAULT '',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_process_task` (`task_id`)
);

-- rpt_correct_task
CREATE TABLE `rpt_correct_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` bigint NOT NULL,
  `data_ids` varchar(1000) DEFAULT '' COMMENT '需要补正的数据ID列表',
  `start_user_id` bigint DEFAULT NULL COMMENT '发起人（任务方）',
  `start_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `close_user_id` bigint DEFAULT NULL,
  `close_time` datetime DEFAULT NULL,
  `status` tinyint DEFAULT '1' COMMENT '1进行中 2已关闭',
  `comment` varchar(512) DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `idx_correct_task` (`task_id`)
);

-- rpt_schedule_log
CREATE TABLE `rpt_schedule_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `job_name` varchar(64) NOT NULL,
  `batch` varchar(32) DEFAULT '',
  `created_count` int DEFAULT '0',
  `detail` varchar(2000) DEFAULT '',
  `status` tinyint DEFAULT '1',
  `cost_ms` bigint DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

-- rpt_oper_log
CREATE TABLE `rpt_oper_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `username` varchar(32) DEFAULT '',
  `module` varchar(64) DEFAULT '',
  `action` varchar(64) DEFAULT '',
  `target` varchar(128) DEFAULT '',
  `detail` varchar(2000) DEFAULT '',
  `ip` varchar(64) DEFAULT '',
  `cost_ms` bigint DEFAULT '0',
  `status` tinyint DEFAULT '1' COMMENT '1成功 0失败',
  `error_msg` varchar(1000) DEFAULT '',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_log_user` (`user_id`),
  KEY `idx_log_time` (`create_time`),
  KEY `idx_log_module` (`module`),
  KEY `idx_log_action` (`action`)
);

-- rpt_quality_report
CREATE TABLE `rpt_quality_report` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_data_id` bigint NOT NULL COMMENT '填报数据ID',
  `task_id` bigint DEFAULT NULL,
  `template_id` bigint DEFAULT NULL,
  `provider` varchar(32) DEFAULT NULL COMMENT 'none/http',
  `status` varchar(16) DEFAULT 'PENDING' COMMENT 'NONE/PENDING/PASS/FAIL',
  `report_id` varchar(128) DEFAULT NULL,
  `report_url` varchar(500) DEFAULT NULL,
  `message` varchar(500) DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_quality_task_data` (`task_data_id`)
);

-- rpt_sms_log
CREATE TABLE `rpt_sms_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL COMMENT '接收人',
  `task_id` bigint DEFAULT NULL COMMENT '关联任务',
  `biz_type` varchar(16) DEFAULT NULL COMMENT 'TODO/OVERDUE/CORRECT',
  `phone` varchar(32) DEFAULT NULL,
  `template_code` varchar(64) DEFAULT NULL,
  `provider` varchar(32) DEFAULT NULL COMMENT 'log/chunlan',
  `success` tinyint DEFAULT '0',
  `message_id` varchar(64) DEFAULT NULL,
  `message` varchar(500) DEFAULT NULL,
  `content` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sms_log_task` (`task_id`),
  KEY `idx_sms_log_user` (`user_id`)
);

-- rpt_volume_file
CREATE TABLE `rpt_volume_file` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `group_key` varchar(128) NOT NULL COMMENT '分卷组标识',
  `volume_index` int NOT NULL DEFAULT '0' COMMENT '卷号（从 0 开始）',
  `file_id` bigint NOT NULL COMMENT 'rpt_file.id',
  `task_id` bigint DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_volume_group_index` (`group_key`,`volume_index`),
  KEY `idx_volume_task` (`task_id`)
);

-- rpt_statistics_rule
CREATE TABLE `rpt_statistics_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `rule_code` varchar(64) NOT NULL COMMENT '规则编码',
  `rule_name` varchar(128) DEFAULT NULL,
  `grace_days` int DEFAULT '0' COMMENT '超期宽限天数',
  `remind_days` int DEFAULT '0' COMMENT '提前提醒天数',
  `enabled` tinyint DEFAULT '1',
  `remark` varchar(500) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_statistics_rule_code` (`rule_code`)
);

-- rpt_template_auth
CREATE TABLE `rpt_template_auth` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `template_id` bigint NOT NULL COMMENT '模板ID',
  `auth_type` varchar(16) NOT NULL COMMENT 'DEPT=部门 USER=用户',
  `target_id` bigint NOT NULL COMMENT '部门ID或用户ID',
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_auth` (`template_id`,`auth_type`,`target_id`),
  KEY `idx_template_auth_target` (`auth_type`,`target_id`)
);

-- rpt_online_user
CREATE TABLE `rpt_online_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `username` varchar(64) DEFAULT NULL,
  `session_id` varchar(64) DEFAULT NULL COMMENT 'JWT jti',
  `ip` varchar(64) DEFAULT NULL,
  `user_agent` varchar(300) DEFAULT NULL,
  `login_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `last_active_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `status` tinyint DEFAULT '1' COMMENT '1=在线 0=已下线/被踢',
  `offline_reason` varchar(200) DEFAULT NULL,
  `offline_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_online_user` (`user_id`,`status`),
  KEY `idx_online_session` (`session_id`)
);

-- 定时任务跨实例互斥锁（ScheduleLockService 使用）
CREATE TABLE IF NOT EXISTS rpt_schedule_lock (
    job_name   VARCHAR(64) NOT NULL,
    instance   VARCHAR(128) DEFAULT '',
    locked_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (job_name)
);