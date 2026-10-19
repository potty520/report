#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
通过 GitHub REST API 推送提交（绕过被阻断的 github.com:443）

背景：本机到 github.com:443 被阻断（SNI 层面），但 api.github.com 可达。
git push 走 https://github.com，必然失败；这里改用 Git Data API 逐 blob/tree 上传。

用法:
  python push_via_api.py --token <TOKEN>            # 推送当前 HEAD
  python push_via_api.py --token <TOKEN> --dry-run  # 只对比差异，不上传
"""
import base64
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request

REPO = "potty520/report"
BRANCH = "master"
LOCAL = r"E:\C++\report_src\report-master"


def token_from_gh():
    try:
        out = subprocess.run(["gh", "auth", "token"], capture_output=True, text=True, timeout=30)
        return out.stdout.strip()
    except Exception:
        return None


class Api:
    def __init__(self, token):
        self.token = token

    def call(self, method, path, payload=None, timeout=120):
        url = f"https://api.github.com/repos/{REPO}{path}"
        data = json.dumps(payload).encode() if payload is not None else None
        req = urllib.request.Request(url, data=data, method=method)
        req.add_header("Authorization", f"Bearer {self.token}")
        req.add_header("Accept", "application/vnd.github+json")
        req.add_header("X-GitHub-Api-Version", "2022-11-28")
        if data:
            req.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                body = r.read().decode("utf-8", "replace")
                return r.status, (json.loads(body) if body else None)
        except urllib.error.HTTPError as e:
            body = e.read().decode("utf-8", "replace")
            try:
                j = json.loads(body)
            except Exception:
                j = {"raw": body}
            return e.code, j


def git(*args):
    env = dict(os.environ, GIT_PAGER="cat", PYTHONIOENCODING="utf-8")
    r = subprocess.run(["git"] + list(args), cwd=LOCAL, capture_output=True,
                       text=True, encoding="utf-8", errors="replace", env=env)
    if r.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} 失败: {r.stderr}")
    return r.stdout


def changed_files(base_sha, head_sha):
    """返回 (新增/修改文件列表, 删除文件列表)"""
    # -c core.quotepath=false 保证中文路径不被转义成 \344\277\256
    out = git("-c", "core.quotepath=false", "diff", "--name-status", "-z", base_sha, head_sha)
    added, removed = [], []
    # -z 输出：status\0path\0status\0path\0...
    parts = [p for p in out.split("\0") if p]
    i = 0
    while i < len(parts):
        status = parts[i]
        path = parts[i + 1] if i + 1 < len(parts) else None
        i += 2
        if path is None:
            break
        if status.startswith("D"):
            removed.append(path)
        else:
            added.append(path)
    return added, removed


def main():
    token = None
    for i, a in enumerate(sys.argv):
        if a == "--token" and i + 1 < len(sys.argv):
            token = sys.argv[i + 1]
    token = token or token_from_gh()
    if not token:
        print("[FATAL] 没有 GitHub token")
        return 1
    dry = "--dry-run" in sys.argv

    api = Api(token)

    head_sha = git("rev-parse", "HEAD").strip()
    head_msg = git("log", "-1", "--pretty=%B").strip()
    print(f"本地 HEAD: {head_sha[:7]}")

    # 1. 远端当前 master 指向
    st, ref = api.call("GET", f"/git/ref/heads/{BRANCH}")
    if st != 200:
        print(f"[FATAL] 读取远端分支失败: {st} {ref}")
        return 1
    remote_sha = ref["object"]["sha"]
    print(f"远端 HEAD: {remote_sha[:7]}")

    if remote_sha == head_sha:
        print("✅ 已同步，无需推送")
        return 0

    # 2. 变更文件
    added, removed = changed_files(remote_sha, head_sha)
    print(f"变更: {len(added)} 个文件新增/修改, {len(removed)} 个删除")
    for p in added[:20]:
        print(f"  + {p}")
    if removed:
        for p in removed[:10]:
            print(f"  - {p}")

    if dry:
        print("\n[DRY-RUN] 不上传")
        return 0

    # 3. 上传 blob
    print("\n上传 blob ...")
    tree_items = []
    for i, path in enumerate(added):
        full = os.path.join(LOCAL, path.replace("/", os.sep))
        if not os.path.exists(full):
            print(f"  [SKIP] 本地不存在: {path}")
            continue
        with open(full, "rb") as f:
            content = f.read()
        # 大文件走 base64，小文件无所谓，统一 base64 更稳
        payload = {"content": base64.b64encode(content).decode(), "encoding": "base64"}
        st, blob = api.call("POST", "/git/blobs", payload)
        if st not in (200, 201):
            print(f"  [FAIL] {path}: {st} {blob}")
            return 1
        mode = "100755" if os.access(full, os.X_OK) and not full.endswith((".py", ".ts", ".tsx", ".md", ".json", ".html", ".java", ".yml", ".sql")) else "100644"
        tree_items.append({"path": path, "mode": mode, "type": "blob", "sha": blob["sha"]})
        if (i + 1) % 5 == 0 or i + 1 == len(added):
            print(f"  进度 {i+1}/{len(added)}")

    # 删除的文件：tree 中标记 sha=None
    for path in removed:
        tree_items.append({"path": path, "mode": "100644", "type": "blob", "sha": None})

    # 4. 基于远端 tree 创建新 tree
    print("\n创建 tree ...")
    st, remote_commit = api.call("GET", f"/git/commits/{remote_sha}")
    if st != 200:
        print(f"[FATAL] 读取远端 commit 失败: {st}")
        return 1
    base_tree = remote_commit["tree"]["sha"]

    st, tree = api.call("POST", "/git/trees", {"base_tree": base_tree, "tree": tree_items})
    if st not in (200, 201):
        print(f"[FATAL] 创建 tree 失败: {st} {tree}")
        return 1
    print(f"  tree sha: {tree['sha'][:7]}")

    # 5. 创建 commit
    print("创建 commit ...")
    st, commit = api.call("POST", "/git/commits", {
        "message": head_msg,
        "tree": tree["sha"],
        "parents": [remote_sha],
    })
    if st not in (200, 201):
        print(f"[FATAL] 创建 commit 失败: {st} {commit}")
        return 1
    print(f"  commit sha: {commit['sha'][:7]}")

    # 6. 更新分支
    print("更新分支 ...")
    st, res = api.call("PATCH", f"/git/refs/heads/{BRANCH}", {"sha": commit["sha"], "force": False})
    if st not in (200, 201):
        print(f"[FATAL] 更新分支失败: {st} {res}")
        return 1
    print(f"\n✅ 推送成功: {remote_sha[:7]} -> {commit['sha'][:7]}")
    print(f"   https://github.com/{REPO}/commit/{commit['sha']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
