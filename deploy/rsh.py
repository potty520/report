#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""服务器远程执行工具（填报系统部署用）"""
import sys
import paramiko

HOST = "103.85.25.169"
PORT = 23195
USER = "root"
PWD = "***REMOVED_SSH_PASSWORD***"


def get_client():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, port=PORT, username=USER, password=PWD, timeout=30)
    return c


def run(cmd, timeout=120, client=None):
    own = client is None
    c = client or get_client()
    try:
        _, stdout, stderr = c.exec_command(cmd, timeout=timeout)
        out = stdout.read().decode("utf-8", "replace")
        err = stderr.read().decode("utf-8", "replace")
        rc = stdout.channel.recv_exit_status()
        return rc, out, err
    finally:
        if own:
            c.close()


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "echo hello"
    rc, out, err = run(cmd)
    print(out)
    if err.strip():
        print("[stderr]", err, file=sys.stderr)
    sys.exit(rc)
