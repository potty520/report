-- ============================================================================
-- 填报系统 2.0 升级脚本（第二批：P0-C 周期任务 + 双通道审核 + 补正）
-- 目标：MySQL 8.0（系统库 report_system）
-- ============================================================================

-- 一、任务：主子任务关系（原版 report_host_task -> report_task）
ALTER TABLE rpt_task
  ADD COLUMN parent_task_id BIGINT NULL COMMENT '父（主）任务ID，NULL 表示主任务',
  ADD COLUMN generate_batch VARCHAR(32) DEFAULT '' COMMENT '自动生成批次（属期）',
  ADD KEY idx_task_parent (parent_task_id);

-- 二、填报数据：双通道审核（部门审核 + 任务方审核）与补正状态
ALTER TABLE rpt_task_data
  ADD COLUMN dept_audit_status TINYINT DEFAULT 0 COMMENT '部门审核 0待审 1通过 2驳回',
  ADD COLUMN dept_audit_user_id BIGINT NULL,
  ADD COLUMN dept_audit_time DATETIME NULL,
  ADD COLUMN dept_audit_comment VARCHAR(512) DEFAULT '',
  ADD COLUMN task_audit_status TINYINT DEFAULT 0 COMMENT '任务方审核 0待审 1通过 2驳回',
  ADD COLUMN task_audit_user_id BIGINT NULL,
  ADD COLUMN task_audit_time DATETIME NULL,
  ADD COLUMN task_audit_comment VARCHAR(512) DEFAULT '',
  ADD COLUMN correct_status TINYINT DEFAULT 0 COMMENT '补正 0无 1进行中 2已完成',
  ADD COLUMN correct_way TINYINT NULL COMMENT '补正方式 1在线 2导入',
  ADD COLUMN correct_comment VARCHAR(512) DEFAULT '';

-- 三、补正批次（原版的双向补正流程：任务方发起 -> 部门补正 -> 关闭）
CREATE TABLE IF NOT EXISTS rpt_correct_task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_id BIGINT NOT NULL,
  data_ids VARCHAR(1000) DEFAULT '' COMMENT '需要补正的数据ID列表',
  start_user_id BIGINT NULL COMMENT '发起人（任务方）',
  start_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  close_user_id BIGINT NULL,
  close_time DATETIME NULL,
  status TINYINT DEFAULT 1 COMMENT '1进行中 2已关闭',
  comment VARCHAR(512) DEFAULT '',
  KEY idx_correct_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='补正批次';

-- 四、调度任务执行记录（便于核对定时生成结果）
CREATE TABLE IF NOT EXISTS rpt_schedule_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  job_name VARCHAR(64) NOT NULL,
  batch VARCHAR(32) DEFAULT '',
  created_count INT DEFAULT 0,
  detail VARCHAR(2000) DEFAULT '',
  status TINYINT DEFAULT 1,
  cost_ms BIGINT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调度任务执行记录';
