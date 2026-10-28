# -*- coding: utf-8 -*-
"""端到端业务闭环：建模板 → 物化 → 审核启用 → 建任务 → 分派 → 填报(校验拦截) → 两级审核 → 入库 → 校验目标表。"""
import json
import time

import requests

BASE = "http://103.85.25.169:8096/api"
steps = []


def step(name, ok, detail=""):
    steps.append((name, ok, str(detail)[:160]))
    print(("PASS " if ok else "FAIL ") + name + ((" | " + str(detail)[:140]) if detail else ""))


def login(u, p):
    cap = requests.get(BASE + "/auth/captcha", timeout=10).json()
    r = requests.post(BASE + "/auth/login", json={
        "username": u, "password": p, "uuid": cap["data"]["uuid"], "captcha": "8888"}, timeout=15).json()
    assert r.get("code") == 200, r
    return {"Authorization": "Bearer " + r["data"]["token"]}


admin = login("admin", "Admin@2026")
user = login("user", "User@2026")
stamp = str(int(time.time()))[-6:]

# 1) 建模板（含必填数值字段 + 备注）
tpl_body = {
    "templateName": f"E2E模板-{stamp}",
    "templateCode": f"E2E{stamp}",
    "fillingCycle": 3,
    "deptId": 1,
    "datasourceId": 1,
    "status": 0,
    "fields": [
        {"fieldName": "名称", "fieldCode": "name", "fieldType": "VARCHAR", "fieldLength": 100, "isKey": 1, "isRequired": 1, "sort": 1},
        {"fieldName": "金额", "fieldCode": "amount", "fieldType": "DECIMAL", "fieldLength": 12, "fieldScale": 2, "isRequired": 1, "sort": 2},
        {"fieldName": "备注", "fieldCode": "remark", "fieldType": "VARCHAR", "fieldLength": 200, "sort": 3},
    ],
}
r = requests.post(BASE + "/template", headers=admin, json=tpl_body, timeout=20).json()
step("1 创建模板", r.get("code") == 200, r.get("msg"))
lst = requests.get(BASE + "/template/page", headers=admin, params={"page": 1, "pageSize": 10}, timeout=20).json()["data"]["list"]
tid = next((x["id"] for x in lst if x.get("templateName") == f"E2E模板-{stamp}"), None)
step("2 模板落库", tid is not None, tid)

# 2) 物化
r = requests.post(BASE + f"/template/{tid}/materialize", headers=admin, json={"datasourceId": 1}, timeout=60).json()
step("3 物化建表", r.get("code") == 200, r.get("msg"))

# 3) 审核通过 + 启用
r = requests.put(BASE + f"/template/{tid}/audit", headers=admin,
                 params={"auditStatus": 1, "auditComment": "E2E"}, timeout=20).json()
step("4 模板审核通过", r.get("code") == 200, r.get("msg"))
r = requests.put(BASE + f"/template/{tid}/status", headers=admin, params={"status": 1}, timeout=20).json()
step("5 模板启用", r.get("code") == 200, r.get("msg"))

# 4) 建任务
r = requests.post(BASE + "/task", headers=admin, json={
    "taskName": f"E2E任务-{stamp}", "templateId": tid, "deptId": 1,
    "fillingCycle": 3, "belongTime": time.strftime("%Y-%m"), "status": 1}, timeout=20).json()
step("6 创建任务", r.get("code") == 200, r.get("msg"))
tasks = requests.get(BASE + "/task/page", headers=admin, params={"page": 1, "pageSize": 10, "taskName": f"E2E任务-{stamp}"}, timeout=20).json()["data"]["list"]
task_id = tasks[0]["id"] if tasks else None
requests.put(BASE + f"/task/{task_id}/publish", headers=admin, timeout=20)

# 5) 分派（填报人 user=2，审核人 admin=1）
r = requests.post(BASE + f"/task/{task_id}/assign", headers=admin, params={"assignType": "FILL", "userIds": 2}, timeout=20).json()
step("7 分派填报人", r.get("code") == 200, r.get("msg"))
r = requests.post(BASE + f"/task/{task_id}/assign", headers=admin, params={"assignType": "AUDIT", "userIds": 1}, timeout=20).json()
step("8 分派审核人", r.get("code") == 200, r.get("msg"))

# 6) 校验拦截：金额填非数字
bad_rows = [{"name": "测试", "amount": "abc", "remark": ""}]
v = requests.post(BASE + f"/fill/{task_id}/verify", headers=user, json={"rows": bad_rows}, timeout=20).json()
step("9 校验拦截非数字", v.get("code") == 200 and not v["data"]["passed"], v["data"]["stage1"]["errors"][:1])

# 7) 正常提交
payload = json.dumps({"name": "测试单位", "amount": "1234.56", "remark": "E2E"})
v2 = requests.post(BASE + f"/fill/{task_id}/verify", headers=user,
                   json={"rows": [{"name": "测试单位", "amount": "1234.56", "remark": "E2E"}]}, timeout=20).json()
step("10 校验通过", v2.get("code") == 200 and v2["data"]["passed"], v2["data"]["passed"])
r = requests.post(BASE + "/fill/submit", headers=user, data={"taskId": task_id, "dataJson": payload}, timeout=20).json()
step("11 提交填报", r.get("code") == 200, r.get("msg"))

# 8) 两级审核
detail = requests.get(BASE + f"/fill/task/{task_id}", headers=user, timeout=20).json()
data_id = (detail.get("data") or {}).get("id")
step("12 取填报数据ID", data_id is not None, data_id)
r = requests.post(BASE + "/audit/dept-audit", headers=admin,
                  params={"taskDataId": data_id, "pass": "true", "comment": "E2E 部门通过"}, timeout=20).json()
step("13 部门审核通过", r.get("code") == 200, r.get("msg"))
r = requests.post(BASE + "/audit/task-audit", headers=admin,
                  params={"taskDataId": data_id, "pass": "true", "comment": "E2E 任务方通过"}, timeout=20).json()
step("14 任务方审核通过", r.get("code") == 200, r.get("msg"))

# 9) 入库
r = requests.post(BASE + f"/fill/{task_id}/commit", headers=admin, timeout=60).json()
step("15 入库", r.get("code") == 200, r.get("msg"))

# 10) 目标表校验
rows = requests.get(BASE + f"/fill/{task_id}/target-rows", headers=admin, params={"limit": 10}, timeout=20).json()
_d = rows.get("data")
n = len(_d.get("rows") or _d) if isinstance(_d, dict) else len(_d or [])
step("16 目标表有数据", n >= 1, f"rows={n}")

# 11) 重复入库幂等
r2 = requests.post(BASE + f"/fill/{task_id}/commit", headers=admin, timeout=60).json()
rows2 = requests.get(BASE + f"/fill/{task_id}/target-rows", headers=admin, params={"limit": 10}, timeout=20).json()
_d2 = rows2.get("data")
n2 = len(_d2.get("rows") or _d2) if isinstance(_d2, dict) else len(_d2 or [])
step("17 重复入库不重复", n2 == n, f"{n} -> {n2}")

print("\n" + "=" * 50)
ok = sum(1 for _, o, _ in steps if o)
print(f"端到端通过 {ok}/{len(steps)}")
for n, o, d in steps:
    if not o:
        print("  FAIL:", n, d)
