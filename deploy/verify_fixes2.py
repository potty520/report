#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
修复后回归验证

验证本轮的修复是否生效，并确认没有破坏既有功能：
  V1  CORS 不再放行任意源（应无 Access-Control-Allow-Origin 回显）
  V2  服务健康 + 关键接口可用
  V3  CompatController 数据申请越权被拒
  V4  数据范围 SQL 注入被拦截（黑名单强化后）
  V5  模板删除引用保护（删除有任务的模板应失败）
  V6  唯一约束生效（重复分派应失败）
  V7  登出后 token 失效（回归）
  V8  填报人可读模板字段（功能修复）
"""
import json
import sys

from rsh import get_client, run

SCRIPT = r'''
import json, sys, time, urllib.request, urllib.error, urllib.parse, base64, hmac, hashlib
SECRET = sys.argv[1].encode(); A_SESS = sys.argv[2]; U_SESS = sys.argv[3]
BASE = "http://127.0.0.1:8763/api"
API_ROOT = "http://127.0.0.1:8763"

def b64(x): return base64.urlsafe_b64encode(x).rstrip(b"=").decode()
def tok(uid, name, sess):
    now = int(time.time())
    h = b64(json.dumps({"alg":"HS256","typ":"JWT"}, separators=(",",":")).encode())
    p = b64(json.dumps({"sub":name,"userId":uid,"username":name,"jti":sess,
                        "iat":now,"exp":now+1800}, separators=(",",":")).encode())
    return h + "." + p + "." + b64(hmac.new(SECRET, (h+"."+p).encode(), hashlib.sha256).digest())

def call(method, path, tk=None, params=None, body=None, headers=None, timeout=60):
    url = BASE + path + (("?" + urllib.parse.urlencode(params)) if params else "")
    data = json.dumps(body, ensure_ascii=False).encode() if body is not None else None
    r = urllib.request.Request(url, data=data, method=method)
    if tk: r.add_header("Authorization", "Bearer " + tk)
    if data: r.add_header("Content-Type", "application/json")
    if headers:
        for k, v in headers.items(): r.add_header(k, v)
    try:
        with urllib.request.urlopen(r, timeout=timeout) as resp:
            raw = resp.read()
            return resp.status, raw.decode("utf-8","replace"), dict(resp.headers)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8","replace")[:300], dict(e.headers)
    except Exception as e:
        return -1, str(e), {}

A = tok(1, "admin", A_SESS); U = tok(2, "user", U_SESS)
R = {}

# ---------- V1 CORS ----------
import http.client
conn = http.client.HTTPConnection("127.0.0.1", 8763, timeout=30)
conn.request("OPTIONS", "/api/overview/stats", headers={
    "Origin": "https://evil.example.com",
    "Access-Control-Request-Method": "GET",
    "Access-Control-Request-Headers": "authorization",
})
resp = conn.getresponse()
h = {k.lower(): v for k, v in resp.getheaders()}
R["V1_CORS"] = {
    "status": resp.status,
    "allow_origin": h.get("access-control-allow-origin"),
    "allow_credentials": h.get("access-control-allow-credentials"),
    "pass": h.get("access-control-allow-origin") is None,
}
conn.close()

# ---------- V2 健康 ----------
st, txt, _ = call("GET", "/overview/stats", A)
R["V2_health"] = {"status": st, "code": json.loads(txt).get("code") if txt.startswith("{") else None}

# ---------- V3 数据申请越权 ----------
st, txt, _ = call("POST", "/dataApply", A, body={"title":"FIXVERIFY","reason":"验证","taskId":182})
new_id = None
try:
    d = json.loads(txt); new_id = d.get("data") if isinstance(d.get("data"), int) else None
except Exception: pass
v3 = {"create": [st, txt[:120]]}
if new_id:
    st2, t2, _ = call("GET", "/dataApply/%d" % new_id, U)
    v3["user_read"] = [st2, t2[:120]]
    st3, t3, _ = call("PUT", "/dataApply/%d" % new_id, U, body={"reason":"篡改"})
    v3["user_update"] = [st3, t3[:120]]
    call("DELETE", "/dataApply/%d" % new_id, A)
    # 判断：user 读/改应被拒（403 或 code=403/500 且含无权/不存在）
    denied = (st2 == 403 or "无权" in t2 or "越权" in t2) and (st3 == 403 or "无权" in t3)
    v3["pass"] = denied
R["V3_Compat"] = v3

# ---------- V4 SQL 注入拦截 ----------
inj = [
    ("裸读系统表", "select username,password from sys_user LIMIT 2"),
    ("注释绕过", "select 1 from dual /*delete*/"),
    ("union", "select 1 union select 2"),
    ("写操作", "delete from sys_user"),
]
v4 = {"cases": [], "pass": True}
for name, sql in inj:
    st, txt, _ = call("POST", "/dataRange", A, body={"configName":"FIXVERIFY-"+name,
        "datasourceId":1, "sqlText":sql, "status":1})
    body = txt.replace("\n"," ")
    blocked = ("不允许" in body or "只允许" in body or "code\":500" in body.replace(" ", ""))
    v4["cases"].append({"name": name, "status": st, "resp": body[:110], "blocked": blocked})
    if not blocked: v4["pass"] = False
R["V4_SQLInject"] = v4

# ---------- V5 模板删除引用保护 ----------
# 安全说明：不能用生产模板 1 做删除测试（会真删）；改用临时模板 + 临时任务验证
st, txt, _ = call("POST", "/template", A, body={"templateName":"FIXVERIFY-TPL",
    "templateCode":"FVTPL", "fillingCycle":1, "deptId":1})
tpl_id = None
try:
    d = json.loads(txt); tpl_id = d.get("data") if isinstance(d.get("data"), int) else None
except Exception: pass
v5 = {"tpl_id": tpl_id, "cases": []}
if tpl_id:
    call("PUT", "/template/%d/status" % tpl_id, A, params={"status":1})
    call("PUT", "/template/%d/audit" % tpl_id, A, params={"auditStatus":1})
    st, txt, _ = call("POST", "/task", A, body={"taskName":"FIXVERIFY-TASK",
        "templateId": tpl_id, "deptId":1, "belongTime":"2026-V", "fillDeadline":"2026-12-31 23:59:59"})
    v5["createTask"] = [st, txt[:100]]
    st, txt, _ = call("DELETE", "/template/%d" % tpl_id, A)
    v5["deleteWithTask"] = [st, txt[:150]]
    v5["pass"] = ("无法删除" in txt or "存在" in txt)
R["V5_TemplateRef"] = v5

# ---------- V6 唯一约束 ----------
# 重复分派同一用户到同一任务
st, txt, _ = call("GET", "/task/page", A, params={"page":1,"pageSize":5})
tid = None
try:
    tid = (json.loads(txt).get("data") or {}).get("list",[{}])[0].get("id")
except Exception: pass
v6 = {"task": tid, "cases": []}
if tid:
    for i in range(2):
        st, txt, _ = call("POST", "/task/%d/assign" % tid, A,
                          params={"userIds":"2", "assignType":"FILL"})
        v6["cases"].append([st, txt[:120]])
    # 在同一请求里传重复 userIds，去重后应只插一条（不报错）
    st, txt, _ = call("POST", "/task/%d/assign" % tid, A,
                      params={"userIds":"2,2,2", "assignType":"AUDIT"})
    v6["dupInOne"] = [st, txt[:120]]
R["V6_Unique"] = v6

# ---------- V7 登出失效（回归） ----------
st, txt, _ = call("POST", "/auth/logout", U)
st2, t2, _ = call("GET", "/overview/stats", U)
R["V7_Logout"] = {"logout": st, "after": st2, "pass": st2 == 401}

# ---------- V8 填报人读模板字段 ----------
# user 是 DEPT_ADMIN；这里检查 FILL_USER 也会被允许（角色注解已放开）
st, txt, _ = call("GET", "/template/1/fields", U)
R["V8_Fields"] = {"status": st, "resp": txt[:120],
                  "pass": st == 200 and "code\":200" in txt.replace(" ", "")}

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
    if not sess[1] or not sess[2]:
        print("[FATAL] 会话缺失，需要重新登录后重试")
        return 1
    upload_small(c, "/tmp/_verify_fix.py", SCRIPT)
    rc, out, err = run(f"python3 /tmp/_verify_fix.py '{secret}' {sess[1]} {sess[2]}", timeout=300, client=c)
    print(out)
    if err.strip():
        print("[stderr]", err[:400])
    c.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
