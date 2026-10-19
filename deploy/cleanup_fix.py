#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""清理本次修复验证产生的 ZZFIX-* 数据"""
import sys

from rsh import get_client, run


def q(client, sql):
    sftp = client.open_sftp()
    with sftp.file("/tmp/_c.sql", "w") as f:
        f.write(sql)
    sftp.close()
    rc, out, err = run(
        "mysql -ureport -p***REMOVED_DB_PASSWORD*** report_system -B -N < /tmp/_c.sql 2>&1 | grep -v Warning",
        timeout=120, client=client)
    return out.strip()


TABLES = ["rpt_task", "rpt_task_data", "rpt_task_assign", "rpt_task_process", "rpt_template"]
BASELINE = {"rpt_task": 55, "rpt_task_data": 1, "rpt_task_assign": 51,
            "rpt_task_process": 296, "rpt_template": 1}


def main():
    apply = "--apply" in sys.argv
    client = get_client()

    print("=== 当前计数 ===")
    for t in TABLES:
        print(f"  {t:20s} {q(client, f'SELECT COUNT(*) FROM {t};').splitlines()[-1]}")

    ids = q(client, "SELECT id FROM rpt_task WHERE task_name LIKE 'ZZFIX-%';")
    idl = [x for x in ids.splitlines() if x.strip()]
    print(f"\n待清理任务: {idl}")
    if not idl:
        print("无测试数据")
        client.close()
        return

    tk = ",".join(idl)
    sql = f"""
SET FOREIGN_KEY_CHECKS=0;
DELETE FROM rpt_task_process WHERE task_id IN ({tk});
DELETE FROM rpt_correct_task WHERE task_id IN ({tk});
DELETE FROM rpt_task_assign  WHERE task_id IN ({tk});
DELETE FROM rpt_task_data    WHERE task_id IN ({tk});
DELETE FROM rpt_task         WHERE id IN ({tk});
SET FOREIGN_KEY_CHECKS=1;
"""
    if not apply:
        print("预览模式，加 --apply 执行")
        client.close()
        return

    sftp = client.open_sftp()
    with sftp.file("/tmp/_del.sql", "w") as f:
        f.write(sql)
    sftp.close()
    run("mysql -ureport -p***REMOVED_DB_PASSWORD*** report_system < /tmp/_del.sql 2>&1 | grep -v Warning",
        timeout=180, client=client)
    print("\n=== 清理后 ===")
    for t in TABLES:
        v = q(client, f"SELECT COUNT(*) FROM {t};").splitlines()[-1]
        base = BASELINE[t]
        flag = "✅" if v == str(base) else f"⚠️ 基线={base}"
        print(f"  {t:20s} {v} {flag}")

    print("\n=== 生产数据校验 ===")
    print(f"  用户: {q(client, 'SELECT COUNT(*) FROM sys_user;').splitlines()[-1]}")
    sql_p1 = "SELECT COUNT(*) FROM rpt_task WHERE task_name LIKE 'P1%';"
    print(f"  原有任务(P1*): {q(client, sql_p1).splitlines()[-1]}")
    client.close()


if __name__ == "__main__":
    main()
