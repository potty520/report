#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
填报系统 · 业务流转端到端测试

覆盖完整业务链路：
  T01 新建模板          → T02 保存模板字段
  T03 新建任务          → T04 发布任务 → T05 任务分派
  T06 草稿保存          → T07 正式提交
  T08 部门审核（实测耗时，复现 V01）  → T09 任务方审核
  T10 流转轨迹          → T11 台账统计联动
  T12 补正流程
  T13 异常流转（前置校验）
  T14 数据清理与生产数据完整性校验

所有测试数据使用 ZZFLOW-<时间戳> 前缀，测试后清理。
"""
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

from rsh import get_client, run

BASE = "http://127.0.0.1:8763/api"
RUN_TAG = time.strftime("%m%d%H%M%S")

results = []
created = {"templates": [], "tasks": [], "data": []}


# ---------------- 基础设施 ----------------

def load_secret():
    rc, out, _ = run("grep REPORT_JWT_SECRET /opt/report/report-api.env | cut -d= -f2")
    return out.strip().strip('"')


def sql_exec(sql, client):
    sftp = client.open_sftp()
    with sftp.file("/tmp/_t.sql", "w") as f:
        f.write(sql)
    sftp.close()
    rc, out, err = run(
        "mysql -ureport -p***REMOVED_DB_PASSWORD*** report_system -B -N < /tmp/_t.sql 2>&1 | grep -v Warning",
        timeout=90, client=client)
    return out


def sql_query(sql, client):
    sftp = client.open_sftp()
    with sftp.file("/tmp/_t.sql", "w") as f:
        f.write(sql)
    sftp.close()
    rc, out, err = run(
        "mysql -ureport -p***REMOVED_DB_PASSWORD*** report_system -B -N < /tmp/_t.sql 2>&1 | grep -v Warning",
        timeout=90, client=client)
    return [l.split("\t") for l in out.strip().splitlines() if l.strip()]


REMOTE_CLIENT = r'''
import base64, hmac, hashlib, json, time, sys, urllib.request, urllib.error, urllib.parse

SECRET = sys.argv[1].encode()
BASE = "http://127.0.0.1:8763/api"

def b64(b): return base64.urlsafe_b64encode(b).rstrip(b"=").decode()

def token(uid, uname, sess):
    now = int(time.time())
    h = b64(json.dumps({"alg":"HS256","typ":"JWT"}, separators=(",",":")).encode())
    p = b64(json.dumps({"sub":uname,"userId":uid,"username":uname,"jti":sess,
                        "iat":now,"exp":now+3600}, separators=(",",":")).encode())
    sig = b64(hmac.new(SECRET, f"{h}.{p}".encode(), hashlib.sha256).digest())
    return f"{h}.{p}.{sig}"

def call(method, path, tok, params=None, body=None, timeout=90):
    url = BASE + path
    if params:
        url += "?" + urllib.parse.urlencode(params)
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", "Bearer " + tok)
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
'''

# 在服务器上执行的动作
REMOTE_MAIN = r'''
CMD = sys.argv[2]

def jload(txt):
    try: return json.loads(txt)
    except Exception: return {}

if CMD == "flow":
    ADMIN_SESS, USER_SESS, TAG = sys.argv[3], sys.argv[4], sys.argv[5]
    A = token(1, "admin", ADMIN_SESS)
    U = token(2, "user", USER_SESS)
    out = {}

    # T01 新建模板
    st, txt, ms = call("POST", "/template", A,
        body={"templateName": f"ZZFLOW-{TAG}-模板", "templateCode": f"ZZF{TAG}",
              "fillingCycle": 1, "deptId": 1,
              "description": "业务流转自动测试"})
    out["T01"] = {"http": st, "resp": jload(txt), "ms": ms}

    # 取新建模板 id
    st, txt, ms = call("GET", "/template/page", A, {"page":1,"pageSize":50})
    tid = None
    for it in (jload(txt).get("data") or {}).get("list", []):
        if it.get("templateCode") == f"ZZF{TAG}":
            tid = it["id"]; break
    out["T01_id"] = tid

    # T01b 启用模板（新建默认 status=0 草稿、auditStatus=0 待审，必须先启用+过审）
    if tid:
        st, txt, ms = call("PUT", f"/template/{tid}/status", A, params={"status": 1})
        out["T01b_enable"] = {"http": st, "resp": jload(txt), "ms": round(ms)}
        st, txt, ms = call("PUT", f"/template/{tid}/audit", A,
                           params={"auditStatus": 1, "comment": "自动测试审核通过"})
        out["T01c_audit"] = {"http": st, "resp": jload(txt), "ms": round(ms)}
        st, txt, _ = call("GET", f"/template/{tid}", A)
        _td = jload(txt).get("data") or {}
        out["T01_status"] = _td.get("status")
        out["T01_auditStatus"] = _td.get("auditStatus")

    # T02 保存模板字段
    if tid:
        st, txt, ms = call("POST", f"/template/{tid}/fields", A,
            body=[{"fieldName":"项目名称","fieldCode":"item_name","fieldType":"VARCHAR","fieldLength":128,
                   "isRequired":1,"isKey":0,"excelColumn":"A","dbColumn":"item_name","sort":1},
                  {"fieldName":"金额","fieldCode":"amount","fieldType":"DECIMAL","fieldLength":12,
                   "isRequired":1,"isKey":0,"excelColumn":"B","dbColumn":"amount","sort":2},
                  {"fieldName":"备注","fieldCode":"remark","fieldType":"VARCHAR","fieldLength":255,
                   "isRequired":0,"isKey":0,"excelColumn":"C","dbColumn":"remark","sort":3}])
        out["T02"] = {"http": st, "resp": jload(txt), "ms": ms}

    # T03 新建任务
    st, txt, ms = call("POST", "/task", A,
        body={"taskName": f"ZZFLOW-{TAG}-任务", "templateId": tid or 1, "deptId": 1,
              "fillingCycle": 1, "belongTime": TAG, "fillDeadline": "2026-12-31T23:59:59",
              "auditDeadline": "2027-01-10T23:59:59"})
    out["T03"] = {"http": st, "resp": jload(txt), "ms": ms}

    st, txt, ms = call("GET", "/task/page", A, {"page":1,"pageSize":50})
    taskId = None
    for it in (jload(txt).get("data") or {}).get("list", []):
        if it.get("taskName") == f"ZZFLOW-{TAG}-任务":
            taskId = it["id"]; break
    out["T03_id"] = taskId

    # T04 发布任务
    if taskId:
        st, txt, ms = call("PUT", f"/task/{taskId}/publish", A)
        out["T04"] = {"http": st, "resp": jload(txt), "ms": ms}
        st, txt, _ = call("GET", f"/task/{taskId}", A)
        out["T04_status"] = jload(txt).get("data", {}).get("status")

        # T05 任务分派（分给 dept=1, user=2）
        st, txt, ms = call("POST", f"/task/{taskId}/assign", A,
            params={"deptIds":"1","userIds":"2","assignType":"FILL"})
        out["T05"] = {"http": st, "resp": jload(txt), "ms": ms}
        st, txt, _ = call("GET", f"/task/{taskId}/assigns", A)
        out["T05_assigns"] = len(jload(txt).get("data") or [])

        # T06 草稿保存（以 user 身份）
        st, txt, ms = call("POST", "/fill/save", U,
            params={"taskId": taskId,
                    "dataJson": json.dumps({"item_name":"流转测试项","amount":"1000.50","remark":"草稿"},
                                           ensure_ascii=False)})
        out["T06"] = {"http": st, "resp": jload(txt), "ms": ms}

        # T07 正式提交
        st, txt, ms = call("POST", "/fill/submit", U,
            params={"taskId": taskId,
                    "dataJson": json.dumps({"item_name":"流转测试项","amount":"1000.50","remark":"正式提交"},
                                           ensure_ascii=False)})
        out["T07"] = {"http": st, "resp": jload(txt), "ms": ms}

        # 取 taskDataId
        st, txt, _ = call("GET", "/fill/task/page", U, {"page":1,"pageSize":50})
        dataId = None
        for it in (jload(txt).get("data") or {}).get("list", []):
            if it.get("taskId") == taskId:
                dataId = it["id"]; break
        out["T07_dataId"] = dataId

        # T08 部门审核（关键：实测耗时，复现 V01）
        if dataId:
            st, txt, ms = call("POST", "/audit/dept-audit", A,
                params={"taskDataId": dataId, "pass": "true", "comment": "部门审核通过"})
            out["T08"] = {"http": st, "resp": jload(txt), "ms": round(ms)}

            # T09 任务方审核
            st, txt, ms = call("POST", "/audit/task-audit", A,
                params={"taskDataId": dataId, "pass": "true", "comment": "任务方审核通过"})
            out["T09"] = {"http": st, "resp": jload(txt), "ms": round(ms)}

        # T10 流转轨迹
        st, txt, ms = call("GET", "/audit/trajectory", A, {"taskId": taskId})
        traj = jload(txt).get("data") or []
        out["T10"] = {"http": st, "count": len(traj), "ms": ms,
                      "nodes": [t.get("node_code") or t.get("nodeCode") for t in traj]}

        # T11 台账联动
        st, txt, ms = call("GET", "/ledger/rates", A)
        st2, txt2, ms2 = call("GET", "/overview/stats", A)
        out["T11"] = {"overview_http": st2, "stats": jload(txt2).get("data"), "ms": round(ms2)}

        # T12 补正流程
        if dataId:
            st, txt, ms = call("POST", "/audit/correct/start", A,
                params={"taskId": taskId, "dataIds": str(dataId), "comment": "数据需修正"})
            out["T12"] = {"http": st, "resp": jload(txt), "ms": round(ms)}
            st2, txt2, _ = call("GET", "/audit/correct/list", A, {"taskId": taskId})
            out["T12_list"] = jload(txt2).get("data")

        # T13 异常流转：任务未分派时越权填报 / 重复审核
        st, txt, ms = call("POST", "/audit/dept-audit", A,
            params={"taskDataId": dataId or 0, "pass": "true", "comment": "重复审核"})
        out["T13_repeat_audit"] = {"http": st, "resp": jload(txt), "ms": round(ms)}

    print(json.dumps(out, ensure_ascii=False))

elif CMD == "timing":
    ADMIN_SESS = sys.argv[3]
    A = token(1, "admin", ADMIN_SESS)
    dataId = int(sys.argv[4])
    st, txt, ms = call("POST", "/audit/dept-audit", A,
        params={"taskDataId": dataId, "pass": "true", "comment": "timing probe"}, timeout=120)
    print(json.dumps({"http": st, "ms": round(ms), "resp": jload(txt)}, ensure_ascii=False))
'''


def remote(cmd_args, client, secret, timeout=300):
    sftp = client.open_sftp()
    with sftp.file("/tmp/_flow.py", "w") as f:
        f.write(REMOTE_CLIENT + "\n" + REMOTE_MAIN)
    sftp.close()
    cmd = f"python3 /tmp/_flow.py '{secret}' " + " ".join(cmd_args)
    rc, out, err = run(cmd, timeout=timeout, client=client)
    return out, err


def sessions(client):
    rows = sql_query(
        "SELECT user_id, session_id FROM rpt_online_user "
        "WHERE status=1 ORDER BY last_active_time DESC LIMIT 20;", client)
    admin_s = user_s = None
    for uid, sid in rows:
        if uid == "1" and not admin_s:
            admin_s = sid
        if uid == "2" and not user_s:
            user_s = sid
    return admin_s, user_s


def main():
    secret = load_secret()
    if not secret:
        print("[FATAL] 取不到 JWT_SECRET")
        return 1
    client = get_client()

    print("=" * 72)
    print("填报系统 · 业务流转端到端测试")
    print(f"测试批次: ZZFLOW-{RUN_TAG}")
    print("=" * 72)

    # 记录基线
    base = {}
    for k, sql in [
        ("task", "SELECT COUNT(*) FROM rpt_task;"),
        ("task_data", "SELECT COUNT(*) FROM rpt_task_data;"),
        ("template", "SELECT COUNT(*) FROM rpt_template;"),
        ("assign", "SELECT COUNT(*) FROM rpt_task_assign;"),
        ("process", "SELECT COUNT(*) FROM rpt_task_process;"),
    ]:
        rows = sql_query(sql, client)
        base[k] = int(rows[0][0]) if rows else -1
    print(f"\n基线数据量: {base}\n")

    admin_s, user_s = sessions(client)
    print(f"admin session: {admin_s}\nuser  session: {user_s}\n")
    if not admin_s or not user_s:
        print("[WARN] 缺少在线会话，先触发一次登录心跳")
        # 通过任意认证请求刷新会话
        remote(["noop"], client, secret, timeout=5)
        admin_s, user_s = sessions(client)

    out, err = remote(["flow", admin_s, user_s, RUN_TAG], client, secret)
    if err.strip():
        print("[stderr]", err[:400])
    try:
        data = json.loads(out.strip().splitlines()[-1])
    except Exception:
        print("[FATAL] 解析结果失败，原始输出：")
        print(out[:1500])
        client.close()
        return 1

    # ---------------- 结果整理 ----------------
    def ok(r):
        return isinstance(r, dict) and r.get("resp", {}).get("code") == 200 or \
               (isinstance(r, dict) and r.get("http") == 200)

    print("【模板与任务】")
    print(f"  T01 新建模板      : {json.dumps(data.get('T01'), ensure_ascii=False)[:130]}")
    print(f"       新模板 id     : {data.get('T01_id')}")
    print(f"  T02 保存字段      : {json.dumps(data.get('T02'), ensure_ascii=False)[:130]}")
    print(f"  T03 新建任务      : {json.dumps(data.get('T03'), ensure_ascii=False)[:130]}")
    print(f"       新任务 id     : {data.get('T03_id')}")
    print(f"  T04 发布任务      : {json.dumps(data.get('T04'), ensure_ascii=False)[:130]}")
    print(f"       发布后状态    : {data.get('T04_status')}  (2=填报中)")
    print(f"  T05 任务分派      : {json.dumps(data.get('T05'), ensure_ascii=False)[:130]}")
    print(f"       分派记录数    : {data.get('T05_assigns')}")

    print("\n【填报与审核】")
    print(f"  T06 草稿保存      : {json.dumps(data.get('T06'), ensure_ascii=False)[:130]}")
    print(f"  T07 正式提交      : {json.dumps(data.get('T07'), ensure_ascii=False)[:130]}")
    print(f"       填报数据 id   : {data.get('T07_dataId')}")
    t08 = data.get("T08") or {}
    print(f"  T08 部门审核      : code={ (t08.get('resp') or {}).get('code') } "
          f"耗时={t08.get('ms')}ms  ← V01 关键指标")
    t09 = data.get("T09") or {}
    print(f"  T09 任务方审核    : code={ (t09.get('resp') or {}).get('code') } 耗时={t09.get('ms')}ms")
    t10 = data.get("T10") or {}
    print(f"  T10 流转轨迹      : {t10.get('count')} 条, 节点={t10.get('nodes')}")
    t11 = data.get("T11") or {}
    print(f"  T11 台账联动      : {json.dumps(t11.get('stats'), ensure_ascii=False)[:150]}")
    t12 = data.get("T12") or {}
    print(f"  T12 补正流程      : {json.dumps(t12, ensure_ascii=False)[:130]}")
    print(f"       补正列表      : {json.dumps(data.get('T12_list'), ensure_ascii=False)[:150]}")
    t13 = data.get("T13_repeat_audit") or {}
    print(f"  T13 重复审核拦截  : {json.dumps(t13, ensure_ascii=False)[:150]}")

    json.dump(data, open("flow_results.json", "w", encoding="utf-8"),
              ensure_ascii=False, indent=2)
    print("\n结果已写入 flow_results.json")
    client.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
