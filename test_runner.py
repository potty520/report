#!/usr/bin/env python3
"""填报系统全功能深度测试脚本"""

import requests
import json
import sys
import time

BASE_URL = "http://localhost:8763/api"
FRONTEND_URL = "http://192.168.1.41:3000"

class TestResults:
    def __init__(self):
        self.passed = 0
        self.failed = 0
        self.skipped = 0
        self.results = []
        self.token = None
        self.captcha_key = None
        self.captcha_code = None
        self.session = requests.Session()
        self.created_ids = {}  # 记录创建的资源ID，便于清理
    
    def record(self, category, method, path, status_desc, expected_status, actual_status, 
               response_text="", passed=None):
        if passed is None:
            passed = (actual_status == expected_status) or (expected_status == 0)
        
        status = "✅ PASS" if passed else "❌ FAIL"
        result = {
            "category": category,
            "method": method,
            "path": path,
            "status_desc": status_desc,
            "expected": expected_status,
            "actual": actual_status,
            "passed": passed,
            "response_snippet": response_text[:200] if response_text else ""
        }
        
        if passed:
            self.passed += 1
        else:
            self.failed += 1
        
        self.results.append(result)
        print(f"  {status} {method} {path} → {actual_status} (expected {expected_status})")
        if not passed:
            print(f"    Response: {response_text[:300]}")
        return result
    
    def headers(self):
        h = {"Content-Type": "application/json"}
        if self.token:
            h["Authorization"] = f"Bearer {self.token}"
        return h
    
    def get(self, category, path, expected=200):
        try:
            r = self.session.get(f"{BASE_URL}{path}", headers=self.headers(), timeout=10)
            return self.record(category, "GET", path, "", expected, r.status_code, r.text, r.status_code == expected)
        except Exception as e:
            return self.record(category, "GET", path, str(e), expected, 0, str(e), False)
    
    def post(self, category, path, data=None, expected=200):
        try:
            if data is None:
                data = {}
            r = self.session.post(f"{BASE_URL}{path}", json=data, headers=self.headers(), timeout=10)
            return self.record(category, "POST", path, "", expected, r.status_code, r.text, r.status_code == expected)
        except Exception as e:
            return self.record(category, "POST", path, str(e), expected, 0, str(e), False)
    
    def put(self, category, path, data=None, expected=200):
        try:
            if data is None:
                data = {}
            r = self.session.put(f"{BASE_URL}{path}", json=data, headers=self.headers(), timeout=10)
            return self.record(category, "PUT", path, "", expected, r.status_code, r.text, r.status_code == expected)
        except Exception as e:
            return self.record(category, "PUT", path, str(e), expected, 0, str(e), False)
    
    def delete(self, category, path, expected=200):
        try:
            r = self.session.delete(f"{BASE_URL}{path}", headers=self.headers(), timeout=10)
            return self.record(category, "DELETE", path, "", expected, r.status_code, r.text, r.status_code == expected)
        except Exception as e:
            return self.record(category, "DELETE", path, str(e), expected, 0, str(e), False)

    def post_formdata(self, category, path, files=None, data=None, expected=200):
        try:
            h = {}
            if self.token:
                h["Authorization"] = f"Bearer {self.token}"
            r = self.session.post(f"{BASE_URL}{path}", files=files, data=data, headers=h, timeout=10)
            return self.record(category, "POST", path, "", expected, r.status_code, r.text, r.status_code == expected)
        except Exception as e:
            return self.record(category, "POST", path, str(e), expected, 0, str(e), False)


def run_all_tests():
    t = TestResults()
    
    print("=" * 60)
    print("1. 认证模块测试")
    print("=" * 60)
    
    # 验证码
    r = t.get("认证模块", "/auth/captcha")
    try:
        data = r.get("response_snippet") and json.loads(r["response_snippet"]) if isinstance(r.get("response_snippet"), str) and r["response_snippet"].startswith("{") else None
    except:
        data = None
    if data and "data" in data:
        t.captcha_key = data["data"].get("key", "")
        t.captcha_code = data["data"].get("code", "")
        print(f"    验证码: key={t.captcha_key}, code={t.captcha_code}")
    
    # 登录
    login_data = {"username": "admin", "password": "***REMOVED_DEFAULT_ADMIN_PASSWORD***"}
    if t.captcha_key:
        login_data["key"] = t.captcha_key
        login_data["code"] = t.captcha_code
    
    try:
        r = t.session.post(f"{BASE_URL}/auth/login", json=login_data, timeout=10)
        t.record("认证模块", "POST", "/auth/login", "", 200, r.status_code, r.text, r.status_code == 200)
        if r.status_code == 200:
            resp = r.json()
            t.token = resp.get("data", {}).get("token", "") or resp.get("token", "")
            print(f"    登录成功, token 长度: {len(t.token)}")
    except Exception as e:
        t.record("认证模块", "POST", "/auth/login", str(e), 200, 0, str(e), False)
    
    if t.token:
        # 获取菜单
        t.get("认证模块", "/auth/menu")
        # 修改密码
        t.put("认证模块", "/auth/password", {"oldPassword": "***REMOVED_DEFAULT_ADMIN_PASSWORD***", "newPassword": "***REMOVED_DEFAULT_ADMIN_PASSWORD***"})
        # 登出
        t.post("认证模块", "/auth/logout")
        # 重新登录
        r = t.session.post(f"{BASE_URL}/auth/login", json=login_data, timeout=10)
        if r.status_code == 200:
            resp = r.json()
            t.token = resp.get("data", {}).get("token", "") or resp.get("token", "")
    
    print("\n" + "=" * 60)
    print("2. 用户管理测试")
    print("=" * 60)
    
    timestamp = int(time.time())
    test_user = f"testuser_{timestamp}"
    
    t.get("用户管理", "/user/page?page=1&size=10")
    t.get("用户管理", "/user/page?page=999&size=10")  # 分页边界
    
    # 创建用户
    create_resp = t.post("用户管理", "/user", {
        "username": test_user,
        "password": "Test@123456",
        "nickname": f"测试用户{timestamp}",
        "phone": "13800138000",
        "email": f"{test_user}@test.com",
        "status": 1
    })
    
    user_id = None
    try:
        resp_data = json.loads(create_resp.get("response_snippet", "{}"))
        # Try to extract user id from response
        if isinstance(resp_data, dict):
            data = resp_data.get("data", {})
            if isinstance(data, dict):
                user_id = data.get("id") or data.get("userId")
            elif isinstance(data, int):
                user_id = data
    except:
        pass
    
    if user_id:
        t.created_ids["user"] = user_id
        print(f"    创建的用户ID: {user_id}")
        
        # 更新用户信息
        t.put("用户管理", "/user", {
            "id": user_id,
            "username": test_user,
            "nickname": f"测试用户{timestamp}_updated",
            "phone": "13800138001",
            "email": f"{test_user}_updated@test.com"
        })
        
        # 测试重复用户名创建
        t.post("用户管理", "/user", {
            "username": test_user,
            "password": "Test@123456",
            "nickname": "重复用户",
            "status": 1
        }, expected=0)  # 应该失败
        
        # 更改用户状态
        t.put("用户管理", f"/user/{user_id}/status", {"status": 0})
        t.put("用户管理", f"/user/{user_id}/status", {"status": 1})
        
        # 删除用户
        t.delete("用户管理", f"/user/{user_id}")
        
        # 删除不存在的用户
        t.delete("用户管理", "/user/999999", expected=0)
    else:
        print("    ⚠️ 未获取到用户ID，跳过部分测试")
        t.skipped += 5
    
    # 缺少必填字段测试
    t.post("用户管理", "/user", {"nickname": "nouser"}, expected=0)
    
    print("\n" + "=" * 60)
    print("3. 角色管理测试")
    print("=" * 60)
    
    t.get("角色管理", "/role/list")
    
    role_create = t.post("角色管理", "/role", {
        "name": f"test_role_{timestamp}",
        "code": f"test_role_code_{timestamp}",
        "remark": "测试角色",
        "status": 1
    })
    
    role_id = None
    try:
        resp_data = json.loads(role_create.get("response_snippet", "{}"))
        if isinstance(resp_data, dict):
            data = resp_data.get("data", {})
            if isinstance(data, dict):
                role_id = data.get("id")
            elif isinstance(data, int):
                role_id = data
    except:
        pass
    
    if role_id:
        t.created_ids["role"] = role_id
        
        t.put("角色管理", "/role", {
            "id": role_id,
            "name": f"test_role_{timestamp}_updated",
            "code": f"test_role_code_{timestamp}",
            "remark": "测试角色(已更新)",
            "status": 1
        })
        
        # 设置角色菜单
        t.put("角色管理", f"/role/{role_id}/menus", {"menuIds": [1, 2]})
        
        # 删除角色
        t.delete("角色管理", f"/role/{role_id}")
        
        # 删除不存在的角色
        t.delete("角色管理", "/role/999999", expected=0)
    else:
        print("    ⚠️ 未获取到角色ID，跳过部分测试")
        t.skipped += 4
    
    print("\n" + "=" * 60)
    print("4. 菜单管理测试")
    print("=" * 60)
    
    t.get("菜单管理", "/menu/tree")
    t.get("菜单管理", "/menu/page?page=1&size=10")
    
    menu_create = t.post("菜单管理", "/menu", {
        "parentId": 0,
        "name": f"test_menu_{timestamp}",
        "path": f"/test/menu/{timestamp}",
        "component": "test",
        "type": 1,
        "sort": 99,
        "visible": 1,
        "status": 1
    })
    
    menu_id = None
    try:
        resp_data = json.loads(menu_create.get("response_snippet", "{}"))
        if isinstance(resp_data, dict):
            data = resp_data.get("data", {})
            if isinstance(data, dict):
                menu_id = data.get("id")
            elif isinstance(data, int):
                menu_id = data
    except:
        pass
    
    if menu_id:
        t.created_ids["menu"] = menu_id
        
        t.put("菜单管理", "/menu", {
            "id": menu_id,
            "parentId": 0,
            "name": f"test_menu_{timestamp}_updated",
            "path": f"/test/menu/{timestamp}",
            "type": 1,
            "sort": 99,
            "visible": 1,
            "status": 1
        })
        
        t.delete("菜单管理", f"/menu/{menu_id}")
    else:
        print("    ⚠️ 未获取到菜单ID，跳过部分测试")
        t.skipped += 2
    
    print("\n" + "=" * 60)
    print("5. 部门管理测试")
    print("=" * 60)
    
    t.get("部门管理", "/dept/list")
    
    dept_create = t.post("部门管理", "/dept", {
        "parentId": 0,
        "name": f"测试部门_{timestamp}",
        "sort": 1,
        "status": 1
    })
    
    dept_id = None
    try:
        resp_data = json.loads(dept_create.get("response_snippet", "{}"))
        if isinstance(resp_data, dict):
            data = resp_data.get("data", {})
            if isinstance(data, dict):
                dept_id = data.get("id")
            elif isinstance(data, int):
                dept_id = data
    except:
        pass
    
    if dept_id:
        t.created_ids["dept"] = dept_id
        
        t.put("部门管理", "/dept", {
            "id": dept_id,
            "parentId": 0,
            "name": f"测试部门_{timestamp}_updated",
            "sort": 1,
            "status": 1
        })
        
        t.delete("部门管理", f"/dept/{dept_id}")
    else:
        print("    ⚠️ 未获取到部门ID，跳过部分测试")
        t.skipped += 2
    
    print("\n" + "=" * 60)
    print("6. 项目管理测试")
    print("=" * 60)
    
    t.get("项目管理", "/project/list")
    
    project_create = t.post("项目管理", "/project", {
        "name": f"测试项目_{timestamp}",
        "code": f"prj_{timestamp}",
        "status": 1
    })
    
    project_id = None
    try:
        resp_data = json.loads(project_create.get("response_snippet", "{}"))
        if isinstance(resp_data, dict):
            data = resp_data.get("data", {})
            if isinstance(data, dict):
                project_id = data.get("id")
            elif isinstance(data, int):
                project_id = data
    except:
        pass
    
    if project_id:
        t.created_ids["project"] = project_id
        
        t.put("项目管理", "/project", {
            "id": project_id,
            "name": f"测试项目_{timestamp}_updated",
            "code": f"prj_{timestamp}",
            "status": 1
        })
        
        t.delete("项目管理", f"/project/{project_id}")
    else:
        print("    ⚠️ 未获取到项目ID，跳过部分测试")
        t.skipped += 2
    
    print("\n" + "=" * 60)
    print("7. 数据源管理测试")
    print("=" * 60)
    
    t.get("数据源管理", "/datasource/page?page=1&size=10")
    
    ds_create = t.post("数据源管理", "/datasource", {
        "name": f"test_ds_{timestamp}",
        "type": "mysql",
        "host": "127.0.0.1",
        "port": 3306,
        "database": f"test_db_{timestamp}",
        "username": "root",
        "password": "test",
        "status": 1
    })
    
    ds_id = None
    try:
        resp_data = json.loads(ds_create.get("response_snippet", "{}"))
        if isinstance(resp_data, dict):
            data = resp_data.get("data", {})
            if isinstance(data, dict):
                ds_id = data.get("id")
            elif isinstance(data, int):
                ds_id = data
    except:
        pass
    
    if ds_id:
        t.created_ids["datasource"] = ds_id
        
        # 测试连接
        t.post("数据源管理", "/datasource/test", {
            "id": ds_id,
            "name": f"test_ds_{timestamp}",
            "type": "mysql",
            "host": "127.0.0.1",
            "port": 3306,
            "database": f"test_db_{timestamp}",
            "username": "root",
            "password": "test"
        }, expected=0)
        
        t.put("数据源管理", "/datasource", {
            "id": ds_id,
            "name": f"test_ds_{timestamp}_updated",
            "type": "mysql",
            "host": "127.0.0.1",
            "port": 3306,
            "database": f"test_db_{timestamp}",
            "username": "root",
            "password": "test",
            "status": 1
        })
        
        t.delete("数据源管理", f"/datasource/{ds_id}")
    else:
        print("    ⚠️ 未获取到数据源ID，跳过部分测试")
        t.skipped += 3
    
    print("\n" + "=" * 60)
    print("8. 模板管理测试")
    print("=" * 60)
    
    t.get("模板管理", "/template/page?page=1&size=10")
    
    template_create = t.post("模板管理", "/template", {
        "name": f"测试模板_{timestamp}",
        "type": "excel",
        "status": 0
    })
    
    template_id = None
    try:
        resp_data = json.loads(template_create.get("response_snippet", "{}"))
        if isinstance(resp_data, dict):
            data = resp_data.get("data", {})
            if isinstance(data, dict):
                template_id = data.get("id")
            elif isinstance(data, int):
                template_id = data
    except:
        pass
    
    if template_id:
        t.created_ids["template"] = template_id
        
        # 更改模板状态
        t.put("模板管理", f"/template/{template_id}/status", {"status": 1})
        t.put("模板管理", f"/template/{template_id}/status", {"status": 0})
        
        t.put("模板管理", "/template", {
            "id": template_id,
            "name": f"测试模板_{timestamp}_updated",
            "type": "excel",
            "status": 0
        })
        
        t.delete("模板管理", f"/template/{template_id}")
    else:
        print("    ⚠️ 未获取到模板ID，跳过部分测试")
        t.skipped += 3
    
    print("\n" + "=" * 60)
    print("9. 填报任务测试")
    print("=" * 60)
    
    t.get("填报任务", "/task/page?page=1&size=10")
    t.get("填报任务", "/task/page?page=999&size=10")  # 分页边界
    
    task_create = t.post("填报任务", "/task", {
        "name": f"测试任务_{timestamp}",
        "templateId": 1,
        "startTime": "2026-01-01 00:00:00",
        "endTime": "2026-12-31 23:59:59",
        "status": 0
    })
    
    task_id = None
    try:
        resp_data = json.loads(task_create.get("response_snippet", "{}"))
        if isinstance(resp_data, dict):
            data = resp_data.get("data", {})
            if isinstance(data, dict):
                task_id = data.get("id")
            elif isinstance(data, int):
                task_id = data
    except:
        pass
    
    if task_id:
        t.created_ids["task"] = task_id
        
        t.put("填报任务", "/task", {
            "id": task_id,
            "name": f"测试任务_{timestamp}_updated",
            "templateId": 1,
            "startTime": "2026-01-01 00:00:00",
            "endTime": "2026-12-31 23:59:59",
            "status": 0
        })
        
        # 发布任务
        t.put("填报任务", f"/task/{task_id}/publish")
        
        # 删除任务
        t.delete("填报任务", f"/task/{task_id}")
    else:
        print("    ⚠️ 未获取到任务ID，跳过部分测试")
        t.skipped += 4
    
    print("\n" + "=" * 60)
    print("10. 数据审批测试")
    print("=" * 60)
    
    t.get("数据审批", "/dataApply/page?page=1&size=10")
    
    apply_create = t.post("数据审批", "/dataApply", {
        "taskId": 1,
        "templateId": 1,
        "data": {"field1": "value1"},
        "remark": "测试申请"
    })
    
    apply_id = None
    try:
        resp_data = json.loads(apply_create.get("response_snippet", "{}"))
        if isinstance(resp_data, dict):
            data = resp_data.get("data", {})
            if isinstance(data, dict):
                apply_id = data.get("id")
            elif isinstance(data, int):
                apply_id = data
    except:
        pass
    
    if apply_id:
        t.created_ids["apply"] = apply_id
        
        t.post("数据审批", f"/dataApply/{apply_id}/approve", {"remark": "同意"})
        
        # 测试拒绝（如果状态允许）
        t.post("数据审批", f"/dataApply/{apply_id}/reject", {"remark": "拒绝原因"}, expected=0)
    else:
        print("    ⚠️ 未获取到审批ID，跳过部分测试")
        t.skipped += 2
    
    print("\n" + "=" * 60)
    print("11. 填报审核测试")
    print("=" * 60)
    
    t.post("填报审核", "/fill/save", {"taskId": 1, "data": {"test": "draft"}})
    t.post("填报审核", "/fill/submit", {"taskId": 1, "data": {"test": "submitted"}})
    t.get("填报审核", "/fill/page?page=1")
    
    # 文件上传（无需真实文件，测试接口可达性）
    t.post_formdata("填报审核", "/fill/upload", data={"taskId": "1"}, expected=0)
    
    print("\n" + "=" * 60)
    print("12. 边界与安全测试")
    print("=" * 60)
    
    # 未登录访问受保护接口
    temp_token = t.token
    t.token = None
    t.get("安全测试", "/user/page?page=1&size=10", expected=0)  # 应返回401/403
    t.get("安全测试", "/role/list", expected=0)
    t.token = temp_token
    
    # JWT 篡改测试
    original_token = t.token
    if t.token:
        t.token = t.token[:-5] + "xxxxx"
        t.get("安全测试", "/user/page?page=1&size=10", expected=0)  # 篡改token应失败
        t.token = original_token
    
    # JWT 过期模拟（直接使用一个已知过期的token格式）
    t.token = "eyJhbGciOiJIUzI1NiJ9.eyJleHAiOjEwMDAwMDAwMDAsInVzZXJJZCI6MX0.invalid"
    t.get("安全测试", "/user/page?page=1&size=10", expected=0)
    t.token = original_token
    
    print("\n" + "=" * 60)
    print("测试结果汇总")
    print("=" * 60)
    
    return t


if __name__ == "__main__":
    results = run_all_tests()
    
    print(f"\n总计: {len(results.results)} 个测试")
    print(f"  ✅ 通过: {results.passed}")
    print(f"  ❌ 失败: {results.failed}")
    print(f"  ⏭️  跳过: {results.skipped}")
    
    # 保存详细结果
    output_file = "/vol2/@apphome/trim.openclaw/data/workspace/report-system/api_test_results.json"
    import os
    os.makedirs(os.path.dirname(output_file), exist_ok=True)
    
    with open(output_file, "w") as f:
        json.dump({
            "total": len(results.results),
            "passed": results.passed,
            "failed": results.failed,
            "skipped": results.skipped,
            "results": results.results
        }, f, ensure_ascii=False, indent=2)
    
    print(f"\n详细结果已保存到: {output_file}")
    sys.exit(0 if results.failed == 0 else 1)
