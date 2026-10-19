#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""核实模板与数据源结构"""
from rsh import get_client, run

QUERIES = [
    ("模板全部", "SELECT id, template_name, template_code, status, datasource_id, "
                 "materialized, create_time FROM rpt_template;"),
    ("模板字段", "SELECT id, template_id, field_name, field_label, field_type "
                 "FROM rpt_template_field ORDER BY template_id, id LIMIT 20;"),
    ("数据源结构", "DESC rpt_datasource;"),
    ("数据源数据", "SELECT * FROM rpt_datasource LIMIT 5\\G"),
    ("任务状态分布", "SELECT status, COUNT(*) c FROM rpt_task GROUP BY status;"),
    ("填报审核链路", "SELECT id, task_id, status, dept_audit_status, task_audit_status, "
                     "audit_status, fill_user_id FROM rpt_task_data;"),
    ("流转轨迹", "SELECT id, task_id, data_id, node_code, action, operator_id, "
                 "DATE_FORMAT(create_time,'%m-%d %H:%i') t FROM rpt_task_process "
                 "ORDER BY id DESC LIMIT 10;"),
]


def main():
    c = get_client()
    sftp = c.open_sftp()
    for title, sql in QUERIES:
        with sftp.file("/tmp/_q.sql", "w") as f:
            f.write(sql)
        rc, out, err = run(
            "mysql -ureport -p***REMOVED_DB_PASSWORD*** report_system -B < /tmp/_q.sql 2>&1 | grep -v Warning | head -25",
            timeout=60, client=c)
        print(f"===== {title} =====")
        print((out or "").strip()[:1200])
        print()
    sftp.close()
    c.close()


if __name__ == "__main__":
    main()
