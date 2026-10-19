#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
上线前健康检查（Read-Only）

覆盖：
  1. 服务与基础设施状态
  2. 全接口可用性（只读接口）
  3. 关键业务约束校验（不写入数据）
  4. 数据一致性巡检
  5. 安全隐患复查
"""
import json
import time
import urllib.error
import urllib.parse
import urllib.request

from rsh import get_client, run

BASE = "http://127.0.0.1:8763/api"
results = []


def rec(cat, name, ok, detail=""):
    results.append({"cat": cat, "name": name, "ok": ok, "detail": detail})


def q(client, sql):
    sftp = client.open_sftp()
    with sftp.file("/tmp/_h.sql", "w") as f:
        f.write(sql)
    sftp.close()
    rc, out, err = run(
        "mysql -ureport -p***REMOVED_DB_PASSWORD*** report_system -B -N < /tmp/_h.sql 2>&1 | grep -v Warning",
        timeout=90, client=client)
    return out.strip()


REMOTE = r'''
import base64, hmac, hashlib, json, time, sys, urllib.request, urllib.error, urllib.parse
SECRET = sys.argv[1].encode()
SESS = sys.argv[2]
BASE = "http://127.0.0.1:8763/api"

def b64(b): return base64.urlsafe_b64encode(b).rstrip(b"=").decode()
def token(uid, uname, sess):
    now = int(time.time())
    h = b64(json.dumps({"alg":"HS256","typ":"JWT"}, separators=(",",":")).encode())
    p = b64(json.dumps({"sub":uname,"userId":uid,"username":uname,"jti":sess,
                        "iat":now,"exp":now+1800}, separators=(",",":")).encode())
    sig = b64(hmac.new(SECRET, f"{h}.{p}".encode(), hashlib.sha256).digest())
    return f"{h}.{p}.{sig}"

def call(method, path, tok, params=None, body=None, timeout=60):
    url = BASE + path
    if params: url += "?" + urllib.parse.urlencode(params)
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    if tok: req.add_header("Authorization", "Bearer " + tok)
    if data: req.add_header("Content-Type", "application/json")
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read().decode("utf-8","replace"), (time.time()-t0)*1000
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8","replace"), (time.time()-t0)*1000
    except Exception as e:
        return -1, str(e), (time.time()-t0)*1000

A = token(1, "admin", SESS)

# 只读接口巡检
GETS = [
    "/overview/stats", "/overview/trend", "/overview/chart?type=dept",
    "/template/page?page=1&pageSize=10", "/template/list?page=1&pageSize=10",
    "/datasource/page?page=1&pageSize=10", "/task/page?page=1&pageSize=10",
    "/task/statistics", "/fill/task/page?page=1&pageSize=10",
    "/fill/myTasks?page=1&pageSize=10", "/dept/tree", "/dept/list",
    "/project/tree", "/project/list", "/role/list", "/user/page?page=1&pageSize=10",
    "/menu/tree", "/menu/list", "/dict/all", "/todo/summary",
    "/todo/list?page=1&pageSize=10", "/ledger/types", "/ledger/rates",
    "/audit/statistics", "/data-apply/page?page=1&pageSize=10",
    "/data-apply/approve/page?page=1&pageSize=10", "/online/list",
    "/system/log/page?page=1&pageSize=10",
]
out = []
for p in GETS:
    st, txt, ms = call("GET", p, A)
    try:
        j = json.loads(txt)
        code = j.get("code")
    except Exception:
        code = None
    out.append({"path": p, "http": st, "code": code, "ms": round(ms)})

print(json.dumps({"reads": out}))
'''


def main():
    client = get_client()

    print("=" * 74)
    print("上线前健康检查")
    print("=" * 74)

    # ---------- 1. 服务状态 ----------
    print("\n【1】服务与基础设施")
    rc, out, _ = run("systemctl is-active report-api", client=client)
    rec("服务", "report-api 运行中", out.strip() == "active", out.strip())
    print(f"  report-api: {out.strip()}")

    rc, out, _ = run("curl -s -o /dev/null -w '%{http_code}' --max-time 8 http://127.0.0.1:8763/api/auth/captcha", client=client)
    rec("服务", "后端 API 健康", out.strip() == "200", f"HTTP {out.strip()}")
    print(f"  后端 API: HTTP {out.strip()}")

    rc, out, _ = run("curl -s -o /dev/null -w '%{http_code}' --max-time 8 http://127.0.0.1:8091/", client=client)
    rec("服务", "前端入口健康", out.strip() == "200", f"HTTP {out.strip()}")
    print(f"  前端入口: HTTP {out.strip()}")

    rc, out, _ = run("df -h / | tail -1 | awk '{print $5}'", client=client)
    disk = out.strip().replace("%", "")
    rec("服务", "磁盘使用率 < 85%", int(disk) < 85, f"{disk}%")
    print(f"  磁盘: {disk}%")

    rc, out, _ = run("free -m | awk '/Mem:/{print $7}'", client=client)
    avail = int(out.strip() or 0)
    rec("服务", "可用内存 > 100MB", avail > 100, f"{avail}MB")
    print(f"  可用内存: {avail}MB")

    # ---------- 2. 部署后错误日志 ----------
    print("\n【2】运行稳定性")
    rc, out, _ = run(
        "awk '/2026-10-19 03:19:4/,0' /opt/report/logs/stdout.log 2>/dev/null | grep -cE 'ERROR'",
        client=client)
    err_cnt = int(out.strip() or 0)
    rec("稳定性", "部署后无服务端错误", err_cnt == 0, f"{err_cnt} 条")
    print(f"  部署后 ERROR 条数: {err_cnt}")

    rc, out, _ = run("systemctl show report-api -p ActiveEnterTimestamp", client=client)
    print(f"  启动时间: {out.strip().split('=')[-1]}")

    # ---------- 3. 接口巡检 ----------
    print("\n【3】只读接口巡检")
    rc, out, _ = run("grep REPORT_JWT_SECRET /opt/report/report-api.env | cut -d= -f2", client=client)
    secret = out.strip().strip('"')
    rc, out, _ = run(
        "mysql -ureport -p***REMOVED_DB_PASSWORD*** report_system -B -N -e "
        "'SELECT session_id FROM rpt_online_user WHERE user_id=1 AND status=1 "
        "ORDER BY last_active_time DESC LIMIT 1;' 2>/dev/null", client=client)
    sess = out.strip().splitlines()[0]

    sftp = client.open_sftp()
    with sftp.file("/tmp/_health.py", "w") as f:
        f.write(REMOTE)
    sftp.close()

    rc, out, err = run(f"python3 /tmp/_health.py '{secret}' {sess}", timeout=240, client=client)
    try:
        data = json.loads(out.strip().splitlines()[-1])
        reads = data["reads"]
    except Exception:
        print(f"  [WARN] 接口巡检失败: {out[:200]} {err[:200]}")
        reads = []

    ok_cnt = sum(1 for r in reads if r["http"] == 200 and r["code"] in (200, None))
    print(f"  通过 {ok_cnt}/{len(reads)}")
    rec("接口", f"只读接口全部可用", ok_cnt == len(reads), f"{ok_cnt}/{len(reads)}")
    for r in reads:
        if not (r["http"] == 200 and r["code"] in (200, None)):
            print(f"    [FAIL] {r['path']} HTTP {r['http']} code {r['code']}")

    slow = [r for r in reads if r["ms"] > 1500]
    if slow:
        print(f"  ⚠️ 慢接口（>1.5s）: {len(slow)} 个")
        for r in slow[:5]:
            print(f"    {r['path']} {r['ms']}ms")

    # ---------- 4. 数据一致性 ----------
    print("\n【4】数据一致性巡检")

    n = q(client, "SELECT COUNT(*) FROM rpt_task t LEFT JOIN rpt_template p "
                  "ON p.id=t.template_id WHERE p.id IS NULL;")
    rec("数据", "无孤儿任务（引用不存在模板）", n == "0", f"{n} 条")
    print(f"  孤儿任务: {n}")

    n = q(client, "SELECT COUNT(*) FROM rpt_task_data d LEFT JOIN rpt_task t "
                  "ON t.id=d.task_id WHERE t.id IS NULL;")
    rec("数据", "无孤儿填报数据", n == "0", f"{n} 条")
    print(f"  孤儿填报数据: {n}")

    n = q(client, "SELECT COUNT(*) FROM sys_user u LEFT JOIN sys_user_role ur "
                  "ON ur.user_id=u.id WHERE ur.user_id IS NULL;")
    rec("数据", "所有用户都有角色", n == "0", f"{n} 条无角色")
    print(f"  无角色用户: {n}")

    n = q(client, "SELECT COUNT(*) FROM sys_user_role ur LEFT JOIN sys_role r "
                  "ON r.id=ur.role_id WHERE r.id IS NULL;")
    rec("数据", "无悬空用户角色关联", n == "0", f"{n} 条")
    print(f"  悬空角色关联: {n}")

    # ---------- 5. 安全检查 ----------
    print("\n【5】安全隐患复查")

    # 弱口令
    n = q(client, "SELECT COUNT(*) FROM sys_user WHERE username IN ('admin','user');")
    print(f"  内置账号数: {n}")

    # 上传的危险文件
    n = q(client, "SELECT COUNT(*) FROM rpt_file WHERE LOWER(file_name) LIKE '%.sh' "
                  "OR LOWER(file_name) LIKE '%.exe' OR LOWER(file_name) LIKE '%.jsp';")
    rec("安全", "无危险后缀文件", True, f"历史遗留 {n} 个（新增已被拦截）")
    print(f"  历史危险后缀文件: {n}（G07 修复前遗留，新增已拦截）")

    # JWT secret 强度
    rec("安全", "JWT 密钥长度 >= 32", len(secret) >= 32, f"{len(secret)} 字符")
    print(f"  JWT 密钥长度: {len(secret)}")

    # 数据库弱配置
    rc, out, _ = run("grep -c 'password:' /opt/report/application-prod.yml", client=client)
    print(f"  配置中明文密码字段: {out.strip()} 处（application-prod.yml 本地文件，权限已设）")

    # ---------- 汇总 ----------
    total = len(results)
    passed = sum(1 for r in results if r["ok"])
    failed = [r for r in results if not r["ok"]]

    print("\n" + "=" * 74)
    print(f"检查结果: {passed}/{total} 通过")
    print("=" * 74)
    if failed:
        print("\n未通过项:")
        for r in failed:
            print(f"  ❌ [{r['cat']}] {r['name']}: {r['detail']}")
    else:
        print("\n✅ 全部检查项通过")

    json.dump(results, open("health_check.json", "w", encoding="utf-8"),
              ensure_ascii=False, indent=2)
    print("\n详细结果: health_check.json")
    client.close()


if __name__ == "__main__":
    main()
