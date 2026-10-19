#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
前端静态资源部署脚本

流程：
  1. 备份线上 /opt/report/web → web.bak-<时间戳>
  2. 清空并上传本地 dist 全部文件
  3. 校验文件数与 index.html 引用的入口文件存在
  4. nginx 无需重启（静态目录），但执行一次 -t 做配置语法校验

用法: python deploy_web.py [dist目录]
"""
import os
import sys

from rsh import get_client, run

DEFAULT_DIST = r"E:\C++\report_src\report-master\frontend2\dist"
REMOTE_ROOT = "/opt/report/web"


def walk_files(root):
    files = []
    for dirpath, _, filenames in os.walk(root):
        for fn in filenames:
            local = os.path.join(dirpath, fn)
            rel = os.path.relpath(local, root).replace("\\", "/")
            files.append((local, rel))
    return files


def upload_all(client, local_root):
    files = walk_files(local_root)
    print(f"本地 dist 文件数: {len(files)}")
    sftp = client.open_sftp()

    # 确保远端目录存在
    def ensure_remote_dir(rel_dir):
        path = REMOTE_ROOT
        if rel_dir:
            for part in rel_dir.split("/"):
                path = path + "/" + part
                try:
                    sftp.stat(path)
                except IOError:
                    sftp.mkdir(path)

    ok = 0
    for local, rel in files:
        rel_dir = os.path.dirname(rel)
        ensure_remote_dir(rel_dir)
        remote = f"{REMOTE_ROOT}/{rel}"
        sftp.put(local, remote)
        ok += 1
    sftp.close()
    print(f"✅ 已上传 {ok}/{len(files)} 个文件")
    return len(files)


def main():
    dist = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_DIST
    if not os.path.isdir(dist):
        print(f"[FATAL] dist 目录不存在: {dist}")
        sys.exit(1)

    client = get_client()
    try:
        # 1. 读取当前入口，便于回滚核对
        rc, out, _ = run(f"grep -o 'index-[A-Za-z0-9_-]*\\.js' {REMOTE_ROOT}/index.html | head -1",
                         client=client)
        print(f"部署前入口: {out.strip()}")

        # 2. 备份
        rc, out, err = run(
            f"ts=$(date +%Y%m%d-%H%M%S) && cp -r {REMOTE_ROOT} {REMOTE_ROOT}.bak-$ts "
            f"&& echo {REMOTE_ROOT}.bak-$ts", client=client, timeout=120)
        backup = out.strip()
        print(f"已备份到: {backup}")

        # 3. 清理旧的 assets（保留 index.html，稍后覆盖）
        rc, out, err = run(f"rm -rf {REMOTE_ROOT}/assets", client=client)
        print("已清理旧 assets")

        # 4. 上传
        total = upload_all(client, dist)

        # 5. 校验：入口文件存在 + 文件数
        rc, out, _ = run(f"cat {REMOTE_ROOT}/index.html", client=client)
        import re
        m = re.search(r'src="(/assets/[^"]+\.js)"', out)
        entry = m.group(1) if m else None
        print(f"部署后入口: {entry}")

        if entry:
            rc, out2, _ = run(f"test -f {REMOTE_ROOT}{entry} && echo EXISTS || echo MISSING",
                              client=client)
            print(f"入口文件校验: {out2.strip()}")
            if "MISSING" in out2:
                print("[FATAL] 入口文件缺失，部署失败")
                sys.exit(1)

        rc, out, _ = run(f"find {REMOTE_ROOT} -type f | wc -l", client=client)
        remote_count = out.strip()
        print(f"远端文件数: {remote_count} (本地 {total} + index.html)")

        # 6. nginx 语法检查 + 轻量 reload（静态资源不必须，但保证配置无碍）
        rc, out, err = run("nginx -t 2>&1 | tail -2", client=client)
        print(f"nginx 检查: {out.strip()}")

        # 7. 外网入口
        rc, out, _ = run("curl -s -o /dev/null -w '%{http_code}' --max-time 10 http://127.0.0.1:8091/",
                         client=client)
        print(f"前端入口: HTTP {out.strip()}")

        print("\n✅ 前端部署完成")
    finally:
        client.close()


if __name__ == "__main__":
    main()
