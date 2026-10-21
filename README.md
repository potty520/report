# 填报系统 (Report System)

基于原"大数据基座填报系统"骨架重构的完整填报平台，集成了**授权系统**和**组织架构管理**。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot 2.7 + MyBatis Plus 3.5 + Spring Security + JWT |
| 前端 | React 17 + Umi 3 + Ant Design 4 + Dva + ECharts 5 |
| 数据库 | MySQL 8.0 |
| 缓存 | Redis (可选) |

## 功能模块

### 1. 授权系统
- 用户管理 (CRUD + 角色分配)
- 角色管理 (CRUD + 菜单权限分配)
- 菜单管理 (树形管理)
- 登录/退出 (JWT + 验证码)
- 登录日志

### 2. 组织架构
- 部门管理 (无限级树)
- 项目管理 (关联部门)

### 3. 模板管理
- 模板定义 (Excel上传 + 字段映射)
- 模板审核
- 数据库配置 (数据源绑定 + 表映射)

### 4. 数据源管理
- 数据源 CRUD
- 连接测试

### 5. 任务管理
- 填报任务制定
- 任务分派
- 任务台账

### 6. 填报管理
- 数据填报 (在线填写 + 文件上传)
- 部门审核
- 任务方审核

### 7. 数据申请
- 数据申请
- 申请审批

### 8. 总览统计
- 填报总览仪表盘
- 部门填报统计

## 项目结构

```
report-system/
├── README.md
├── docs/
│   └── database.sql          # 完整数据库建表脚本
├── backend/                   # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/reportsys/
│       │   ├── ReportApplication.java
│       │   ├── model/         # 实体类
│       │   ├── dao/           # MyBatis Mapper
│       │   ├── service/       # 业务逻辑
│       │   ├── controller/    # REST API
│       │   ├── config/        # 配置类
│       │   └── common/        # 通用类
│       └── resources/
│           └── application.yml
└── frontend/                  # React 前端
    ├── package.json
    ├── config/
    │   ├── config.js
    │   └── router.config.js
    └── src/
        ├── layouts/           # 布局
        ├── pages/             # 页面
        ├── models/            # Dva models
        ├── services/          # API层
        ├── components/        # 公共组件
        └── utils/             # 工具
```

## 快速开始

### 1. 数据库
```bash
mysql -u root -p < docs/database.sql
```

### 2. 后端
```bash
cd backend
mvn spring-boot:run
```

### 3. 前端
```bash
cd frontend
npm install
npm start
```

管理员账号不再使用仓库内默认口令；首次初始化时通过 `REPORT_BOOTSTRAP_ADMIN_PASSWORD` / `REPORT_BOOTSTRAP_USER_PASSWORD` 环境变量显式设置。
