#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""深挖孤儿任务问题"""
from rsh import get_client, run


def q(client, sql):
    sftp = client.open_sftp()
    with sftp.file("/tmp/_o.sql", "w") as f:
        f.write(sql)
    sftp.close()
    rc, out, err = run(
        "mysql -ureport -p***REMOVED_DB_PASSWORD*** report_system -B < /tmp/_o.sql 2>&1 | grep -v Warning",
        timeout=90, client=client)
    return out.strip()


QUERIES = [
    ("当前所有模板",
     "SELECT id, template_name, template_code, status, audit_status FROM rpt_template;"),
    ("任务引用的 template_id 分布",
     "SELECT template_id, COUNT(*) cnt FROM rpt_task GROUP BY template_id ORDER BY cnt DESC;"),
    ("孤儿任务样例(前5)",
     "SELECT id, task_name, template_id, status, create_time FROM rpt_task "
     "WHERE template_id NOT IN (SELECT id FROM rpt_template) ORDER BY id DESC LIMIT 5;"),
    ("这些任务有没有填报数据",
     "SELECT COUNT(*) FROM rpt_task_data d JOIN rpt_task t ON t.id=d.task_id "
     "WHERE t.template_id NOT IN (SELECT id FROM rpt_template);"),
    ("是否有其他表引用任务",
     "SELECT 'assign' t, COUNT(*) c FROM rpt_task_assign WHERE task_id IN (SELECT id FROM rpt_task WHERE template_id NOT IN (SELECT id FROM rpt_template)) "
     "UNION ALL SELECT 'process', COUNT(*) FROM rpt_task_process WHERE task_id IN (SELECT id FROM rpt_task WHERE template_id NOT IN (SELECT id FROM rpt_template)) "
     "UNION ALL SELECT 'apply', COUNT(*) FROM rpt_data_apply WHERE task_id IN (SELECT id FROM rpt_task WHERE template_id NOT IN (SELECT id FROM rpt_template));"),
    ("最近一次模板表变更线索(rpt_template 全量含时间)",
     "SELECT id, template_code, create_time, update_time FROM rpt_template;"),
]


def main():
    c = get_client()
    for name, sql in QUERIES:
        print(f"===== {name} =====")
        print(q(c, sql)[:1200])
        print()
    c.close()


if __name__ == "__main__":
    main()
