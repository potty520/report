# CentOS 7.9 部署清单（Spring Boot 3 + JDK 17）

> 适用：政务外网生产机，CentOS 7.9，系统默认 java 为 1.8，需要跑 Spring Boot 3.5 版本的填报系统。

## 一、先确认三件事

```bash
cat /etc/redhat-release            # CentOS Linux release 7.9.2009
getconf GNU_LIBC_VERSION           # 应为 glibc 2.17 —— Temurin 17 要求 ≥2.17，满足
java -version                      # 当前 1.8，保持不动
```

## 二、安装 JDK 17（不动系统默认 java）

CentOS 7 官方源**没有** openjdk-17，必须用 tar.gz 离线包：

```bash
mkdir -p /opt/jdk17
tar -xzf OpenJDK17U-jdk_x64_linux_hotspot_17.0.20.1_1.tar.gz -C /opt/jdk17 --strip-components=1
/opt/jdk17/bin/java -version        # 应输出 17.0.20.1
```

离线包来源（任选其一）：

- 清华镜像：`https://mirrors.tuna.tsinghua.edu.cn/Adoptium/17/jdk/x64/linux/OpenJDK17U-jdk_x64_linux_hotspot_17.0.20.1_1.tar.gz`
- 华为镜像：`https://mirrors.huaweicloud.com/openjdk/17.0.2/openjdk-17.0.2_linux-x64_bin.tar.gz`

**不要**改 `update-alternatives` 或 `/etc/profile` 里的 `JAVA_HOME`——机器上其它 1.8 应用会跟着变。

## 三、systemd 单元（关键：适配 CentOS 7 的 systemd 219）

CentOS 7 的 systemd 是 **219**，不支持 `StandardOutput=append:`（240+ 才有），直接抄 Ubuntu 的单元会起不来。用 shell 重定向：

```ini
# /etc/systemd/system/report.service
[Unit]
Description=Report System
After=network.target mysqld.service
Wants=mysqld.service

[Service]
Type=simple
User=zk
WorkingDirectory=/opt/report
EnvironmentFile=/opt/report/report.env
ExecStart=/bin/bash -c 'exec /opt/jdk17/bin/java -Xms512m -Xmx1g -Dfile.encoding=UTF-8 -Djava.awt.headless=true -jar /opt/report/report-api.jar --server.port=8763 >> /opt/report/logs/app.log 2>&1'
Restart=on-failure
RestartSec=5
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
```

```bash
systemctl daemon-reload
systemctl enable --now report
systemctl status report --no-pager
tail -f /opt/report/logs/app.log
```

> 因为日志被重定向到文件，`journalctl -u report` 只会看到 systemd 层的信息，应用日志看 `/opt/report/logs/app.log`（logrotate 已配置）。

## 四、CentOS 特有的三个坑

| 坑 | 现象 | 处理 |
|---|---|---|
| **SELinux** | nginx 反代 8763 返回 502，`audit.log` 有 `denied` | `setsebool -P httpd_can_network_connect 1`（若 Enforcing） |
| **firewalld** | 外部访问 8080 超时 | `firewall-cmd --permanent --add-port=8080/tcp && firewall-cmd --reload` |
| **缺字体** | 登录页验证码接口 500，日志里有 `java.awt.Font` 相关异常 | `yum install -y fontconfig dejavu-sans-fonts`（最小化安装常见） |

## 五、数据库与 nginx

- MySQL 5.7 在 CentOS 7 上是原生支持版本，按 `docs/database.sql` + 升级脚本初始化即可（已在 5.7.38 实测 34/34 通过）。
- 时区：`timedatectl set-timezone Asia/Shanghai`；应用启动时会把 JVM 与会话统一到 +08:00。
- nginx 用 `conf.d/report-lan.conf`，路径与 Ubuntu 版一致（`/etc/nginx/conf.d/`）。

## 六、验证清单

```bash
systemctl is-active report
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8763/api/auth/captcha   # 200
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/                   # 200
tail -n 5 /opt/report/logs/app.log                                               # Started ReportApplication
```

再用浏览器走一遍：登录（验证码能出图）→ 数据总览 → 建模板 → 建任务。

## 七、两个必须知道的风险

1. **CentOS 7 已于 2024-06-30 EOL**：不再有安全更新，等保测评会列"操作系统停止维护"。建议在材料里写补偿措施，并把迁移到麒麟/统信/Rocky 9 列入计划。
2. **JDK 17 与 JDK 8 共存没问题**，但只对本服务生效；如果运维统一要求"只能用系统 java"，那就得回退到 `/opt/report/report-api-boot2.jar`（Boot 2.7，Java 8 可跑，但会带回 12 严重 / 61 高危组件漏洞）。
