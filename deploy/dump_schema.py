#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
补齐 schema.sql 缺失表的真实 DDL（从生产库导出）

审计发现 schema.sql 只建了 13 张表，代码读写但未定义的有 10 张：
  rpt_task_process / rpt_correct_task / rpt_schedule_log / rpt_oper_log /
  rpt_quality_report / rpt_sms_log / rpt_volume_file / rpt_statistics_rule /
  rpt_template_auth / rpt_online_user
这些表缺失时写操作被 catch 吞掉 → 功能静默失效。

本脚本：
  1. 在生产库创建 rpt_schedule_lock（定时任务互斥锁）
  2. 导出上述 11 张表的 SHOW CREATE TABLE
  3. 生成可直接追加到 schema.sql 的片段
"""
import re
import sys

from rsh import get_client, run

MYSQL = "mysql -ureport -p***REMOVED_DB_PASSWORD*** report_system"

MISSING = [
    "rpt_task_process", "rpt_correct_task", "rpt_schedule_log", "rpt_oper_log",
    "rpt_quality_report", "rpt_sms_log", "rpt_volume_file", "rpt_statistics_rule",
    "rpt_template_auth", "rpt_online_user",
]

CREATE_LOCK = """
CREATE TABLE IF NOT EXISTS rpt_schedule_lock (
    job_name   VARCHAR(64) NOT NULL,
    instance   VARCHAR(128) DEFAULT '',
    locked_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (job_name)
);
"""


def q(client, sql):
    sftp = client.open_sftp()
    with sftp.file("/tmp/_ddl.sql", "w") as f:
        f.write(sql)
    sftp.close()
    rc, out, err = run(f"{MYSQL} -B < /tmp/_ddl.sql 2>&1 | grep -v Warning", timeout=120, client=client)
    return out.strip(), err.strip()


def main():
    apply = "--apply" in sys.argv
    c = get_client()

    # 1. 建锁表
    out, err = q(c, CREATE_LOCK)
    print("=== 创建 rpt_schedule_lock ===")
    print(out[:300] or "(无输出=成功)")
    if err and "ERROR" in err:
        print("ERR:", err[:300])

    # 2. 导出 DDL
    print("\n=== 导出缺失表 DDL ===")
    blocks = []
    for t in MISSING:
        out, err = q(c, f"SHOW CREATE TABLE {t};")
        # 注意：不能用 "ERROR" in out 判断（表里可能有 error_msg 列），只认 mysql 的报错行
        if (not out) or out.lstrip().startswith("ERROR"):
            print(f"  [SKIP] {t}: {out[:80] or err[:80]}")
            continue
        # 直接从 "CREATE TABLE `" 开始截取（带反引号，避免命中输出表头 "Create Table"）
        idx = out.find("CREATE TABLE `")
        if idx < 0:
            print(f"  [SKIP] {t}: 未找到 CREATE TABLE")
            continue
        ddl = out[idx:]
        # mysql -B 会把换行输出成字面 \n，还原
        ddl = ddl.replace("\\n", "\n").replace("\\t", "\t").replace("\\'", "'")
        # 去掉末尾可能残留的 \r
        ddl = ddl.replace("\\r", "").strip()
        # 去掉 ENGINE/AUTO_INCREMENT/CHARSET/COMMENT 等实例相关尾缀，保留纯结构（H2 兼容）
        ddl = re.sub(r"\)\s*ENGINE=.*$", ")", ddl, flags=re.S).strip()
        if not ddl.endswith(";"):
            ddl += ";"
        blocks.append((t, ddl))
        print(f"  [OK] {t} ({len(ddl)} 字符)")

    # 3. 生成 schema 片段
    frag = []
    frag.append("\n-- ============================================================")
    frag.append("-- 补齐缺失表（审计：schema.sql 只建了 13 张，以下表代码在用但未定义）")
    frag.append("--")
    frag.append("-- 这些表缺失时，相关写操作会被 catch(Exception ignored) 吞掉，")
    frag.append("-- 表现为「功能静默失效」（流转轨迹丢失、定时任务无记录、在线会话不生效）。")
    frag.append("-- 以下 DDL 由生产库 SHOW CREATE TABLE 导出，已去除 AUTO_INCREMENT 当前值。")
    frag.append("-- ============================================================")
    for t, ddl in blocks:
        # 去掉 AUTO_INCREMENT=xxx 这类实例相关的选项
        ddl = re.sub(r"\s*AUTO_INCREMENT=\d+", "", ddl)
        frag.append("")
        frag.append(f"-- {t}")
        frag.append(ddl)

    # 锁表也加进去
    frag.append("")
    frag.append("-- 定时任务跨实例互斥锁（ScheduleLockService 使用）")
    frag.append(CREATE_LOCK.strip())

    text = "\n".join(frag)
    out_path = r"E:\C++\report_src\report-master\backend\src\main\resources\schema_patch.sql"
    if apply:
        with open(out_path, "w", encoding="utf-8") as f:
            f.write(text)
        print(f"\n✅ 已写入 {out_path} ({len(text)} 字符)")
    else:
        print("\n--- 预览（加 --apply 写入文件）---")
        print(text[:2500])
    c.close()


if __name__ == "__main__":
    main()
