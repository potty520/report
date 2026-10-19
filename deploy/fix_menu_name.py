#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
修复菜单名称脏数据：sys_menu.id=1 的 menu_name 从 'x' 改为 '数据总览'

背景：生产库 sys_menu.id=1 的 menu_name 实际存储为单字符 "x"（HEX=78），
导致首页左侧菜单显示为 "x"。该行 path=/overview、component=pages/Overview 均正确，
仅名称为脏数据。此处做定点修正，并保留原值便于回滚。

用法: python fix_menu_name.py [--apply]
  不带 --apply 为只读预览
"""
import sys

from rsh import run


def main():
    apply = "--apply" in sys.argv

    # 先看当前值
    from rsh import get_client
    c = get_client()
    sftp = c.open_sftp()
    with sftp.file("/tmp/_m1.sql", "w") as f:
        f.write("SELECT id, menu_name, HEX(menu_name), path FROM sys_menu WHERE id=1;")
    sftp.close()
    rc, out, err = run(
        "mysql -ureport -p***REMOVED_DB_PASSWORD*** report_system -B < /tmp/_m1.sql 2>/dev/null",
        timeout=60, client=c)
    print("=== 修复前 ===")
    print(out or "(空)")

    if not apply:
        print("\n当前为预览模式，未做修改。加 --apply 执行修复。")
        c.close()
        return

    # 记录备份表
    sql = """
CREATE TABLE IF NOT EXISTS _bak_sys_menu_name_20261019 AS
SELECT id, menu_name, NOW() AS backup_time FROM sys_menu WHERE id=1;
UPDATE sys_menu SET menu_name='数据总览' WHERE id=1;
SELECT id, menu_name, HEX(menu_name), path FROM sys_menu WHERE id=1;
"""
    sftp = c.open_sftp()
    with sftp.file("/tmp/_m2.sql", "w") as f:
        f.write(sql)
    sftp.close()
    rc, out, err = run(
        "mysql -ureport -p***REMOVED_DB_PASSWORD*** report_system -B < /tmp/_m2.sql 2>&1",
        timeout=120, client=c)
    print("\n=== 修复后 ===")
    print(out or "(空)")
    if err.strip():
        print("[stderr]", err[:300])
    c.close()


if __name__ == "__main__":
    main()
