# 数据填报系统（Report System）

一个面向多部门数据填报、审核和台账统计的管理平台，包含模板管理、数据源接入、任务制定与分派、在线填报、双级审核、数据申请、组织架构、权限管理、附件管理和审计日志等能力。

> 仓库不再保存任何默认账号口令、数据库口令或部署密钥。所有敏感配置必须通过环境变量或受保护的运行时配置文件注入。

## 目录

- [核心功能](#核心功能)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [环境要求](#环境要求)
- [配置项](#配置项)
- [本地开发](#本地开发)
- [生产构建](#生产构建)
- [Nginx 部署示例](#nginx-部署示例)
- [测试](#测试)
- [安全要求](#安全要求)
- [上线前检查](#上线前检查)

## 核心功能

### 数据总览

- 任务总数、待处理、已完成、逾期任务统计
- 填报数量、审核通过率和参与部门统计
- 趋势图及部门维度图表

### 模板管理

- Excel 模板上传和表头解析
- 字段编码、类型、长度、必填和主键配置
- 模板创建、发布、详情和授权
- 模板物化建表及数据源绑定

### 数据源管理

- MySQL、PostgreSQL、Oracle、CirroData 等数据源配置
- 数据源新增、编辑、删除和连接测试
- 敏感连接信息加密存储
- 连接测试主机白名单

### 任务管理

- 任务制定、发布和关闭
- 按部门或用户分派填报/审核任务
- 任务台账多视图统计
- Excel 导出和任务执行情况分析

### 数据填报

- 按模板动态生成填报表单
- 草稿保存、提交和重新填报
- 附件上传，支持大文件分片上传
- 补正流程

### 双级审核

- 部门审核
- 任务方审核
- 禁止审核本人提交的数据
- 审核轨迹和补正记录

### 数据申请

- 按任务提交数据申请
- 申请修改、删除和审批
- 申请人、审批人、申请部门和审批意见展示
- 禁止自审批

### 系统管理

- 用户、角色、菜单和字典管理
- 角色菜单权限和用户角色分配
- 登录日志、操作审计日志
- 在线用户和统计规则配置

### 消息中心

- 待办、超期和补正通知
- 单条/全部已读
- 催办与超期扫描

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.5、Spring Security 6、JWT、MyBatis-Plus 3.5 |
| 前端 | React 18、Vite 5、Ant Design 5、React Router 6、Zustand、ECharts 5 |
| 数据库 | MySQL 5.7+ / 8.0 |
| 文件存储 | 本地文件系统，可扩展对象存储 |
| 反向代理 | Nginx |
| 测试 | JUnit 5、Mockito、AssertJ、TypeScript、Vite Build |

## 项目结构

```text
report/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/reportsys/
│       │   ├── controller/       # REST API
│       │   ├── service/          # 业务逻辑
│       │   ├── dao/              # MyBatis Mapper
│       │   ├── model/            # 实体模型
│       │   ├── config/           # 安全、JWT、异常处理等
│       │   ├── aop/              # 操作审计切面
│       │   └── common/           # 通用返回、分页、密码策略
│       ├── main/resources/       # application.yml、SQL 参考脚本
│       └── test/                 # 后端测试
├── frontend2/
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
│       ├── pages/                # 业务页面
│       ├── services/             # API 与 DTO 适配
│       ├── stores/               # Zustand 状态
│       ├── components/           # 公共组件
│       └── utils/                # 请求、鉴权等工具
├── docs/                         # 数据库升级和部署文档
└── README.md
```

生产构建入口为 `frontend2/`（旧版 umi 前端已移除）。交付文档见 `docs/交付物/`。

## 环境要求

- JDK 17
- Maven 3.8+
- Node.js 18+
- npm 9+
- MySQL 5.7+ / 8.0
- Nginx 1.20+（生产部署）

## 配置项

### 必填环境变量

| 变量 | 说明 |
| --- | --- |
| `REPORT_DB_URL` | JDBC 地址，例如 `jdbc:mysql://127.0.0.1:3306/report_system?...` |
| `REPORT_DB_USERNAME` | 数据库用户名 |
| `REPORT_DB_PASSWORD` | 数据库口令 |
| `REPORT_JWT_SECRET` | JWT 签名密钥，至少 32 字节，不得使用示例值 |
| `REPORT_CRYPTO_KEY` | 数据源口令等敏感字段的 AES/GCM 密钥，至少 16 位 |

### 初始化账号

系统不再内置默认账号口令。首次初始化时显式提供：

```bash
export REPORT_BOOTSTRAP_ADMIN_PASSWORD='使用密码管理器生成的高强度口令'
export REPORT_BOOTSTRAP_USER_PASSWORD='使用密码管理器生成的高强度口令'
```

未提供上述变量时，初始化器不会创建默认账号。

### 可选配置

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `REPORT_JWT_EXPIRE_HOURS` | `24` | JWT 有效期 |
| `REPORT_UPLOAD_MAX_FILE_MB` | `50` | 单个上传文件上限 |
| `REPORT_UPLOAD_MAX_REQUEST_MB` | `60` | 单次请求总上限 |
| `REPORT_DS_TEST_ALLOWED_HOSTS` | 空 | 数据源连接测试允许的主机，逗号分隔；生产环境必须配置 |
| `REPORT_CORS_ALLOWED_ORIGINS` | 空 | 允许的前端来源，逗号分隔；禁止使用 `*` |
| `REPORT_CAPTCHA_FIXED_CODE` | 空 | 仅供自动化测试；生产环境必须为空 |
| `REPORT_SMS_ENABLED` | `false` | 是否启用短信通道 |
| `REPORT_SMS_PROVIDER` | `log` | `log` 或 `chunlan` |
| `REPORT_QUALITY_PROVIDER` | `none` | 外部质检服务类型 |
| `REPORT_QUALITY_REQUIRED` | `false` | 未通过质检时是否阻止审核 |

完整配置见 `backend/src/main/resources/application.yml`。

## 本地开发

### 1. 准备数据库

创建 MySQL 数据库：

```sql
CREATE DATABASE report_system
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

按实际版本执行 `docs/` 下的建表和升级脚本。升级已有环境前必须备份数据库。

### 2. 启动后端

```bash
cd backend
export REPORT_DB_URL='jdbc:mysql://127.0.0.1:3306/report_system?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false'
export REPORT_DB_USERNAME='report'
export REPORT_DB_PASSWORD='从密码管理器读取'
export REPORT_JWT_SECRET='至少32字节的随机密钥'
export REPORT_CRYPTO_KEY='至少16位的随机密钥'
mvn spring-boot:run
```

默认后端地址：

```text
http://127.0.0.1:8763/api
```

### 3. 启动前端

```bash
cd frontend2
npm ci
npm run dev
```

开发服务器默认监听 `http://127.0.0.1:3000`，并将 `/api` 代理到 `http://localhost:8763`。

## 生产构建

### 后端

```bash
cd backend
mvn clean package
java -jar target/report-system-1.0.0.jar
```

生产环境通过受保护的环境文件、systemd `EnvironmentFile` 或容器 Secret 注入配置，不要把口令写入 JAR 或 Git。

### 前端

```bash
cd frontend2
npm ci
npm run build
```

构建产物位于 `frontend2/dist/`。

## Nginx 部署示例

```nginx
server {
    listen 443 ssl http2;
    server_name report.example.com;

    ssl_certificate     /etc/nginx/certs/report.crt;
    ssl_certificate_key /etc/nginx/certs/report.key;

    client_max_body_size 60m;
    root /var/www/report;
    index index.html;

    location /api/ {
        proxy_pass http://127.0.0.1:8763/api/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 30s;
        proxy_send_timeout 120s;
        proxy_read_timeout 120s;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

生产环境必须启用 HTTPS。MySQL 端口不得直接暴露到公网。

## 测试

### 后端

```bash
cd backend
mvn test
```

### 前端

```bash
cd frontend2
npm run build
```

建议上线前补充：

- 用户、角色、菜单权限回归
- 数据申请提交、审批、自审批拦截
- 任务创建、分派、填报、双级审核和补正
- 数据源新增、编辑和连接测试
- 审计日志成功/失败记录
- 并发提交和重复审批测试
- 数据库备份恢复演练

## API 约定

- 后端上下文路径：`/api`
- 成功响应：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {}
}
```

- 分页参数统一为 `page`、`pageSize`
- 除登录、验证码等公开接口外，请求需携带：

```http
Authorization: Bearer <JWT>
```

## 安全要求

1. 禁止提交密码、JWT 密钥、数据库口令、私钥和 API Token。
2. 所有凭据通过部署环境变量或 Secret 管理服务注入。
3. `REPORT_CAPTCHA_FIXED_CODE` 仅用于测试，生产环境必须为空。
4. 必须配置 `REPORT_DS_TEST_ALLOWED_HOSTS`，避免任意主机连接测试。
5. 必须启用 HTTPS，并限制数据库、Redis和管理端口的网络访问。
6. 定期轮换数据库账号、JWT 密钥和管理员口令。
7. 审计日志应设置保留周期、访问控制和完整性保护。
8. 备份文件必须加密并限制访问。

## 上线前检查

- 所有任务引用的模板均存在且已配置字段。
- 不存在“草稿状态但已审核通过”的填报记录。
- 数据申请、任务、部门、用户和角色之间不存在悬空外键。
- 默认账号口令、测试账号和测试数据已清理。
- 通知表中不存在重复待办或重复超期消息。
- 角色菜单包含完整父目录和叶子权限。
- 数据库备份可成功恢复。
- HTTPS、防火墙、日志告警和监控已启用。
- 完成业务部门 UAT 验收。

## 分支说明

- `master`：稳定主线
- `fix/functional-contracts-20261020`：本轮功能契约、权限菜单、审计和状态展示修复分支

## 文档

- `docs/`：数据库、部署与历史审计资料
- `backend/src/main/resources/schema.sql`：数据库结构参考
- `backend/src/main/resources/schema_patch.sql`：增量结构参考

---

如需部署到生产环境，请先完成敏感凭据轮换、数据库异常数据修复、HTTPS 配置和备份恢复验证。
