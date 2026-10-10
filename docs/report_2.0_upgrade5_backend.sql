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

-- ---------------------------------------------------------------------------
-- 统计/提醒规则配置（对应原版 statistics_rule_config）
--   grace_days  超期宽限天数（默认 0 = 与原行为一致）
--   remind_days 提前提醒天数（默认 0 = 不提前提醒）
--   enabled     是否启用；未启用时业务按默认值处理
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rpt_statistics_rule (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  rule_code   VARCHAR(64)  NOT NULL COMMENT '规则编码',
  rule_name   VARCHAR(128) DEFAULT NULL,
  grace_days  INT          DEFAULT 0 COMMENT '超期宽限天数',
  remind_days INT          DEFAULT 0 COMMENT '提前提醒天数',
  enabled     TINYINT      DEFAULT 1,
  remark      VARCHAR(500) DEFAULT NULL,
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_statistics_rule_code (rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统计/提醒规则配置';

-- 默认规则：grace_days=0 保证与配置前行为一致（不改变任何统计口径）
INSERT IGNORE INTO rpt_statistics_rule (rule_code, rule_name, grace_days, remind_days, enabled, remark)
VALUES ('OVERDUE_REMIND', '超期提醒', 0, 0, 1, '默认宽限 0 天，与原行为一致；把 grace_days 调大即延后判定超期'),
       ('TODO_REMIND', '待办提醒', 0, 0, 0, 'remind_days>0 时到期前 N 天提醒（当前未启用）'),
       ('CORRECT_REMIND', '补正提醒', 0, 0, 0, '补正流程提醒（当前未启用）');

-- 校验
-- SELECT rule_code, grace_days, remind_days, enabled FROM rpt_statistics_rule;
-- 生效值接口：GET /api/statistics-rule/effective

-- ---------------------------------------------------------------------------
-- 分卷附件（对应原版 report_volume_file / 分卷与非分卷附件）
--   每个卷本身仍存 rpt_file（含秒传/去重），本表只记录「卷号 → 文件」归属，
--   成组下载时按 volume_index 顺序打包为 zip。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rpt_volume_file (
  id           BIGINT   NOT NULL AUTO_INCREMENT,
  group_key    VARCHAR(128) NOT NULL COMMENT '分卷组标识',
  volume_index INT      NOT NULL DEFAULT 0 COMMENT '卷号（从 0 开始）',
  file_id      BIGINT   NOT NULL COMMENT 'rpt_file.id',
  task_id      BIGINT   DEFAULT NULL,
  create_by    BIGINT   DEFAULT NULL,
  create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_volume_group_index (group_key, volume_index),
  KEY idx_volume_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分卷附件归属（多卷成组）';

-- 校验
-- SELECT group_key, volume_index, file_id, task_id FROM rpt_volume_file ORDER BY group_key, volume_index;
-- 列表接口：GET /api/file/volume/list?taskId=1
-- 打包下载：GET /api/file/volume/{groupKey}/download

-- ---------------------------------------------------------------------------
-- 在线用户与异地登录检测（对应原版 online_user_info_t / 踢下线）
--   session_id 取自 JWT 的 jti；status=0 表示被管理员踢下线，
--   JwtAuthFilter 会对该会话直接返回 HTTP 401。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rpt_online_user (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  user_id          BIGINT       NOT NULL,
  username         VARCHAR(64)  DEFAULT NULL,
  session_id       VARCHAR(64)  DEFAULT NULL COMMENT 'JWT jti',
  ip               VARCHAR(64)  DEFAULT NULL,
  user_agent       VARCHAR(300) DEFAULT NULL,
  login_time       DATETIME     DEFAULT CURRENT_TIMESTAMP,
  last_active_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
  status           TINYINT      DEFAULT 1 COMMENT '1=在线 0=已下线/被踢',
  offline_reason   VARCHAR(200) DEFAULT NULL,
  offline_time     DATETIME     DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_online_user (user_id, status),
  KEY idx_online_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='在线用户会话';

-- 校验
-- SELECT user_id, username, ip, login_time, status FROM rpt_online_user WHERE status = 1 ORDER BY login_time DESC;
-- 列表接口：GET /api/online/list ；踢下线：POST /api/online/kick?sessionId=xxx
