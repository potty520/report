#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""对比生产库与 H2 schema.sql 的列差异（找出 H2 缺失的列）"""
import re

from rsh import get_client, run

SCHEMA = r"E:\C++\report_src\report-master\backend\src\main\resources\schema.sql"
TABLES = ["rpt_task_data", "rpt_task_assign", "rpt_template", "rpt_template_field",
          "rpt_data_apply", "rpt_file", "rpt_datasource"]


def prod_columns(client, table):
    sftp = client.open_sftp()
    with sftp.file("/tmp/_c.sql", "w") as f:
        f.write(f"SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                f"WHERE TABLE_SCHEMA='report_system' AND TABLE_NAME='{table}' ORDER BY ORDINAL_POSITION;")
    sftp.close()
    rc, out, _ = run("mysql -ureport -p***REMOVED_DB_PASSWORD*** report_system -B -N < /tmp/_c.sql 2>/dev/null",
                     timeout=60, client=client)
    return [x.strip() for x in out.splitlines() if x.strip()]


def h2_columns(schema_text, table):
    m = re.search(rf"CREATE TABLE IF NOT EXISTS {table} \((.*?)\n\);", schema_text, re.S)
    if not m:
        return None
    body = m.group(1)
    cols = []
    for line in body.splitlines():
        line = line.strip().rstrip(",")
        if not line or line.startswith("--") or line.upper().startswith("PRIMARY KEY"):
            continue
        parts = line.split()
        if parts:
            cols.append(parts[0].lower())
    return cols


def main():
    c = get_client()
    schema = open(SCHEMA, encoding="utf-8").read()
    for t in TABLES:
        prod = prod_columns(c, t)
        h2 = h2_columns(schema, t)
        if h2 is None:
            print(f"{t}: H2 schema 中未定义")
            continue
        missing = [x for x in prod if x.lower() not in [y.lower() for y in h2]]
        extra = [x for x in h2 if x.lower() not in [y.lower() for y in prod]]
        print(f"--- {t} ---")
        print(f"  生产 {len(prod)} 列 / H2 {len(h2)} 列")
        if missing:
            print(f"  ❌ H2 缺列: {', '.join(missing)}")
        if extra:
            print(f"  ⚠️ H2 多列: {', '.join(extra)}")
        if not missing and not extra:
            print("  ✅ 一致")
    c.close()


if __name__ == "__main__":
    main()
