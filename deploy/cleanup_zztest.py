#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""清理 ZZTEST 遗留数据申请"""
from rsh import get_client, run


def q(client, sql):
    sftp = client.open_sftp()
    with sftp.file("/tmp/_zz.sql", "w") as f:
        f.write(sql)
    sftp.close()
    rc, out, err = run(
        "mysql -ureport -p***REMOVED_DB_PASSWORD*** report_system -B -N < /tmp/_zz.sql 2>&1 | grep -v Warning",
        timeout=120, client=client)
    return out.strip()


c = get_client()
print("清理前:", q(c, "SELECT COUNT(*) FROM rpt_data_apply;").splitlines()[-1])
print("ZZTEST 条数:", q(c, "SELECT COUNT(*) FROM rpt_data_apply WHERE reason LIKE 'ZZTEST%';").splitlines()[-1])
q(c, "DELETE FROM rpt_data_apply WHERE reason LIKE 'ZZTEST%';")
print("清理后:", q(c, "SELECT COUNT(*) FROM rpt_data_apply;").splitlines()[-1])
c.close()
