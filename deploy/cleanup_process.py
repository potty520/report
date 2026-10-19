#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
清理孤儿进程记录（验证「删任务不清理 rpt_task_process」缺陷）

精确删除：只删 task_id 在 (212,215) 且任务已不存在的记录 = 7 条。
这些是我用 API 删除测试任务时留下的，恰好是审计发现 T-3 的现场证据。
"""
import sys

from rsh import get_client, run


def q(client, sql, dashes=False):
    sftp = client.open_sftp()
    with sftp.file("/tmp/_c.sql", "w") as f:
        f.write(sql)
    sftp.close()
    flag = "" if dashes else "-N"
    rc, out, err = run(
        f"mysql -ureport -p***REMOVED_DB_PASSWORD*** report_system -B {flag} < /tmp/_c.sql 2>&1 | grep -v Warning",
        timeout=120, client=client)
    return out.strip()


def main():
    apply = "--apply" in sys.argv
    c = get_client()

    print("=== 待清理的孤儿进程记录（任务已删除）===")
    rows = q(c, "SELECT p.id, p.task_id, p.node_code, DATE_FORMAT(p.create_time,'%m-%d %H:%i') t "
                "FROM rpt_task_process p LEFT JOIN rpt_task t ON t.id=p.task_id "
                "WHERE t.id IS NULL AND p.task_id IN (212,215) ORDER BY p.id;")
    print(rows or "(无)")

    cnt = q(c, "SELECT COUNT(*) FROM rpt_task_process p LEFT JOIN rpt_task t ON t.id=p.task_id "
               "WHERE t.id IS NULL AND p.task_id IN (212,215);").splitlines()
    n = int(cnt[-1]) if cnt else 0
    print(f"\n共计 {n} 条")

    if not apply or n == 0:
        print("预览模式，加 --apply 执行" if n else "无需清理")
        c.close()
        return

    # 备份后删除
    q(c, "CREATE TABLE IF NOT EXISTS _bak_process_20261019 AS "
         "SELECT p.* FROM rpt_task_process p LEFT JOIN rpt_task t ON t.id=p.task_id "
         "WHERE t.id IS NULL AND p.task_id IN (212,215);")
    q(c, "DELETE p FROM rpt_task_process p LEFT JOIN rpt_task t ON t.id=p.task_id "
         "WHERE t.id IS NULL AND p.task_id IN (212,215);")

    print("\n=== 清理后 ===")
    for t, base in [("rpt_task_process", 296), ("rpt_task", 55), ("rpt_task_data", 1)]:
        v = q(c, f"SELECT COUNT(*) FROM {t};").splitlines()[-1]
        print(f"  {t:20s} {v} {'✅' if v == str(base) else f'⚠️ 基线={base}'}")
    c.close()


if __name__ == "__main__":
    main()
