#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""核查残余数据"""
from rsh import get_client, run

Q = [
    ("补正记录", "SELECT id, task_id, status, comment FROM rpt_correct_task ORDER BY id DESC LIMIT 3;"),
    ("附件", "SELECT id, file_name, task_id, DATE_FORMAT(create_time,'%Y-%m-%d %H:%i') ct FROM rpt_file ORDER BY id DESC LIMIT 5;"),
]


def main():
    c = get_client()
    for name, sql in Q:
        sftp = c.open_sftp()
        with sftp.file("/tmp/_r.sql", "w") as f:
            f.write(sql)
        sftp.close()
        rc, out, err = run(
            "mysql -ureport -p***REMOVED_DB_PASSWORD*** report_system -B < /tmp/_r.sql 2>&1 | grep -v Warning",
            timeout=60, client=c)
        print(f"=== {name} ===")
        print(out.strip()[:600])
        print()
    c.close()


if __name__ == "__main__":
    main()
