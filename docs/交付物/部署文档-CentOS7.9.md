# 填报系统部署文档

**适用环境**：CentOS 7.9 · 政务外网（内网）· MySQL 5.7 / 8.0 · JDK 17 · nginx
**应用版本**：report-system 1.0.0（Spring Boot 3.5.16 + Jakarta + Security 6）
**文档版本**：v1.0 ｜ 2026-10-24

---

## 1. 部署架构

系统为「前后端分离 + 单机部署」，三件套即可运行：

```
浏览器 ──HTTP:8080──> nginx ──/api 反代──> Spring Boot 8763 ──JDBC──> MySQL 3306
                        │                                              ├─ report_system（系统库，30 张表）
                        └─ /opt/report/static（前端静态资源）            └─ report_target（物化目标库）
```

| 端口 | 用途 | 暴露范围 |
|---|---|---|
| 8080 | 前端页面 + `/api` 反向代理 | 对内网开放 |
| 8763 | 后端 API（context-path `/api`） | 仅本机（127.0.0.1） |
| 3306 | MySQL | 仅本机 |

> 政务外网若要求 HTTPS，由前置统一网关终结 TLS，回源到本机 `http://<IP>:8080`；本机不启 TLS。

---

## 2. 环境要求

| 项 | 要求 | 检查命令 |
|---|---|---|
| 操作系统 | CentOS 7.9 x86_64 | `cat /etc/redhat-release` |
| glibc | ≥ 2.17（JDK 17 要求） | `getconf GNU_LIBC_VERSION` |
| CPU / 内存 | 4C / 8G 起（当前实测 4C8G 足够） | `nproc` / `free -m` |
| 磁盘 | ≥ 100G（应用 + 备份 + 附件） | `df -h /` |
| JDK | **17**（与系统 1.8 共存，不改默认） | `/opt/jdk17/bin/java -version` |
| 数据库 | MySQL 5.7.44 或 8.0.x | `mysql --version` |
| nginx | 1.20+ | `nginx -v` |
| 系统默认 java | 1.8（保持不动，供其它应用使用） | `java -version` |

---

## 3. 部署前准备

### 3.1 账号与目录

```bash
useradd -r -m -s /sbin/nologin report || true
mkdir -p /opt/report/{logs,static,files}
chown -R report:report /opt/report
```

### 3.2 离线包清单

| 文件 | 说明 |
|---|---|
| `OpenJDK17U-jdk_x64_linux_hotspot_17.0.20.1_1.tar.gz` | JDK 17 离线包 |
| `report-api.jar` | 后端可执行包（约 83MB） |
| `report-dist.zip` | 前端构建产物 |
| `database.sql`、`report_2.0_upgrade*.sql` | 建库与升级脚本（共 7 个） |
| `report-lan.conf`、`report-security.conf`、`report.service`、`report.env`、`report-backup.sh` | 配置与运维脚本 |

### 3.3 系统设置

```bash
timedatectl set-timezone Asia/Shanghai
timedatectl status | grep 'Time zone'

# SELinux（若为 Enforcing，nginx 反代后端必须放行）
getenforce
setsebool -P httpd_can_network_connect 1

# 防火墙
firewall-cmd --permanent --add-port=8080/tcp
firewall-cmd --reload
firewall-cmd --list-ports
```

---

## 4. 安装 JDK 17

CentOS 7 官方源没有 openjdk-17，使用 tar.gz 离线包，装到独立目录，**不改系统默认 java**。

```bash
mkdir -p /opt/jdk17
tar -xzf OpenJDK17U-jdk_x64_linux_hotspot_17.0.20.1_1.tar.gz -C /opt/jdk17 --strip-components=1
/opt/jdk17/bin/java -version
```

预期输出：

```
openjdk version "17.0.20.1" 2026-08-18
OpenJDK Runtime Environment Temurin-17.0.20.1+1 (build 17.0.20.1+1)
```

> 不要执行 `update-alternatives --config java`，也不要在 `/etc/profile` 里改 `JAVA_HOME`——
> 机器上其它业务仍依赖 1.8，本服务在 systemd 单元里用绝对路径指定 JDK 17 即可。

---

## 5. 数据库初始化

### 5.1 建库与账号

```sql
CREATE DATABASE IF NOT EXISTS report_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS report_target DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER 'report'@'127.0.0.1' IDENTIFIED BY '<强口令>';
GRANT ALL PRIVILEGES ON report_system.* TO 'report'@'127.0.0.1';
GRANT ALL PRIVILEGES ON report_target.* TO 'report'@'127.0.0.1';
FLUSH PRIVILEGES;
```

### 5.2 执行建表与升级脚本（顺序不可颠倒）

```bash
mysql --default-character-set=utf8mb4 < database.sql
for f in report_2.0_upgrade.sql report_2.0_upgrade2.sql report_2.0_upgrade3.sql \
         report_2.0_upgrade4_audit_menus.sql report_2.0_upgrade5_backend.sql \
         report_2.0_upgrade6_data_apply.sql; do
  mysql --default-character-set=utf8mb4 report_system < $f && echo "OK $f"
done
```

### 5.3 校验

```bash
mysql -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='report_system';"   # 应为 30
mysql -N -e "SELECT id,username FROM report_system.sys_user;"                                      # 应有 admin/user
```

### 5.4 初始化账号

仓库中不保留默认口令。首次部署需写入 bcrypt 口令：

```bash
# 生成本地哈希（任意装有 bcrypt 的机器）
python3 -c "import bcrypt;print(bcrypt.hashpw(b'你的口令', bcrypt.gensalt(10)).decode().replace('\$2b\$','\$2a\$',1))"

UPDATE report_system.sys_user SET password='<哈希>' WHERE username='admin';
```

### 5.5 MySQL 参数建议

```ini
# /etc/my.cnf
[mysqld]
character-set-server = utf8mb4
collation-server     = utf8mb4_general_ci
default-time-zone    = '+08:00'
max_allowed_packet   = 64M
sql_mode = STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION
```

---

## 6. 部署后端

### 6.1 目录与文件

```bash
install -m 644 -o report -g report report-api.jar /opt/report/report-api.jar
install -m 600 -o report -g report report.env     /opt/report/report.env
```

### 6.2 环境变量文件 `/opt/report/report.env`

```ini
REPORT_DB_URL=jdbc:mysql://127.0.0.1:3306/report_system?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
REPORT_DB_USERNAME=report
REPORT_DB_PASSWORD=<数据库口令>
REPORT_JWT_SECRET=<≥32 字节随机串>
REPORT_CRYPTO_KEY=<≥16 位随机串>
```

生成随机串：

```bash
head -c 48 /dev/urandom | base64 | tr -d '\n'; echo
```

### 6.3 systemd 单元（CentOS 7 专用写法）

CentOS 7 的 systemd 为 219，**不支持** `StandardOutput=append:`，用 shell 重定向：

```ini
# /etc/systemd/system/report.service
[Unit]
Description=Report System
After=network.target mysqld.service
Wants=mysqld.service

[Service]
Type=simple
User=report
WorkingDirectory=/opt/report
EnvironmentFile=/opt/report/report.env
ExecStart=/bin/bash -c 'exec /opt/jdk17/bin/java -Xms512m -Xmx1g -Dfile.encoding=UTF-8 -Djava.awt.headless=true -jar /opt/report/report-api.jar --server.port=8763 >> /opt/report/logs/app.log 2>&1'
Restart=on-failure
RestartSec=5
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
```

### 6.4 启动与确认

```bash
systemctl daemon-reload
systemctl enable --now report
systemctl status report --no-pager
tail -n 30 /opt/report/logs/app.log        # 期望看到 Started ReportApplication
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8763/api/auth/captcha   # 200
```

---

## 7. 部署前端与 nginx

### 7.1 静态资源

```bash
unzip -q report-dist.zip -d /tmp/report-dist
cp -r /tmp/report-dist/* /opt/report/static/
chown -R report:report /opt/report/static
```

### 7.2 安全响应头片段 `/etc/nginx/snippets/report-security.conf`

```nginx
add_header X-Content-Type-Options "nosniff" always;
add_header X-Frame-Options "DENY" always;
add_header Referrer-Policy "same-origin" always;
add_header Permissions-Policy "geolocation=(), microphone=(), camera=()" always;
add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'" always;
```

### 7.3 站点配置 `/etc/nginx/conf.d/report-lan.conf`

```nginx
upstream report_backend {
    server 127.0.0.1:8763;
    keepalive 32;
}
limit_req_zone $binary_remote_addr zone=report_api:10m rate=20r/s;

server {
    listen 8080;
    server_name _;
    server_tokens off;
    root /opt/report/static;
    index index.html;
    charset utf-8;
    client_max_body_size 60m;

    gzip on;
    gzip_static on;
    gzip_min_length 1024;
    gzip_types text/plain text/css application/javascript application/json image/svg+xml;

    include /etc/nginx/snippets/report-security.conf;

    location /assets/ {
        include /etc/nginx/snippets/report-security.conf;
        expires 365d;
        add_header Cache-Control "public, immutable";
        try_files $uri =404;
    }
    location = /index.html {
        include /etc/nginx/snippets/report-security.conf;
        add_header Cache-Control "no-cache, must-revalidate";
    }
    location / {
        include /etc/nginx/snippets/report-security.conf;
        try_files $uri $uri/ /index.html;
    }
    location /api/ {
        include /etc/nginx/snippets/report-security.conf;
        limit_req zone=report_api burst=40 nodelay;
        limit_req_status 429;
        proxy_pass http://report_backend;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 180s;
    }
}
```

### 7.4 生效与检查

```bash
nginx -t
systemctl reload nginx
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/
curl -s -I http://127.0.0.1:8080/ | grep -i content-security-policy
```

---

## 8. 环境变量一览

| 变量 | 必填 | 说明 |
|---|---|---|
| `REPORT_DB_URL` | 是 | JDBC URL，必须带 `serverTimezone=Asia/Shanghai` |
| `REPORT_DB_USERNAME` / `REPORT_DB_PASSWORD` | 是 | 数据库账号 |
| `REPORT_JWT_SECRET` | 是 | ≥32 字节，缺失拒绝启动；变更后旧 token 全部失效 |
| `REPORT_CRYPTO_KEY` | 建议 | 数据源密码 AES/GCM 加密密钥，缺失则明文存储并告警 |
| `REPORT_CAPTCHA_FIXED_CODE` | 否 | 仅供自动化测试的固定验证码，**生产必须为空** |
| `REPORT_INBOUND_REQUIRE_AUDIT` | 否 | 默认 `true`，入库前要求两级审核通过 |
| `REPORT_UPLOAD_MAX_FILE_MB` | 否 | 单文件上限，默认 50 |
| `REPORT_CORS_ALLOWED_ORIGINS` | 否 | 默认空=不开启跨域；前后端同源无需配置 |
| `REPORT_BOOTSTRAP_ADMIN_PASSWORD` / `..._USER_PASSWORD` | 否 | 仅首次初始化账号时使用 |

---

## 9. 安全加固（部署即生效）

### 9.1 应用侧

- 登录验证码 + 连续失败锁定；未登录 401、越权 403；错误信息脱敏；
- 数据源口令 AES/GCM 加密存储；JWT 无状态，支持强制下线；
- 上传文件后缀白名单 + 大小上限；入库幂等 + 两级审核门禁。

### 9.2 系统侧

```bash
# 日志轮转（保留 30 天）
cat > /etc/logrotate.d/report <<'EOF'
/opt/report/logs/*.log {
    daily
    rotate 30
    missingok
    notifempty
    compress
    delaycompress
    copytruncate
    create 0640 report report
}
EOF

# 每日备份（02:30，保留 30 天）
install -m 755 report-backup.sh /usr/local/bin/report-backup.sh
echo '30 2 * * * root /usr/local/bin/report-backup.sh >> /var/log/report-backup.log 2>&1' > /etc/cron.d/report-backup
```

### 9.3 口令与账号

- 上线前修改 `admin` / 业务账号口令，符合单位口令规范；
- 数据库账号仅授权 `report_system`、`report_target`；
- nginx、MySQL、应用均不得监听 0.0.0.0（除 nginx 8080 按需）。

---

## 10. 验证与验收

### 10.1 冒烟命令

```bash
systemctl is-active report nginx mysqld
curl -s -o /dev/null -w 'web=%{http_code}\n' http://127.0.0.1:8080/
curl -s -o /dev/null -w 'api=%{http_code}\n' http://127.0.0.1:8763/api/auth/captcha
tail -n 5 /opt/report/logs/app.log
```

### 10.2 浏览器走查

1. 登录页能出图形验证码，`admin` 可登录；
2. 数据总览四个指标卡与图表正常渲染；
3. 模板管理 → 新建模板（5 步向导）→ 物化建表成功；
4. 任务管理 → 建任务 → 发布 → 分派（填报/审核）；
5. 填报人登录 → 填报 → 保存草稿 → 提交；
6. 部门审核 → 任务方审核 → 台账「入库」；
7. 审核轨迹能看到完整节点。

### 10.3 接口验收脚本（可选）

`acceptance_test.py` 覆盖 34 项断言（登录、模板、任务、填报、驳回、两级审核、入库幂等、周期生成）。
执行前需临时开启 `REPORT_CAPTCHA_FIXED_CODE`，**执行后务必移除并重启**。

---

## 11. 日常运维

| 操作 | 命令 |
|---|---|
| 启动 / 停止 / 重启 | `systemctl start\|stop\|restart report` |
| 查看状态 | `systemctl status report --no-pager` |
| 应用日志 | `tail -f /opt/report/logs/app.log` |
| 访问日志 | `tail -f /var/log/nginx/report-lan-access.log` |
| 手动备份 | `/usr/local/bin/report-backup.sh` |
| 查看备份 | `ls -lh /var/backups/report` |
| 升级后端 | 替换 jar → `systemctl restart report` |
| 升级前端 | 覆盖 `/opt/report/static` → `systemctl reload nginx` |

### 11.1 备份与恢复

```bash
# 备份内容：report_system、report_target、/opt/report/files
# 恢复：
systemctl stop report
gunzip -c /var/backups/report/report_system_<时间戳>.sql.gz | mysql report_system
tar -xzf /var/backups/report/files_<时间戳>.tar.gz -C /opt/report
systemctl start report
```

### 11.2 升级与回滚

```bash
# 升级前保留旧包
cp /opt/report/report-api.jar /opt/report/report-api.bak.$(date +%F)

# 回滚
cp /opt/report/report-api.bak.<日期> /opt/report/report-api.jar
systemctl restart report
```

---

## 12. 故障排查

| 现象 | 可能原因 | 排查/处理 |
|---|---|---|
| 启动即退出，日志无 Started | 用了 JDK 8 启动 | 检查 `ExecStart` 是否为 `/opt/jdk17/bin/java`；报 `UnsupportedClassVersionError` 即版本不对 |
| `UnsupportedClassVersionError: 61` | 同上 | 同上，Boot 3 必须 Java 17 |
| 502 Bad Gateway | 后端未起 / SELinux 拦截 | `systemctl status report`；`setsebool -P httpd_can_network_connect 1` |
| 8080 打不开 | firewalld 未放行 | `firewall-cmd --add-port=8080/tcp --permanent && firewall-cmd --reload` |
| 验证码 500 | 缺字体 | `yum install -y fontconfig dejavu-sans-fonts` |
| 登录报「验证码错误」 | 用了固定验证码但未开启 | 生产环境请走真实验证码，勿设 `REPORT_CAPTCHA_FIXED_CODE` |
| 页面空白 / 刷新 404 | nginx 未配置 `try_files` | 检查 `/` location |
| 时间差 8 小时 | 服务器或 MySQL 时区非东八区 | `timedatectl set-timezone Asia/Shanghai`；MySQL `default-time-zone='+08:00'` |
| 数据库连接失败 | 账号只授了 localhost / 口令错 | 核对 `REPORT_DB_*` 与 `mysql.user` |
| 上传失败 | 超过 50MB 或后缀不在白名单 | 调整 `REPORT_UPLOAD_MAX_FILE_MB`；确认后缀 |

---

## 13. 已知限制与合规提示

| 项 | 说明 |
|---|---|
| CentOS 7 EOL | 2024-06-30 停止维护，等保测评会标注；建议制定迁移计划（麒麟/统信/Rocky 9） |
| MySQL 5.7 EOL | 建议用 5.7.44 并锁定补丁；具备条件时升 8.0 |
| HTTPS | 本机不启 TLS，由政务外网前置网关终结；网关需回源 `http://<IP>:8080` |
| 组件漏洞 | 迁移 Boot 3 后 SCA 剩余 4 条（Spring 6.2 的两条需 7.x、react-router 需 7.x），需在材料中说明不可达/已补偿 |
| 高可用 | 当前单机单实例；如需双节点，需先引入 Redis（验证码/限流/会话）与共享文件存储，见部署说明第十二节 |
| 等保/密评 | 定级备案、测评、密评需走单位流程，本部署只完成技术侧加固 |

---

## 14. 附录

### 14.1 目录结构

```
/opt/report/
├── report-api.jar
├── report.env                 # 600，含数据库口令与密钥
├── static/                    # 前端产物
├── files/                     # 上传附件
└── logs/
    ├── app.log
    └── app.log-YYYYMMDD
```

### 14.2 上线检查清单

- [ ] JDK 17 安装完成，`/opt/jdk17/bin/java -version` 为 17
- [ ] 数据库建库、7 个 SQL 执行完毕（30 张表）
- [ ] `report.env` 权限 600，JWT_SECRET/CRYPTO_KEY 已生成
- [ ] systemd 单元为 CentOS 7 写法（无 `append:`）
- [ ] nginx 配置通过 `nginx -t`，8080 可访问
- [ ] SELinux 布尔已设置，firewalld 已放行 8080
- [ ] 日志轮转与每日备份已配置并手工验证一次
- [ ] 固定验证码开关为空
- [ ] admin 口令已修改
- [ ] 冒烟：登录、建模板、建任务、填报、审核、入库全部走通
