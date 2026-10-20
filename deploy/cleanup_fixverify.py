#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""清理本轮修复验证产生的测试数据并校验基线"""
import sys

from rsh import get_client, run

TABLES = {
    "rpt_task": 55, "rpt_task_data": 1, "rpt_task_assign": 51,
    "rpt_task_process": 296, "rpt_template": 1, "rpt_template_field": 0,
    "rpt_data_range_config": 26, "rpt_data_apply": 10,
}


def q(client, sql):
    sftp = client.open_sftp()
    with sftp.file("/tmp/_c3.sql", "w") as f:
        f.write(sql)
    sftp.close()
    rc, out, err = run(
        "mysql -ureport -p***REMOVED_DB_PASSWORD*** report_system -B -N < /tmp/_c3.sql 2>&1 | grep -v Warning",
        timeout=120, client=client)
    return out.strip()


def main():
    apply = "--apply" in sys.argv
    c = get_client()

    print("=== 当前计数 ===")
    for t in TABLES:
        print(f"  {t:24s} {q(c, f'SELECT COUNT(*) FROM {t};').splitlines()[-1]}")

    tids = [x for x in q(c, "SELECT id FROM rpt_task WHERE task_name LIKE 'FIXVERIFY%' OR task_name LIKE 'AUDIT%' OR task_name LIKE 'ZZ%';").splitlines() if x.strip()]
    tpids = [x for x in q(c, "SELECT id FROM rpt_template WHERE template_code IN ('FVTPL') OR template_name LIKE 'FIXVERIFY%';").splitlines() if x.strip()]
    cfids = [x for x in q(c, "SELECT id FROM rpt_data_range_config WHERE config_name LIKE 'FIXVERIFY%' OR config_name LIKE 'H5-%' OR config_name LIKE 'AUDIT%';").splitlines() if x.strip()]
    apids = [x for x in q(c, "SELECT id FROM rpt_data_apply WHERE reason LIKE 'FIXVERIFY%' OR reason LIKE 'AUDIT%';").splitlines() if x.strip()]
    print(f"\n测试任务: {tids}")
    print(f"测试模板: {tpids}")
    print(f"测试配置: {cfids}")
    print(f"测试申请: {apids}")

    if not (tids or tpids or cfids or apids):
        print("\n无测试数据")
        c.close()
        return

    tk = ",".join(tids) or "0"
    tp = ",".join(tpids) or "0"
    cf = ",".join(cfids) or "0"
    ap = ",".join(apids) or "0"
    sql = f"""
SET FOREIGN_KEY_CHECKS=0;
DELETE FROM rpt_task_process WHERE task_id IN ({tk});
DELETE FROM rpt_correct_task WHERE task_id IN ({tk});
DELETE FROM rpt_task_assign  WHERE task_id IN ({tk});
DELETE FROM rpt_task_data    WHERE task_id IN ({tk});
DELETE FROM rpt_task         WHERE id IN ({tk});
DELETE FROM rpt_template_field WHERE template_id IN ({tp});
DELETE FROM rpt_template       WHERE id IN ({tp});
DELETE FROM rpt_data_range_config WHERE id IN ({cf});
DELETE FROM rpt_data_apply WHERE id IN ({ap});
SET FOREIGN_KEY_CHECKS=1;
"""
    # 清掉孤儿进程记录（本次测试删除任务时遗留）
    sql += """
DELETE p FROM rpt_task_process p LEFT JOIN rpt_task t ON t.id=p.task_id WHERE t.id IS NULL AND p.create_time >= '2026-10-20 00:00:00';
"""

    if not apply:
        print("\n预览模式，加 --apply 执行")
        c.close()
        return

    q(c, sql)
    print("\n=== 清理后 ===")
    ok = True
    for t, base in TABLES.items():
        v = q(c, f"SELECT COUNT(*) FROM {t};").splitlines()[-1]
        flag = "✅" if v == str(base) else f"⚠️ 基线={base}"
        if v != str(base):
            ok = False
        print(f"  {t:24s} {v} {flag}")

    print("\n=== 生产数据 ===")
    print("  用户:", q(c, "SELECT COUNT(*) FROM sys_user;").splitlines()[-1])
    print("  角色:", q(c, "SELECT COUNT(*) FROM sys_role;").splitlines()[-1])
    print("  部门:", q(c, "SELECT COUNT(*) FROM sys_dept;").splitlines()[-1])
    print("  原有P1*任务:", q(c, "SELECT COUNT(*) FROM rpt_task WHERE task_name LIKE 'P1%';").splitlines()[-1])
    c.close()
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main() or 0)
