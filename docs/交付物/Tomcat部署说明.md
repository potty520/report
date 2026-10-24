# Tomcat 外部容器部署说明

> 适用：要求把应用打成 war、部署到独立 Tomcat 的场景（政务外网中间件白名单、统一运维等）。
> 默认方式仍是用可执行 jar + systemd（见部署文档），两种方式并存，代码不需要分叉。

## 一、版本要求（最容易踩的坑）

| 项 | 要求 | 说明 |
|---|---|---|
| Tomcat | **10.1.x** | Spring Boot 3 用 Jakarta EE 9+，`jakarta.servlet` 命名空间 |
| Tomcat 9 及以下 | ❌ 不可用 | 其 `javax.servlet` 与 Boot 3 完全不兼容 |
| JDK | 17 | 与 jar 方式一致 |

Tomcat 版本还要跟着补丁走：本次验证用的是 **10.1.61**（与 jar 内嵌 Tomcat 同版本）。

## 二、打包

```bash
cd backend
mvn -Pwar clean package          # 产出 target/report-system-1.0.0.war
mvn clean package                # 不带 -Pwar 仍是可执行 jar
```

`war` profile 做了三件事：

1. `packaging` 切换为 `war`；
2. `spring-boot-starter-tomcat` 改为 `provided`（容器提供，不打进包，war 体积约 66MB 而非 80MB）；
3. `spring-boot-maven-plugin` 跳过 repackage，产出标准 war。

主类已继承 `SpringBootServletInitializer`，外部容器可从 war 启动。

## 三、部署

### 3.1 目录与命名（决定访问路径）

**WAR 文件名就是 context path。** 现有前端与 nginx 都按 `/api/**` 调用，所以命名为 `api.war` 最省事：

```bash
cp report-system-1.0.0.war $CATALINA_HOME/webapps/api.war
```

- `api.war` → 访问路径 `/api/auth/login`
- `report.war` → `/report/auth/login`（nginx 要相应改写）

> 注意：`application.yml` 里的 `server.servlet.context-path=/api` 在**外部容器下不生效**，
> context 由 war 文件名决定；jar 模式下仍然生效。

### 3.2 setenv.sh

```bash
cat > $CATALINA_HOME/bin/setenv.sh <<'EOF'
#!/bin/sh
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export CATALINA_OPTS="-Xms512m -Xmx1g -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai -Djava.awt.headless=true"
set -a
. /opt/report/report.env          # 复用 jar 方式的数据库口令、JWT 密钥等
set +a
EOF
chmod +x $CATALINA_HOME/bin/setenv.sh
```

`-Duser.timezone=Asia/Shanghai` 和 `-Djava.awt.headless=true` 别省：前者保证时间，后者保证验证码图片能画。

### 3.3 端口与 nginx

`conf/server.xml` 把 Connector 端口改成 nginx 反代的目标（默认 8080，若与 nginx 冲突可改 8081）：

```xml
<Connector port="8763" protocol="HTTP/1.1" connectionTimeout="20000" />
```

nginx 侧无需改动（仍然 `/api/ → 127.0.0.1:8763`），前提是 war 命名为 `api.war`。

### 3.4 启动

```bash
$CATALINA_HOME/bin/startup.sh
tail -f $CATALINA_HOME/logs/catalina.out
```

## 四、本次实测踩到的坑（已在代码中修复）

**现象**：war 部署后 Context 启动失败，`localhost.log` 报

```
严重 [main] StandardContext.filterStart 启动过滤器异常[jwtAuthFilter]
javax.naming.NameNotFoundException: 名称[com.reportsys.config.JwtAuthFilter/jwtTokenUtil]未在此上下文中绑定
```

**原因**：`JwtAuthFilter` 带 `@Component` 且实现 `Filter`，Spring Boot 会把它当作 Servlet 过滤器再注册一次。
打 jar（内嵌 Tomcat）时只是重复执行（幂等，不易发现）；打 war 时容器会对已存在的实例做注解处理，
遇到 `@Resource` 就去 JNDI 找 `JwtAuthFilter/jwtTokenUtil`，找不到直接抛异常。

**修复**：在 `SecurityConfig` 中显式关闭容器注册：

```java
@Bean
public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(JwtAuthFilter filter) {
    FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);   // 只保留 Security 过滤器链里的那一次
    return registration;
}
```

该修复对 jar 模式同样有益——过滤器从“每请求执行两次”变成一次。

## 五、验证

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8763/api/auth/captcha   # 200
```

再跑接口验收（把 BASE 指向 Tomcat 端口）：

```bash
REPORT_BASE=http://127.0.0.1:8763/api python acceptance_test.py   # 34/34
```

本次实测结果：外部 Tomcat 10.1.61 部署 `api.war`，**34 项接口验收全部通过**，启动耗时约 4 秒。

## 六、与 jar 方式对比

| 项 | 可执行 jar | 外部 Tomcat |
|---|---|---|
| 部署复杂度 | 低（一个 jar + systemd） | 中（war + 容器 + setenv） |
| Tomcat 版本 | 随 jar 固定（10.1.61） | 由容器决定，需自己跟补丁 |
| 日志 | `/opt/report/logs/app.log` | Spring 日志仍走 logback；容器日志在 `logs/catalina.out` |
| 热部署 | 重启进程 | 可替换 war（不推荐生产用） |
| 适用 | 默认推荐 | 中间件白名单 / 统一运维要求 |
