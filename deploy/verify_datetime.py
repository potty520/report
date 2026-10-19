#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
验证 LocalDateTime 反序列化缺陷

测试报告未覆盖此问题，本次业务流测试中发现：
  POST /task 的 fillDeadline 传 "yyyy-MM-dd HH:mm:ss" 会失败，
  因为 Jackson 的 spring.jackson.date-format 只对 java.util.Date 生效，
  对 java.time.LocalDateTime 需要 JavaTimeModule + 显式 pattern 配置。

本脚本对比两种格式，确认缺陷存在与否。
"""
from rsh import get_client, run

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
                        "iat":now,"exp":now+3600}, separators=(",",":")).encode())
    sig = b64(hmac.new(SECRET, f"{h}.{p}".encode(), hashlib.sha256).digest())
    return f"{h}.{p}.{sig}"

def call(method, path, tok, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Authorization", "Bearer " + tok)
    if data: req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.status, r.read().decode("utf-8","replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8","replace")

A = token(1, "admin", SESS)
base = {"templateId": 108, "deptId": 1, "fillingCycle": 1, "belongTime": "20261019"}

cases = [
    ("空格格式 yyyy-MM-dd HH:mm:ss", "2026-12-31 23:59:59"),
    ("ISO 格式 yyyy-MM-ddTHH:mm:ss", "2026-12-31T23:59:59"),
    ("ISO 带毫秒", "2026-12-31T23:59:59.000"),
]
for name, val in cases:
    body = dict(base, taskName=f"ZZFLOW-FMT-{name[:4]}", fillDeadline=val)
    st, txt = call("POST", "/task", A, body)
    try:
        j = json.loads(txt)
        print(f"{name:32s} | HTTP {st} | code={j.get('code')} | {str(j.get('msg'))[:60]}")
    except Exception:
        print(f"{name:32s} | HTTP {st} | {txt[:100]}")

# 清理本次 FMT 测试任务
print("---")
for name, _ in cases:
    tn = f"ZZFLOW-FMT-{name[:4]}"
    st, txt = call("GET", "/task/page?page=1&pageSize=200", A)
    try:
        for it in (json.loads(txt).get("data") or {}).get("list", []):
            if it.get("taskName") == tn:
                sid = it["id"]
                st2, t2 = call("DELETE", f"/task/{sid}", A)
                print(f"cleanup {tn} (id={sid}) -> {t2[:60]}")
    except Exception as e:
        print("cleanup err", e)
'''


def main():
    c = get_client()
    rc, out, _ = run("grep REPORT_JWT_SECRET /opt/report/report-api.env | cut -d= -f2", client=c)
    secret = out.strip().strip('"')
    rc, out, _ = run(
        "mysql -ureport -p***REMOVED_DB_PASSWORD*** report_system -B -N -e "
        "'SELECT session_id FROM rpt_online_user WHERE user_id=1 AND status=1 "
        "ORDER BY last_active_time DESC LIMIT 1;' 2>/dev/null", client=c)
    sess = out.strip().splitlines()[0]
    sftp = c.open_sftp()
    with sftp.file("/tmp/_fmt.py", "w") as f:
        f.write(REMOTE)
    sftp.close()
    rc, out, err = run(f"python3 /tmp/_fmt.py '{secret}' {sess}", timeout=180, client=c)
    print(out)
    if err.strip():
        print("[stderr]", err[:400])
    c.close()


if __name__ == "__main__":
    main()
