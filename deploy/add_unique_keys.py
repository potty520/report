#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
唯一约束落地：先体检、后加约束

背景（审计：严重）：关键表缺少唯一约束，导致
  - rpt_task_data 可产生同一人同一任务的重复填报（之后 selectOne 抛 TooManyResults 永久锁死）
  - rpt_task_assign 可重复分派
  - rpt_task 周期任务可重复生成

顺序：先查重复 → 有重复则报告并中止 → 无重复则建唯一索引 → 复查
用法: python add_unique_keys.py [--apply]
"""
import sys

from rsh import get_client, run

MYSQL = "mysql -ureport -p***REMOVED_DB_PASSWORD*** report_system"

CHECKS = [
    ("rpt_task_data 重复(task_id,fill_user_id)",
     "SELECT task_id, fill_user_id, COUNT(*) c FROM rpt_task_data "
     "GROUP BY task_id, fill_user_id HAVING COUNT(*) > 1;"),
    ("rpt_task_assign 重复(task_id,assign_type,user_id)",
     "SELECT task_id, assign_type, user_id, COUNT(*) c FROM rpt_task_assign "
     "GROUP BY task_id, assign_type, user_id HAVING COUNT(*) > 1;"),
    ("rpt_task 周期任务重复(parent_task_id,belong_time,dept_id)",
     "SELECT parent_task_id, belong_time, dept_id, COUNT(*) c FROM rpt_task "
     "WHERE parent_task_id IS NOT NULL "
     "GROUP BY parent_task_id, belong_time, dept_id HAVING COUNT(*) > 1;"),
]

INDEXES = [
    ("rpt_task_data", "uk_task_data_user", "task_id, fill_user_id"),
    ("rpt_task_assign", "uk_task_assign", "task_id, assign_type, user_id"),
    ("rpt_task", "uk_periodic", "parent_task_id, belong_time, dept_id"),
]


def q(client, sql, dashes=True):
    sftp = client.open_sftp()
    with sftp.file("/tmp/_uk.sql", "w") as f:
        f.write(sql)
    sftp.close()
    flag = "" if dashes else "-N"
    rc, out, err = run(f"{MYSQL} -B {flag} < /tmp/_uk.sql 2>&1 | grep -v Warning", timeout=120, client=client)
    return out.strip()


def main():
    apply = "--apply" in sys.argv
    c = get_client()

    print("=" * 68)
    print("唯一约束落地前体检")
    print("=" * 68)

    has_dup = False
    for name, sql in CHECKS:
        out = q(c, sql)
        if out:
            has_dup = True
            print(f"\n⚠️ 发现重复 —— {name}")
            print(out[:800])
        else:
            print(f"✅ 无重复 —— {name}")

    print()
    # 查现有索引
    for table, idx, cols in INDEXES:
        out = q(c, f"SHOW INDEX FROM {table} WHERE Key_name='{idx}';")
        print(f"  {table}.{idx}: {'已存在' if out else '缺失'}")

    if has_dup:
        print("\n❌ 存在重复数据，不能直接加唯一约束。请先人工清理后再执行。")
        c.close()
        return 1

    if not apply:
        print("\n预览模式，加 --apply 执行建索引。")
        c.close()
        return 0

    print("\n=== 建唯一索引 ===")
    ok = True
    for table, idx, cols in INDEXES:
        existing = q(c, f"SHOW INDEX FROM {table} WHERE Key_name='{idx}';")
        if existing:
            print(f"  [SKIP] {table}.{idx} 已存在")
            continue
        sql = f"CREATE UNIQUE INDEX {idx} ON {table}({cols});"
        out = q(c, sql)
        if "ERROR" in out.upper():
            print(f"  [FAIL] {sql}\n         {out[:300]}")
            ok = False
        else:
            print(f"  [OK]   {sql}")

    print("\n=== 复查 ===")
    for table, idx, cols in INDEXES:
        out = q(c, f"SHOW INDEX FROM {table} WHERE Key_name='{idx}';")
        print(f"  {table}.{idx}: {'✅ 已建立' if out else '❌ 缺失'}")
    c.close()
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
