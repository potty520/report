#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
填报系统生产库：索引核查与补齐

背景：测试报告 V01 指出部门审核 25s 超时，根因是 rpt_task_assign / rpt_task_data 等表
的 task_id 列缺索引，审核链路（AuthzService.assertCanAudit 按 task_id 查分派）全表扫描。

本脚本做两件事：
  1. --check  只读，列出相关表现有索引，并判断必需索引是否缺失
  2. --apply  在缺失时执行 CREATE INDEX（幂等，已存在的跳过）

用法:
  python fix_indexes.py --check
  python fix_indexes.py --apply
"""
import sys

from rsh import run

DB = "report_system"
MYSQL = "mysql -ureport -p***REMOVED_DB_PASSWORD***"

# (表, 索引名, 列定义)
REQUIRED_INDEXES = [
    ("rpt_task_assign", "idx_task_assign_task_id", "task_id"),
    ("rpt_task_assign", "idx_task_assign_dept_id", "dept_id"),
    ("rpt_task_assign", "idx_task_assign_user_id", "user_id"),
    ("rpt_task_data", "idx_task_data_task_id", "task_id"),
    ("rpt_task_data", "idx_task_data_task_status", "task_id, status"),
    ("rpt_task_data", "idx_task_data_fill_user", "fill_user_id"),
    ("rpt_task_data", "idx_task_data_dept_id", "dept_id"),
    ("rpt_task_process", "idx_task_process_task_id", "task_id"),
    ("rpt_data_apply", "idx_data_apply_task_id", "task_id"),
    ("rpt_data_apply", "idx_data_apply_applicant", "applicant_id"),
    ("rpt_file", "idx_file_task_id", "task_id"),
    ("rpt_file", "idx_file_md5", "md5"),
]


def query_existing():
    """返回 {(table, index_name): columns}"""
    sql = (
        "SELECT TABLE_NAME, INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) "
        "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='%s' "
        "GROUP BY TABLE_NAME, INDEX_NAME;" % DB
    )
    cmd = f'{MYSQL} {DB} -N -B -e "{sql}" 2>/dev/null'
    rc, out, err = run(cmd, timeout=60)
    if rc != 0:
        print(f"[ERROR] 查询索引失败: {err or out}")
        sys.exit(1)
    existing = {}
    for line in out.strip().splitlines():
        parts = line.split("\t")
        if len(parts) >= 3:
            existing[(parts[0], parts[1])] = parts[2]
    return existing


def check():
    existing = query_existing()
    # 同时收集每张表的"列前缀"索引，用于判断是否已被等效覆盖
    covered_cols = {}
    for (tbl, idx), cols in existing.items():
        covered_cols.setdefault(tbl, []).append(cols)

    missing = []
    print(f"{'表':<20} {'索引名':<32} {'列':<28} 状态")
    print("-" * 95)
    for tbl, idx, cols in REQUIRED_INDEXES:
        cols_norm = [c.strip() for c in cols.split(",")]
        if (tbl, idx) in existing:
            status = "已存在"
        else:
            # 判断是否有等效索引：某索引的列前缀 == 目标列 或 以目标列开头
            equiv = None
            for c in covered_cols.get(tbl, []):
                c_list = [x.strip() for x in c.split(",")]
                if c_list[: len(cols_norm)] == cols_norm:
                    equiv = c
                    break
            if equiv:
                status = f"已被等效索引覆盖({equiv})"
            else:
                status = "【缺失】"
                missing.append((tbl, idx, cols))
        print(f"{tbl:<20} {idx:<32} {cols:<28} {status}")

    print()
    if missing:
        print(f"缺失 {len(missing)} 个索引：")
        for t, i, c in missing:
            print(f"  - {t}.{c}  ({i})")
    else:
        print("✅ 所有必需索引均已存在或已等效覆盖，无需变更")
    return missing


def apply(missing):
    if not missing:
        print("无需执行 DDL")
        return True
    ok = True
    for tbl, idx, cols in missing:
        sql = f"CREATE INDEX {idx} ON {tbl}({cols});"
        cmd = f'{MYSQL} {DB} -e "{sql}" 2>&1'
        rc, out, err = run(cmd, timeout=180)
        combined = (out or "") + (err or "")
        if rc != 0 and "Duplicate key name" not in combined:
            print(f"[FAIL] {sql}")
            print(f"       {combined.strip()[:300]}")
            ok = False
        else:
            print(f"[OK]   {sql}")
    return ok


if __name__ == "__main__":
    action = sys.argv[1] if len(sys.argv) > 1 else "--check"
    if action == "--check":
        m = check()
        sys.exit(0)
    elif action == "--apply":
        m = check()
        print("\n开始执行 DDL ...")
        ok = apply(m)
        print()
        print("=== 执行后复查 ===")
        check()
        sys.exit(0 if ok else 1)
    else:
        print(__doc__)
        sys.exit(2)
