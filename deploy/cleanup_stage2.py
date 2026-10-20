#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""清理第二阶段测试数据（ZZC/ZZAUDIT/ZZD 前缀），并校验基线"""
import sys

from rsh import get_client, run

BASE_COUNT = {
    "rpt_task": 55, "rpt_task_data": 1, "rpt_task_assign": 51,
    "rpt_task_process": 296, "rpt_template": 1, "rpt_template_field": 0,
    "rpt_data_range_config": 26, "rpt_data_apply": 2,
}


def q(client, sql):
    sftp = client.open_sftp()
    with sftp.file("/tmp/_c2.sql", "w") as f:
        f.write(sql)
    sftp.close()
    rc, out, err = run(
        "mysql -ureport -p***REMOVED_DB_PASSWORD*** report_system -B -N < /tmp/_c2.sql 2>&1 | grep -v Warning",
        timeout=180, client=client)
    return out.strip()


def main():
    apply = "--apply" in sys.argv
    c = get_client()

    # 找出所有测试数据
    tasks = [x for x in q(c, "SELECT id FROM rpt_task WHERE task_name LIKE 'ZZ%';").splitlines() if x.strip()]
    tpls = [x for x in q(c, "SELECT id FROM rpt_template WHERE template_code LIKE 'ZZ%' OR template_name LIKE 'ZZ%';").splitlines() if x.strip()]
    print(f"测试任务 {len(tasks)} 个: {tasks[:20]}{'...' if len(tasks)>20 else ''}")
    print(f"测试模板 {len(tpls)} 个: {tpls}")

    if not (tasks or tpls):
        print("无测试数据")
        c.close(); return 0

    tk = ",".join(tasks) or "0"
    tp = ",".join(tpls) or "0"
    sql = f"""
SET FOREIGN_KEY_CHECKS=0;
DELETE FROM rpt_task_process WHERE task_id IN ({tk});
DELETE FROM rpt_correct_task WHERE task_id IN ({tk});
DELETE FROM rpt_task_assign  WHERE task_id IN ({tk});
DELETE FROM rpt_task_data    WHERE task_id IN ({tk});
DELETE FROM rpt_file         WHERE task_id IN ({tk});
DELETE FROM rpt_data_apply   WHERE task_id IN ({tk});
DELETE FROM rpt_task         WHERE id IN ({tk});
DELETE FROM rpt_template_field WHERE template_id IN ({tp});
DELETE FROM rpt_template_auth  WHERE template_id IN ({tp});
DELETE FROM rpt_template       WHERE id IN ({tp});
SET FOREIGN_KEY_CHECKS=1;
-- 清理孤儿轨迹（本轮测试删除任务时可能遗留）
DELETE p FROM rpt_task_process p LEFT JOIN rpt_task t ON t.id=p.task_id
 WHERE t.id IS NULL AND p.create_time >= '2026-10-20 00:00:00';
"""
    if not apply:
        print("\n预览模式，加 --apply 执行")
        c.close(); return 0

    q(c, sql)
    print("\n=== 清理后 ===")
    ok = True
    for t, base in BASE_COUNT.items():
        v = q(c, f"SELECT COUNT(*) FROM {t};").splitlines()[-1]
        flag = "✅" if v == str(base) else f"⚠️ 基线={base}"
        if v != str(base):
            ok = False
        print(f"  {t:24s} {v} {flag}")

    print("\n=== 生产数据 ===")
    print("  用户:", q(c, "SELECT COUNT(*) FROM sys_user;").splitlines()[-1])
    print("  原有P1任务:", q(c, "SELECT COUNT(*) FROM rpt_task WHERE task_name LIKE 'P1%';").splitlines()[-1])
    print("  数据源:", q(c, "SELECT COUNT(*) FROM rpt_datasource;").splitlines()[-1])
    c.close()
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main() or 0)
