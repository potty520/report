-- ============================================================================
-- 填报系统 2.1 功能补齐（对应《缺失功能需求规格说明书》R-01 ~ R-15）
-- 全部为向后兼容的新增：ADD COLUMN 带默认值 / CREATE TABLE IF NOT EXISTS
-- 适用于 MySQL 5.7 / 8.0
-- ============================================================================

DROP PROCEDURE IF EXISTS rpt_add_col;
DELIMITER $$
CREATE PROCEDURE rpt_add_col(IN tbl VARCHAR(64), IN col VARCHAR(64), IN ddl VARCHAR(500))
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = tbl AND column_name = col) THEN
    SET @s = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN ', ddl);
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
  END IF;
END$$
DELIMITER ;

-- R-02 无更新提交
CALL rpt_add_col('rpt_task_data', 'no_update',        'no_update TINYINT DEFAULT 0 COMMENT ''0否 1是（本期无更新）'' AFTER status');
CALL rpt_add_col('rpt_task_data', 'no_update_reason', 'no_update_reason VARCHAR(512) DEFAULT NULL COMMENT ''无更新说明'' AFTER no_update');

-- R-03 / R-04 / R-05 任务退回
CALL rpt_add_col('rpt_task', 'return_status', 'return_status TINYINT DEFAULT 0 COMMENT ''0正常 1已退回制定人 2已退回分派人'' AFTER status');
CALL rpt_add_col('rpt_task', 'return_reason', 'return_reason VARCHAR(512) DEFAULT NULL COMMENT ''退回原因'' AFTER return_status');
CALL rpt_add_col('rpt_task', 'return_by',     'return_by BIGINT DEFAULT NULL AFTER return_reason');
CALL rpt_add_col('rpt_task', 'return_time',   'return_time DATETIME DEFAULT NULL AFTER return_by');

-- R-06 质量报告结论
CALL rpt_add_col('rpt_quality_report', 'check_result', 'check_result VARCHAR(16) DEFAULT NULL COMMENT ''PASS 合格 / FAIL 不合格'' AFTER status');

-- R-07 字段级附件
CALL rpt_add_col('rpt_template_field', 'field_kind', 'field_kind TINYINT DEFAULT 0 COMMENT ''0普通字段 1附件字段'' AFTER field_type');

-- R-08 物化类型
CALL rpt_add_col('rpt_template', 'materialize_type', 'materialize_type TINYINT DEFAULT 1 COMMENT ''1新增表 2关联已有表'' AFTER materialized');

-- R-10 填报部门标记
CALL rpt_add_col('sys_dept', 'is_fill_dept', 'is_fill_dept TINYINT DEFAULT 0 COMMENT ''是否填报部门 0否 1是'' AFTER status');

DROP PROCEDURE IF EXISTS rpt_add_col;

-- R-03/R-04/R-05 退回记录
CREATE TABLE IF NOT EXISTS rpt_task_return (
  id           BIGINT NOT NULL AUTO_INCREMENT,
  task_id      BIGINT NOT NULL COMMENT '任务ID',
  return_type  TINYINT NOT NULL COMMENT '1分派退回 2填报退回 3部门退回',
  from_user_id BIGINT DEFAULT NULL,
  to_user_id   BIGINT DEFAULT NULL,
  from_dept_id BIGINT DEFAULT NULL,
  to_dept_id   BIGINT DEFAULT NULL,
  reason       VARCHAR(512) DEFAULT NULL COMMENT '退回原因',
  status       TINYINT DEFAULT 0 COMMENT '0待处理 1已处理',
  handle_type  TINYINT DEFAULT NULL COMMENT '1撤销 2变更部门 3重新提交 4重新分派',
  handle_by    BIGINT DEFAULT NULL,
  handle_time  DATETIME DEFAULT NULL,
  create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_task (task_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务退回记录';

-- R-01 模板展示信息配置
CREATE TABLE IF NOT EXISTS rpt_template_display_config (
  id                BIGINT NOT NULL AUTO_INCREMENT,
  template_id       BIGINT NOT NULL COMMENT '模板ID',
  config_type       TINYINT NOT NULL DEFAULT 0 COMMENT '0无 1关联模板 2关联表',
  ref_template_id   BIGINT DEFAULT NULL COMMENT '关联模板ID(config_type=1)',
  ref_datasource_id BIGINT DEFAULT NULL COMMENT '数据源ID(config_type=2)',
  ref_table_name    VARCHAR(64) DEFAULT NULL COMMENT '关联表名(config_type=2)',
  ref_columns       TEXT COMMENT '展示信息项 JSON 数组',
  link_checked      TINYINT DEFAULT 0 COMMENT '0未检测 1通过 2失败',
  link_check_time   DATETIME DEFAULT NULL,
  create_by         BIGINT DEFAULT NULL,
  create_time       DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_template (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模板展示信息配置';

-- R-09 导入校验记录
CREATE TABLE IF NOT EXISTS rpt_import_check (
  id           BIGINT NOT NULL AUTO_INCREMENT,
  task_data_id BIGINT DEFAULT NULL COMMENT '填报数据ID',
  task_id      BIGINT DEFAULT NULL,
  check_stage  TINYINT NOT NULL COMMENT '1报表核验 2导入预校验',
  total_rows   INT DEFAULT 0,
  pass_rows    INT DEFAULT 0,
  fail_rows    INT DEFAULT 0,
  passed       TINYINT DEFAULT 0 COMMENT '0未通过 1通过',
  detail_json  TEXT COMMENT '错误明细 JSON',
  create_by    BIGINT DEFAULT NULL,
  create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导入校验记录';

-- R-14 补正明细
CREATE TABLE IF NOT EXISTS rpt_correct_item (
  id           BIGINT NOT NULL AUTO_INCREMENT,
  correct_id   BIGINT NOT NULL COMMENT '补正单ID',
  task_id      BIGINT DEFAULT NULL,
  task_data_id BIGINT DEFAULT NULL,
  op_type      TINYINT NOT NULL COMMENT '1新增 2修改 3删除',
  row_key      VARCHAR(128) DEFAULT NULL COMMENT '明细行标识',
  before_json  TEXT COMMENT '原值 JSON',
  after_json   TEXT COMMENT '新值 JSON',
  create_by    BIGINT DEFAULT NULL,
  create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_correct (correct_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='补正明细项';

-- R-10 存量回填：凡已被分派过的部门，视为填报部门
UPDATE sys_dept d
SET d.is_fill_dept = 1
WHERE EXISTS (SELECT 1 FROM rpt_task_assign a WHERE a.dept_id = d.id)
  AND d.is_fill_dept = 0;

-- R-21 周期字典补齐「一次性」（原系统字典只有 5 项，代码支持 6=一次性）
INSERT INTO sys_dict (dict_type, dict_code, dict_label, sort, status)
SELECT 'filling_cycle', '6', '一次性', 6, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_type='filling_cycle' AND dict_code='6');
