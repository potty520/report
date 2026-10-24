# MySQL 5.7 兼容性实测报告

| 项 | 值 |
|---|---|
| 问题 | 系统能否跑在 MySQL 5.7 上 |
| 实测时间 | 2026-10-24 22:45 – 22:50 |
| 实测版本 | MySQL 5.7.38（官方 winx64 包，独立实例，端口 3307） |
| 应用 | `report-system` 1.0.0（JDK 17，Druid + MyBatis-Plus + Connector/J 8.0.33） |
| 配置 | `utf8mb4 / utf8mb4_general_ci`，`sql_mode=STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION`，`time_zone='+08:00'` |
| 结论 | **可以用，全量验收 34/34 通过**；但 5.7 已 EOL，上线需评估合规影响（见第四节） |

## 一、实测过程

1. 在 5.7 实例上执行 `docs/database.sql` + 6 个 2.0 升级脚本（含数据申请的 title/data_scope 迁移）→ **30 张表全部建成，无报错**。
2. 写入 admin/user 账号（bcrypt）与角色关联。
3. 应用指向 5.7 启动，4 秒起服，**启动日志 0 条 ERROR**。
4. 跑完整接口验收脚本：建模板（含物化建表）→ 建任务 → 发布 → 分派（填报/审核）→ 填报 → 提交 → 驳回 → 重报 → 部门审 → 任务方审 → 入库 → 重复入库拦截 → 未审核拦截 → 待办 → 周期生成。

## 二、验收结果

**34/34 通过**，与 MySQL 8.0.46 上的结果完全一致，关键项：

| 验证点 | 5.7 实测结果 |
|---|---|
| 建库 + 6 个升级脚本 | 30 张表，0 报错 |
| 物化建表到 `report_target` | `CREATE TABLE ... DECIMAL(18,4)/DATE/VARCHAR` 成功 |
| 中文列名/中文数据读写 | 正常（`营业额`、`填报日期`、`备注`） |
| 时间戳时区 | `submitTime` 与服务器北京时间偏差 0 秒（会话 `SET time_zone='+08:00'` 生效） |
| 两级审核 + 轨迹 | 正常，`REJECT` 节点与意见可查 |
| 入库幂等 | 首次 `rows=1/affected=1`，二次返回「已全部入库」 |
| 审核门禁 | 未审核数据入库被拒 |
| 待办口径 | `todoCount = 待填+待审+待批` |
| 周期生成 | 月报子任务属期 `2026-10`、截止 `2026-10-31 23:59:59` |

数据库错误日志无 `[ERROR]`，应用日志无 `ERROR`（仅有原本就存在的 MyBatis-Plus 注解提示）。

## 三、为什么能兼容（代码侧依据）

- **没有用到 MySQL 8 专有语法**：全仓检索无 CTE（`WITH`）、窗口函数（`OVER()`）、`JSON_TABLE`、`REGEXP_REPLACE`、生成列、`utf8mb4_0900_*` 排序规则、角色（roles）等特性。
- **业务 SQL 没有 `GROUP BY`**，不存在 `ONLY_FULL_GROUP_BY` 冲突。
- **DDL 显式指定 `utf8mb4` + `utf8mb4_general_ci`**，5.7 与 8.0 下行为一致。
- **JSON 数据存 `TEXT`**（`rpt_task_data.data_json`），不依赖 5.7/8.0 的 JSON 类型差异。
- **建表脚本只用了 5.6.5+ 就支持的 `DEFAULT CURRENT_TIMESTAMP` / `ON UPDATE`**。
- **Connector/J 8.0.33 + Druid 兼容 5.7**；`serverTimezone=Asia/Shanghai`、`allowPublicKeyRetrieval=true` 在 5.7 下均正常。

## 四、用 5.7 必须注意的四件事

1. **5.7 已 EOL**：Oracle 对 5.7 的扩展支持已于 2025-10 结束，等保测评/漏扫会把它列为「使用停止维护的数据库版本」。
   - 若 5.7 是硬约束（现有环境、信创替代前的过渡），技术上可接受，但要在测评材料里写明**补偿措施**（网络隔离、最小权限、定期备份、WAF/审计）与**升级计划**。
   - 若还能选，优先 8.0（当前生产已验证）；必须用 5.7 时请用 **5.7.44**（5.7 线最后一个版本），不要用 5.7.38 这类旧补丁。
2. **`sql_mode` 必须显式收紧**：默认宽松模式下超长字符串会静默截断、非法日期会写成 `0000-00-00`。建议至少：
   ```sql
   sql_mode=STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION
   ```
3. **字符集必须是 `utf8mb4` / `utf8mb4_general_ci`**：5.7 没有 `utf8mb4_0900_ai_ci`，如果从 8.0 导库过来要先转换，否则建表会报「Unknown collation」。
4. **备份/迁移工具要同版本**：用 5.7 自带的 `mysqldump` 备份；从 8.0 往 5.7 导数据需要先去掉 `0900` 排序规则和 8.0 专有语法。

## 五、建议

- **技术上：可以上 5.7**，代码、脚本、驱动、时区、幂等逻辑都实测通过，不需要改代码。
- **合规上：不推荐长期用 5.7**。如果政务外网环境只提供 5.7，建议：
  1. 升到 5.7.44 并锁定补丁；
  2. 在测评材料中说明 EOL 现状、补偿措施与迁移时间表；
  3. 同步推进 8.0 或信创数据库（达梦/人大金仓）的迁移评估——后者需要改 SQL 方言，工作量另算。
