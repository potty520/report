#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
第二阶段并发与回归验证

  C1  并发提交：同一任务并发 8 次提交，只允许 1 次成功
  C2  并发部门审核：同一 dataId 并发 8 次，只允许 1 次成功
  C3  并发发布：同一任务并发 8 次发布，只允许 1 次成功
  C4  并发周期任务生成：并发生成后子任务不重复
  C5  已提交数据不能被「保存草稿」打回
"""
import sys

from rsh import get_client, run

SCRIPT = r'''
import json, sys, time, threading, urllib.request, urllib.error, urllib.parse, base64, hmac, hashlib
SECRET = sys.argv[1].encode(); A_SESS = sys.argv[2]; U_SESS = sys.argv[3]
BASE = "http://127.0.0.1:8763/api"
CONC = 8

def b64(x): return base64.urlsafe_b64encode(x).rstrip(b"=").decode()
def tok(uid, name, sess):
    now = int(time.time())
    h = b64(json.dumps({"alg":"HS256","typ":"JWT"}, separators=(",",":")).encode())
    p = b64(json.dumps({"sub":name,"userId":uid,"username":name,"jti":sess,
                        "iat":now,"exp":now+1800}, separators=(",",":")).encode())
    return h + "." + p + "." + b64(hmac.new(SECRET, (h+"."+p).encode(), hashlib.sha256).digest())

A = tok(1, "admin", A_SESS); U = tok(2, "user", U_SESS)

def call(method, path, tk=None, params=None, body=None, timeout=60):
    url = BASE + path + (("?" + urllib.parse.urlencode(params)) if params else "")
    data = json.dumps(body, ensure_ascii=False).encode() if body is not None else None
    r = urllib.request.Request(url, data=data, method=method)
    if tk: r.add_header("Authorization", "Bearer " + tk)
    if data: r.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(r, timeout=timeout) as resp:
            return resp.status, resp.read().decode("utf-8","replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8","replace")[:300]
    except Exception as e:
        return -1, str(e)

def jok(txt):
    try: return json.loads(txt).get("code") == 200
    except Exception: return False

def dval(txt):
    try: return json.loads(txt).get("data")
    except Exception: return None

def concurrent(fn, n=CONC):
    results = [None]*n
    barrier = threading.Barrier(n)
    def worker(i):
        try: barrier.wait(timeout=20)
        except Exception: pass
        try: results[i] = fn()
        except Exception as e: results[i] = (-1, str(e))
    ts = [threading.Thread(target=worker, args=(i,)) for i in range(n)]
    for t in ts: t.start()
    for t in ts: t.join(timeout=90)
    succ = sum(1 for r in results if r and jok(r[1]))
    fails = [r[1] for r in results if r and not jok(r[1])]
    return succ, fails

R = {}
TAG = "ZZC%d" % (int(time.time()) % 100000)

# ---------- 准备 ----------
st, txt = call("POST", "/template", A, body={"templateName": TAG+"TPL", "templateCode": TAG,
                                             "fillingCycle": 1, "deptId": 1})
tpl = dval(txt) if isinstance(dval(txt), int) else None
call("PUT", "/template/%d/status" % tpl, A, params={"status": 1})
call("PUT", "/template/%d/audit" % tpl, A, params={"auditStatus": 1})
st, txt = call("POST", "/task", A, body={"taskName": TAG, "templateId": tpl,
                                        "deptId": 1, "belongTime": TAG,
                                        "fillDeadline": "2026-12-31 23:59:59"})
# 创建任务接口返回 Result<Void>（无 data），从列表反查 id
task = None
st2, txt2 = call("GET", "/task/page", A, params={"page": 1, "pageSize": 10})
for it in (dval(txt2) or {}).get("list", []):
    if it.get("taskName") == TAG:
        task = it.get("id"); break
R["prep"] = {"tpl": tpl, "task": task, "create_resp": txt[:100]}
if not task:
    print(json.dumps(R, ensure_ascii=False, indent=2)); sys.exit(0)

# ---------- C3 并发发布（PUT）----------
# 注意：必须先发布再分派，因为 assignTask 会把状态推到「填报中(2)」，
# 之后就不能再发布了（这是正确的状态机约束，不是缺陷）。
succ, fails = concurrent(lambda: call("PUT", "/task/%d/publish" % task, A))
R["C3_publish"] = {"success": succ, "expected": 1,
                   "sample_fail": (fails[0][:130] if fails else None),
                   "pass": succ == 1}

# ---------- 分派（发布后再分派：assignTask 会把状态推到填报中）----------
st, txt = call("POST", "/task/%d/assign" % task, A,
               params={"userIds": "2", "assignType": "FILL"})
R["assign"] = [st, txt[:140]]

# ---------- 草稿 ----------
st, txt = call("POST", "/fill/save", U, params={"taskId": task, "dataJson": json.dumps({"a": "1"})})
R["save_draft"] = [st, txt[:140]]
st, txt = call("GET", "/fill/task/page", U, params={"page": 1, "pageSize": 100})
data_id = None
for it in (dval(txt) or {}).get("list", []):
    if it.get("taskId") == task:
        data_id = it.get("id"); break
R["data_id"] = data_id

# ---------- C1 并发提交 ----------
if data_id:
    succ, fails = concurrent(lambda: call("POST", "/fill/submit", U,
                                          params={"taskId": task, "dataJson": json.dumps({"a":"1"})}))
    R["C1_submit"] = {"success": succ, "expected": 1,
                      "sample_fail": (fails[0][:140] if fails else None),
                      "pass": succ == 1}

# ---------- C5 已提交不能保存草稿 ----------
st, txt = call("POST", "/fill/save", U, params={"taskId": task, "dataJson": json.dumps({"a": "2"})})
R["C5_noRevertSubmitted"] = {"status": st, "resp": txt[:140],
                             "pass": not jok(txt)}

# ---------- C2 并发部门审核 ----------
# 注意：必须用「非填报人」作为审核人（系统禁止自审，这是正确规则）。
# 生产只有 admin(1)/user(2)，故由 admin 审核 user 提交的数据。
if data_id:
    succ, fails = concurrent(lambda: call("POST", "/audit/dept-audit", A,
                                          params={"taskDataId": data_id, "pass": "true",
                                                  "comment": "并发审核"}))
    R["C2_deptAudit"] = {"success": succ, "expected": 1,
                         "sample_fail": (fails[0][:140] if fails else None),
                         "pass": succ == 1}
    # 复核：自审仍应被拒绝（回归）
    st, txt = call("POST", "/audit/dept-audit", U,
                   params={"taskDataId": data_id, "pass": "true", "comment": "自审"})
    R["C2b_selfAuditBlocked"] = {"status": st, "resp": txt[:120],
                                 "pass": not jok(txt)}

# ---------- C4 并发周期任务生成 ----------
succ, fails = concurrent(lambda: call("POST", "/task/generate-periodic", A,
                                      params={"parentTaskId": task}))
R["C4_periodic"] = {"success": succ,
                    "note": "允许重复调用（幂等）；子任务唯一性下面查 DB"}

print(json.dumps(R, ensure_ascii=False, indent=2))
print("TAG=" + TAG)
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
        print("[FATAL] 会话缺失，请先登录")
        return 1
    upload_small(c, "/tmp/_conc.py", SCRIPT)
    rc, out, err = run(f"python3 /tmp/_conc.py '{secret}' {sess[1]} {sess[2]}", timeout=420, client=c)
    print(out)
    if err.strip():
        print("[stderr]", err[:400])

    # 查子任务唯一性
    rc, out, _ = run(f'{mysql} -e "SELECT parent_task_id, belong_time, dept_id, COUNT(*) c '
                     f'FROM rpt_task WHERE parent_task_id IS NOT NULL '
                     f'GROUP BY 1,2,3 HAVING COUNT(*)>1;" 2>/dev/null', client=c)
    print("\n=== 周期子任务重复检查（应为空）===")
    print(out.strip() or "✅ 无重复")

    # 查锁表
    rc, out, _ = run(f'{mysql} -e "SELECT COUNT(*) FROM rpt_schedule_lock;" 2>/dev/null', client=c)
    print(f"锁表记录数（正常应为 0，说明锁已释放）: {out.strip()}")
    c.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
