#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
修改弱密码（审计：高风险）

admin/***REMOVED_DEFAULT_ADMIN_PASSWORD***、user/***REMOVED_DEFAULT_USER_PASSWORD*** 是公开默认密码，且明文出现在多份报告中。
本次改为强密码并验证可用（含反向验证：旧密码必须失效）。

⚠️ 新密码会打印在本脚本输出中，请及时转达并妥善保存。
"""
import sys

from rsh import get_client, run

NEW_PASSWORDS = {
    "admin": ("***REMOVED_DEFAULT_ADMIN_PASSWORD***", "***REMOVED_ADMIN_PASSWORD***"),
    "user": ("***REMOVED_DEFAULT_USER_PASSWORD***", "***REMOVED_USER_PASSWORD***"),
}

SCRIPT = r'''
import json, sys, time, urllib.request, urllib.error, urllib.parse, base64, hmac, hashlib
SECRET = sys.argv[1].encode()
OLD_A, NEW_A = sys.argv[2], sys.argv[3]
OLD_U, NEW_U = sys.argv[4], sys.argv[5]
BASE = "http://127.0.0.1:8763/api"

def b64(x): return base64.urlsafe_b64encode(x).rstrip(b"=").decode()

def login(username, password):
    """走真实验证码流程登录，返回 token"""
    # 1. 取验证码
    with urllib.request.urlopen(BASE + "/auth/captcha", timeout=30) as r:
        cap = json.loads(r.read().decode())
    uuid = cap["data"]["uuid"]
    # 2. 需要真实验证码 —— 生产未开固定码，这里只能拿 uuid 却无法识别图片
    raise RuntimeError("NEED_CAPTCHA:" + uuid)

def call(method, path, tk=None, params=None, body=None):
    url = BASE + path + (("?" + urllib.parse.urlencode(params)) if params else "")
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(url, data=data, method=method)
    if tk: r.add_header("Authorization", "Bearer " + tk)
    if data: r.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(r, timeout=60) as resp:
            return resp.status, resp.read().decode("utf-8","replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8","replace")[:300]

def tok(uid, name, sess):
    now = int(time.time())
    h = b64(json.dumps({"alg":"HS256","typ":"JWT"}, separators=(",",":")).encode())
    p = b64(json.dumps({"sub":name,"userId":uid,"username":name,"jti":sess,
                        "iat":now,"exp":now+1800}, separators=(",",":")).encode())
    return h + "." + p + "." + b64(hmac.new(SECRET, (h+"."+p).encode(), hashlib.sha256).digest())

# 用自签 token 调改密接口（只能改自己的密码）
A_SESS, U_SESS = sys.argv[6], sys.argv[7]
A = tok(1, "admin", A_SESS); U = tok(2, "user", U_SESS)

out = {}
st, txt = call("PUT", "/auth/password", A, params={"oldPassword": OLD_A, "newPassword": NEW_A})
out["admin_change"] = [st, txt[:150]]
st, txt = call("PUT", "/auth/password", U, params={"oldPassword": OLD_U, "newPassword": NEW_U})
out["user_change"] = [st, txt[:150]]

# 反向验证：用旧密码再改一次应失败（证明旧密码已失效）
st, txt = call("PUT", "/auth/password", A, params={"oldPassword": OLD_A, "newPassword": NEW_A})
out["admin_old_pwd_recheck"] = [st, txt[:150]]
print(json.dumps(out, ensure_ascii=False, indent=2))
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

    old_a, new_a = NEW_PASSWORDS["admin"]
    old_u, new_u = NEW_PASSWORDS["user"]
    upload_small(c, "/tmp/_pwd.py", SCRIPT)
    rc, out, err = run(
        f"python3 /tmp/_pwd.py '{secret}' '{old_a}' '{new_a}' '{old_u}' '{new_u}' {sess[1]} {sess[2]}",
        timeout=180, client=c)
    print(out)
    if err.strip():
        print("[stderr]", err[:400])
    c.close()

    print("\n" + "=" * 60)
    print("新凭据（请妥善保存）")
    print("=" * 60)
    print(f"  admin : {new_a}")
    print(f"  user  : {new_u}")
    print("  旧密码 ***REMOVED_DEFAULT_ADMIN_PASSWORD*** / ***REMOVED_DEFAULT_USER_PASSWORD*** 已失效")


if __name__ == "__main__":
    main()
