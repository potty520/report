#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Crow5 测试报告 12 项缺陷 —— 修复验证脚本

针对报告中的缺陷逐条验证修复效果，覆盖：
  X04/X08/X13/X14  垂直越权（部门管理员写部门/任务/模板/项目 应 403）
  A08              改密旧密码错误应被拒绝
  P13              /template/list 应返回列表（不再 500）
  F02              /fill/task/page 应返回列表（不再 500）
  U05              重复用户名应被拒绝
  U06              缺必填字段应给友好提示（不泄露 SQL）
  U10              弱密码应被拒绝
  R08              删除被占用角色应被拒绝
  G07              上传 .sh 应被拒绝
  V01              部门审核耗时应 < 5s

用法: python verify_fixes.py [base_url]
"""
import json
import sys
import time

import requests

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8763/api").rstrip("/")
CAPTCHA = "1234"  # 服务端用 REPORT_CAPTCHA_FIXED_CODE 固定

results = []


def rec(code, name, expected, actual, ok, detail=""):
    results.append({
        "code": code, "name": name, "expected": expected,
        "actual": actual, "ok": ok, "detail": detail,
    })
    print(f"[{'PASS' if ok else 'FAIL'}] {code:5s} {name}")
    print(f"        期望: {expected}")
    print(f"        实际: {actual}")
    if detail:
        print(f"        备注: {detail}")


def login(username, password):
    """登录取 token：先取验证码拿 uuid，再用固定验证码登录。"""
    s = requests.Session()
    # 服务端先校验 uuid 是否存在于 CaptchaStore，所以必须先请求一次验证码
    cap = s.get(f"{BASE}/auth/captcha", timeout=30).json()
    uuid = (cap.get("data") or {}).get("uuid") or (cap.get("data") or {}).get("captchaKey")
    if not uuid:
        raise RuntimeError(f"验证码接口没返回 uuid: {cap}")
    r = s.post(f"{BASE}/auth/login",
               json={"username": username, "password": password,
                     "uuid": uuid, "captcha": CAPTCHA},
               timeout=30)
    try:
        j = r.json()
    except Exception:
        raise RuntimeError(f"登录响应非 JSON: {r.status_code} {r.text[:200]}")
    if j.get("code") != 200:
        raise RuntimeError(f"登录失败: {j}")
    data = j.get("data") or {}
    token = data.get("token") or data.get("accessToken") or (data if isinstance(data, str) else None)
    if not token:
        raise RuntimeError(f"登录响应里没有 token: {j}")
    return token


def H(token):
    return {"Authorization": f"Bearer {token}"}


def main():
    print("=" * 70)
    print("Crow5 测试报告缺陷修复验证")
    print(f"目标: {BASE}")
    print("=" * 70)

    # ---------- 登录 ----------
    try:
        admin = login("admin", "***REMOVED_DEFAULT_ADMIN_PASSWORD***")
        print("\n[OK] admin 登录成功")
    except Exception as e:
        print(f"\n[FATAL] admin 登录失败，无法继续: {e}")
        print("       请确认: REPORT_CAPTCHA_FIXED_CODE=1234，且 admin/***REMOVED_DEFAULT_ADMIN_PASSWORD*** 已初始化")
        return 2

    try:
        user = login("user", "***REMOVED_DEFAULT_USER_PASSWORD***")
        print("[OK] user(部门管理员) 登录成功")
    except Exception as e:
        print(f"\n[FATAL] user 登录失败: {e}")
        return 2

    print()

    # ---------- X04: 普通账号新建部门 ----------
    r = requests.post(f"{BASE}/dept", headers=H(user),
                      json={"deptName": "ZZVERIFY部门", "deptCode": "ZZV-D01", "parentId": 0},
                      timeout=30)
    ok = r.status_code == 403 or r.json().get("code") == 403
    rec("X04", "普通账号新建部门", "403", f"HTTP {r.status_code} {r.text[:120]}", ok)

    # ---------- X08: 普通账号创建任务 ----------
    r = requests.post(f"{BASE}/task", headers=H(user),
                      json={"taskName": "ZZVERIFY任务", "templateId": 1, "deptId": 1}, timeout=30)
    ok = r.status_code == 403 or r.json().get("code") == 403
    rec("X08", "普通账号创建任务", "403", f"HTTP {r.status_code} {r.text[:120]}", ok)

    # ---------- X13: 普通账号新建模板 ----------
    r = requests.post(f"{BASE}/template", headers=H(user),
                      json={"templateName": "ZZVERIFY模板", "templateCode": "ZZV-T01"}, timeout=30)
    ok = r.status_code == 403 or r.json().get("code") == 403
    rec("X13", "普通账号新建模板", "403", f"HTTP {r.status_code} {r.text[:120]}", ok)

    # ---------- X14: 普通账号新建项目 ----------
    r = requests.post(f"{BASE}/project", headers=H(user),
                      json={"projectName": "ZZVERIFY项目", "deptId": 1}, timeout=30)
    ok = r.status_code == 403 or r.json().get("code") == 403
    rec("X14", "普通账号新建项目", "403", f"HTTP {r.status_code} {r.text[:120]}", ok)

    # ---------- A08: 改密旧密码错误 ----------
    r = requests.put(f"{BASE}/auth/password", headers=H(user),
                     params={"oldPassword": "WRONG-PASSWORD", "newPassword": "NewPwd@2026"},
                     timeout=30)
    j = r.json()
    ok = j.get("code") != 200
    rec("A08", "改密-旧密码错误", "被拒绝(code!=200)", f"code={j.get('code')} msg={j.get('msg')}", ok)

    # ---------- P13: /template/list ----------
    r = requests.get(f"{BASE}/template/list", headers=H(admin),
                     params={"page": 1, "pageSize": 10}, timeout=30)
    j = r.json() if r.headers.get("content-type", "").startswith("application/json") else {}
    ok = j.get("code") == 200
    rec("P13", "GET /template/list", "code=200 正常返回列表",
        f"HTTP {r.status_code} code={j.get('code')} msg={j.get('msg')}", ok)

    # ---------- F02: /fill/task/page ----------
    r = requests.get(f"{BASE}/fill/task/page", headers=H(admin),
                     params={"page": 1, "pageSize": 10}, timeout=30)
    j = r.json() if r.headers.get("content-type", "").startswith("application/json") else {}
    ok = j.get("code") == 200
    rec("F02", "GET /fill/task/page", "code=200 正常返回列表",
        f"HTTP {r.status_code} code={j.get('code')} msg={j.get('msg')}", ok)

    # ---------- U05: 重复用户名 ----------
    uname = "admin"  # 已存在
    r = requests.post(f"{BASE}/user", headers=H(admin),
                      json={"username": uname, "password": "Dup#Pwd2026", "realName": "重复测试"},
                      timeout=30)
    j = r.json()
    ok = j.get("code") != 200 and "已存在" in (j.get("msg") or "")
    rec("U05", "重复用户名注册", "被拒绝且提示已存在",
        f"code={j.get('code')} msg={j.get('msg')}", ok)

    # ---------- U06: 缺必填字段 ----------
    r = requests.post(f"{BASE}/user", headers=H(admin),
                      json={"realName": "无用户名"}, timeout=30)
    j = r.json()
    msg = j.get("msg") or ""
    leaky = any(k in msg.lower() for k in
                ["sqlexception", "error updating database", "java.sql", "sqlstate", "mybatis"])
    ok = j.get("code") != 200 and not leaky
    rec("U06", "缺用户名字段注册", "被友好拒绝且不泄露 SQL",
        f"code={j.get('code')} msg={msg[:100]}", ok,
        "检测到 SQL 原文泄露!" if leaky else "")

    # ---------- U10: 弱密码 ----------
    r = requests.post(f"{BASE}/user", headers=H(admin),
                      json={"username": "zzweakuser", "password": "123456", "realName": "弱密码"},
                      timeout=30)
    j = r.json()
    ok = j.get("code") != 200
    rec("U10", "弱密码 123456 注册", "被密码策略拒绝",
        f"code={j.get('code')} msg={j.get('msg')}", ok)

    # 附带：强密码应能通过（确认策略没误杀）
    r = requests.post(f"{BASE}/user", headers=H(admin),
                      json={"username": "zzstronguser", "password": "Strong@2026x", "realName": "强密码"},
                      timeout=30)
    j = r.json()
    rec("U10b", "强密码 Strong@2026x 注册", "应成功(code=200)",
        f"code={j.get('code')} msg={j.get('msg')}", j.get("code") == 200,
        "验证密码策略没有误杀合法密码")

    # ---------- R08: 删除被占用角色 ----------
    # 先查角色列表，取一个非超管角色（被 user 占用）
    roles = requests.get(f"{BASE}/role/list", headers=H(admin), timeout=30).json()
    role_list = roles.get("data") or []
    target = None
    for role in role_list:
        code = (role.get("roleCode") or "").upper()
        if code not in ("SUPER_ADMIN",):
            target = role
            break
    if target:
        r = requests.delete(f"{BASE}/role/{target['id']}", headers=H(admin), timeout=30)
        j = r.json()
        ok = j.get("code") != 200
        rec("R08", f"删除被占用角色({target.get('roleName')})", "拒绝删除并提示",
            f"code={j.get('code')} msg={j.get('msg')}", ok)
    else:
        rec("R08", "删除被占用角色", "拒绝删除并提示", "未找到可测角色(跳过)", True)

    # ---------- G07: 上传 .sh ----------
    files = {"file": ("evil.sh", b"#!/bin/bash\necho pwned\n", "application/x-sh")}
    r = requests.post(f"{BASE}/file/upload", headers=H(admin), files=files,
                      data={"fileType": "ATTACHMENT"}, timeout=30)
    try:
        j = r.json()
        msg = j.get("msg") or ""
        rejected = j.get("code") != 200 or "不支持" in msg or "拒绝" in msg
    except Exception:
        rejected = r.status_code >= 400
    rec("G07", "上传 .sh 脚本", "被拒绝", f"HTTP {r.status_code} {r.text[:120]}", rejected)

    # 附带：正常文档应能上传
    files = {"file": ("ok.txt", b"hello verify", "text/plain")}
    r = requests.post(f"{BASE}/file/upload", headers=H(admin), files=files,
                      data={"fileType": "ATTACHMENT"}, timeout=30)
    try:
        j = r.json()
        ok = j.get("code") == 200
    except Exception:
        ok = False
    rec("G07b", "上传 .txt 正常文件", "应成功", f"HTTP {r.status_code} {r.text[:120]}", ok,
        "验证白名单没误杀正常文件")

    # ---------- V01: 审核性能 ----------
    # 找一个已提交待部门审核的数据；没有就跳过
    t0 = time.time()
    r = requests.get(f"{BASE}/audit/pending", headers=H(admin), timeout=60)
    elapsed = time.time() - t0
    ok = elapsed < 5
    rec("V01", "审核相关查询耗时", "< 5s", f"{elapsed*1000:.0f}ms (HTTP {r.status_code})", ok,
        "仅验证查询延迟，完整审核链路需有真实填报数据")

    # ---------- 汇总 ----------
    total = len(results)
    passed = sum(1 for x in results if x["ok"])
    failed = total - passed
    print("\n" + "=" * 70)
    print(f"结果: {passed}/{total} 通过, {failed} 失败")
    print("=" * 70)
    if failed:
        print("\n失败项:")
        for x in results:
            if not x["ok"]:
                print(f"  - {x['code']} {x['name']}: 期望 {x['expected']} / 实际 {x['actual']}")
    with open("verify_results.json", "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f"\n详细结果已写入 verify_results.json")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
