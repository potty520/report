# -*- coding: utf-8 -*-
"""补齐功能验收：R-01 ~ R-15 在 v2 实例上的实际行为。"""
import json
import time

import requests

BASE = "http://103.85.25.169:8096/api"
results = []


def check(name, ok, detail=""):
    results.append((name, ok, str(detail)[:200]))
    print(("PASS " if ok else "FAIL ") + name + (" | " + str(detail)[:180] if detail else ""))


def login(u, p):
    cap = requests.get(BASE + "/auth/captcha", timeout=10).json()
    r = requests.post(BASE + "/auth/login", json={
        "username": u, "password": p, "uuid": cap["data"]["uuid"], "captcha": "8888"}, timeout=15).json()
    assert r.get("code") == 200, r
    return {"Authorization": "Bearer " + r["data"]["token"]}


admin = login("admin", "Admin@2026")
user = login("user", "User@2026")

# 基础数据
tpl = requests.get(BASE + "/template/page", headers=admin, params={"page": 1, "pageSize": 1}, timeout=20).json()
tpl_id = tpl["data"]["list"][0]["id"]
users = requests.get(BASE + "/user/page", headers=admin, params={"page": 1, "pageSize": 10}, timeout=20).json()
user_list = users["data"]["list"]
fill_uid = next((u["id"] for u in user_list if u["username"] == "user"), user_list[0]["id"])


def create_task(name):
    body = {"taskName": name, "templateId": tpl_id, "deptId": 1, "fillingCycle": 3,
            "belongTime": time.strftime("%Y-%m"), "status": 1}
    r = requests.post(BASE + "/task", headers=admin, json=body, timeout=20).json()
    if r.get("code") != 200:
        return None, r
    page = requests.get(BASE + "/task/page", headers=admin,
                        params={"page": 1, "pageSize": 10, "taskName": name}, timeout=20).json()
    lst = page["data"]["list"]
    return (lst[0]["id"] if lst else None), r


# ---------------- R-03 分派退回 + R-05 撤销
tid, r = create_task("FT-R03-" + str(int(time.time())))
if tid:
    resp = requests.post(BASE + f"/task/{tid}/return", headers=admin, json={"reason": "部门不符"}, timeout=20).json()
    check("R-03 分派退回", resp.get("code") == 200, resp.get("msg"))
    lst = requests.get(BASE + "/task/returned", headers=admin, timeout=20).json()
    got = [x for x in (lst.get("data") or {}).get("list", []) if x["id"] == tid]
    check("R-03 被退回列表可见", bool(got), lst.get("code"))

    resp = requests.put(BASE + f"/task/{tid}/resubmit", headers=admin, json={"reason": "重新提交"}, timeout=20).json()
    check("R-05 重新提交", resp.get("code") == 200, resp.get("msg"))

    # 再退回后撤销
    requests.post(BASE + f"/task/{tid}/return", headers=admin, json={"reason": "再退回"}, timeout=20)
    resp = requests.put(BASE + f"/task/{tid}/revoke", headers=admin, json={"reason": "任务作废"}, timeout=20).json()
    detail = requests.get(BASE + f"/task/{tid}/return-detail", headers=admin, timeout=20).json()
    check("R-05 撤销", resp.get("code") == 200 and detail.get("code") == 200, resp.get("msg"))

# ---------------- R-04 填报退回 + R-02 无更新提交
tid2, r = create_task("FT-R04-" + str(int(time.time())))
if tid2:
    requests.post(BASE + f"/task/{tid2}/assign", headers=admin,
                  json={"fillUserIds": [fill_uid], "auditUserIds": []}, timeout=20)
    resp = requests.post(BASE + f"/fill/{tid2}/return", headers=user, json={"reason": "非本部门任务"}, timeout=20).json()
    check("R-04 填报退回", resp.get("code") == 200, resp.get("msg"))

    # 重新分派后做无更新提交
    requests.post(BASE + f"/task/{tid2}/assign", headers=admin,
                  json={"fillUserIds": [fill_uid], "auditUserIds": []}, timeout=20)
    resp = requests.post(BASE + "/fill/no-update", headers=user,
                         json={"taskId": tid2, "reason": "本期数据无变化"}, timeout=20).json()
    check("R-02 无更新提交", resp.get("code") == 200, resp.get("msg"))
    resp = requests.post(BASE + "/fill/no-update", headers=user,
                         json={"taskId": tid2, "reason": ""}, timeout=20).json()
    check("R-02 空说明被拒", resp.get("code") != 200, resp.get("msg"))

# ---------------- R-10 填报部门
resp = requests.put(BASE + "/dept/fill-dept", headers=admin, json={"deptIds": [1]}, timeout=20).json()
check("R-10 设置填报部门", resp.get("code") == 200, resp.get("msg"))
resp = requests.get(BASE + "/dept/fill-list", headers=admin, timeout=20).json()
check("R-10 填报部门查询", resp.get("code") == 200 and len(resp.get("data") or []) >= 1, len(resp.get("data") or []))

# ---------------- R-15 项目发布
pr = requests.post(BASE + "/project", headers=admin,
                   json={"projectName": "FT项目" + str(int(time.time())), "deptId": 1, "status": 1}, timeout=20).json()
projects = requests.get(BASE + "/project/list", headers=admin, timeout=20).json()
pid = None
for p in (projects.get("data") or []):
    if str(p.get("projectName", "")).startswith("FT项目"):
        pid = p["id"]
if pid:
    requests.put(BASE + f"/project/{pid}/close", headers=admin, timeout=20)
    resp = requests.put(BASE + f"/project/{pid}/publish", headers=admin, timeout=20).json()
    check("R-15 项目发布", resp.get("code") == 200, resp.get("msg"))
else:
    check("R-15 项目发布", False, "项目创建失败")

# ---------------- R-01 展示信息配置
resp = requests.post(BASE + f"/template/{tpl_id}/display-config", headers=admin,
                     json={"configType": 2, "refDatasourceId": 1, "refTableName": "report_target", "refColumns": "[]"},
                     timeout=20).json()
cfg = requests.get(BASE + f"/template/{tpl_id}/display-config", headers=admin, timeout=20).json()
check("R-01 展示配置保存/读取", resp.get("code") == 200 and cfg.get("data", {}).get("configType") == 2,
      cfg.get("data"))
_tables = requests.get(BASE + "/datasource/1/tables", headers=admin, timeout=60).json().get("data") or ["report_target"]
link = requests.post(BASE + "/template/display/link-test", headers=admin,
                     json={"datasourceId": 1, "tableName": _tables[0]}, timeout=30).json()
check("R-01 链接检测", link.get("code") == 200 and bool(link.get("data", {}).get("ok")), link.get("data"))

# ---------------- R-08 表/列清单 + 字段绑定
tables = requests.get(BASE + "/datasource/1/tables", headers=admin, timeout=60).json()
table_name = (tables.get("data") or [None])[0]
cols = requests.get(BASE + f"/datasource/1/tables/{table_name}/columns", headers=admin, timeout=60).json()
check("R-08 表清单/列清单", tables.get("code") == 200 and cols.get("code") == 200,
      f"tables={len(tables.get('data') or [])} cols={len(cols.get('data') or [])}")

fields = requests.get(BASE + f"/template/{tpl_id}/fields", headers=admin, timeout=20).json()
flist = fields.get("data") or []
if flist:
    binds = [{"fieldId": f["id"], "dbColumn": f.get("dbColumn") or f.get("fieldCode") or f"col_{i}"}
             for i, f in enumerate(flist)]
    resp = requests.post(BASE + f"/template/{tpl_id}/bind-columns", headers=admin, json=binds, timeout=20).json()
    check("R-08 字段绑定", resp.get("code") == 200, resp.get("msg"))

# ---------------- R-12 四类统计
for name, path in [("R-12 任务制定统计", "/statistics/task-make"),
                   ("R-12 部门完成排行", "/statistics/dept-finish-rank"),
                   ("R-12 填报任务统计", "/statistics/fill-task"),
                   ("R-12 部门下发排行", "/statistics/dept-issue-rank")]:
    r = requests.get(BASE + path, headers=admin, timeout=20).json()
    check(name, r.get("code") == 200 and r.get("data") is not None, str(r.get("data"))[:120])

# ---------------- R-09 导入校验记录
r = requests.get(BASE + f"/fill/{tid2}/import-checks", headers=user, timeout=20).json()
check("R-09 导入校验记录", r.get("code") == 200, r.get("msg"))

# ---------------- R-14 补正明细
corrects = requests.get(BASE + "/audit/correct/list", headers=admin, params={"page": 1, "pageSize": 1}, timeout=20).json()
cid = None
data = corrects.get("data") or {}
if isinstance(data, dict):
    lst = data.get("list") or []
    cid = lst[0]["id"] if lst else None
elif isinstance(data, list) and data:
    cid = data[0].get("id")
if cid:
    add = requests.post(BASE + f"/correct/{cid}/item", headers=admin,
                        json={"opType": 2, "rowKey": "1", "beforeJson": "{\"a\":1}", "afterJson": "{\"a\":2}"},
                        timeout=20).json()
    items = requests.get(BASE + f"/correct/{cid}/items", headers=admin, timeout=20).json()
    check("R-14 补正明细增/查", add.get("code") == 200 and len(items.get("data") or []) >= 1, len(items.get("data") or []))
else:
    check("R-14 补正明细增/查", False, "无补正单可测")

print("\n" + "=" * 60)
ok = sum(1 for _, o, _ in results if o)
print(f"通过 {ok}/{len(results)}")
for n, o, d in results:
    if not o:
        print("  FAIL:", n, d)
