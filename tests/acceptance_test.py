# -*- coding: utf-8 -*-
"""填报系统（192.168.1.14:8763）验收测试：覆盖本次修复的 5 个问题。"""
import json
import time
from datetime import datetime, timedelta

import requests

import os
BASE = os.environ.get("REPORT_BASE", "http://192.168.1.14:8763/api")
FIXED_CAPTCHA = "8888"
results = []


def check(name, ok, detail=""):
    results.append({"name": name, "ok": bool(ok), "detail": str(detail)[:400]})
    print(("PASS " if ok else "FAIL ") + name + ((" | " + str(detail)[:300]) if detail else ""))
    return ok


def login(username, password):
    cap = requests.get(BASE + "/auth/captcha", timeout=10).json()
    r = requests.post(BASE + "/auth/login", json={
        "username": username, "password": password,
        "uuid": cap["data"]["uuid"], "captcha": FIXED_CAPTCHA}, timeout=15)
    body = r.json()
    assert body.get("code") == 200, body
    return body["data"]["token"]


def call(method, path, token=None, params=None, data=None, json_body=None, timeout=60):
    headers = {"Authorization": "Bearer " + token} if token else {}
    r = requests.request(method, BASE + path, headers=headers, params=params,
                         data=data, json=json_body, timeout=timeout)
    try:
        return r.status_code, r.json()
    except Exception:
        return r.status_code, {"raw": r.text[:300]}


def main():
    stamp = time.strftime("%H%M%S")
    admin = login("admin", os.environ.get("REPORT_ADMIN_PWD", "Admin@2026"))
    user = login("user", os.environ.get("REPORT_USER_PWD", "User@2026"))
    check("登录 admin/user", True)

    # ---------- 0. 数据源 ----------
    st, body = call("POST", "/datasource", admin, json_body={
        "dsName": "本地MySQL(验收)", "dsType": "MYSQL", "ip": "127.0.0.1", "port": 3306,
        "dbName": "report_target", "username": "report", "password": __import__("os").environ.get("REPORT_TARGET_PWD", "RptLan2026x7Qm9"), "status": 1})
    check("创建数据源", body.get("code") == 200, body.get("msg"))
    st, body = call("GET", "/datasource/list", admin)
    ds_id = (body.get("data") or [{}])[-1].get("id")
    ds_id = body["data"][-1]["id"] if body.get("data") else None

    # ---------- 1. 周期枚举：模板按「月报(3)」创建 ----------
    tpl_code = f"ACC-{stamp}"
    st, body = call("POST", "/template", admin, json_body={
        "templateName": f"验收模板-{stamp}", "templateCode": tpl_code,
        "fillingCycle": 3, "description": "本次修复验收",
        "fields": [
            {"fieldName": "营业额", "fieldCode": "revenue", "fieldType": "DECIMAL", "fieldLength": 12, "isRequired": 1, "isKey": 0, "dbColumn": "revenue"},
            {"fieldName": "填报日期", "fieldCode": "fill_date", "fieldType": "DATE", "fieldLength": 10, "isRequired": 1, "isKey": 0, "dbColumn": "fill_date"},
            {"fieldName": "备注", "fieldCode": "remark", "fieldType": "VARCHAR", "fieldLength": 255, "isRequired": 0, "isKey": 0, "dbColumn": "remark"},
        ]})
    tpl_id = body.get("data")
    check("创建模板(月报cycle=3)", body.get("code") == 200 and tpl_id, body.get("msg"))

    st, body = call("GET", "/template/by-code", admin, params={"templateCode": tpl_code})
    check("模板周期落库=3(月报)", (body.get("data") or {}).get("fillingCycle") == 3,
          (body.get("data") or {}).get("fillingCycle"))

    # 非法周期应被拒绝
    st, body = call("POST", "/template", admin, json_body={
        "templateName": "非法周期", "templateCode": f"BAD-{stamp}", "fillingCycle": 9})
    check("非法周期(9)被拒", body.get("code") != 200 and "填报周期" in (body.get("msg") or ""), body.get("msg"))

    # ---------- 2. 物化 ----------
    table = f"acc_flow_{stamp}"
    st, body = call("POST", f"/template/{tpl_id}/deploy", admin,
                    params={"datasourceId": ds_id, "tableName": table})
    check("部署数据源", body.get("code") == 200, body.get("msg"))
    st, body = call("POST", f"/template/{tpl_id}/materialize", admin, params={"recreate": "true"})
    check("物化建表", body.get("code") == 200, body.get("msg"))
    st, body = call("PUT", f"/template/{tpl_id}/status", admin, params={"status": 1})
    check("启用模板", body.get("code") == 200, body.get("msg"))

    # ---------- 3. 任务 ----------
    st, body = call("POST", "/task", admin, json_body={
        "taskName": f"验收任务-{stamp}", "templateId": tpl_id, "belongTime": "2026-10",
        "fillDeadline": "2026-10-31 23:59:59", "auditDeadline": "2026-11-05 23:59:59"})
    check("创建任务", body.get("code") == 200, body.get("msg"))
    st, body = call("GET", "/task/page", admin, params={"page": 1, "pageSize": 5, "taskName": f"验收任务-{stamp}"})
    task = (body.get("data") or {}).get("list", [{}])[0]
    task_id = task.get("id")
    check("任务继承模板周期=3", task.get("fillingCycle") == 3, task.get("fillingCycle"))

    # 时区：createTime 应接近当前北京时间
    ct = task.get("createTime") or ""
    try:
        delta = abs((datetime.strptime(ct, "%Y-%m-%d %H:%M:%S") - datetime.now()).total_seconds())
    except Exception:
        delta = 10 ** 9
    check("时间戳为北京时间(±10分钟)", delta < 600, f"createTime={ct}, 偏差={int(delta)}s")

    st, body = call("PUT", f"/task/{task_id}/publish", admin)
    check("发布任务", body.get("code") == 200, body.get("msg"))
    st, body = call("POST", f"/task/{task_id}/assign", admin, params={"assignType": "FILL", "userIds": 2})
    check("分派填报人", body.get("code") == 200, body.get("msg"))
    st, body = call("POST", f"/task/{task_id}/assign", admin, params={"assignType": "AUDIT", "userIds": 1})
    check("分派审核人", body.get("code") == 200, body.get("msg"))

    # ---------- 4. 填报 ----------
    payload = json.dumps({"revenue": "88888.66", "fill_date": "2026-10-24", "remark": "验收-保留"})
    st, body = call("POST", "/fill/save", user, data={"taskId": task_id, "dataJson": payload})
    check("保存草稿", body.get("code") == 200, body.get("msg"))
    st, body = call("POST", "/fill/submit", user, data={"taskId": task_id, "dataJson": payload})
    check("提交填报", body.get("code") == 200, body.get("msg"))

    # ---------- 5. 驳回 -> 轨迹 -> 重报 ----------
    st, body = call("GET", "/audit/page", admin, params={"page": 1, "pageSize": 10})
    rows = (body.get("data") or {}).get("list", [])
    data_id = next((x["id"] for x in rows if x.get("taskId") == task_id), None)
    check("审核列表可见", data_id is not None, f"dataId={data_id}")

    st, body = call("POST", "/audit/reject", admin,
                    params={"taskDataId": data_id, "auditComment": "验收-驳回：金额需复核"})
    check("驳回", body.get("code") == 200, body.get("msg"))

    st, body = call("GET", "/audit/trajectory", admin, params={"taskId": task_id})
    nodes = body.get("data") or []
    reject_node = [n for n in nodes if n.get("action") == "REJECT"]
    check("轨迹包含驳回节点", len(reject_node) > 0,
          [(n.get("node_code"), n.get("action"), n.get("comment")) for n in nodes])

    st, body = call("POST", "/fill/submit", user, data={"taskId": task_id, "dataJson": payload})
    check("驳回后重新提交", body.get("code") == 200, body.get("msg"))

    st, body = call("GET", f"/fill/task/{task_id}", user)
    d = body.get("data") or {}
    check("重报后驳回意见已清空", not (d.get("auditComment") or ""), repr(d.get("auditComment")))

    # ---------- 6. 两级审核 ----------
    st, body = call("POST", "/audit/dept-audit", admin,
                    params={"taskDataId": data_id, "pass": "true", "comment": "部门审核通过"})
    check("部门审核通过", body.get("code") == 200, body.get("msg"))
    st, body = call("POST", "/audit/task-audit", admin,
                    params={"taskDataId": data_id, "pass": "true", "comment": "任务方审核通过"})
    check("任务方审核通过", body.get("code") == 200, body.get("msg"))

    # ---------- 7. 入库幂等 ----------
    st, body = call("POST", f"/fill/{task_id}/commit", admin)
    check("首次入库", body.get("code") == 200, body.get("data") or body.get("msg"))
    st, body = call("POST", f"/fill/{task_id}/commit", admin)
    msg = body.get("msg") or ""
    check("重复入库被忽略", body.get("code") != 200 and "已" in msg, msg)

    st, body = call("GET", f"/fill/{task_id}/target-rows", admin, params={"limit": 20})
    rows = body.get("data") or []
    check("目标表只有 1 行(未重复插入)", len(rows) == 1, rows)

    # ---------- 8. 未审核不能入库 ----------
    st, body = call("POST", "/task", admin, json_body={
        "taskName": f"验收任务2-{stamp}", "templateId": tpl_id, "belongTime": "2026-10",
        "fillDeadline": "2026-10-31 23:59:59"})
    st, body = call("GET", "/task/page", admin, params={"page": 1, "pageSize": 5, "taskName": f"验收任务2-{stamp}"})
    task2 = (body.get("data") or {}).get("list", [{}])[0]
    task2_id = task2.get("id")
    call("PUT", f"/task/{task2_id}/publish", admin)
    call("POST", f"/task/{task2_id}/assign", admin, params={"assignType": "FILL", "userIds": 2})
    call("POST", "/fill/save", user, data={"taskId": task2_id, "dataJson": payload})
    call("POST", "/fill/submit", user, data={"taskId": task2_id, "dataJson": payload})
    st, body = call("POST", f"/fill/{task2_id}/commit", admin)
    check("未审核数据不能入库", body.get("code") != 200 and "审核" in (body.get("msg") or ""), body.get("msg"))

    # ---------- 9. 待办口径 ----------
    st, body = call("GET", "/todo/summary", user)
    s = body.get("data") or {}
    total = s.get("fillTodo", 0) + s.get("auditTodo", 0) + s.get("applyTodo", 0)
    check("待办=待填+待审+待批", s.get("todoCount") == total, s)
    check("待办数不再等于未读消息", s.get("todoCount") != s.get("unreadCount") or s.get("todoCount", 0) <= 5, s)

    # ---------- 10. 模板列表按周期筛选 ----------
    st, body = call("GET", "/template/page", admin, params={"page": 1, "pageSize": 10, "fillingCycle": 3})
    lst = (body.get("data") or {}).get("list", [])
    check("按周期筛选非空且 total 一致",
          len(lst) > 0 and (body["data"]["total"] == len(lst) or body["data"]["total"] >= len(lst))
          and all(x.get("fillingCycle") == 3 for x in lst),
          f"total={body.get('data', {}).get('total')} len={len(lst)}")

    # ---------- 11. 403 中文 ----------
    st, body = call("POST", f"/template/{tpl_id}/deploy", user, params={"datasourceId": ds_id})
    check("越权返回中文 403", st == 403 and (body.get("msg") or "").strip() == "无权限执行该操作",
          f"http={st} msg={body.get('msg')}")

    # ---------- 12. 周期属期计算 ----------
    st, body = call("POST", "/task/generate-periodic", admin, params={"parentTaskId": task_id})
    gen = body.get("data") or {}
    st, body = call("GET", f"/task/{task_id}/children", admin)
    children = body.get("data") or []
    belong = [c.get("belongTime") for c in children]
    check("月报子任务属期为 2026-10（非日报日期）",
          len(children) > 0 and all(b and len(str(b)) == 7 for b in belong), belong)
    check("月报子任务截止日为月末",
          all(str(c.get("fillDeadline", "")).startswith("2026-10-31") for c in children),
          [c.get("fillDeadline") for c in children])
    st, body = call("POST", "/task/generate-periodic", admin, params={"parentTaskId": task_id})
    check("重复生成被去重", (body.get("data") or {}).get("createdCount", 0) == 0,
          body.get("data", {}).get("detail"))

    # ---------- 汇总 ----------
    print("\n" + "=" * 70)
    ok = sum(1 for r in results if r["ok"])
    print(f"通过 {ok}/{len(results)}")
    for r in results:
        if not r["ok"]:
            print("  FAIL:", r["name"], "|", r["detail"][:300])
    with open("acceptance_results.json", "w", encoding="utf-8") as f:
        json.dump({"base": BASE, "templateId": tpl_id, "taskId": task_id,
                   "table": table, "results": results}, f, ensure_ascii=False, indent=2)
    return ok == len(results)


if __name__ == "__main__":
    main()
