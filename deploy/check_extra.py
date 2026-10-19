#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""上线前补充核查：账号、遗留文件、密码策略实证"""
from rsh import get_client, run


def q(client, sql):
    sftp = client.open_sftp()
    with sftp.file("/tmp/_q3.sql", "w") as f:
        f.write(sql)
    sftp.close()
    rc, out, err = run(
        "mysql -ureport -p***REMOVED_DB_PASSWORD*** report_system -B < /tmp/_q3.sql 2>&1 | grep -v Warning",
        timeout=90, client=client)
    return out.strip()


Q = [
    ("账号与角色",
     "SELECT u.username, u.real_name, r.role_code FROM sys_user u "
     "JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id;"),
    ("危险后缀遗留文件",
     "SELECT id, file_name, DATE_FORMAT(create_time,'%m-%d %H:%i') ct FROM rpt_file "
     "WHERE LOWER(file_name) LIKE '%.sh' OR LOWER(file_name) LIKE '%.exe';"),
    ("历史测试数据标记统计",
     "SELECT 'zz前缀文件' k, COUNT(*) c FROM rpt_file WHERE file_name LIKE 'zz%' "
     "UNION ALL SELECT 'P1*测试任务', COUNT(*) FROM rpt_task WHERE task_name LIKE 'P1%' "
     "UNION ALL SELECT '自动化补正', COUNT(*) FROM rpt_correct_task WHERE comment LIKE '%自动化%';"),
    ("任务状态分布",
     "SELECT status, COUNT(*) c FROM rpt_task GROUP BY status;"),
]


def main():
    c = get_client()
    for name, sql in Q:
        print(f"===== {name} =====")
        print(q(c, sql)[:900])
        print()
    c.close()


if __name__ == "__main__":
    main()
