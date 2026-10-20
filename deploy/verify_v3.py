#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""V3 数据申请越权验证（taskId 与 reason 均为 query 参数）"""
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

def call(method, path, tk=None, params=None, body=None):
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
R = {}

# admin 创建申请（query 参数）
st, txt = call("POST", "/dataApply", A, params={"taskId": 182, "reason": "FIXVERIFY-越权验证"})
R["create"] = [st, txt[:140]]

# 找新申请
st, txt = call("GET", "/dataApply/approveList", A, params={"page":1,"pageSize":20})
new_id = None
try:
    j = json.loads(txt)
    lst = (j.get("data") or {}).get("list") or j.get("data") or []
    for it in lst:
        if it.get("reason") == "FIXVERIFY-越权验证":
            new_id = it.get("id"); break
except Exception: pass
R["new_id"] = new_id

if new_id:
    st2, t2 = call("GET", "/dataApply/%d" % new_id, U)
    R["user_read_other"] = [st2, t2[:140]]
    st3, t3 = call("PUT", "/dataApply/%d" % new_id, U, body={"reason": "被user篡改"})
    R["user_update_other"] = [st3, t3[:140]]
    st4, t4 = call("DELETE", "/dataApply/%d" % new_id, U)
    R["user_delete_other"] = [st4, t4[:140]]
    # admin 回查，确认未被篡改
    st5, t5 = call("GET", "/dataApply/%d" % new_id, A)
    R["admin_recheck"] = [st5, t5[:160]]
    call("DELETE", "/dataApply/%d" % new_id, A)
    R["cleanup"] = "done"

def denied(st_, t_):
    return st_ == 403 or "无权" in t_ or "不存在" in t_
if new_id:
    R["pass"] = denied(*R["user_read_other"]) and denied(*R["user_update_other"]) and denied(*R["user_delete_other"])

print(json.dumps(R, ensure_ascii=False, indent=2))
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
    upload_small(c, "/tmp/_v3.py", SCRIPT)
    rc, out, err = run(f"python3 /tmp/_v3.py '{secret}' {sess[1]} {sess[2]}", timeout=300, client=c)
    print(out)
    if err.strip():
        print("[stderr]", err[:400])
    c.close()


if __name__ == "__main__":
    main()
