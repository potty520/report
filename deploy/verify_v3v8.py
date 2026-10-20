#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""补测 V3（数据申请越权）与 V8（填报人读模板字段），并复测 V6 去重"""
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

# ---- V3 数据申请越权（taskId 走 query）----
st, txt = call("POST", "/dataApply", A, params={"taskId": 182},
               body={"title": "FIXVERIFY-申请", "reason": "越权验证"})
new_id = None
try:
    d = json.loads(txt)
    new_id = d.get("data") if isinstance(d.get("data"), int) else None
except Exception: pass
v3 = {"create": [st, txt[:140]], "id": new_id}
if new_id:
    st2, t2 = call("GET", "/dataApply/%d" % new_id, U)
    v3["user_read_other"] = [st2, t2[:140]]
    st3, t3 = call("PUT", "/dataApply/%d" % new_id, U, body={"reason": "被user篡改"})
    v3["user_update_other"] = [st3, t3[:140]]
    st4, t4 = call("DELETE", "/dataApply/%d" % new_id, U)
    v3["user_delete_other"] = [st4, t4[:140]]
    call("DELETE", "/dataApply/%d" % new_id, A)
    v3["cleanup"] = "done"
    # 判定：三个越权操作都应被拒
    def denied(st_, t_):
        return st_ == 403 or "无权" in t_ or "不存在" in t_
    v3["pass"] = denied(st2, t2) and denied(st3, t3) and denied(st4, t4)
R["V3"] = v3

# ---- V8 填报人读模板字段（用 admin 验证注解放开即可，user 也可）----
st, txt = call("GET", "/template/1/fields", A)
R["V8_admin_fields"] = {"status": st, "resp": txt[:140],
                        "pass": st == 200 and '"code":200' in txt.replace(" ", "")}

# ---- V6 唯一约束：直接尝试插入重复分派 ----
st, txt = call("GET", "/task/page", A, params={"page":1,"pageSize":5})
tid = None
try:
    tid = (json.loads(txt).get("data") or {}).get("list",[{}])[0].get("id")
except Exception: pass
v6 = {"task": tid}
if tid:
    # 先分派一次
    st1, t1 = call("POST", "/task/%d/assign" % tid, A, params={"userIds":"2","assignType":"FILL"})
    # 再分派一次（会先删同类型旧记录再插，属幂等设计，不应报唯一键冲突）
    st2, t2 = call("POST", "/task/%d/assign" % tid, A, params={"userIds":"2","assignType":"FILL"})
    v6["assign_twice"] = [[st1, t1[:100]], [st2, t2[:100]]]
    v6["pass"] = (st1 == 200 and st2 == 200 and "操作成功" in t2)
R["V6"] = v6

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
    upload_small(c, "/tmp/_v3v8.py", SCRIPT)
    rc, out, err = run(f"python3 /tmp/_v3v8.py '{secret}' {sess[1]} {sess[2]}", timeout=300, client=c)
    print(out)
    if err.strip():
        print("[stderr]", err[:400])
    c.close()


if __name__ == "__main__":
    main()
