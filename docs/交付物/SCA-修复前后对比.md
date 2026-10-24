# SCA 修复前后对比

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
