-- ============================================================================
-- 上线前核对：填报周期（filling_cycle）历史数据
--
-- 背景：
--   旧前端模板向导提交的是 1月度 / 2季度 / 3半年 / 4年度 / 9一次性，
--   而后端一直按 1日报 / 2周报 / 3月报 / 4季报 / 5年报 计算属期与截止日。
--   也就是说：界面上选「月度」的模板，实际一直按「日报」在跑。
--
--   本次修改统一了口径（1日报 2周报 3月报 4季报 5年报 6一次性），
--   但**不会**自动改写历史数据——历史值的语义要靠业务确认。
--
-- 本脚本只读，用来找出需要人工确认的记录。
-- ============================================================================

-- 1. 分布：先看有哪些取值
SELECT 'rpt_template' AS tbl, filling_cycle, COUNT(*) AS cnt
FROM rpt_template GROUP BY filling_cycle
UNION ALL
SELECT 'rpt_task', filling_cycle, COUNT(*)
FROM rpt_task GROUP BY filling_cycle
ORDER BY tbl, filling_cycle;

-- 2. 非法/历史取值（新口径只允许 1-6；出现 9 说明是旧界面「一次性」）
SELECT '模板' AS obj, id, template_name AS name, filling_cycle, create_time
FROM rpt_template WHERE filling_cycle IS NULL OR filling_cycle < 1 OR filling_cycle > 6
UNION ALL
SELECT '任务', id, task_name, filling_cycle, create_time
FROM rpt_task WHERE filling_cycle IS NULL OR filling_cycle < 1 OR filling_cycle > 6;

-- 3. 需要业务确认的模板（1/2/4/6 这几个值在旧界面和新口径下含义不同）
SELECT id, template_name, template_code, filling_cycle, status, create_time,
       CASE filling_cycle
         WHEN 1 THEN '旧界面=月度；新口径=日报 —— 若业务要月报，改成 3'
         WHEN 2 THEN '旧界面=季度；新口径=周报 —— 若要季报，改成 4'
         WHEN 3 THEN '旧界面=半年；新口径=月报 —— 半年无对应口径，需业务确认'
         WHEN 4 THEN '旧界面=年度；新口径=季报 —— 若要年报，改成 5'
         WHEN 5 THEN '新口径=年报（旧界面没有这个值）'
         WHEN 6 THEN '新口径=一次性（旧界面存的是 9）'
         ELSE '需人工确认'
       END AS hint
FROM rpt_template
WHERE filling_cycle IN (1, 2, 3, 4)
ORDER BY id;

-- 4. 已生成的周期子任务（parent_task_id 非空）：抽查属期与截止日是否与所选周期一致
-- SELECT id, task_name, filling_cycle, belong_time, fill_deadline, parent_task_id
-- FROM rpt_task WHERE parent_task_id IS NOT NULL ORDER BY id DESC LIMIT 20;

-- 5. 确认后的修正（示例，逐条确认后执行，不要整段跑）
-- UPDATE rpt_template SET filling_cycle = 3 WHERE id = ?;   -- 月度 → 月报
-- UPDATE rpt_task     SET filling_cycle = 3 WHERE template_id = ?;
