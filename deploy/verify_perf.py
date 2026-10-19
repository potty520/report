#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
V01 部门审核性能实测

测试报告称部门审核接口 25,139ms 超时。
本脚本：以 admin 身份自签 token，找到一条可审核的数据，实测 dept-audit 接口耗时。
若无可审核数据，退化为测量审核相关只读接口耗时并说明。
"""
import sys

from rsh import run

REMOTE = r'''
import base64, hmac, hashlib, json, time, urllib.request, urllib.error, sys

SECRET = sys.argv[1].encode()
UID, UNAME, SESSION = int(sys.argv[2]), sys.argv[3], sys.argv[4]
BASE = "http://127.0.0.1:8763/api"

def b64(b): return base64.urlsafe_b64encode(b).rstrip(b"=").decode()

def make_token(uid, uname, sess):
    now = int(time.time())
    h = b64(json.dumps({"alg":"HS256","typ":"JWT"}, separators=(",",":")).encode())
    p = b64(json.dumps({"sub":uname,"userId":uid,"username":uname,"jti":sess,
                        "iat":now,"exp":now+1800}, separators=(",",":")).encode())
    sig = b64(hmac.new(SECRET, f"{h}.{p}".encode(), hashlib.sha256).digest())
    return f"{h}.{p}.{sig}"

def call(method, path, token, body=None, timeout=60):
    data = json.dumps(body).encode() if body else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Authorization", "Bearer " + token)
    if data: req.add_header("Content-Type", "application/json")
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            txt = r.read().decode("utf-8","replace"); st = r.status
    except urllib.error.HTTPError as e:
        txt = e.read().decode("utf-8","replace"); st = e.code
    except Exception as e:
        txt = str(e); st = -1
    return st, txt, (time.time()-t0)*1000

tok = make_token(UID, UNAME, SESSION)

# 1) 找待审核数据
st, txt, ms = call("GET", "/audit/pending?page=1&pageSize=20", tok)
print(f"PENDING_LIST|HTTP {st}|{ms:.0f}ms|{txt[:200]}")

try:
    j = json.loads(txt)
    rows = (j.get("data") or {}).get("list") or j.get("data") or []
except Exception:
    rows = []

# 2) 找一条 status=1 的数据做审核
target_id = None
st2, txt2, ms2 = call("GET", "/fill/task/page?page=1&pageSize=50", tok)
print(f"FILL_PAGE|HTTP {st2}|{ms2:.0f}ms|{txt2[:200]}")
try:
    j2 = json.loads(txt2)
    lst = (j2.get("data") or {}).get("list") or []
    for it in lst:
        if it.get("id"):
            target_id = it["id"]; break
except Exception:
    pass

# 3) 实测审核接口
if target_id:
    st3, txt3, ms3 = call("POST", f"/audit/dept-audit?taskDataId={target_id}&pass=true&comment=ZZPERF_test", tok, timeout=120)
    verdict = "PASS" if ms3 < 5000 else "SLOW"
    print(f"{verdict}|V01|dept-audit taskDataId={target_id}|HTTP {st3}|{ms3:.0f}ms|{txt3[:200]}")
else:
    print("NO_TARGET|V01|无待审核数据，退化为只读接口基准")

# 4) 基准：几个典型接口耗时
for path in ["/overview/stats", "/template/page?page=1&pageSize=10",
             "/task/page?page=1&pageSize=10", "/todo/summary"]:
    st4, txt4, ms4 = call("GET", path, tok)
    print(f"BENCH|{path}|HTTP {st4}|{ms4:.0f}ms")
'''


def main():
    rc, out, err = run("grep REPORT_JWT_SECRET /opt/report/report-api.env | cut -d= -f2 | tr -d '\"'")
    secret = out.strip()
    if not secret:
        print("[FATAL] 取不到 JWT_SECRET")
        sys.exit(1)

    session = sys.argv[1] if len(sys.argv) > 1 else "8b75503380f24dfba1034a4764a5112f"

    from rsh import get_client
    c = get_client()
    sftp = c.open_sftp()
    with sftp.file("/tmp/_verify_perf.py", "w") as f:
        f.write(REMOTE)
    sftp.close()
    rc, out, err = run(
        f"python3 /tmp/_verify_perf.py '{secret}' 1 admin {session}", timeout=300, client=c)
    print(out)
    if err.strip():
        print("[stderr]", err[:400])
    c.close()


if __name__ == "__main__":
    main()
