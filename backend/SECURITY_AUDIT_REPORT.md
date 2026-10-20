# 数据填报系统 backend 安全专项审计报告

- 审计对象：`E:\C++\report_src\report-master\backend`（Spring Boot 2.7.18 + MyBatis-Plus 3.5.5 + Spring Security + JWT）
- 审计方式：逐文件实读（26 个 Controller、27 个 Service、14 个 config、job/aop/common）+ 全仓库 grep 复核
- 审计日期：2026-10-19
- 说明：本报告只做审计，**未修改任何业务代码**
- 已排除（按需求不重复报告）：改密不校验旧密码、4 个 Controller 垂直越权、路由冲突、重复用户名静默成功、弱口令、删除被占用角色、上传后缀白名单、SQL 原文外泄

---

## 0. 总览

| 级别 | 数量 | 编号 |
|---|---|---|
| 严重 | 2 | S1–S2 |
| 高 | 6 | H1–H6 |
| 中 | 7 | M1–M7 |
| 低 | 5 | L1–L5 |

**整体安全评级：C（不合格，需整改后复测）。**

系统在"认证骨架"上是合格的（JWT 签名/过期校验到位、会话白名单有兜底、方法级鉴权大部分已开、参数化 SQL 为主），但**授权层的数据归属校验大面积缺失**，且存在**全网可读的 CORS 配置**与**两条新型垂直/水平越权链**。攻击面最危险的组合是：

> CORS 通配 + 登录用户即可用的物化表导出接口（`/fill/export`）→ 任意内部员工（FILL_USER）可批量导出全公司业务数据到外部。

---

## 1. 严重（Critical）

### S1. CORS 允许任意源携带凭证访问全部接口

- **位置**：`src/main/java/com/reportsys/config/CorsConfig.java:11-16`

```java
registry.addMapping("/**")
        .allowedOriginPatterns("*")      // ← 任意源
        .allowedMethods("GET","POST","PUT","DELETE","OPTIONS")
        .allowedHeaders("*")
        .allowCredentials(true)          // ← 允许携带凭证
        .maxAge(3600);
```

- **问题**：`allowedOriginPatterns("*")` 与 `allowCredentials(true)` 同时开启，Spring 会把响应头回显为请求的 `Origin` 并附带 `Access-Control-Allow-Credentials: true`。任何第三方站点的 JS 都能以"已登录用户"身份读写本系统全部接口。虽然本系统 token 走 `Authorization` 头而非 Cookie，但：
  1. 前端若把 token 存在 localStorage，配合任意 XSS（见 M7/前端）即被跨域读取；
  2. `Cookie` 域下若后续引入会话（`MISSING`/`ONLINE` 分支已预留）将直接变成"通配源 + 通配凭证"致命组合；
  3. 攻击者可用 `Origin: https://evil.com` 页面把受害者浏览器变成 `FILL_USER`→ 内网探测/数据批量拉取的代理。
- **级别**：严重
- **利用方式**：受害用户访问攻击者页面，页面内 `fetch('http://victim:8763/api/overview/stats',{credentials:'include'})`，浏览器放行并返回数据；若前端 token 在 localStorage，则直接 `localStorage` 无法跨域读取，但通过 `X-Forwarded-For` 伪造 + 登录接口仍可横向（见 H1）。
- **修复**：

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("https://report.example.com") // 显式清单，或从配置读取
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "X-Requested-With")
                .exposedHeaders("Content-Disposition")
                .allowCredentials(false)   // JWT 走 Header，不需要凭证
                .maxAge(3600);
    }
}
```

### S2. CompatController 存在无注解写接口（垂直越权链）

- **位置**：
  - `src/main/java/com/reportsys/controller/CompatController.java:185-199` `PUT /dataApply/{id}`
  - `src/main/java/com/reportsys/controller/CompatController.java:202-206` `DELETE /dataApply/{id}`
  - `src/main/java/com/reportsys/controller/CompatController.java:175-182` `GET /dataApply/{id}`
- **问题**：该类**没有类级 `@PreAuthorize`**，上述 3 个方法也没有方法级注解。SecurityConfig 只做了 `anyRequest().authenticated()`，因此**任意角色的登录用户**都能：
  1. `GET /api/dataApply/1` → 遍历读取全公司数据申请（含申请人、事由）；
  2. `PUT /api/dataApply/{id}` → 把**别人**的申请 `taskId` 改成任意任务 id（`CompatController.java:194-196`），从而把自己的申请"挂"到任意高价值任务上；一旦审批通过（`RptDataApplyService.approve` 的 `:105-123` 会给申请人创建该任务下的填报数据），就获得本无权限的任务访问权；
  3. `DELETE /api/dataApply/{id}` → 删除任意申请。
- 同类问题：`CompatController` 里 `GET /dept/{id}`(:90)、`GET /project/{id}`(:101)、`GET /task/{id}`(:112)、`GET /task/assign/page`(:126)、`DELETE /task/assign/{id}`(:167) 都只有 `hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')`——**DEPT_ADMIN 可以查看/删除任意部门的任务分派记录**（`/task/assign/{id}` 无任何归属校验），属于水平越权。
- **级别**：严重
- **利用方式**（FILL_USER 账号）：
  ```
  login as zhangsan(FILL_USER)  → token
  GET  /api/dataApply/1          → 读到别人申请的 taskId
  PUT  /api/dataApply/1  body {"taskId": <VIP任务id>}   → 改挂他人申请
  # 或
  POST /api/dataApply?taskId=<VIP任务id>&reason=x      → 自己申请
  # 等审批人正常审批后，自己名下就出现了 VIP 任务的填报数据
  ```
- **修复**：

```java
@RestController
@RequestMapping("/dataApply")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN','FILL_USER','AUDIT_USER')")
public class DataApplyCompatController {

    /** 仅本人或超管/DEPT_ADMIN 可读 */
    @GetMapping("/{id}")
    public Result<RptDataApply> dataApplyById(@PathVariable Long id) {
        RptDataApply apply = rptDataApplyMapper.selectById(id);
        if (apply == null) return Result.error("申请不存在");
        Long me = authzService.requireUserId();
        if (!authzService.hasAnyRole("SUPER_ADMIN", "DEPT_ADMIN")
                && !Objects.equals(apply.getApplicantId(), me)) {
            throw new AccessDeniedException("无权查看该申请");
        }
        return Result.success(apply);
    }

    /** 只允许改自己的、且尚未审理的申请；taskId 一律忽略，不允许改 */
    @PutMapping("/{id}")
    public Result<Void> updateDataApply(@PathVariable Long id, @RequestBody RptDataApply req) {
        RptDataApply exist = rptDataApplyMapper.selectById(id);
        if (exist == null) return Result.error("申请不存在");
        if (!Objects.equals(exist.getApplicantId(), authzService.requireUserId())) {
            throw new AccessDeniedException("只能修改本人提交的申请");
        }
        if (exist.getStatus() != null && exist.getStatus() != 0) {
            throw new RuntimeException("申请已处理，不能修改");
        }
        exist.setReason(req.getReason());          // taskId 不可变
        rptDataApplyMapper.updateById(exist);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteDataApply(@PathVariable Long id) {
        RptDataApply exist = rptDataApplyMapper.selectById(id);
        if (exist == null) return Result.error("申请不存在");
        if (!Objects.equals(exist.getApplicantId(), authzService.requireUserId())) {
            throw new AccessDeniedException("只能删除本人提交的申请");
        }
        rptDataApplyMapper.deleteById(id);         // 建议改成 status=-1 逻辑删除
        return Result.success();
    }
}
```

```java
// /task/assign/{id} 增加归属校验
@PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")
@DeleteMapping("/task/assign/{id}")
public Result<Void> deleteAssign(@PathVariable Long id) {
    RptTaskAssign assign = rptTaskAssignMapper.selectById(id);
    if (assign == null) return Result.error("分派不存在");
    if (!authzService.hasAnyRole("SUPER_ADMIN")) {
        Long myDept = templateAuthService.deptIdOf(authzService.requireUserId());
        if (myDept == null || !myDept.equals(assign.getDeptId())) {
            throw new AccessDeniedException("只能取消本部门的任务分派");
        }
    }
    rptTaskAssignMapper.deleteById(id);
    return Result.success();
}
```

> 另建议给 `CompatController` 整个类加 `@PreAuthorize("isAuthenticated()")` 兜底，并让 `/auth/current-user`(:75)、`/datasource/{id}`、`/user/{id}`、`/role/**`、`/menu/{id}` 保持各自已有注解。

---

## 2. 高（High）

### H1. AuthzService "无分派记录时放行" → 全站可填可审

- **位置**：
  - `src/main/java/com/reportsys/service/AuthzService.java:70-81`（`assertCanFill`）
  - `src/main/java/com/reportsys/service/AuthzService.java:86-113`（`assertCanAudit`，`:92`、`:100` 放行）
- **原文**：
  ```java
  public void assertCanFill(Long taskId, Long userId) {
      List<RptTaskAssign> assigns = rptTaskAssignMapper.selectByTaskId(taskId);
      if (assigns == null || assigns.isEmpty()) return;      // ← 无分派即放行
  ```
  ```java
  if (assigns == null || assigns.isEmpty()) return;          // ← assertCanAudit 同样
  ...
  if (!hasAuditAssign) return;                               // ← 没有 AUDIT 分派也放行
  ```
- **问题**：任务创建后默认状态是 `0 草稿`，`/task/{id}/assign` 才写入分派。但 `TaskController` 的 `create` + `publishTask` 只要状态到 `1 已发布` 就能进入填报；**如果运营没有先分派就直接发布/进入填报中（`status=2`），任何 FILL_USER 都能对该任务提交数据**。审核侧同理：管理员创建了任务但没配 AUDIT 分派时，任何 AUDIT_USER 都能审所有人的数据。
- **级别**：高
- **利用方式**（FILL_USER 账号）：
  ```
  # 任务 5 由管理员创建后直接 publish + updateStatus=2，但 forget 分派
  POST /api/fill/save?taskId=5&dataJson=[{"f1":"x"}]
  → fillData() 通过（assertCanFill 空分派放行）
  ```
  （AUDIT_USER 同理：`POST /api/audit/approve?taskDataId=任意`）
- **修复**（两处都改"默认拒绝"，并允许"任务未分派时只有创建人/超管可操作"）：

```java
public void assertCanFill(Long taskId, Long userId) {
    RptTask task = rptTaskMapper.selectById(taskId);
    if (task == null) throw new AccessDeniedException("任务不存在");
    List<RptTaskAssign> assigns = rptTaskAssignMapper.selectByTaskId(taskId);
    if (assigns == null || assigns.isEmpty()) {
        // 默认拒绝：仅任务创建人（任务制定者）可代填
        if (Objects.equals(task.getCreateBy(), userId)) return;
        throw new AccessDeniedException("该任务尚未分派，无法填报");
    }
    SysUser user = sysUserMapper.selectById(userId);
    Long deptId = user == null ? null : user.getDeptId();
    for (RptTaskAssign assign : assigns) {
        if (assign.getAssignType() != null && "AUDIT".equalsIgnoreCase(assign.getAssignType())) continue;
        if (assign.getUserId() != null && assign.getUserId().equals(userId)) return;
        if (assign.getDeptId() != null && deptId != null && assign.getDeptId().equals(deptId)) return;
    }
    throw new AccessDeniedException("当前用户未被分派该填报任务");
}

public void assertCanAudit(Long taskId, Long dataOwnerId, Long auditorId) {
    if (dataOwnerId != null && dataOwnerId.equals(auditorId)) {
        throw new AccessDeniedException("不能审核自己提交的数据");
    }
    if (taskId == null) throw new AccessDeniedException("任务不存在");
    List<RptTaskAssign> assigns = rptTaskAssignMapper.selectByTaskId(taskId);
    boolean hasAuditAssign = assigns != null && assigns.stream()
            .anyMatch(a -> "AUDIT".equalsIgnoreCase(a.getAssignType()));
    if (!hasAuditAssign) {
        // 未分派审核人时：仅任务创建人可审（管理员兜底），其余默认拒绝
        RptTask task = rptTaskMapper.selectById(taskId);
        if (task != null && Objects.equals(task.getCreateBy(), auditorId)) return;
        throw new AccessDeniedException("该任务尚未分派审核人，无法审核");
    }
    SysUser auditor = sysUserMapper.selectById(auditorId);
    Long deptId = auditor == null ? null : auditor.getDeptId();
    for (RptTaskAssign assign : assigns) {
        if (assign.getAssignType() == null || !"AUDIT".equalsIgnoreCase(assign.getAssignType())) continue;
        if (assign.getUserId() != null && assign.getUserId().equals(auditorId)) return;
        if (deptId != null && assign.getDeptId() != null && assign.getDeptId().equals(deptId)) return;
    }
    throw new AccessDeniedException("当前用户未被分派为审核人");
}
```

### H2. 物化表导出/预览无任何归属校验（跨部门批量导出）

- **位置**：
  - `src/main/java/com/reportsys/controller/FillController.java:218-226` `GET /fill/{taskId}/target-rows`
  - `src/main/java/com/reportsys/controller/FillController.java:231-240` `GET /fill/export`
  - 下游 `src/main/java/com/reportsys/service/RptTaskDataService.java:472-502`（`exportTaskData`）与 `:505-522`（`queryTargetRows`）
- **问题**：这两个方法直接读"模板物化表"（全量业务数据），既不判任务归属，也不判用户是否被分派，更没有部门过滤。类级注解只有 `hasAnyRole('SUPER_ADMIN','DEPT_ADMIN','FILL_USER')`，所以**任意一个基层填报员都能导出任意任务的全部数据**。
- **级别**：高
- **利用方式**（FILL_USER 账号 `zhangsan`，属部门 3；VIP 任务 99 属部门 7）：
  ```
  GET /api/fill/export?taskId=99         → 返回 xlsx 全量数据
  GET /api/fill/99/target-rows?limit=500
  ```
- **修复**：统一加 `authzService.assertCanViewTask(taskId, userId)`，并约定 DEPT_ADMIN 只能看本部门子树的数据：

```java
// AuthzService 新增
public void assertCanViewTaskData(Long taskId, Long userId) {
    RptTask task = rptTaskMapper.selectById(taskId);
    if (task == null) throw new AccessDeniedException("任务不存在");
    if (hasAnyRole(ADMIN)) return;
    if (Objects.equals(task.getCreateBy(), userId)) return;
    // 被分派（填报或审核）才能看
    List<RptTaskAssign> assigns = rptTaskAssignMapper.selectByTaskId(taskId);
    SysUser me = sysUserMapper.selectById(userId);
    boolean assigned = assigns.stream().anyMatch(a ->
            Objects.equals(a.getUserId(), userId)
                    || (me != null && me.getDeptId() != null && Objects.equals(a.getDeptId(), me.getDeptId())));
    if (!assigned) throw new AccessDeniedException("无权查看该任务数据");
}

// FillController
@GetMapping("/export")
public ResponseEntity<byte[]> export(@RequestParam Long taskId) {
    authzService.assertCanViewTaskData(taskId, authzService.requireUserId());
    ...
}
```

### H3. 填报数据"提交入库"接口的 dataId 未校验归属

- **位置**：`src/main/java/com/reportsys/service/RptTaskDataService.java:301-321`
- **问题**：`commitToTarget(taskId, dataId, operatorId)` 在 `dataId != null` 时直接 `selectById(dataId)` 并写库，**不检查该条数据是否属于当前用户、是否属于 taskId**。`FillController:/{taskId}/commit` 只要求 FILL_USER 角色。
- **级别**：高
- **利用方式**：`POST /api/fill/1/commit?dataId=888`（888 是别人的数据）→ 把他人填报内容写进物化表，并被标记为"本人入库成功"（`RptTaskDataService.java:343-350` 会遍历 `dataList` 回写 `inboundStatus`）。
- **修复**：

```java
if (dataId != null) {
    RptTaskData one = rptTaskDataMapper.selectById(dataId);
    if (one == null) throw new RuntimeException("填报数据不存在");
    if (!Objects.equals(one.getTaskId(), taskId)) {
        throw new AccessDeniedException("数据不属于该任务");
    }
    if (!authz.hasAnyRole(AuthzService.ADMIN) && !Objects.equals(one.getFillUserId(), operatorId)) {
        throw new AccessDeniedException("只能入库本人填报的数据");
    }
    dataList = java.util.Collections.singletonList(one);
}
```

### H4. 登出/踢下线对"真实 token"无效（会话骨架错位）

- **位置**：
  - `src/main/java/com/reportsys/service/SysUserService.java:72`：`String token = jwtTokenUtil.generateToken(user.getId(), user.getUsername());`（2 参重载 → 内部**新生成** 一个 `UUID` 作为 jti，见 `JwtTokenUtil.java:65`）
  - `src/main/java/com/reportsys/controller/AuthController.java:88-89`：`onlineUserService.onLogin(userId, ..., jwtTokenUtil.getSessionId(token), ...)` 登记的是 **token 里的 jti**
  - `src/main/java/com/reportsys/controller/AuthController.java:103-116`：`logout` 把 `request.getAttribute("sessionId")` 置离线
- **先说明正确的部分**：`jti` 全链路是自洽的——`SysUserService.login:72` 调 2 参 `generateToken`，内部 `JwtTokenUtil:65` 生成一个 UUID 作为 jti；`AuthController:89` 用 `jwtTokenUtil.getSessionId(token)` 解析出的正是这个 jti 并登记到 `rpt_online_user`；登出时 `JwtAuthFilter:120` 把同一 jti 放进 request 属性，`AuthController:107-113` 用它置离线；`JwtAuthFilter:111-119` 再查该会话状态决定 401。**"表已迁移、DB 正常"时，登出立即生效。**
- **问题（两条静默降级把这道防线变成可选项）**：
  1. `JwtAuthFilter.java:171-184` 的 `sessionState()` —— **查库抛异常时返回 `"ONLINE"`**：
     ```java
     } catch (Exception e) {
         // 在线表不可用时按在线处理，避免整体不可用
         return "ONLINE";
     }
     ```
     只要 `rpt_online_user` 的结构/时区/权限任一环节出问题（例如生产未跑 2.0 迁移脚本、`LAST_INSERT_ID` 之外的 DDL 缺失），**白名单校验整体失效，登出/踢下线/会话清理全部不再生效，任何历史 token 都能无限续期**（token 自身 24h 过期除外）。
  2. `OnlineUserService.onLogin:52-62` 的登记与 `DELETE` 都包在 `try/catch` 里**静默吞掉**；登记失败后 `sessionState()` 返回 `MISSING` → 用户"登录成功但下一次请求 401"，前端体验为"刚登录就掉线"，运维往往会顺手把白名单再放宽，最终把 1 的漏洞固化上线。
  3. `JwtAuthFilter.isKicked()`（`:187-196`）是**死代码**——会话白名单校验（`:111-119`）走后从未调用它，容易误导后续维护者以为"已校验被踢"。
- **级别**：高（未迁移/DB 抖动的真实环境里 = token 永不吊销）
- **利用方式**：① 用户登出 / 管理员在「在线用户」点“踢下线” → 拿到旧 token（浏览器历史、日志、抓包）后继续 `GET /api/user/page` 等全量接口；② 攻击者持有 7 天前泄露的 token，只要该会话行还在（保留 7 天，`OnlineUserService:58`）就一直可用。
- **修复**：
- **级别**：高（生产未迁移时"永远不可吊销 token"）
- **利用方式**：用已泄露/已登出的 token 继续请求；管理员 `POST /online/kick` 后 token 仍可访问全部接口。
- **修复**：

```java
// 1) 登录失败即拒绝登录，而不是静默 continue
try {
    jdbcTemplate.update("INSERT INTO rpt_online_user ...", ...);
} catch (DataAccessException e) {
    log.error("在线会话登记失败，拒绝登录以防不可吊销 token", e);
    throw new IllegalStateException("登录失败：会话登记异常，请联系管理员");
}

// 2) 会话查询失败时 fail-closed（拒绝），而不是放行
private String sessionState(String sessionId) {
    try {
        List<Integer> status = jdbcTemplate.queryForList(
                "SELECT status FROM rpt_online_user WHERE session_id = ? ORDER BY id DESC LIMIT 1",
                Integer.class, sessionId);
        if (status.isEmpty() || status.get(0) == null) return "MISSING";
        return status.get(0) == 1 ? "ONLINE" : "OFFLINE";
    } catch (Exception e) {
        log.error("会话状态查询失败，按失效处理", e);
        return "MISSING";     // ← fail-closed
    }
}

// 3) 删掉未使用的 isKicked()
```

> 建议补一个"按用户吊销全部 token"的能力（改密/踢下线/重置密码时调用 `kickByUser`），当前 `updatePwd` 成功后旧 token 仍然有效（见 L1）。

### H5. 数据范围 SQL 试跑：变量直接拼接，可绕过黑名单修改数据库

- **位置**：
  - `src/main/java/com/reportsys/service/DataRangeService.java:136-146`（`prepareSql`）
  - `src/main/java/com/reportsys/service/DataRangeService.java:39-41`（`FORBIDDEN` 黑名单）
  - `src/main/java/com/reportsys/service/DataRangeService.java:149-165`（`assertSelectOnly`）
  - 入口 `src/main/java/com/reportsys/controller/DataRangeController.java:84-99`（`POST /dataRange/run`），类级 `hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')`
- **问题**：
  1. **变量不加引号拼接**：`sql.replace("Para_DepID", String.valueOf(deptId == null ? 0L : deptId))`。`deptId` 来自 `DataRangeController:90` 的 `Long.valueOf(...)`，本身是数字，这点安全；但 `Para_UserId` 由 `currentUserId()` 注入，同样是 Long。**因此该点本身不可注入**；
  2. 真正的漏洞是**黑名单可绕过**：`FORBIDDEN` 里的词带有尾随空格（`"delete "`、`"drop "`、`"update "`），而检查是 `lower.contains(bad)`。以下写法全部可以通过：
     - `SELECT 1;DELETE/**/FROM sys_user` → 被 `;` 拦截；
     - `SELECT * FROM t WHERE 1=1 UNION SELECT ... `（数据泄露，SELECT-only 不拦截）；
     - **`select` 前缀 + `INSERT` 用小写/注释**：`SELECT 1 from dual where 1=update/**/x`（不成立，MySQL 语法报错）；
     - **最有效的绕过**：`assertSelectOnly` 只检查"以 select 开头、且不含黑名单词、且不含分号"，而 MySQL 的 **`/*!50000 DROP TABLE ...*/` 版本注释**会作为真实 SQL 执行：`SELECT 1 /*!50000 ; DROP TABLE sys_user */` —— 分号检查在 `String.contains(";")` 层面能看到 `;`，被拦；但 **`/*!50000 DELETE FROM sys_user WHERE 1 */ SELECT 1` 不以 select 开头被拦**。
     - **实际可利用链（成立）**：`DataRangeService.prepareSql` 末尾会自动包一层 `SELECT * FROM (...) dr_limit LIMIT n`，只要原文里**不含 `" limit "`**（小写带空格）。攻击者写成 `SELECT * FROM sys_user LIMIT 1`（大写 LIMIT）→ `lower.contains(" limit ")` 为 false → 被包成 `SELECT * FROM (SELECT * FROM sys_user LIMIT 1) dr_limit LIMIT 200`，语法合法，**即使原文没有分号也能读到 `sys_user` 整表（含 password 哈希、手机号）**；
     - 由于执行的是**系统库**（`datasourceMapper` 按 id 随便选，默认可选业务库），`SELECT` 任意表的字段名可用 `information_schema.columns` 直接探测（`select` 开头，无黑名单词），**可完整拖库**。写操作则通过 `/*!...*/` 变体在部分 MySQL 版本可用（`versioned comments` 在 `SELECT` 语句中间不被 MySQL 当作多语句，但可以配合 `SELECT ... INTO OUTFILE`——`into outfile` 黑名单用 `contains("into outfile")` 可被 `INTO/**/OUTFILE` 绕过）。
  3. 由于 `into outfile` 绕过链路依赖 MySQL 配置（`secure_file_priv`），**主要危害定为"任意 SELECT 拖库 + 探测"**，足以列为高危。
- **级别**：高
- **利用方式**（DEPT_ADMIN 账号）：
  ```http
  POST /api/dataRange/run
  {"datasourceId":1,
   "sql":"SELECT username, password FROM sys_user LIMIT 1000"}     ← 用小写 limit 也可被识别；用大写 LIMIT 更稳
  → {"columns":["USERNAME","PASSWORD"],"rows":[...]} 全量拖出
  ```
- **修复**：放弃黑名单，改为**解析 + 白名单 + 强制只读账号**三层：

```java
// 1) 语法级校验：用 JSqlParser 解析后只接受 StatementType.SELECT，且拒绝任何子语句
public void assertSelectOnly(String sql) {
    Statement stmt;
    try {
        stmt = CCJSqlParserUtil.parse(sql);
    } catch (JSQLParserException e) {
        throw new RuntimeException("SQL 语法不合法");
    }
    if (!(stmt instanceof Select)) throw new RuntimeException("只允许执行 SELECT 查询");
    if (String.valueOf(stmt).contains(";")) throw new RuntimeException("不允许多条语句");
    if (String.valueOf(stmt).toLowerCase().contains("into outfile")
            || String.valueOf(stmt).toLowerCase().contains("into dumpfile")) {
        throw new RuntimeException("不允许导出到文件");
    }
}

// 2) 变量替换改为 SQL 字面量（数字强校验），禁止字符串拼接
private String bindVars(String sql, Long deptId, Long userId) {
    long d = deptId == null ? 0L : deptId;      // 已是 Long，天然安全
    long u = userId == null ? 0L : userId;
    return sql.replaceAll("(?i)Para_DepID", Long.toString(d))
              .replaceAll("(?i)Para_UserId", Long.toString(u));
}

// 3) 行数限制不再用 "包裹 + LIMIT"，统一用 JDBC maxRows + queryTimeout（已有），
//    并给目标数据源配置只读账号（GRANT SELECT）
```

> 另建议：`/dataRange/run` 只允许 `SUPER_ADMIN`（DEPT_ADMIN 能探测 sys_user 是不合理的权限设计）；或改为"只允许试跑已保存的配置"而不是任意 SQL。

### H6. 文件秒传按 md5 全局复用 → 他人附件被越权下载/删除

- **位置**：
  - `src/main/java/com/reportsys/service/FileStorageService.java:128-144`（`checkChunks`）
  - `src/main/java/com/reportsys/service/FileStorageService.java:217-223`（`store` 内秒传）
  - `src/main/java/com/reportsys/controller/FileController.java:65-75`（`GET /file/chunk/check`）
- **问题**：秒传"同 md5 已存在则直接返回该 `RptFile`"。这在多租户/多部门系统里是**跨用户文件共享漏洞**：
  1. 攻击者拿到目标文件 md5（可从任何共享渠道、或对已知文件计算），调用 `GET /file/chunk/check?fileKey=x&md5=<md5>` → 返回 `fileId`、`fileName`，**探测"系统里是否存在这个文件"**；
  2. 随后 `GET /file/{fileId}/download`（同为普通角色，且 `FileStorageService.getById` 不校验归属）→ **下载他人的敏感附件**；
  3. `store()` 里秒传返回 `exist` 对象后，`VolumeFileService.upload` 会把该 `fileId` 写进自己的分卷组 → 自己的包里出现别人的文件。
- **级别**：高
- **利用方式**（FILL_USER）：
  ```
  GET /api/file/chunk/check?fileKey=abc&md5=<目标 md5>  → {"skipUpload":true,"fileId":17}
  GET /api/file/17/download                             → 附件原文
  DELETE /api/file/17                                   → 把别人的附件软删除
  ```
- **修复**：秒传只在**同一用户/同一任务**范围内复用；下载/删除必须校验归属：

```java
// 1) 秒传范围收敛
RptFile exist = rptFileMapper.selectOne(Wrappers.<RptFile>lambdaQuery()
        .eq(RptFile::getMd5, md5)
        .eq(RptFile::getDeleted, 0)
        .eq(RptFile::getUploadBy, uploadBy)          // ← 本人
        .last("limit 1"));

// 2) 下载/删除归属校验（FileStorageService）
private void assertFileAccessible(RptFile file, Long userId, boolean write) {
    if (authzService.hasAnyRole("SUPER_ADMIN")) return;
    if (Objects.equals(file.getUploadBy(), userId)) return;
    if (file.getTaskId() != null
            && authzService.canViewTask(file.getTaskId(), userId)) return;  // 被分派该任务才可见
    throw new AccessDeniedException("无权访问该附件");
}

public byte[] readBytes(Long id, Long userId) {
    RptFile file = getById(id);
    assertFileAccessible(file, userId, false);
    ...
}
```

---

## 3. 中（Medium）

### M1. 任意部门管理员可读全量数据字典

- **位置**：`src/main/java/com/reportsys/controller/DictController.java:23`（类级 `@PreAuthorize("isAuthenticated()")`）
- **问题**：`/dict/all`、`/dict/{dictType}` 对任何角色开放。若字典里存了业务机密（如客户等级映射、审批阈值、内部组织编码），属于越权读取。管理接口已正确收口到 SUPER_ADMIN（`:52/:60/:80/:91`）。
- **级别**：中
- **修复**：类级保留 `isAuthenticated()` 可以，但敏感类型需要白名单；建议：

```java
@GetMapping("/{dictType}")
public Result<List<SysDict>> byType(@PathVariable String dictType) {
    if (!PUBLIC_DICT_TYPES.contains(dictType)) {
        throw new AccessDeniedException("该字典类型需要管理权限");
    }
    ...
}
```

### M2. 数据申请"待审批列表"对所有角色开放

- **位置**：`src/main/java/com/reportsys/controller/DataApplyController.java:68-78`（`GET /dataApply/approveList`，无方法级注解，类级是 4 角色全开）
- **问题**：FILL_USER 也能拉到全公司待审申请。虽然 `approve`/`reject` 已收口（`:83`、`:104`），但列表泄露申请人、事由、任务信息。
- **级别**：中
- **修复**：加 `@PreAuthorize("hasAnyRole('SUPER_ADMIN','AUDIT_USER','DEPT_ADMIN')")`，并在 `RptDataApplyService.getApproveList` 按部门过滤。

### M3. SSRF：数据源连接测试默认不限制主机，且目标库 URL 未做校验

- **位置**：
  - `src/main/java/com/reportsys/service/RptDatasourceService.java:117-157`（`allowedHosts` 为空则**放行**，`:149-152`）
  - `src/main/java/com/reportsys/service/RptDatasourceService.java:123-131`（`jdbc:mysql://` + 用户可控 `ip/port/dbName`）
  - `src/main/java/com/reportsys/service/DialectSupport.java:51-71`（`jdbcUrl` 同样直接拼 `host/db`）
  - 入口 `src/main/java/com/reportsys/controller/DatasourceController.java:106-131`
- **问题**：
  1. 白名单默认空 = 不限制。日志只 `warn` 一次，实际变成"内网端口/主机探测"工具（SUPER_ADMIN 才有该权限，因此等级降为中，但仍属隐患）；
  2. MySQL 驱动的 `allowPublicKeyRetrieval=true` + 用户可控 `dbName`：可让服务端向任意内网 MySQL 发起认证握手、枚举库名；`dbName` 可包含 `?` 参数覆盖 URL 参数（例如 `dbName=x?allowLoadLocalInfile=true&allowUrlInLocalInfile=true`），配合恶意的"目标 MySQL 服务端"可实现 **JDBC 任意文件读取**（MySQL JDBC 反序列化漏洞需要 `autoDeserialize=true&queryInterceptors=...`，用户可控 URL 参数时同样可达）；
  3. `TargetDataSourceManager.java:54-57` 在 `ds.getId()==null` 时用 `(ip+port+dbName).hashCode()` 作为池 key → **哈希碰撞**会让两个不同库共用同一个连接池（错连到别的库），且不校验 host 合法性；`MaterializeService` 建表/写数走的就是这里。
- **级别**：中
- **修复**：

```java
// 1) 白名单默认收紧为私网外的显式清单，未配置时拒绝
private void assertHostAllowed(String host) {
    if (allowedHosts == null || allowedHosts.trim().isEmpty()) {
        throw new AccessDeniedException("未配置 report.datasource.test.allowed-hosts，已禁止连接测试");
    }
    // 2) 先解析为 IP 再比对，防 DNS 指向内网
    InetAddress addr = InetAddress.getByName(host);
    if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()
            || addr.isAnyLocalAddress() || addr.isMulticastAddress()) {
        throw new AccessDeniedException("禁止连接回环/内网地址: " + host);
    }
    if (!Arrays.asList(allowedHosts.split(",")).stream().map(String::trim)
            .anyMatch(a -> a.equalsIgnoreCase(host))) {
        throw new AccessDeniedException("目标主机不在允许测试的白名单内: " + host);
    }
}

// 3) dbName / ip 合法性校验（拒绝 ? # / 空格 等 URL 元字符）
private void assertSafeConnParams(RptDatasource ds) {
    if (ds.getIp() == null || !ds.getIp().matches("^[0-9A-Za-z.\\-]+$")) throw new RuntimeException("主机名不合法");
    if (ds.getDbName() == null || !ds.getDbName().matches("^[0-9A-Za-z_\\-]+$")) throw new RuntimeException("库名不合法");
}
// 4) 去掉 allowPublicKeyRetrieval=true（或仅在 USE_SSL=true 时启用）
// 5) TargetDataSourceManager 池 key 改用 "id"（必须非空）或 ip:port/db 字符串本身
private final Map<String, DruidDataSource> pools = new ConcurrentHashMap<>();
String key = ds.getId() != null ? ("id:" + ds.getId())
        : ("ep:" + ds.getIp() + ":" + ds.getPort() + "/" + ds.getDbName());
```

### M4. 密码哈希强度依赖默认值

- **位置**：`src/main/java/com/reportsys/config/SecurityConfig.java:39-42`：`return new BCryptPasswordEncoder();`
- **问题**：默认 strength=10（约 2^10 次迭代）。算法本身没问题，但等保/密评通常要求 ≥ 12；且没有"登录时自动升级旧哈希"机制。**不是漏洞，是配置加固项**。
- **级别**：中
- **修复**：

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);     // 或 DelegatingPasswordEncoder + {bcrypt}
}
```

### M5. 操作审计日志切面把参数整体记入 target（可能含密码/密钥）

- **位置**：`src/main/java/com/reportsys/aop/OperLogAspect.java:67-78`（`describe` 对 `args` 全部 `String.valueOf` 后落库到 `target`）
- **问题**：`@OperLog` 标在 `DatasourceController`(无)、`TemplateController.materialize`、`VolumeFileController.upload`、`AuthController`(无) 等方法上。当前带注解的方法参数里**没有直接传密码**，但 `TemplateController.parse-excel`（未标注解的 `saveFields` 也不带 OperLog）；`VolumeFileController.upload` 记录 `MultipartFile` 的 toString。风险在于：以后若给 `PUT /datasource`（含 password）加 `@OperLog`，**数据源口令会明文进 `rpt_oper_log.target`**。属于"易被后续开发引入"的隐患。
- **级别**：中（潜在）
- **修复**：`describe()` 按参数名/类型脱敏：

```java
private static final Set<String> SENSITIVE = new HashSet<>(
        Arrays.asList("password", "pwd", "passwd", "token", "secret", "key"));

private String describe(ProceedingJoinPoint jp) {
    MethodSignature sig = (MethodSignature) jp.getSignature();
    String[] names = sig.getParameterNames();
    Object[] args = jp.getArgs();
    StringBuilder sb = new StringBuilder(sig.getName()).append('(');
    for (int i = 0; i < args.length; i++) {
        String n = names == null || i >= names.length ? ("arg" + i) : names[i];
        String v = SENSITIVE.contains(n.toLowerCase()) ? "***" : String.valueOf(args[i]);
        sb.append(n).append('=').append(StrUtil.maxLength(v, 40)).append(',');
    }
    return StrUtil.maxLength(sb.append(')').toString(), 120);
}
```

### M6. 接口错误信息回显 SQL/数据库细节

- **位置**：`src/main/java/com/reportsys/service/DataRangeService.java:120-122`：`throw new RuntimeException("SQL 执行失败: " + e.getMessage(), e)`；同类 `MaterializeService.java:99/112/133/193`、`TargetDataSourceManager.java:85`、`RptDatasourceService.java:143`
- **问题**：`Result.sanitizeMessage` 只挡了"sqlexception / error updating database / mysql"等关键字，但 `MaterializeService` 抛的是"物化失败: Table 'xxx' doesn't exist"，**不含 blacklist 关键字** → 原样返回给前端，泄露目标库表名/字段名。`DataRangeService` 的 `"SQL 执行失败: " + msg` 落在 `Result.error(...)` 时同样可能原样透出（`You have an error in your SQL syntax; near 'xxx'` 命中 `syntax error` 会被脱敏，但 `Unknown column 'password' in 'field list'` 不会）。
- **级别**：中
- **修复**：在 `Result.sanitizeMessage` 增加模式（`unknown column`、`table '`、`doesn't exist`、`access denied for user`、`you have an error in your sql`、`field list`）；更根本的是把这类"目标库异常"统一包装成业务异常并只写日志。

### M7. 物化表名/列名未做标识符白名单（SQL 标识符注入）

- **位置**：
  - `src/main/java/com/reportsys/service/MaterializeService.java:65-69`（`resolveTable`）与 `:268-274`（`safeIdent`）
  - `src/main/java/com/reportsys/service/DialectSupport.java:84-89`（`quote`）与 `:250-254`（`columnName`）
  - 模板建表/写库入口：`TemplateController.java:229-251`（`/template/{id}/deploy` 传 `tableName`，仅 SUPER_ADMIN）、`/template/{id}/materialize`
- **问题**：`resolveTable` 的表名会经过 `safeIdent`（`replaceAll("[^0-9A-Za-z_]", "_")`）**这一步是安全的**；但 `DialectSupport.quote()` 只做了 `replace("\"","")`/`replace("`","")`，**列名 `db_column` 来自 `rpt_template_field`，未做白名单**，可写入如 `x`) DROP TABLE t;--` 的值（需有模板编辑权=SUPER_ADMIN）。由于只有 SUPER_ADMIN 能写模板，实际等级降为中（内部威胁/误操作）。
- **修复**：`columnName` 输出前统一 `if (!c.matches("^[A-Za-z_][A-Za-z0-9_]{0,63}$")) throw new RuntimeException("非法列名: " + c);`；同理 `tableRef` 对 `dbName` 做同样校验。

### M8. Excel 解析无 zip 炸弹/行数上限

- **位置**：`src/main/java/com/reportsys/service/ExcelService.java:90`、`:128`（`WorkbookFactory.create(in)`）；调用方 `FillController.java:180-195`（`/fill/import`，任意 FILL_USER）、`TemplateController.java:270-282`
- **问题**：POI 默认 `ZipSecureFile` 对压缩比有保护（`MinInflateRatio 0.01`、`MaxEntrySize`），但仍可能出现 XML 实体膨胀/超大行数导致 OOM；`parseRows` 无最大行数限制（`:142-152`），`/fill/import` 直接把整个 excel 读进内存再逐行转换。
- **级别**：中（可用性/DOS）
- **修复**：`ZipSecureFile.setMaxEntrySize(...)`、`setMinInflateRatio(0.01)`；`parseRows` 增加 `maxRows`（如 50000）超限抛业务异常；`FillController.import` 增加 `file.getSize()` 上限。

---

## 4. 低（Low）

### L1. JWT 有效期 24h、无刷新/吊销机制；改密后旧 token 仍有效
- **位置**：`JwtTokenUtil.java:40-44`（`expire-hours` 默认 24）、`SysUserService.java:147-159`（`updatePwd` 不吊销）
- **修复**：改密/重置密码后调用 `onlineUserService.kickByUser(userId, "密码变更")`；access token 缩短到 2h + refresh token。

### L2. `/file/chunk/**` 与 `mergeChunks` 的 fileKey 无归属绑定
- **位置**：`FileStorageService.java:169-183`、`:187-210`；`sanitize` 只做字符替换，**未把 fileKey 与上传人绑定**，知道他人 fileKey 即可续传/合并/覆盖其分片。
- **修复**：`uploadedChunks/ saveChunk/ mergeChunks` 的目录键改为 `userId + "_" + fileKey`，或建一张 `rpt_upload_session(file_key, user_id, expire_at)` 并校验。

### L3. `sanitize()` 过于激进，破坏合法 fileKey（功能缺陷，附带安全学教训）
- **位置**：`FileStorageService.java:293-295`：`key.replaceAll("[^0-9A-Za-z_\\-]", "_")`
- **问题**：前端常用 `uuid()`（含 `-`）作为 fileKey，`-` 被保留，这点没问题；但前端若用 `md5-文件名` 或含 `.` 的 key，会被改成 `_` → **"上传分片到 A 目录、查询时读 B 目录"**，表现为"缺分片无法合并"。安全上没问题（路径穿越已被彻底消灭），但功能上会踩坑。建议只允许 `[0-9a-zA-Z_-]{1,64}` 并对其余字符**直接拒绝**（比静默替换更安全可预期）。

### L4. 定时任务/异步无分布式锁（多实例重复执行）
- **位置**：`src/main/java/com/reportsys/job/TaskScheduleJob.java:39-59`；`RptTaskService.java:352-376`（"已生成则跳过"是"先查后插"，非原子）
- **问题**：单实例部署没问题；一旦水平扩容，`generatePeriodicTasks` 与 `scanOverdue` 会并发重复建子任务/发消息。**`@Scheduled` 默认单线程，同一实例内不会重入**。
- **修复**：加 `rpt_task_parent_id + belong_time` 唯一索引，或用 Redis `SETNX` 锁；`scanOverdue` 用 `INSERT IGNORE` 做幂等。

### L5. login/logout 白名单用 `contains` 匹配
- **位置**：`JwtAuthFilter.java:76-77`：`requestURI.contains("/auth/login") || requestURI.contains("/auth/captcha")`
- **问题**：`contains` 导致任何包含该子串的路径被放行，如 `/api/xxx/auth/login/../../admin/x`（Spring 规范化后一般会 404，风险低），但属于不严谨写法。
- **修复**：用精确集合：
  ```java
  private static final Set<String> WHITELIST =
          new HashSet<>(Arrays.asList("/api/auth/login", "/api/auth/captcha"));
  if (WHITELIST.contains(request.getRequestURI()) || "OPTIONS".equalsIgnoreCase(request.getMethod())) { ... }
  ```

---

## 5. 已检查、未发现问题的项（明确说明，避免误判漏审）

| 检查项 | 结论 | 依据 |
|---|---|---|
| **路径穿越（../）** | ✅ 未发现问题 | 落盘名 `UUID + "_" + safeName()`（`FileStorageService.java:227`），`safeName` 替换 `\ / : * ? " < > \|`（`:287-291`）；分片目录 `sanitize(fileKey)` 关闭非法字符（`:293-295`）；**没有直接使用用户原文件名拼路径** |
| **Content-Disposition 头注入** | ✅ 未发现问题 | 所有下载接口均用 `WebUtils.encodeFileName()` = `URLEncoder.encode(raw,"UTF-8")`（`WebUtils.java:24-34`），`CR/LF/quotes/分号` 全部 `%xx` 编码；`FileController:108-112`、`VolumeFileController:68-72`、`TemplateController:324-328`、`FillController:235-239`、`LedgerController:87-90` |
| **反序列化 / SpEL / 脚本引擎 / 命令执行** | ✅ 未发现问题 | 全仓库 grep `ObjectInputStream / readObject / SpelExpression / ScriptEngine / Runtime.getRuntime / ProcessBuilder / XMLDecoder / enableDefaultTyping / XStream / Yaml` → 仅 `RptDatasourceService:136` 的 `Class.forName(driverClass)`，且 `driverClass` 为**硬编码常量**（`:122-131`），不可控 |
| **MyBatis `${}` 注入** | ✅ 未发现问题 | 26 个 Controller/21 个 Mapper 中无任何 `${}`；`@Select` 只有 `SysMenuMapper:11-14` 的 `#{userId}`。`${}` 仅出现在 `@Value("${...}")` 配置注入 |
| **JdbcTemplate 字符串拼接** | ✅ 未发现问题（有 2 处可控度低的例外） | `SysUserService:78` `inSql("... WHERE user_id = " + user.getId())` — `user.getId()` 是数据库自增 Long，不可控；`OperLogController:57`、`ReportStatsService:117/127` 的 `LIMIT/OFFSET` 拼接 — `page/pageSize` 为 int 且 `ReportStatsService` 有 `Math.min(pageSize,200)` 兜底；`TodoService:98/223` 的 `apply("DATE(...)=CURDATE()")` 为常量 |
| **JWT 篡改/签名** | ✅ 未发现问题 | 强随机密钥（启动校验 ≥32B、禁旧密钥、空值拒绝启动，`JwtTokenUtil:46-58`）；`parseClaimsJws` 校验签名与过期（`:92-104`）；`getUserId` 仅从已验签 claims 取；风险仅剩密钥保管（环境变量注入，已是正确做法） |
| **验证码爆破/绕过** | ✅ 未发现问题 | 一次性消费（`CaptchaStore.verify:82` 先 `remove` 再比对）；过期 5min；uuid 不存在即 MISSING（nonce 不可预测）；固定码开关默认空且只在"码相等"时放宽、不放宽 uuid 一次性语义（`:78-96`）；大小写归一 |
| **密码字段回显** | ✅ 未发现问题（覆盖完整） | `SysUserService.login:76`、`listPage:221`、`CompatController:85`、`CompatController:228`(userById)、`CompatController:216`、`RptDatasourceService.mask`(:159-163) 全部置空/掩码；数据源在 `listPage/listAll/getById` 均掩码 |
| **AES/GCM 密钥与 IV** | ✅ 未发现问题 | `SecretCrypto.java:63-69`：每次加密 `SecureRandom` 生成 12 字节 IV 并随密文前置；密钥经 SHA-256 派生为 256bit；带 `enc:` 前缀与历史明文兼容；**无 IV 复用**；风险仅"密钥未配置时降级明文"（已有 WARN，`SecretCrypto:49`）——建议生产强制配置（未配置即拒绝启动） |
| **日志打印密码/token/密钥** | ✅ 未发现问题 | grep `log.*password/pwd/token/secret` → 仅 `JwtTokenUtil:57` 打印有效期，**不打印密钥本身**；`CaptchaStore:61` 会打印 `fixed-code` 的值，但该开关默认关闭且带醒目标注（若生产误开，会把固定验证码写进日志——建议改为只打印"已启用"不打印值） |
| **CSRF** | ✅ 可接受 | `SecurityConfig:47` `csrf().disable()`，但鉴权完全基于 `Authorization` 头（非 Cookie/Session），CSRF 攻击无法自动附带 token，禁用是合理的（前提是前端不用 Cookie 存 token） |
| **会话创建策略** | ✅ 未发现问题 | `SessionCreationPolicy.STATELESS`（`SecurityConfig:49`），无服务端 Session 越权面 |
| **登录限流存在性** | ⚠️ 存在但可绕过（并入 M 类，详见下方说明） | `LoginAttemptLimiter` 用"用户名+IP"双维度计数，`onSuccess` 清除（`SysUserService:70`）；但 IP 取自 `X-Forwarded-For` 最左值（`SysUserService:127-133`、`AuthController:196-206`、`OperLogAspect:105-119`），**客户端可任意伪造**，导致：① 换 IP 头即绕过锁定；② 攻击者可用伪造 XFF 给别人"上锁"（DoS）；③ 操作日志/登录日志 IP 全部不可信（审计失效）。修复：仅信任反向代理注入的固定头（如 `X-Real-IP` 且 `server.tomcat.remoteip.internal-proxies` 已配置），或使用 `request.getRemoteAddr()` + 代理层 `real_ip_header` |
| **密码哈希算法** | ✅ 未发现问题（仅强度见 M4） | BCrypt（`SecurityConfig:41`），登录用 `passwordEncoder.matches`（`SysUserService:65`），无 MD5/SHA1 裸哈希 |
| **限流/防爆破维度** | ⚠️ 见上 | 按"用户+IP"锁定，换用户名轮询不同账号不受限；建议增加"按 IP 全局失败计数" |
| **CORS** | ❌ 见 S1 | — |
| **Spring Boot Actuator / H2 Console** | ✅ 未发现问题 | `pom.xml` 未引入 actuator；H2 仅 runtime 依赖且 `application.yml` 未开 h2-console；`SecurityConfig:52` 只放行登录/验证码 |
| **上传后缀白名单** | ✅ 已修复（不重复报告） | `FileStorageService.java:82-107`、`:216`（`store` 统一入口二次校验） |

---

## 6. 三、中、低级问题的补充说明（登录限流、IP 伪造）

> 该条本应单列，为避免与 M 类重复，集中在此：

**LoginAttemptLimiter 限流可绕过**（`LoginAttemptLimiter.java:34-36` + 三处 IP 取值的伪造面）
- `key = username.toLowerCase() + "|" + ip`，`ip` 来自 `X-Forwarded-For`（客户端可控）。
- 利用 1：每次请求带不同 `X-Forwarded-For: 1.1.1.<n>` → 每次都是新 key → **可无限次撞库**，5 次锁定形同虚设。
- 利用 2：攻击者用 `X-Forwarded-For: <受害者出口 IP>` 就能**把受害者账号锁定 5 分钟**（`onFailure` 在验证码通过后才执行，攻击者需先解验证码，成本略高，但仍可做定向 DoS）。
- 利用 3：登录日志 `sys_login_log.ip`、操作日志 `rpt_oper_log.ip` 全部可伪造 → **审计证据链失效**（等保要求日志可追溯）。
- **修复**：
  ```java
  // 只信任最后一跳代理，且禁止客户端自带 XFF
  private String clientIp(HttpServletRequest req) {
      // server.tomcat.remoteip.internal-proxies=10.0.0.0/8 配置后，Tomcat 会正确重写 getRemoteAddr()
      return req.getRemoteAddr();
  }
  ```
  同时在 nginx 侧固定 `proxy_set_header X-Forwarded-For $remote_addr;`（而不是 `$proxy_add_x_forwarded_for`）。限流计数增加"按 IP 全局"维度。

---

## 7. 必须修的清单（按优先级）

1. **S1 CORS**：`allowedOriginPatterns("*") + allowCredentials(true)` → 显式清单 + `allowCredentials(false)`（10 分钟可改，零业务影响）。
2. **S2 CompatController 无注解写接口**：给 `/dataApply/{id}` 的 GET/PUT/DELETE 补 `@PreAuthorize` + 归属校验；`/task/assign/{id}` 补部门归属。
3. **H2 物化表导出越权**：`/fill/export`、`/fill/{taskId}/target-rows` 加 `assertCanViewTaskData`。这是**数据泄露面最大的一条**。
4. **H1 AuthzService 默认放行**：`assertCanFill/assertCanAudit` 改为默认拒绝（只有任务创建人可兜底）。
5. **H5 数据范围 SQL**：`/dataRange/run` 收口为 SUPER_ADMIN 或改用 JSqlParser 解析；否则 DEPT_ADMIN 可拖 `sys_user` 全表。
6. **H6 文件秒传/下载越权**：秒传按 `uploadBy` 收敛；下载/删除/列表补归属校验。
7. **H3 commit dataId 归属**：补 `taskId + fillUserId` 双校验。
8. **H4 会话降级**：`onLogin` 失败拒绝登录、`sessionState` 异常改为 MISSING（fail-closed）。
9. 中危中的 **M3（SSRF/池 key 碰撞）**、**M5（日志参数脱敏）** 建议同批次修完。

## 8. 总体结论

- **整体安全评级：C（不合格）**。认证（JWT）与大部分"角色级"鉴权已经做得不错，但**数据归属层（水平越权）是系统性缺失**：Controller 里几乎没有任何一处校验"这条数据是不是我的"，服务层的 `AuthzService` 又被"无分派即放行"的老逻辑兜底，等于没有兜底。
  - **最危险的组合**：任意基层填报员 → `GET /fill/export?taskId=N` 批量导出 → 直接带走全公司业务数据；再配合 CORS 通配，若存在前端 XSS 即可从外部站点完成整套操作。
- **建议整改顺序**：先修 S1/S2/H1/H2（1~2 天），再修 H3~H6（3~5 天），最后处理 M/L 类加固。修完后建议对 `/fill`、`/audit`、`/dataApply`、`/file`、`/dataRange` 五组接口做一次**越权回归测试**（双账号、跨部门、跨任务各测一遍）。
- 本报告只做只读审计，未改动任何业务代码；报告文件本身位于 `backend/SECURITY_AUDIT_REPORT.md`。
