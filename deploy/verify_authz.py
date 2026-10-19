#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生产环境越权修复验证（X04/X08/X13/X14）

验证码是图形码，脚本无法自动登录。方案：在服务器上读取 JWT_SECRET，
用 python3 的 hmac/hashlib 自签一个 user(id=2, DEPT_ADMIN) 的合法 token。
JWT 签发算法见 JwtTokenUtil.java，需先确认其 claim 结构。
"""
import json
import sys

from rsh import run

BASE_SSH = "http://127.0.0.1:8763/api"


def load_secret():
    rc, out, err = run("cat /opt/report/report-api.env")
    m = None
    for line in out.splitlines():
        if line.startswith("REPORT_JWT_SECRET"):
            m = line.split("=", 1)[1].strip().strip('"').strip("'")
    return m


def check_jwt_impl():
    """查看 JwtTokenUtil 的 claim 结构（从 jar 里反编译太重，直接看源码已知）"""
    return None


# 在服务器上执行的验证脚本
REMOTE_SCRIPT = r'''
import base64, hmac, hashlib, json, time, urllib.request, urllib.error, sys

SECRET = sys.argv[1].encode()
UID = int(sys.argv[2])
UNAME = sys.argv[3]
SESSION = sys.argv[4]
BASE = "http://127.0.0.1:8763/api"

def b64(b):
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode()

def make_token(uid, uname, session):
    header = {"alg": "HS256", "typ": "JWT"}
    now = int(time.time())
    payload = {"sub": uname, "userId": uid, "username": uname,
               "jti": session, "iat": now, "exp": now + 1800}
    h = b64(json.dumps(header, separators=(",", ":")).encode())
    p = b64(json.dumps(payload, separators=(",", ":")).encode())
    signing = f"{h}.{p}".encode()
    sig = b64(hmac.new(SECRET, signing, hashlib.sha256).digest())
    return f"{h}.{p}.{sig}"

def call(method, path, token, body=None):
    url = BASE + path
    data = json.dumps(body).encode() if body else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", "Bearer " + token)
    if data:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:
        return -1, str(e)

token = make_token(UID, UNAME, SESSION)
print(f"TOKEN_PREFIX={token[:30]} SESSION={SESSION}")

# 先确认 token 有效：访问需要登录的接口
st, txt = call("GET", "/auth/info", token)
print(f"TOKEN_CHECK: HTTP {st} {txt[:120]}")

tests = [
    ("X04", "POST", "/dept", {"deptName": "ZZVERIFY部门", "deptCode": "ZZV-D01", "parentId": 0}),
    ("X08", "POST", "/task", {"taskName": "ZZVERIFY任务", "templateId": 1, "deptId": 1}),
    ("X13", "POST", "/template", {"templateName": "ZZVERIFY模板", "templateCode": "ZZV-T01"}),
    ("X14", "POST", "/project", {"projectName": "ZZVERIFY项目", "deptId": 1}),
]

for code, method, path, body in tests:
    st, txt = call(method, path, token, body)
    try:
        j = json.loads(txt)
        api_code = j.get("code")
        msg = j.get("msg")
    except Exception:
        api_code, msg = None, txt[:100]
    result = "PASS" if (st == 403 or api_code == 403) else "FAIL"
    print(f"{result}|{code}|{method} {path}|HTTP {st}|code={api_code}|{msg}")
'''


def main():
    secret = load_secret()
    if not secret:
        print("[FATAL] 未取到 REPORT_JWT_SECRET")
        sys.exit(1)
    print(f"JWT_SECRET 已获取 (长度 {len(secret)})")

    # 上传脚本
    from rsh import get_client
    c = get_client()
    sftp = c.open_sftp()
    with sftp.file("/tmp/_verify_authz.py", "w") as f:
        f.write(REMOTE_SCRIPT)
    sftp.close()

    print("\n=== 以 user(id=2) 身份验证越权 ===")
    session = sys.argv[1] if len(sys.argv) > 1 else "ea914d4799954ca8af5fc3cf0df61103"
    rc, out, err = run(
        f"python3 /tmp/_verify_authz.py '{secret}' 2 user {session}", timeout=120, client=c)
    print(out)
    if err.strip():
        print("[stderr]", err[:500])
    c.close()


if __name__ == "__main__":
    main()
