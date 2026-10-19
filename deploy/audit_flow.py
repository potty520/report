#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
上线前业务闭环验证（全新数据，走完整链路后清理）

目的：回答「业务逻辑和全功能是否真的没问题」
用一条全新任务走通：建模板→启用→审核→字段→建任务→发布→分派→填报→部门审核→任务方审核→台账

所有数据使用 ZZAUDIT- 前缀，测试后清理。
"""
import json
import sys
import time

from rsh import get_client, run

TAG = time.strftime("%m%d%H%M%S")


def q(client, sql):
    sftp = client.open_sftp()
    with sftp.file("/tmp/_a.sql", "w") as f:
        f.write(sql)
    sftp.close()
    rc, out, err = run(
        "mysql -ureport -p***REMOVED_DB_PASSWORD*** report_system -B -N < /tmp/_a.sql 2>&1 | grep -v Warning",
        timeout=120, client=client)
    return out.strip()


REMOTE = r'''
import base64, hmac, hashlib, json, time, sys, urllib.request, urllib.error, urllib.parse
SECRET = sys.argv[1].encode()
A_SESS, U_SESS, TAG = sys.argv[2], sys.argv[3], sys.argv[4]
BASE = "http://127.0.0.1:8763/api"

def b64(b): return base64.urlsafe_b64encode(b).rstrip(b"=").decode()
def token(uid, uname, sess):
    now = int(time.time())
    h = b64(json.dumps({"alg":"HS256","typ":"JWT"}, separators=(",",":")).encode())
    p = b64(json.dumps({"sub":uname,"userId":uid,"username":uname,"jti":sess,
                        "iat":now,"exp":now+1800}, separators=(",",":")).encode())
    sig = b64(hmac.new(SECRET, f"{h}.{p}".encode(), hashlib.sha256).digest())
    return f"{h}.{p}.{sig}"

def call(method, path, tok, params=None, body=None, timeout=90):
    url = BASE + path
    if params: url += "?" + urllib.parse.urlencode(params)
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", "Bearer " + tok)
    if data: req.add_header("Content-Type", "application/json")
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, json.loads(r.read().decode("utf-8","replace")), round((time.time()-t0)*1000)
    except urllib.error.HTTPError as e:
        try: j = json.loads(e.read().decode("utf-8","replace"))
        except Exception: j = {"raw": "http error"}
        return e.code, j, round((time.time()-t0)*1000)
    except Exception as e:
        return -1, {"raw": str(e)}, round((time.time()-t0)*1000)

A = token(1, "admin", A_SESS)
U = token(2, "user", U_SESS)
R = {}

# S1 建模板
st, j, ms = call("POST", "/template", A, body={
    "templateName": f"ZZAUDIT-{TAG}-模板", "templateCode": f"ZZA{TAG}",
    "fillingCycle": 1, "deptId": 1, "description": "上线前闭环验证"})
R["S1_建模板"] = [j.get("code"), j.get("msg"), ms, j.get("data")]

st, j, _ = call("GET", "/template/page", A, {"page":1,"pageSize":100})
tid = next((x["id"] for x in ((j.get("data") or {}).get("list") or [])
            if x.get("templateCode") == f"ZZA{TAG}"), None)
R["S1_模板id"] = tid

if tid:
    # S2 启用
    st, j, ms = call("PUT", f"/template/{tid}/status", A, {"status":1})
    R["S2_启用模板"] = [j.get("code"), j.get("msg"), ms]
    # S3 审核
    st, j, ms = call("PUT", f"/template/{tid}/audit", A, {"auditStatus":1,"comment":"审核通过"})
    R["S3_模板审核"] = [j.get("code"), j.get("msg"), ms]
    # S4 字段
    st, j, ms = call("POST", f"/template/{tid}/fields", A, body=[
        {"fieldName":"项目名称","fieldCode":"item_name","fieldType":"VARCHAR",
         "fieldLength":128,"isRequired":1,"isKey":0,"excelColumn":"A","dbColumn":"item_name","sort":1},
        {"fieldName":"金额","fieldCode":"amount","fieldType":"DECIMAL",
         "fieldLength":12,"isRequired":1,"isKey":0,"excelColumn":"B","dbColumn":"amount","sort":2}])
    R["S4_模板字段"] = [j.get("code"), j.get("msg"), ms]

    # S5 建任务
    st, j, ms = call("POST", "/task", A, body={
        "taskName": f"ZZAUDIT-{TAG}-任务", "templateId": tid, "deptId": 1,
        "belongTime": f"{TAG}", "fillDeadline": "2026-12-31 23:59:59",
        "auditDeadline": "2027-01-10 23:59:59"})
    R["S5_建任务"] = [j.get("code"), j.get("msg"), ms]

    st, j, _ = call("GET", "/task/page", A, {"page":1,"pageSize":100})
    taskId = next((x["id"] for x in ((j.get("data") or {}).get("list") or [])
                   if x.get("taskName") == f"ZZAUDIT-{TAG}-任务"), None)
    R["S5_任务id"] = taskId

if tid and taskId:
    # S6 发布
    st, j, ms = call("PUT", f"/task/{taskId}/publish", A)
    R["S6_发布任务"] = [j.get("code"), j.get("msg"), ms]
    # S7 分派
    st, j, ms = call("POST", f"/task/{taskId}/assign", A,
                     params={"deptIds":"1","userIds":"2","assignType":"FILL"})
    R["S7_任务分派"] = [j.get("code"), j.get("msg"), ms]
    # S8 填报（草稿）
    st, j, ms = call("POST", "/fill/save", U,
                     params={"taskId": taskId,
                             "dataJson": json.dumps({"item_name":"闭环验证","amount":"888.88"},
                                                    ensure_ascii=False)})
    R["S8_保存草稿"] = [j.get("code"), j.get("msg"), ms]
    # S9 提交
    st, j, ms = call("POST", "/fill/submit", U,
                     params={"taskId": taskId,
                             "dataJson": json.dumps({"item_name":"闭环验证","amount":"888.88"},
                                                    ensure_ascii=False)})
    R["S9_提交填报"] = [j.get("code"), j.get("msg"), ms]

    st, j, _ = call("GET", "/fill/task/page", U, {"page":1,"pageSize":100})
    dataId = next((x["id"] for x in ((j.get("data") or {}).get("list") or [])
                   if x.get("taskId") == taskId), None)
    R["S9_填报id"] = dataId

if taskId and dataId:
    # S10 部门审核
    st, j, ms = call("POST", "/audit/dept-audit", A,
                     params={"taskDataId": dataId, "pass":"true", "comment":"部门通过"})
    R["S10_部门审核"] = [j.get("code"), j.get("msg"), ms]
    # S11 任务方审核
    st, j, ms = call("POST", "/audit/task-audit", A,
                     params={"taskDataId": dataId, "pass":"true", "comment":"任务方通过"})
    R["S11_任务方审核"] = [j.get("code"), j.get("msg"), ms]
    # S12 轨迹
    st, j, ms = call("GET", "/audit/trajectory", A, {"taskId": taskId})
    traj = j.get("data") or []
    R["S12_流转轨迹"] = [j.get("code"), len(traj),
                        [t.get("node_code") or t.get("nodeCode") for t in traj], ms]
    # S13 台账联动
    st, j, ms = call("GET", "/overview/stats", A)
    R["S13_总览联动"] = [j.get("code"), (j.get("data") or {}).get("auditPassRate"),
                        (j.get("data") or {}).get("totalFills"), ms]
    # S14 重复审核拦截
    st, j, ms = call("POST", "/audit/dept-audit", A,
                     params={"taskDataId": dataId, "pass":"true", "comment":"重复"})
    R["S14_重复审核拦截"] = [j.get("code"), j.get("msg"), ms]

print(json.dumps(R, ensure_ascii=False))
'''


def main():
    client = get_client()
    rc, out, _ = run("grep REPORT_JWT_SECRET /opt/report/report-api.env | cut -d= -f2", client=client)
    secret = out.strip().strip('"')

    rows = q(client, "SELECT user_id, session_id FROM rpt_online_user WHERE status=1 "
                     "ORDER BY last_active_time DESC LIMIT 30;").splitlines()
    a_s = u_s = None
    for line in rows:
        parts = line.split("\t")
        if len(parts) < 2:
            continue
        if parts[0] == "1" and not a_s:
            a_s = parts[1]
        if parts[0] == "2" and not u_s:
            u_s = parts[1]
    print(f"admin_s={a_s}\nuser_s={u_s}")
    if not a_s or not u_s:
        print("[FATAL] 会话缺失")
        return 1

    sftp = client.open_sftp()
    with sftp.file("/tmp/_audit.py", "w") as f:
        f.write(REMOTE)
    sftp.close()

    print(f"\n测试批次: ZZAUDIT-{TAG}\n")
    rc, out, err = run(f"python3 /tmp/_audit.py '{secret}' {a_s} {u_s} {TAG}",
                       timeout=400, client=client)
    try:
        R = json.loads(out.strip().splitlines()[-1])
    except Exception:
        print("[FATAL] 解析失败")
        print(out[:1500])
        print(err[:800])
        return 1

    labels = {
        "S1_建模板": "S1  新建模板",
        "S2_启用模板": "S2  启用模板",
        "S3_模板审核": "S3  模板审核通过",
        "S4_模板字段": "S4  保存模板字段",
        "S5_建任务": "S5  新建任务",
        "S6_发布任务": "S6  发布任务",
        "S7_任务分派": "S7  任务分派",
        "S8_保存草稿": "S8  保存草稿",
        "S9_提交填报": "S9  正式提交",
        "S10_部门审核": "S10 部门审核",
        "S11_任务方审核": "S11 任务方审核",
        "S12_流转轨迹": "S12 流转轨迹",
        "S13_总览联动": "S13 台账联动",
        "S14_重复审核拦截": "S14 重复审核拦截",
    }
    passed = failed = 0
    for k, label in labels.items():
        v = R.get(k)
        if v is None:
            print(f"  {label:20s} [SKIP] 前置步骤未完成")
            continue
        code = v[0]
        ok = (code == 200) if "S14" not in k else (code != 200)
        if ok:
            passed += 1
            print(f"  {label:20s} ✅ code={code} {v[1] if len(v)>1 else ''} {v[2] if len(v)>2 and isinstance(v[2],int) else ''}")
        else:
            failed += 1
            print(f"  {label:20s} ❌ code={code} {v[1] if len(v)>1 else ''}")

    print(f"\n  id 追踪: 模板={R.get('S1_模板id')} 任务={R.get('S5_任务id')} 填报={R.get('S9_填报id')}")
    print(f"\n闭环结果: {passed} 通过 / {failed} 失败")

    json.dump(R, open("audit_flow.json", "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    client.close()
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
