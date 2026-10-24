-- ============================================================================
-- 历史时间数据修复（一次性，执行前务必先备份）
--
-- 背景：
--   修复前 JVM 默认时区与 MySQL 会话时区都是 UTC，所有时间列落库值比真实北京时间早 8 小时。
--   修复后（ReportApplication 设置 JVM=Asia/Shanghai，Druid 连接初始化 SET time_zone='+08:00'），
--   新数据是正确的 +08:00，历史数据需要整体 +8 小时才能对齐。
--
-- 用法：
--   1) 备份：  mysqldump -ureport -p report_system > report_system_$(date +%F).sql
--   2) 确认会话时区：  SELECT NOW(), @@session.time_zone;    -- 应为 +08:00 且与服务器 date 一致
--   3) 执行本文件的 SELECT，把生成的 UPDATE 语句复制出来
--   4) 抽查 2-3 条：先看 rpt_task_data 最近的 submit_time 是否比真实提交时间早 8 小时
--   5) 在事务里执行这些 UPDATE，提交后再抽查一次
--
-- 注意：
--   - 只处理 datetime / timestamp，不碰 date 与纯数字列；
--   - 若某个时间列本来就是按 UTC 语义存的（例如对接外部系统的字段），
--     请把它从生成结果里删掉，不要一刀切。
-- ============================================================================

SET @schema = DATABASE();

-- 1. 生成修复语句（先看，不执行）
SELECT CONCAT(
         'UPDATE `', TABLE_NAME, '` SET `', COLUMN_NAME,
         '` = DATE_ADD(`', COLUMN_NAME, '`, INTERVAL 8 HOUR) WHERE `', COLUMN_NAME, '` IS NOT NULL;'
       ) AS stmt
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @schema
  AND DATA_TYPE IN ('datetime', 'timestamp')
ORDER BY TABLE_NAME, ORDINAL_POSITION;

-- 2. 修复前后对照（建议在执行前后各跑一次）
-- SELECT id, task_id, status, submit_time, audit_time, inbound_time
--   FROM rpt_task_data ORDER BY id DESC LIMIT 5;
-- SELECT id, task_name, create_time, update_time FROM rpt_task ORDER BY id DESC LIMIT 5;
-- SELECT NOW() AS db_now;
