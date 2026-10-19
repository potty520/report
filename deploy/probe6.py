#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""审计取证 v6：用正确路径与正确 HTTP 方法复测"""
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
            raw = resp.read()
            ct = resp.headers.get("Content-Type","")
            return resp.status, raw.decode("utf-8","replace"), len(raw), ct
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8","replace")[:250], 0, ""
    except Exception as e:
        return -1, str(e), 0, ""

A = tok(1, "admin", A_SESS); U = tok(2, "user", U_SESS)

print("=" * 64)
print("[H2] 填报数据导出 —— 归属校验实测")
print("=" * 64)
# 找 admin 建的任务（admin 不是填报人）
st, txt, _, _ = call("GET", "/task/page", A, {"page":1,"pageSize":50})
tasks = []
try:
    tasks = (json.loads(txt).get("data") or {}).get("list") or []
except Exception: pass
print("  可见任务数:", len(tasks))

# 用 AUDIT-TASK 那条（上轮创建，模板健康）
target = None
for t in tasks:
    if "AUDIT-TASK" in (t.get("taskName") or ""):
        target = t; break
if not target and tasks:
    target = tasks[0]
tid = target["id"] if target else None
print("  测试任务 id=%s name=%s" % (tid, target.get("taskName") if target else None))

if tid:
    # user 是填报员，但不是这个任务的分派人 → 应被拒
    st, txt, size, ct = call("GET", "/fill/export", U, {"taskId": tid})
    print()
    print("  【关键】user(非该任务填报人) GET /fill/export?taskId=%d" % tid)
    print("    HTTP %s | %d bytes | Content-Type: %s" % (st, size, ct))
    print("    内容: %r" % txt[:180])
    is_leak = (st == 200 and size > 100 and "code" not in txt[:15] and "xlsx" in ct.lower())
    print("    ==> 判定: %s" % ("❌ 越权导出成功（无归属校验）" if is_leak else "✅ 被拒绝或失败"))

    st, txt, size, _ = call("GET", "/fill/%d/target-rows" % tid, U)
    print()
    print("  user GET /fill/%d/target-rows" % tid)
    print("    HTTP %s %s" % (st, txt[:150]))

print()
print("=" * 64)
print("[H5] 数据范围预览（正确方法：GET /dataRange/{id}/preview）")
print("=" * 64)
for cid in [1, 2]:
    st, txt, size, _ = call("GET", "/dataRange/%d/preview" % cid, U)
    print("  GET /dataRange/%d/preview -> HTTP %s %s" % (cid, st, txt[:180]))

print()
print("  --- 黑名单校验逻辑（通过 save 配置写入恶意 SQL 再 preview）---")
# 用 admin 建一个含大写 LIMIT 的配置，看能否读系统表
evil_sql = "select username, password from sys_user LIMIT 3"
st, txt, _, _ = call("POST", "/dataRange", A, body={
    "configName": "AUDIT-EVIL", "datasourceId": 1, "sqlText": evil_sql, "status": 1})
print("  admin 保存含大写 LIMIT 的SQL -> HTTP %s %s" % (st, txt[:150]))

st, txt, _, _ = call("GET", "/dataRange/list", A)
cid2 = None
try:
    for it in (json.loads(txt).get("data") or []):
        if it.get("configName") == "AUDIT-EVIL": cid2 = it["id"]; break
except Exception: pass
print("  新配置 id =", cid2)
if cid2:
    st, txt, size, _ = call("GET", "/dataRange/%d/preview" % cid2, U)
    print("  user GET preview -> HTTP %s %s" % (st, txt[:250]))

print()
print("=" * 64)
print("[S2] 数据申请：正确路径与参数")
print("=" * 64)
st, txt, _, _ = call("GET", "/dataApply/page", A, {"page":1,"pageSize":3})
print("  列表 -> HTTP %s %s" % (st, txt[:200]))
st, txt, _, _ = call("GET", "/dataApply/myApplies", A, {"page":1,"pageSize":3})
print("  我的申请 -> HTTP %s %s" % (st, txt[:160]))
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
    upload_small(c, "/tmp/_v6.py", SCRIPT)
    rc, out, err = run(f"python3 /tmp/_v6.py '{secret}' {sess[1]} {sess[2]}", timeout=300, client=c)
    print(out)
    if err.strip():
        print("[stderr]", err[:500])
    c.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
