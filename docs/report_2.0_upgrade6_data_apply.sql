-- ============================================================================
-- 数据申请：补充 title / data_scope 两列（幂等，可重复执行）
--
-- 背景：前端「数据申请」表单有「申请标题」「数据范围」，但 rpt_data_apply 只有 reason，
-- 提交接口又只认 taskId+reason，导致界面提交必然失败。补齐字段后前端可完整落库。
-- ============================================================================

SET @db := DATABASE();

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'rpt_data_apply' AND COLUMN_NAME = 'title') = 0,
  'ALTER TABLE rpt_data_apply ADD COLUMN title VARCHAR(128) DEFAULT '''' COMMENT ''申请标题'' AFTER task_id',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'rpt_data_apply' AND COLUMN_NAME = 'data_scope') = 0,
  'ALTER TABLE rpt_data_apply ADD COLUMN data_scope VARCHAR(256) DEFAULT '''' COMMENT ''数据范围说明'' AFTER title',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'rpt_data_apply' ORDER BY ORDINAL_POSITION;
