-- ---------------------------------------------------------------------------
-- 后端能力补齐（第 2 批）：模板两级授权
-- 对应接口：/template/{id}/auths（查询/授权/撤销/校验）
-- 幂等，可重复执行。
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS rpt_template_auth (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  template_id BIGINT       NOT NULL COMMENT '模板ID',
  auth_type   VARCHAR(16)  NOT NULL COMMENT 'DEPT=部门 USER=用户',
  target_id   BIGINT       NOT NULL COMMENT '部门ID或用户ID',
  create_by   BIGINT       DEFAULT NULL,
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_template_auth (template_id, auth_type, target_id),
  KEY idx_template_auth_target (auth_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模板两级授权（部门/用户）';

-- 语义说明：
--   模板一旦存在授权记录 → 变为「仅授权可见」：仅命中 DEPT(该用户所属部门) 或 USER(该用户) 的可见；
--   没有任何授权记录的模板 → 对所有具备模板权限的角色开放（向后兼容，不改变现有行为）；
--   超级管理员不受限制。
--
-- 校验
-- SELECT * FROM rpt_template_auth ORDER BY template_id, auth_type, id;
-- 授权示例（把模板 2 授权给部门 1 和用户 2）：
-- INSERT IGNORE INTO rpt_template_auth (template_id, auth_type, target_id, create_by)
-- VALUES (2, 'DEPT', 1, 1), (2, 'USER', 2, 1);

-- ---------------------------------------------------------------------------
-- 短信催办日志（可插拔通道；REPORT_SMS_ENABLED=false 时记录仿真发送结果）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rpt_sms_log (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  user_id       BIGINT       DEFAULT NULL COMMENT '接收人',
  task_id       BIGINT       DEFAULT NULL COMMENT '关联任务',
  biz_type      VARCHAR(16)  DEFAULT NULL COMMENT 'TODO/OVERDUE/CORRECT',
  phone         VARCHAR(32)  DEFAULT NULL,
  template_code VARCHAR(64)  DEFAULT NULL,
  provider      VARCHAR(32)  DEFAULT NULL COMMENT 'log/chunlan',
  success       TINYINT      DEFAULT 0,
  message_id    VARCHAR(64)  DEFAULT NULL,
  message       VARCHAR(500) DEFAULT NULL,
  content       VARCHAR(500) DEFAULT NULL,
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_sms_log_task (task_id),
  KEY idx_sms_log_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短信发送日志（含仿真）';

-- ---------------------------------------------------------------------------
-- 外部质检报告记录（可插拔通道；provider=none 时记录 PENDING）
-- 开启 REPORT_QUALITY_REQUIRED=true 后：状态非 PASS 的填报数据不允许任务方审核
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rpt_quality_report (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  task_data_id BIGINT       NOT NULL COMMENT '填报数据ID',
  task_id      BIGINT       DEFAULT NULL,
  template_id  BIGINT       DEFAULT NULL,
  provider     VARCHAR(32)  DEFAULT NULL COMMENT 'none/http',
  status       VARCHAR(16)  DEFAULT 'PENDING' COMMENT 'NONE/PENDING/PASS/FAIL',
  report_id    VARCHAR(128) DEFAULT NULL,
  report_url   VARCHAR(500) DEFAULT NULL,
  message      VARCHAR(500) DEFAULT NULL,
  create_by    BIGINT       DEFAULT NULL,
  create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_quality_task_data (task_data_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部质检报告记录';

-- 校验
-- SELECT * FROM rpt_sms_log ORDER BY id DESC LIMIT 10;
-- SELECT * FROM rpt_quality_report ORDER BY id DESC LIMIT 10;
