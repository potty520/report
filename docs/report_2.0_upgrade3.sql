-- ============================================================================
-- 填报系统 2.0 升级脚本（第三批：P1-F 站内消息/待办、P2-G 审计日志）
-- 目标：MySQL 8.0（系统库 report_system）
-- ============================================================================

-- 一、站内消息/待办（替代原版短信催办与待办统计）
CREATE TABLE IF NOT EXISTS sys_message (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL COMMENT '接收人',
  title VARCHAR(128) NOT NULL,
  content VARCHAR(1000) DEFAULT '',
  msg_type VARCHAR(16) DEFAULT 'TODO' COMMENT 'TODO待办/RETURN退回/OVERDUE超期/REMIND催办/SYSTEM系统',
  biz_task_id BIGINT NULL COMMENT '关联任务',
  data_id BIGINT NULL COMMENT '关联填报数据',
  read_flag TINYINT DEFAULT 0 COMMENT '0未读 1已读',
  read_time DATETIME NULL,
  create_by BIGINT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_msg_user (user_id, read_flag),
  KEY idx_msg_task (biz_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内消息与待办';

-- 二、审计日志补充索引（表在第一批已建：rpt_oper_log）
-- 便于按模块/操作人检索
ALTER TABLE rpt_oper_log
  ADD KEY idx_log_module (module),
  ADD KEY idx_log_action (action);
