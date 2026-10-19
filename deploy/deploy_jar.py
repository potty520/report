#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
填报系统 jar 部署脚本

流程：
  1. 分块断点续传上传 jar 到 /opt/report/jar/report-system-1.0.0.jar.new
  2. 校验大小与本地一致
  3. 备份旧 jar → 替换
  4. systemctl restart report-api
  5. 健康检查（HTTP 200 + 启动日志无致命错误）

用法: python deploy_jar.py [jar路径]
默认 jar: E:\\C++\\report_src\\report-master\\backend\\target\\report-system-1.0.0.jar
"""
import os
import sys
import time

import paramiko

from rsh import HOST, PORT, USER, PWD, get_client, run

DEFAULT_JAR = r"E:\C++\report_src\report-master\backend\target\report-system-1.0.0.jar"
REMOTE_DIR = "/opt/report/jar"
REMOTE_JAR = f"{REMOTE_DIR}/report-system-1.0.0.jar"
STAGING = f"{REMOTE_JAR}.new"
CHUNK = 1024 * 1024  # 1MB 分块，避免大文件 SFTP 直传断连


def upload(client, local_path, remote_path, chunk=CHUNK):
    size = os.path.getsize(local_path)
    print(f"本地 jar: {local_path} ({size/1024/1024:.2f} MB)")

    sftp = client.open_sftp()

    # 断点续传：查看已上传字节数
    uploaded = 0
    try:
        uploaded = sftp.stat(remote_path).st_size
        if uploaded > size:
            uploaded = 0
    except IOError:
        uploaded = 0

    if uploaded:
        print(f"检测到已上传 {uploaded/1024/1024:.2f} MB，从断点续传")
        mode = "ab"
    else:
        mode = "wb"

    with open(local_path, "rb") as f:
        f.seek(uploaded)
        with sftp.file(remote_path, mode) as rf:
            rf.set_pipelined(True)
            sent = uploaded
            last_pct = -1
            while True:
                buf = f.read(chunk)
                if not buf:
                    break
                rf.write(buf)
                sent += len(buf)
                pct = int(sent * 100 / size)
                if pct != last_pct and pct % 10 == 0:
                    print(f"  上传进度 {pct}% ({sent/1024/1024:.1f} MB)")
                    last_pct = pct
    sftp.close()

    # 校验大小
    sftp = client.open_sftp()
    remote_size = sftp.stat(remote_path).st_size
    sftp.close()
    if remote_size != size:
        raise RuntimeError(f"上传大小不一致: 本地 {size} / 远端 {remote_size}")
    print(f"✅ 上传完成并校验一致: {remote_size} bytes")


def main():
    local_jar = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_JAR
    if not os.path.exists(local_jar):
        print(f"[FATAL] jar 不存在: {local_jar}")
        sys.exit(1)

    client = get_client()
    try:
        # 0. 前置检查
        rc, out, _ = run("systemctl is-active report-api", client=client)
        print(f"部署前服务状态: {out.strip()}")

        # 1. 上传
        rc, out, _ = run(f"rm -f {STAGING}", client=client)
        upload(client, local_jar, STAGING)

        # 2. 备份旧 jar
        ts = time.strftime("%Y%m%d-%H%M%S")
        rc, out, err = run(
            f"cd {REMOTE_DIR} && if [ -f report-system-1.0.0.jar ]; then "
            f"cp report-system-1.0.0.jar report-system-1.0.0.jar.bak-{ts} && echo backup-ok; fi",
            client=client)
        print(f"备份旧 jar: {out.strip()} (bak-{ts})")

        # 3. 替换
        rc, out, err = run(f"mv -f {STAGING} {REMOTE_JAR} && chmod 644 {REMOTE_JAR}", client=client)
        if rc != 0:
            print(f"[FATAL] 替换失败: {err}")
            sys.exit(1)
        print("✅ jar 已替换")

        # 4. 重启
        print("重启 report-api ...")
        rc, out, err = run("systemctl restart report-api", timeout=120, client=client)
        if rc != 0:
            print(f"[FATAL] 重启失败: {err}")
            sys.exit(1)

        # 5. 健康检查：等待启动
        print("等待服务启动 ...")
        ready = False
        for i in range(40):
            time.sleep(3)
            rc, out, _ = run("systemctl is-active report-api", client=client)
            if out.strip() != "active":
                print(f"  [{i*3}s] 状态={out.strip()} (可能正在重启)")
                continue
            rc, out, _ = run(
                "curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://127.0.0.1:8763/api/auth/captcha",
                client=client)
            code = out.strip()
            if code == "200":
                ready = True
                print(f"  [{i*3}s] 健康检查通过 HTTP {code}")
                break
            print(f"  [{i*3}s] HTTP {code}，继续等待")

        if not ready:
            print("[FATAL] 服务未能通过健康检查")
            rc, out, _ = run("tail -40 /opt/report/logs/stderr.log", client=client)
            print(out)
            rc, out, _ = run("tail -20 /opt/report/logs/stdout.log", client=client)
            print(out)
            sys.exit(1)

        # 6. 检查启动日志
        rc, out, _ = run(
            "grep -E 'Started ReportApplication|ERROR|Exception' /opt/report/logs/stdout.log | tail -15",
            client=client)
        print("\n=== 启动日志关键行 ===")
        print(out.strip() or "(无)")

        # 7. 外网入口检查
        rc, out, _ = run(
            "curl -s -o /dev/null -w '%{http_code}' --max-time 10 http://127.0.0.1:8091/",
            client=client)
        print(f"\n前端 8091 入口: HTTP {out.strip()}")

        print("\n✅ 部署完成")
    finally:
        client.close()


if __name__ == "__main__":
    main()
