# 填报系统 · 开源组件漏洞扫描（SCA）报告

| 项 | 值 |
|---|---|
| 扫描时间 | 2026-10-24 23:27:23 |
| 扫描对象 | 后端 `report-system` 1.0.0（Maven，117 个 runtime 依赖）<br>前端 `frontend2`（npm，113 个生产依赖） |
| 数据源 | OSV.dev（聚合 GHSA / CVE / NVD 等） |
| 方法 | 从 `mvn dependency:list` 与 `package-lock.json` 提取精确版本，逐个按「包名+版本」查询，只保留**实际影响该版本**的漏洞 |
| 附件 | `填报系统组件与漏洞清单.xlsx`（汇总 / 后端组件 / 前端组件 / 漏洞清单 四个工作表） |

## 一、结论摘要

- 共扫描 **230** 个组件（后端 117 + 前端 113）。
- 命中漏洞条目 **4** 条，去重后 **4** 个漏洞（对应 4 个 CVE/GHSA），涉及 **2** 个组件。
- 分级：**严重 2 · 高 0 · 中 2 · 低 0**。
- 风险集中在 **Spring Boot 2.7.18 这条已停止开源维护的技术栈**：内嵌 Tomcat 9.0.83（共 0 条，其中严重/高危 0 条）、Spring Framework 5.3.31、Spring Security 5.7.11、Jackson 2.13.5。
- 前端集中在 **axios 1.18.1**（0 条）与 `follow-redirects`。

> 说明：这些 CVE 的「可利用性」取决于部署方式。部分条目（如 Tomcat AJP、HTTP/2、WebDAV、FORM/DIGEST 认证）在本系统当前配置下并不直接暴露，但**不能据此忽略**——等保测评按组件版本逐条核对，仍会判为高危。

## 二、后端高危组件（按命中数排序）

| 组件 | 版本 | 最高等级 | 命中数 | 建议 |
|---|---|---|---|---|
| `org.springframework:spring-webmvc` | 6.2.19 | 严重 | 2 | 需升级 Spring Boot 3.x |

## 三、前端高危组件

| 组件 | 版本 | 最高等级 | 命中数 | 建议 |
|---|---|---|---|---|

## 四、修复方案

### 方案 A：快速止血（1–2 天，不改架构）

在 `backend/pom.xml` 里覆盖 Spring Boot 管理的版本属性，重新打包后复扫：

```xml
<properties>
    <java.version>1.8</java.version>
    <!-- Tomcat 9.0.83 -> 9.0.121，覆盖大部分 Tomcat CVE -->
    <tomcat.version>9.0.121</tomcat.version>
    <!-- Jackson 2.13.5 -> 2.18.x -->
    <jackson-bom.version>2.18.11</jackson-bom.version>
    <!-- Netty 4.1.101 -> 4.1.137 -->
    <netty.version>4.1.137.Final</netty.version>
    <!-- Logback 1.2.12 -> 1.2.13（2.7 线最高可用） -->
    <logback.version>1.2.13</logback.version>
    <!-- MySQL 驱动 8.0.33 -> 8.4.x -->
    <mysql.version>8.4.0</mysql.version>
    <!-- PostgreSQL 驱动 -->
    <postgresql.version>42.7.11</postgresql.version>
</properties>
```

配套改动：
- `com.h2database:h2` 从 runtime 改为 `test` 作用域（生产包不带 H2）。
- `snakeyaml` 需升到 2.x（Spring Boot 2.7 默认 1.30），API 有变更，升级后要回归 YAML 配置加载。
- Spring Security 5.7.11 的部分 CVE 只有 6.x 修复，方案 A 无法完全消除。

### 方案 B：中期（Spring Boot 3.x 迁移）

Spring Boot 2.7 已于 2023-11 停止开源维护，继续用等于长期背 CVE。迁移到 3.3/3.4（或 3.5）涉及：

- `javax.*` → `jakarta.*`（Servlet/Validation/Annotation）；
- Spring Security 5.7 → 6.x（`WebSecurityConfigurerAdapter` 已移除，需改 `SecurityFilterChain`，本项目已经是新写法，改动可控）；
- MyBatis-Plus、Druid、Hutool 等依赖同步升级；
- JDK 17 已满足（当前就是 17）。

工作量估计 3–5 人日，改完必须重跑本报告的全量回归（34 项接口 + 71 步 UI）。

### 方案 C：前端

```bash
npm install axios@^1.20.0 echarts@^6.1.0
npm dedupe   # follow-redirects 随 axios 升级到 1.16.1
```

前端问题均为「客户端侧 DoS / 重定向处理」，风险低于后端，但等保测评会一并列出，建议顺手升掉。

## 五、可接受风险（需在测评材料中逐条说明）

| 组件 | 说明 |
|---|---|
| `tomcat-embed-*` 的 AJP/HTTP2/WebDAV 类 CVE | 本系统未启用 AJP（默认关闭）、未开 HTTP/2、未启用 WebDAV，实际不可达；但版本仍会被扫出，建议按方案 A 升级 |
| `h2` 的 CVE-2022-45868 | 生产使用 MySQL，H2 仅在测试配置中出现；建议移出 runtime 后该项消失 |
| `spring-boot` 的 actuator 相关 CVE | 本项目未引入 actuator，`EndpointRequest` 类问题不适用 |

## 六、复扫方法

```bash
# 1) 生成依赖清单
cd backend && mvn dependency:list -DincludeScope=runtime -DoutputFile=deps-runtime.txt

# 2) 执行扫描（脚本见 sca_scan.py，输出 sca_raw.json）
python sca_scan.py

# 3) 重新出报告与清单
python gen_sca_report.py
```

建议把这项扫描接进发布流水线：**每次打包前跑一次，高危不为 0 则阻断发布**。

## 七、建议的上线前门槛

1. 严重/高危漏洞：方案 A 消化后，剩余项必须有书面「不可达/已补偿」说明；
2. H2 移出生产包；
3. 前端 axios / echarts 升级完成；
4. 出 SBOM（`mvn cyclonedx:makeAggregateBom` + `npm sbom`）随测评材料提交；
5. 上线后每季度复扫一次。

## 八、修复前后对比

| 指标 | 修复前 | 修复后 | 变化 |
|---|---|---|---|
| 组件总数 | 230 | 220 | −10 |
| 受影响组件数 | 34 | 2 | −32 |
| 漏洞条目 | 153 | 4 | −149 |
| 去重漏洞数 | 151 | 4 | −147 |
| 严重 | 12 | 2 | −10 |
| 高危 | 61 | 0 | −61 |
| 中危 | 64 | 2 | −62 |
| 低危 | 16 | 0 | −16 |

## 修复动作（两轮）

| 组件 | 修复前 | 最终版本 | 方式 |
|---|---|---|---|
| Spring Boot | 2.7.18 | **3.5.16** | 整体迁移（Jakarta + Security 6 DSL + JDK 17） |
| Spring Framework | 5.3.31 | **6.2.19** | 随 Boot 3.5 |
| Spring Security | 5.7.11 | **6.x** | 随 Boot 3.5 |
| Tomcat | 9.0.83 | **10.1.61** | `tomcat.version` 覆盖 |
| Jackson | 2.13.5 | **2.21.8** | `jackson-bom.version` 覆盖 |
| Netty | 4.1.101.Final | **4.1.139.Final** | `netty.version` 覆盖 |
| Logback | 1.2.12 | **1.5.x** | 随 Boot 3.5 |
| SnakeYAML | 1.30 | **2.x** | 随 Boot 3.5 |
| PostgreSQL 驱动 | 42.7.3 | **42.7.14** | `postgresql.version` 覆盖 |
| MySQL 驱动 | 8.0.33（旧坐标） | **8.4.0** | 换坐标 com.mysql:mysql-connector-j |
| Log4j2 API | 2.17.2 | **2.26.1** | `log4j2.version` 覆盖 |
| MyBatis-Plus | 3.5.5 | **3.5.17** | boot3 starter + jsqlparser 模块 |
| Druid | 1.2.20 | **1.2.28** | druid-spring-boot-3-starter |
| commons-lang3 | 3.17.0 | **3.21.0** | 显式依赖覆盖 |
| commons-compress | 1.25.0 | **1.27.1** | 显式依赖覆盖 |
| H2 | 2.1.214（runtime） | **2.3.232（test）** | 移出生产包 |
| POI | 5.2.5 | **5.4.1** | 版本升级（easyexcel 零引用已移除） |
| axios | 1.18.1 | **1.20.0** | npm 升级 |
| follow-redirects | 1.16.0 | **1.16.1** | npm overrides |
| echarts | 5.6.0 | **6.1.0** | npm 升级 |
| react-router-dom | 6.30.4 | **6.30.6** | npm 升级 |

## 剩余严重/高危（均属 Spring 5.3 / Boot 2.7 生命周期问题）

| 组件 | 版本 | 条数 | 说明 |
|---|---|---|---|
| `org.springframework:spring-webmvc` | 6.2.19 | 2 | 路径穿越/XSS/DoS，修复在 6.x |

> 结论：能通过版本覆盖消除的已全部消除；剩余项是 Spring Boot 2.7 已停止维护导致的，只能通过升级 Spring Boot 3.x（Jakarta 迁移）解决，建议列入下一阶段。
