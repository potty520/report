#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""清理 ZZAUDIT-* 上线验证数据"""
import sys

from rsh import get_client, run

TABLES = ["rpt_task", "rpt_task_data", "rpt_task_assign", "rpt_task_process",
          "rpt_template", "rpt_template_field"]
BASELINE = {"rpt_task": 55, "rpt_task_data": 1, "rpt_task_assign": 51,
            "rpt_task_process": 296, "rpt_template": 1}


def q(client, sql):
    sftp = client.open_sftp()
    with sftp.file("/tmp/_c2.sql", "w") as f:
        f.write(sql)
    sftp.close()
    rc, out, err = run(
        "mysql -ureport -p***REMOVED_DB_PASSWORD*** report_system -B -N < /tmp/_c2.sql 2>&1 | grep -v Warning",
        timeout=120, client=client)
    return out.strip()


def main():
    apply = "--apply" in sys.argv
    client = get_client()

    print("=== 清理前 ===")
    for t in TABLES:
        print(f"  {t:22s} {q(client, f'SELECT COUNT(*) FROM {t};').splitlines()[-1]}")

    tids = [x for x in q(client, "SELECT id FROM rpt_template WHERE template_code LIKE 'ZZA%';").splitlines() if x.strip()]
    tkids = [x for x in q(client, "SELECT id FROM rpt_task WHERE task_name LIKE 'ZZAUDIT-%';").splitlines() if x.strip()]
    print(f"\n模板: {tids}\n任务: {tkids}")

    if not tids and not tkids:
        print("无测试数据")
        client.close()
        return

    ti = ",".join(tids) or "0"
    tki = ",".join(tkids) or "0"
    sql = f"""
SET FOREIGN_KEY_CHECKS=0;
DELETE FROM rpt_task_process   WHERE task_id IN ({tki});
DELETE FROM rpt_correct_task   WHERE task_id IN ({tki});
DELETE FROM rpt_task_assign    WHERE task_id IN ({tki});
DELETE FROM rpt_task_data      WHERE task_id IN ({tki});
DELETE FROM rpt_task           WHERE id IN ({tki});
DELETE FROM rpt_template_field WHERE template_id IN ({ti});
DELETE FROM rpt_template       WHERE id IN ({ti});
SET FOREIGN_KEY_CHECKS=1;
"""
    if not apply:
        print("预览模式，加 --apply 执行")
        client.close()
        return

    sftp = client.open_sftp()
    with sftp.file("/tmp/_del2.sql", "w") as f:
        f.write(sql)
    sftp.close()
    run("mysql -ureport -p***REMOVED_DB_PASSWORD*** report_system < /tmp/_del2.sql 2>&1 | grep -v Warning",
        timeout=180, client=client)

    print("\n=== 清理后 ===")
    ok = True
    for t in TABLES:
        v = q(client, f"SELECT COUNT(*) FROM {t};").splitlines()[-1]
        base = BASELINE.get(t)
        flag = ""
        if base is not None:
            flag = "✅" if v == str(base) else f"⚠️ 基线={base}"
            if v != str(base):
                ok = False
        print(f"  {t:22s} {v} {flag}")
    print("\n生产数据: 用户", q(client, "SELECT COUNT(*) FROM sys_user;").splitlines()[-1])
    client.close()
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main() or 0)
