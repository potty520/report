#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
清理业务流转测试数据（ZZFLOW-*）

清理范围：
  - rpt_template   template_code LIKE 'ZZF%'
  - rpt_task       task_name LIKE 'ZZFLOW-%'
  - rpt_task_data  关联上述任务
  - rpt_task_assign / rpt_task_process / rpt_task_data 关联上述任务
  - rpt_correct_task 关联上述任务
  - rpt_file       本次测试上传的文件（verify_ok.txt 等）

清理前先做完整性盘点，清理后再盘点，确保生产存量数据未受影响。
"""
import sys

from rsh import get_client, run

TABLES = ["rpt_task", "rpt_task_data", "rpt_template", "rpt_task_assign",
          "rpt_task_process", "rpt_correct_task", "rpt_file", "rpt_data_apply"]

BASELINE = {
    # 部署验证前记录的干净基线
    "rpt_task": 55,
    "rpt_task_data": 1,
    "rpt_template": 1,
    "rpt_task_assign": 51,
    "rpt_task_process": 296,
}


def q(client, sql):
    sftp = client.open_sftp()
    with sftp.file("/tmp/_c.sql", "w") as f:
        f.write(sql)
    sftp.close()
    rc, out, err = run(
        "mysql -ureport -p***REMOVED_DB_PASSWORD*** report_system -B -N < /tmp/_c.sql 2>&1 | grep -v Warning",
        timeout=120, client=client)
    return out.strip()


def counts(client):
    res = {}
    for t in TABLES:
        out = q(client, f"SELECT COUNT(*) FROM {t};")
        try:
            res[t] = int(out.splitlines()[-1])
        except Exception:
            res[t] = -1
    return res


def main():
    apply = "--apply" in sys.argv
    client = get_client()

    print("=" * 70)
    print("业务流转测试数据清理")
    print("=" * 70)

    print("\n=== 清理前 ===")
    before = counts(client)
    for k, v in before.items():
        print(f"  {k:24s} {v}")

    # 找出测试数据
    tpl_ids = q(client, "SELECT id FROM rpt_template WHERE template_code LIKE 'ZZF%';")
    task_ids = q(client, "SELECT id FROM rpt_task WHERE task_name LIKE 'ZZFLOW-%';")
    tpl_list = [x for x in tpl_ids.splitlines() if x.strip()]
    task_list = [x for x in task_ids.splitlines() if x.strip()]
    print(f"\n测试模板: {tpl_list}")
    print(f"测试任务: {task_list}")

    if not tpl_list and not task_list:
        print("\n✅ 无测试数据残留")
        client.close()
        return

    tid_in = ",".join(tpl_list) if tpl_list else "0"
    tk_in = ",".join(task_list) if task_list else "0"

    cleanup_sql = f"""
SET FOREIGN_KEY_CHECKS=0;
DELETE FROM rpt_task_process    WHERE task_id IN ({tk_in}) OR data_id IN (SELECT id FROM rpt_task_data WHERE task_id IN ({tk_in}));
DELETE FROM rpt_correct_task    WHERE task_id IN ({tk_in});
DELETE FROM rpt_task_assign     WHERE task_id IN ({tk_in});
DELETE FROM rpt_task_data       WHERE task_id IN ({tk_in});
DELETE FROM rpt_task            WHERE id IN ({tk_in});
DELETE FROM rpt_template_field  WHERE template_id IN ({tid_in});
DELETE FROM rpt_template        WHERE id IN ({tid_in});
DELETE FROM rpt_online_user     WHERE 1=0;
SET FOREIGN_KEY_CHECKS=1;
"""

    # 测试上传的临时文件（部署验证时上传的 verify_ok.txt / ok.txt）
    file_sql = """
DELETE FROM rpt_file WHERE file_name IN ('verify_ok.txt','ok.txt') AND task_id IS NULL;
"""

    if not apply:
        print("\n--- 预览 SQL（未执行）---")
        print(cleanup_sql)
        print(file_sql)
        print("加 --apply 执行清理")
        client.close()
        return

    print("\n=== 执行清理 ===")
    for label, sql in [("业务数据", cleanup_sql), ("测试文件", file_sql)]:
        sftp = client.open_sftp()
        with sftp.file("/tmp/_del.sql", "w") as f:
            f.write(sql)
        sftp.close()
        rc, out, err = run(
            "mysql -ureport -p***REMOVED_DB_PASSWORD*** report_system < /tmp/_del.sql 2>&1 | grep -v Warning",
            timeout=180, client=client)
        print(f"  {label}: {out.strip()[:200] or 'OK'}")

    print("\n=== 清理后 ===")
    after = counts(client)
    for k, v in after.items():
        base = BASELINE.get(k)
        flag = ""
        if base is not None:
            flag = " ✅" if v == base else f" ⚠️ 基线={base}"
        print(f"  {k:24s} {v}{flag}")

    print("\n=== 生产存量数据校验 ===")
    # 关键：admin/user 账号、现有任务、外部数据仍在
    checks = [
        ("用户数", "SELECT COUNT(*) FROM sys_user;", "2"),
        ("角色数", "SELECT COUNT(*) FROM sys_role;", "4"),
        ("部门数", "SELECT COUNT(*) FROM sys_dept;", "4"),
        ("原模板(验证模板)", "SELECT COUNT(*) FROM rpt_template WHERE template_code='TVERIFY';", "1"),
        ("原有任务(P1*)", "SELECT COUNT(*) FROM rpt_task WHERE task_name LIKE 'P1%';", None),
    ]
    for name, sql, expect in checks:
        out = q(client, sql).splitlines()[-1]
        ok = "✅" if (expect is None or out == expect) else "⚠️"
        print(f"  {name:20s} {out} {ok}")

    client.close()


if __name__ == "__main__":
    main()
