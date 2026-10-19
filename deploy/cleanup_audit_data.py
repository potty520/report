#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""清理审计过程中产生的测试数据"""
import sys

from rsh import get_client, run


def q(client, sql):
    sqlfile = "/tmp/_clean_audit.sql"
    sftp = client.open_sftp()
    with sftp.file(sqlfile, "w") as f:
        f.write(sql)
    sftp.close()
    rc, out, err = run(
        f"mysql -ureport -p***REMOVED_DB_PASSWORD*** report_system -B -N < {sqlfile} 2>&1 | grep -v Warning",
        timeout=120, client=client)
    return out.strip()


BASELINE = {"rpt_task": 55, "rpt_task_data": 1, "rpt_task_assign": 51,
            "rpt_task_process": 296, "rpt_template": 1, "rpt_template_field": 0,
            "rpt_data_range_config": 26}


def main():
    apply = "--apply" in sys.argv
    c = get_client()

    print("=== 当前状态 ===")
    for t in BASELINE:
        print(f"  {t:24s} {q(c, f'SELECT COUNT(*) FROM {t};').splitlines()[-1]}")

    tids = [x for x in q(c, "SELECT id FROM rpt_task WHERE task_name LIKE 'AUDIT-%';").splitlines() if x.strip()]
    tplids = [x for x in q(c, "SELECT id FROM rpt_template WHERE template_code LIKE 'AUDT%';").splitlines() if x.strip()]
    cfgids = [x for x in q(c, "SELECT id FROM rpt_data_range_config WHERE config_name LIKE 'AUDIT-%';").splitlines() if x.strip()]
    print(f"\n测试任务: {tids}")
    print(f"测试模板: {tplids}")
    print(f"测试配置: {cfgids}")

    if not (tids or tplids or cfgids):
        print("无测试数据")
        c.close()
        return

    tk = ",".join(tids) or "0"
    tp = ",".join(tplids) or "0"
    cf = ",".join(cfgids) or "0"
    sql = f"""
SET FOREIGN_KEY_CHECKS=0;
DELETE FROM rpt_task_process WHERE task_id IN ({tk});
DELETE FROM rpt_task_assign  WHERE task_id IN ({tk});
DELETE FROM rpt_task_data    WHERE task_id IN ({tk});
DELETE FROM rpt_task         WHERE id IN ({tk});
DELETE FROM rpt_template_field WHERE template_id IN ({tp});
DELETE FROM rpt_template       WHERE id IN ({tp});
DELETE FROM rpt_data_range_config WHERE id IN ({cf});
SET FOREIGN_KEY_CHECKS=1;
"""
    if not apply:
        print("\n预览模式，加 --apply 执行")
        c.close()
        return

    print()
    q(c, sql)
    print("=== 清理后 ===")
    ok = True
    for t, base in BASELINE.items():
        v = q(c, f"SELECT COUNT(*) FROM {t};").splitlines()[-1]
        flag = "✅" if v == str(base) else f"⚠️ 基线={base}"
        if v != str(base):
            ok = False
        print(f"  {t:24s} {v} {flag}")

    print("\n=== 生产数据核查 ===")
    print("  用户数:", q(c, "SELECT COUNT(*) FROM sys_user;").splitlines()[-1])
    print("  原有任务(P1*):", q(c, "SELECT COUNT(*) FROM rpt_task WHERE task_name LIKE 'P1%';").splitlines()[-1])
    c.close()
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main() or 0)
