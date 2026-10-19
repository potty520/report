#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""盘点生产数据（逐条查询，容错）"""
from rsh import get_client, run

QUERIES = [
    ("任务表结构", "DESC rpt_task;"),
    ("填报表结构", "DESC rpt_task_data;"),
    ("任务(近期)", "SELECT id, task_name, template_id, dept_id, status, "
                   "DATE_FORMAT(create_time,'%Y-%m-%d %H:%i') ct FROM rpt_task ORDER BY id DESC LIMIT 6;"),
    ("填报数据", "SELECT id, task_id, fill_user_id, dept_id, status, dept_audit_status, "
                 "task_audit_status, DATE_FORMAT(submit_time,'%Y-%m-%d %H:%i') st "
                 "FROM rpt_task_data ORDER BY id DESC LIMIT 6;"),
    ("分派", "SELECT id, task_id, dept_id, user_id, assign_type, status "
             "FROM rpt_task_assign ORDER BY id DESC LIMIT 6;"),
    ("用户角色", "SELECT u.id, u.username, u.real_name, u.dept_id, r.role_code "
                 "FROM sys_user u LEFT JOIN sys_user_role ur ON ur.user_id=u.id "
                 "LEFT JOIN sys_role r ON r.id=ur.role_id;"),
    ("数据源", "SELECT id, name, type, host, port, status FROM rpt_datasource LIMIT 6;"),
    ("部门", "SELECT id, parent_id, dept_name, dept_code FROM sys_dept;"),
]


def main():
    c = get_client()
    sftp = c.open_sftp()
    for title, sql in QUERIES:
        with sftp.file("/tmp/_q.sql", "w") as f:
            f.write(sql)
        rc, out, err = run(
            "mysql -ureport -p***REMOVED_DB_PASSWORD*** report_system -B < /tmp/_q.sql 2>&1 | head -12",
            timeout=60, client=c)
        print(f"===== {title} =====")
        print((out or "").strip()[:900])
        print()
    sftp.close()
    c.close()


if __name__ == "__main__":
    main()
