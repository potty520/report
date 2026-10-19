# 数据填报系统 前端代码审计报告（frontend2）

- 审计对象：`E:\C++\report_src\report-master\frontend2`（Vite 5 + React 18 + TS + AntD 5 + zustand）
- 规模：64 个 ts/tsx 源文件，约 10,300 行；`src/` 下 24 个页面 + 16 个 stores + 7 个 components + 4 个 utils
- 后端交叉核对：`backend/src/main/java/com/reportsys/`（27 个 Controller、22 个 Model）
- 审计方式：read/grep/glob 全量读源码 + pwsh 统计 + 后端实体与 Controller 逐字段逐路径比对；**未修改任何业务代码**（仅新增本报告文件）

---

## 0. 量化指标总览

| 指标 | 数值 | 说明 |
|---|---|---|
| `any` 出现次数 | **186** | 主要集中在 services/api.ts(30)、Template/Add(19)、Template/Wizard(11) |
| `as any` | **12** | File/List 2、request.ts 2、SiderMenu 2、其余各 1 |
| `as unknown as` | **16** | api.ts 归一化层 13 处 |
| `@ts-ignore` / `@ts-expect-error` | **0** | ✅ 已检查，未发现问题 |
| `dangerouslySetInnerHTML` | **0** | ✅ 未使用 |
| `innerHTML =` / `eval(` / `new Function(` | **0 / 0 / 0** | ✅ 未使用 |
| `console.log` | **0** | ✅ |
| 前端 API 调用路径 | **~104 个** | 与后端比对：**0 个 404**（CompatController 已补齐） |
| 路径可通但语义/字段错 | **14 处** | 见 §2 |
| 请求/响应字段不匹配的模块 | **8 个**（模板、数据源、角色、用户、数据申请、字典、审核、台账） | 任务模块已修，其余未修 |
| 权限绕过（仅前端拦截） | **0 处** | ✅ 后端 `@PreAuthorize` 覆盖全部写接口 |
| 吞异常（catch 内只有空/注释） | **9 处** | + store 层静默 catch **30 处** |
| 未使用导出 | **12 个** | |
| 未使用导入 | **12 处** | tsconfig `noUnusedLocals: false` 关闭了检测 |
| 未使用依赖 | **1 个**（bcryptjs） | |
| 大依赖引入 | **1 个严重**（echarts 全量 1033KB） | antd 已由 Vite tree-shake 生效 |
| 主 chunk | **1416.7 KB**（gzip 后约 22%，未开 gzip 预压） | 68 个 JS 共 3461.8 KB |
| 列表页 `columns` 未 useMemo | **23 / 24** | 每次渲染重建列定义 |
| 定时器泄漏 | **0 处** | ✅ 2 个 setInterval 均有清理 |
| 事件监听泄漏 | **1 处（轻微）** | request.ts 的 pagehide 仅注册不注销（全局单例，可接受） |
| 表单校验缺失的必填项 | **9 处** | 见 §4 |

---

## 1. 安全性

### 1.1 XSS —— ✅ 已检查，未发现问题

| 检查项 | 结果 |
|---|---|
| `dangerouslySetInnerHTML` | 0 处 |
| `element.innerHTML =` | 0 处 |
| `eval(` | 0 处 |
| `new Function(` | 0 处 |
| 动态 `href`/`src` 拼接用户数据 | 0 处 |
| Markdown/富文本渲染 | 无 |

所有后端数据均通过 JSX 文本插值（React 自动转义）渲染。Trajectory 页的 `n.comment`、Todo 页的 `content`、File 页的 `fileName` 都走 `{value}`，安全。

> ⚠️ 唯一遗漏：链接跳转用 `window.open`/`location` 的地方也不存在，但 `utils/download.ts:21-30` 的 `a.href = URL.createObjectURL(blob)` 使用 blob URL，安全。
>
> **仍有残余风险**：`index.html` 无 CSP meta，`dist/index.html` 加载 `<script type="module">`。建议部署侧加 `Content-Security-Policy: default-src 'self'; script-src 'self'; object-src 'none'`。

### 1.2 Token 存 localStorage 的风险 + XSS 窃取路径 —— 🟡 中

**位置**：`src/stores/useAuthStore.ts:35-38`、`src/utils/auth.ts:45-47`、`src/utils/request.ts:42`

```ts
// useAuthStore.ts:35
localStorage.setItem('token', token);
```

**问题**：
1. Token（JWT）明文存 localStorage，任何 XSS 或同源第三方脚本都能读取。虽然当前无 XSS 注入点（§1.1），但风险面 = 全部依赖 + 后续改动。
2. localStorage 无 `Secure`/`HttpOnly`/`SameSite` 保护，只能靠 CSP 加固。
3. 前端把 **menus / permissions / userInfo** 也缓存进了 localStorage（`useAuthStore.ts:36-38`），任意脚本可篡改 → 影响 §3.3 的权限兜底。

**影响**：一旦出现 XSS（例如未来引入富文本/第三方 widget），攻击者可完整接管账号，并可在 token 有效期内无限调用 API。
**缓解现状**：后端 `JwtAuthFilter` 有 30s 角色缓存 + 会话白名单（登出/踢下线立即 401），token 过期可控。

**修复建议**：
```ts
// ① 短期：XSS 防御前置——加 CSP（nginx）
// add_header Content-Security-Policy "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; object-src 'none'; base-uri 'self'" always;

// ② 中期：token 改放 sessionStorage（关闭标签即失效），userInfo/menus 不落盘
// src/stores/useAuthStore.ts:34-39
const res = await loginApi(params);
const { token, userInfo, permissions, menus } = res.data.data;
sessionStorage.setItem('token', token);   // 而非 localStorage
set({ token, userInfo, permissions, menus, loading: false });  // 只留内存，不写 userInfo/menus/permissions

// ③ 长期：后端改 HttpOnly + Secure + SameSite=Lax Cookie（前端 axios 加 withCredentials: true，去掉 Authorization 头）
```

### 1.3 前端权限控制是否只是"按钮隐藏" —— ✅ 核心结论：**不是，后端真校验**

**结论**：**未发现"仅靠前端拦截"的敏感操作。** 后端 27 个 Controller 共 **70 处 `@PreAuthorize`**，覆盖所有写接口；`SecurityConfig.java` 开启 `@EnableGlobalMethodSecurity(prePostEnabled = true)`，`JwtAuthFilter` 按用户真实角色装载 `ROLE_xxx`（`loadAuthorities`），无权限返回 **HTTP 403**。

**但有一个重要的反例——前端"完全不拦"**：

| 位置 | 问题 | 级别 |
|---|---|---|
| `src/utils/auth.ts` 全部 | `hasPermission / hasAnyPermission / hasAllPermissions` **三个函数一个都没被调用**（全仓 0 引用）。所有页面的按钮（新增/编辑/删除/审批/授权/踢下线）**对所有已登录用户一律可见** | 中 |
| `src/components/AuthGuard.tsx` | 组件 **从未被 App.tsx 引用**（`withAuth` 也 0 引用），是死代码 | 轻 |

**影响**：非管理员登录后会看到"新增用户/删除角色/数据源/模板授权/踢下线"等按钮，点击后由后端 403 拒绝。用户体验极差（看到能点却不能用的按钮），且 403 还会触发 §1.4 的"被踢出登录"。

**修复建议**：
```tsx
// src/App.tsx —— 给路由加权限包装（复用现有 AuthGuard 思路）
const RequirePerm: React.FC<{ perm: string; children: React.ReactNode }> = ({ perm, children }) => {
  const perms = useAuthStore((s) => s.permissions);
  if (!perms.includes(perm)) return <Result status="403" title="无权限访问" subTitle={`缺少权限：${perm}`} />;
  return <>{children}</>;
};
// <Route path="system/user" element={<RequirePerm perm="sys:user:list"><UserPage /></RequirePerm>} />
```
```tsx
// 页面内按钮：用 hasPermission 包一层（先让它真正被使用）
{hasPermission('sys:user:add') && <Button type="primary" onClick={handleAdd}>新增用户</Button>}
```

**后端权限粒度核对（供确认，均已在后端校验）**：
- 仅 SUPER_ADMIN：`/user/**`、`/role/**`、`/menu/**`、`/datasource/**`、`/system/log/**`、`/operLog/**`、`/online/kick*`、`/statistics-rule`(写)、`/quality/channel/status`、任务写操作、模板写操作、部门/项目写操作
- SUPER_ADMIN / DEPT_ADMIN：部门/项目读、任务列表、模板列表、台账、模板授权读
- 含 FILL_USER：`/fill/**`、`/file/**`
- 含 AUDIT_USER：`/audit/**`、`/ledger/**`
- `isAuthenticated()`：`/todo/**`、`/dict/all`、`/dict/{type}`、`/online/count`、`/statistics-rule/effective`

**⚠️ 注意**：`OverviewController` 整个类**没有 `@PreAuthorize`**（只有 SecurityConfig 的 `anyRequest().authenticated()`），即**任意登录用户可读全量统计**。属于可接受但建议显式声明。

### 1.4 敏感信息硬编码 —— 🟡 中

| 位置 | 内容 | 级别 | 说明 |
|---|---|---|---|
| `src/pages/UserCenter/index.tsx:8-13` | **RSA-1024 公钥** `MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQ...` | 中 | 见下方专项说明，公钥本身不是秘密，但**这段加密逻辑是彻底错误的** |
| `src/pages/Login/index.tsx:179` | `initialValues={{ username: 'admin' }}` | 轻 | 泄露管理员用户名，且默认填死 |
| `src/vite.config.ts:16` | `target: 'http://localhost:8763'` | 轻 | 仅 dev 代理，无泄露 |
| 硬编码 IP / 内网地址 | **0 处** | ✅ | |
| 硬编码数据库名/表名 | **0 处** | ✅ | |
| 明文密码 / 密钥注释 | **0 处** | ✅ | 已检查，未发现问题 |

**专项：`UserCenter` 的 RSA 加密是完整的功能性错误（严重）**

```ts
// UserCenter/index.tsx:32-44
const encrypt = new JSEncrypt();
encrypt.setPublicKey(PUBLIC_KEY);
const encryptedOld = encrypt.encrypt(values.oldPassword);   // → base64，172 字符
await changePasswordApi({ oldPassword: encryptedOld, newPassword: encryptedNew, confirmPassword: encryptedNew });
```
```java
// AuthController.java:168-171  —— 后端只取 @RequestParam，不做任何解密
public Result<Void> updatePassword(@RequestParam String oldPassword, @RequestParam String newPassword)
// SysUserService.java:153 —— 直接拿（base64 密文）去匹配 BCrypt 哈希
if (oldPassword == null || !passwordEncoder.matches(oldPassword, user.getPassword())) return Result.error("原密码错误");
```
全仓 grep `PrivateKey|RSA|decrypt` 在 controller/service 中**无任何 RSA 私钥解密实现**（`SecretCrypto` 是 AES，与密码无关）。且后端接收的是 `@RequestParam`，前端发的是 JSON body（见 §2.2 F-02）。

**修正建议**：删掉 JSEncrypt，走 HTTPS 明文传输即可（密码在 TLS 里已加密）。若确需 RSA，必须后端配套私钥解密。

### 1.5 明文密码在代码/注释里 —— ✅ 已检查，未发现问题

- 源码中 **0 处** 明文口令、密钥、AK/SK。
- 后端 `application.yml` 数据库口令已改为 `${REPORT_DB_PASSWORD:}` 环境变量注入。
- ⚠️ 但存在**弱口令策略**：`UserCenter/index.tsx:26` 前端校验"密码长度 ≥ 6"，后端 `PasswordPolicy.check()` 才是真正策略；前端提示文案与后端策略不一致时会误导（应先拉后端策略或统一文案）。

---

## 2. 接口契约一致性

### 2.1 路径核对：**0 个 404**（重要结论）

前端 `services/api.ts` 共约 **104 个**调用点，逐一比对后端 27 个 Controller 的 `@RequestMapping` 树：

| 结论 | 数量 |
|---|---|
| 路径完全匹配 | **90** |
| 靠 `CompatController` 补齐后匹配（`/auth/current-user`、`/dept/{id}`、`/project/{id}`、`/task/{id}`、`/task/assign/page`、`/task/assign/{id}`、`/dataApply/{id}`、`/datasource/{id}`、`/user/{id}`、`/role/{id}`、`/menu/{id}`） | **11** |
| 靠后端多路径映射兼容（`/audit/myTasks`、`/fill/task/page`、`/dataApply/`） | **3** |
| **会 404 的路径** | **0** ✅ |

> 说明：`/dataApply/`（带尾斜杠）能被 Spring 的 `@RequestMapping("/dataApply")` 匹配；`/dict/manage/`（空参数）已在页面注释里说明会命中 `/dict/{dictType}` —— 见 §2.2 F-13。

**但"路径通"不等于"语义通"**：以下 14 处路径能通、参数或字段却对不上，行为是错误的。

### 2.2 请求参数字段名 vs 后端实体（重点，列出全部对不上的）

| # | 级别 | 位置 | 前端发送 | 后端要求 | 后果 |
|---|---|---|---|---|---|
| **F-01** | **严重** | `services/api.ts:329` `TaskAssignFormParams` / `Task Assign/index.tsx:79-82` | `{ taskId, deptIds }` → `POST /task/{id}/assign?deptIds=` | `TaskController.java:155` `@RequestParam String assignType`（**无 required=false**） | **缺参直接 400**，任务分派 100% 失败；被 `catch { msg.error('分派失败') }` 吞掉真因 |
| **F-02** | **严重** | `services/api.ts:449` `changePasswordApi` | `PUT /auth/password`，**JSON body** `{oldPassword,newPassword,confirmPassword}` | `AuthController.java:168` `@RequestParam oldPassword, newPassword` | 参数取不到 → `oldPassword == null` → 返回"原密码错误"。**改密码 100% 失败** |
| **F-03** | **严重** | `services/api.ts:956-966`、`Fill/Task/index.tsx:29,42-48` | `GET /fill/task/${assignId}`；`POST /fill/submit` body `{assignId, fields[]}` | `FillController.java:62` `GET /fill/task/{taskId:\d+}`；`:92` `@RequestParam Long taskId, String dataJson` | `assignId` 后端**根本不返回**（`RptTaskData` 是 `taskId`）→ URL 变 `/fill/task/undefined` → 404；提交 body 被忽略 → **填报全流程不可用** |
| **F-04** | **严重** | `services/api.ts:479` `assignUserRolesApi` | `PUT /user/{id}/roles` body **`{ roleIds }`** | `UserController.java:134` `@RequestBody List<Long> roleIds`（**裸数组**） | 反序列化失败/映射空 → 传 `{roleIds:[1,2]}` 当 List 解析 → **400 或角色清空** |
| **F-05** | **严重** | `System/Role/index.tsx:118` `handlePermModalOk` | `PUT /role` body `{id,name,code,menuIds}` | `RoleController.java:68` `@RequestBody SysRole`，字段是 `roleName/roleCode`；`SysRole.java:20-21` `menuIds` 是 `@TableField(exist=false)` | `name/code` 被忽略（null 不覆盖，侥幸不炸）；`menuIds` **根本不发给后端** → "权限分配成功"但**一条都没保存**。正确端点是 `PUT /role/{id}/menus`（body 裸数组） |
| **F-06** | 中 | `Datasource/List/index.tsx:206,217,224`、`services/api.ts:265-276` | `name/host/databaseName` | `RptDatasource` 字段是 `dsName/ip/dbName` | 新增/更新数据源 → 名称/主机/库名全丢；`testDatasourceConnectionApi`（api.ts:819）取 `d.ip/dbName` 而表单给的是 `host/databaseName` → **测试连接恒失败** |
| **F-07** | **严重** | `System/User/index.tsx:81-88,160,253-263,300-302` | `nickname/gender` | `SysUser` 字段是 `realName/userType` | 昵称/性别根本不保存；列表"角色"列 `dataIndex:'roleIds'`（后端只给 id 数组）渲染成数字 Tag。见实测评级 |
| **F-08** | **严重** | `Task/Formulation/index.tsx:43,66` | 搜索参数 `name` | `TaskController.java:30` `@RequestParam taskName` | 任务名搜索**永远无效**（`Task/List doFetch` 没做 name→taskName 映射，只有 Template/List 做了） |
| **F-09** | 中 | `Org/Project/index.tsx:131,189,259`、`Org/Dept/index.tsx:236` | `remark`（项目备注） | `SysProject.description` | 备注永远为空 |
| **F-10** | **严重** | `services/api.ts:397-398,997-999`；`DataApply/Apply|Approve/index.tsx` | body `{title, reason, dataScope}`；表格读 `title/dataScope/applyTime/approverName/approveTime` | `DataApplyController.java:44` `@PostMapping apply(@RequestParam Long taskId, @RequestParam String reason)` —— **`@RequestParam` 而非 JSON body，且必须给 `taskId`**；实体只有 `reason/taskId/status/createTime/auditTime/auditorName/auditComment` | 新增申请 **400 必失败**（缺 `taskId`，且 JSON body 不被接收）；列表 6 列（标题/数据范围/申请时间/审批人/审批时间/审批意见）**全部为空或 '-'** |
| **F-11** | 中 | `Task/Assign/index.tsx:118` | `assignTime` | `CompatController.java:148` 返回的是 `createTime` | 分派时间列恒为 '-' |
| **F-12** | 中 | `Fill/Audit/index.tsx:105,99` | `fillTime/projectName` | `RptTaskData` 无 `fillTime`/`projectName` | 填报时间/项目列恒为 '-' |
| **F-13** | 中 | `services/api.ts:1323` `getDictManageApi` | `` `/dict/manage/${dictType \|\| ''}` `` → `/dict/manage/` | `DictController.java:51` `@GetMapping("/manage/{dictType}")`；空串会先被 `/dict/{dictType}` 吞掉 | 页面注释自己承认了这个坑（`Dict/index.tsx:27-29`）；管理列表**看不到停用项** |
| **F-14** | 中 | `services/api.ts:670-687` 归一化只挂在「列表」 | `getTemplateListApi` 做了 `name/code` 归一化，但**详情页 `Template/Detail` 仍读 `currentItem.version`**（后端 `RptTemplate` 无 version） | 详情页「版本」恒显示 `vundefined` |

**后端实体 vs 前端字段总表（核对的 12 个实体）**

| 实体 | 后端真实字段 | 前端假设字段 | 是否已归一化 |
|---|---|---|---|
| `RptTask` | taskName/templateId/belongTime/fillDeadline/fillingCycle/status | name/year/period/startDate/endDate | ✅ 已修（`normalizeTaskRow`/`toTaskPayload`） |
| `RptTemplate` | templateName/templateCode/fileName/datasourceName/materialized | name/code/excelFileName/version | ⚠️ 部分（列表✔ 详情✘ version） |
| `RptTemplateField` | fieldName(中文)/fieldCode/dbColumn/isRequired | fieldLabel/required | ✅ `normalizeTemplateField` |
| `RptDatasource` | dsName/dsType/ip/port/dbName/username | name/type/host/databaseName | ⚠️ 只有读做了归一化（`getDatasourceListApi`），**写和测试连接没做** |
| `SysUser` | username/realName/userType/deptName/roleIds | nickname/gender | ❌ 未做 |
| `SysRole` | roleName/roleCode/menuIds(exist=false) | name/code/menuIds | ❌ 列表用了 roleName✔，新增/改/授权用了 name/code ✘ |
| `SysDept` | 字段基本一致 | — | ✅ |
| `SysProject` | description | remark | ❌ |
| `SysMenu` | 字段一致（menuCode 未用） | — | ✅ |
| `RptDataApply` | reason/taskId/status/createTime/auditTime/auditorName/auditComment | title/dataScope/applyTime/approveTime/approverName/approveRemark | ❌ |
| `RptTaskData` | taskId/dataJson/status/submitTime/fillUserName/belongTime/... | assignId/fillTime/projectName/fields[] | ❌ |
| `RptTaskAssign` | createTime/assignType/userId/realName | assignTime/projectName | ❌ |
| `RptStatisticsRule` / `RptDataRangeConfig` / `RptFile` / `SysMessage` / `SysLoginLog` | 字段一致 | — | ✅ |

### 2.3 响应数据「假设字段一定存在」→ undefined 显示

| 位置 | 假设 | 后端实际 |
|---|---|---|
| `Template/Detail/index.tsx:95` | `currentItem.version` | 不存在 → 显示 `vundefined` |
| `Task/Assign/index.tsx:118` | `assignTime` | `createTime` → '-' |
| `DataApply/*` 多列 | 见 F-10 | 全空 |
| `Fill/Audit/index.tsx:267` | `auditDetail.fields` 是数组 | `RptTaskData` 返回 **`dataJson` 字符串** → 详情"填报数据"永远显示 '-' |
| `Fill/Task/index.tsx:157` | `currentFillData?.fields` 是数组 | `RptTaskData` 无 `fields` → 永远"暂无填报字段" |
| `Overview/index.tsx:97-106` | `stats` 为 falsy 时用假数据兜底 | **真实存在的未返回也不会走到兜底**（后端 `non_null` 过滤，缺字段时 `stats` 仍为对象、字段为 undefined → `Statistic value={undefined}` 显示空白而非 0） |
| `Template/List/index.tsx:81` | `datasourceName` | 后端有该 `@TableField(exist=false)`，但需 Service 填充；未填充则为 null → '-'（可接受） |
| `SiderMenu.tsx:14` | `Icons[menu.icon]` 能解析 | **21 个种子菜单的 icon 全部解析失败**，见 §7.3 |

> 后端全局配置 `spring.jackson.default-property-inclusion: non_null`（`application.yml`）意味着**null 字段不会出现在 JSON 里** —— 前端任何 `data.xxx.yyy` 链式访问都可能拿到 `undefined`，`response?.data?.data?.list || []` 这种防御写法只在少数新页面有，老页面（如 `res.data.data` 直接解构）会**直接抛 TypeError**。

---

## 3. 状态管理

### 3.1 zustand store 设计问题

| # | 级别 | 位置 | 问题 | 影响 |
|---|---|---|---|---|
| S-01 | 中 | `stores/useFillStore.ts:8-28` | 一个 store 同时管 **填报**（taskList/currentFillData）与 **审核**（auditList/auditDetail），职责混杂；`auditDetail: any` | 两个无关页面互相影响 loading；改动风险高 |
| S-02 | 中 | `stores/useTaskStore.ts:10-38` | 任务 / 分派 / 台账 **三套列表**挤在一个 store，`fetchList/fetchAssignList/fetchLedgerList` 三套独立 loading，但共享 `currentItem` | `Task/Ledger` 页面其实用的是 `useLedgerStore`，`useTaskStore.ledgerList` **全仓无人使用**（死状态） |
| S-03 | 中 | 全部 store 的 `fetchList`（如 `useUserStore.ts:34-43`） | `catch { set({loading:false}) }` —— **吞掉所有错误**（16 个 store 共 30 处静默 catch） | 列表加载失败时界面**空表 + 无任何提示**，用户以为"没数据"。这是"点了没反应"的最主要来源 |
| S-04 | 中 | `stores/useRoleStore.ts:55-58` + `Role/index.tsx:104-111` | `fetchRoleMenuIds(record.id)` 已经在 store 里 set 了 `roleMenuIds`，页面**又直接 import API 再请求一次**并 `setCheckedMenuKeys(res.data.data)` | 一次点击发 2 个相同请求；`roleMenuIds` 状态实际上没被用到（重复状态） |
| S-05 | 中 | `stores/useAuthStore.ts:76-89` + `App.tsx:66-68` | `initialize()` 只在挂载时从 localStorage **读取一次**，之后不刷新；`menus/permissions` 与后端永久不同步 | 管理员改了菜单/角色后，用户**必须重新登录**才能生效 |
| S-06 | 轻 | 9 个 store 的 `currentItem` + `setCurrentItem` | 22 处 `setCurrentItem` 被解构出来但**从未调用**（`Role/User/Menu/Dept/Project/Task/Datasource` 页面） | 死代码 + 死状态 |
| S-07 | 轻 | `stores/useFileStore.ts:34` `clear()` | `clear: () => set({ files: [], taskId: undefined })` 未清 `loading` | 理论上可残留 true（实际很少触发） |
| S-08 | 轻 | `stores/useTodoStore.ts:20` | `create<TodoState>((set, get) => ...)` 中 `get` 在 `fetchSummary` 未用，但 `useUserStore`/`useRoleStore` 等用 `(set)` 签名不一致 | 风格不统一，轻微 |

**没有发现内存泄漏**：2 个 `setInterval`（`MainLayout.tsx:37`、`OnlineUser/index.tsx:40`）都有 `clearInterval` 清理；zustand store 是模块级单例，不随组件卸载丢失（这是设计，不是泄漏）。

### 3.2 登录态失效处理 —— 🟡 中（有统一跳转，但实现有坑）

**现状**：`utils/request.ts:94-113` 是唯一的统一出口：

```ts
if (res.code === 401 || res.code === 403) { localStorage.clear(); window.location.href = '/login'; }
if (error.response?.status === 401) { localStorage.clear(); window.location.href = '/login'; }
```

| # | 级别 | 问题 | 影响 | 修复 |
|---|---|---|---|---|
| S-09 | **严重** | **403 被当成登录失效**（`request.ts:96`） | 后端对"角色不够"返回 403（如 AUDIT_USER 打开 `Template/Wizard`，它内部调 `/datasource/page` 仅超管；`TemplateAuthModal` 非超管调 `/user/page`）。**结果是：用户只是打开一个无权限的下拉，就被强制退出登录** | 403 只提示"无权限"，**不跳登录、不清 token** |
| S-10 | 中 | `localStorage.clear()` 全量清空（`request.ts:97,111`、`UserCenter:47`） | 清掉了**同域其他应用**的所有键（同域部署时会互相踢） | 改调 `clearAuth()`（只清 4 个键），且同步重置 zustand 内存态 |
| S-11 | 中 | `window.location.href = '/login'` 硬整页跳转（2 处） | 丢失当前页面与未保存表单；若部署在子路径（如 `/report/`）会跳到错误地址 | `navigate('/login', {state:{from}})` + 内存态清理；或环境变量 `import.meta.env.BASE_URL` 拼接 |
| S-12 | 中 | **登录成功后不刷新用户信息** | `useAuthStore.fetchCurrentUser()` **全仓 0 引用**；登录只信 login 响应里的 userInfo | 登录后 `menu/permissions` 若被管理员改动，客户端永不更新（配合 S-05） |
| S-13 | 轻 | 业务错误分支 `res.code===401` 触发跳转，但**后端 401 走的是 HTTP 状态码**（SecurityConfig `HttpStatusEntryPoint`），业务分支基本走不到 | 冗余代码 | 合并两支 |
| S-14 | 轻 | 没有 401 防抖 | 页面并发 5 个请求同时 401 → 5 次 `location.href` 赋值 | 加 `let redirecting` 标志 |

### 3.3 localStorage 缓存的 menus/permissions 与后端不一致时的兜底 —— 🔴 严重（无兜底）

| # | 级别 | 问题 |
|---|---|---|
| S-15 | **严重** | **前端菜单/权限完全取自 localStorage 快照，从不校验后端**。`useAuthStore.initialize()` 只读缓存；`MainLayout` 直接把 `menus` 交给 `SiderMenu` 渲染。管理员删除菜单/收回角色后，只要用户不重新登录，**旧菜单一直显示**；点击后要么 403 被踢（S-09），要么"点了没反应" |
| S-16 | 中 | `JSON.parse` 无 try/catch（`useAuthStore.ts:84-86`） | localStorage 被手工改坏 → 应用**白屏崩溃** |
| S-17 | 中 | menus/permissions 可被用户在 DevTools 任意篡改，前端据此渲染菜单 | 配合 §1.3「按钮不隐藏」，用户在本地可以"看见"任何页面（但后端会拦） |

**修复建议**（关键代码）：
```ts
// src/stores/useAuthStore.ts
initialize: () => {
  const token = getToken();
  if (!token) return;
  const safe = <T,>(k: string, fallback: T): T => {
    try { const v = localStorage.getItem(k); return v ? JSON.parse(v) : fallback; } catch { return fallback; }
  };
  set({ token, userInfo: safe('userInfo', null), permissions: safe('permissions', []), menus: safe('menus', []) });
},
// 启动时异步校验一次，与缓存合并（缓存只用于"首屏不闪"）
syncFromServer: async () => {
  const res = await getCurrentUserApi();          // 已有接口 /auth/current-user
  set({ userInfo: res.data.data });
  const menuRes = await request.get('/auth/menu'); // 后端已有 GET /auth/menu
  set({ menus: menuRes.data.data });
},
```
```ts
// src/utils/request.ts —— 403 不踢人
if (res.code === 403) { return Promise.reject(new Error(res.msg || '无权限执行该操作')); }
if (res.code === 401) { redirectToLogin(); return Promise.reject(new Error(res.msg)); }
```

---

## 4. 错误处理与用户体验

### 4.1 接口报错提示是否友好 —— 🔴 严重（大多数场景"没有提示"）

**后端已做脱敏**：`Result.java:36-70` 的 `sanitizeMessage()` 会把 SQL 异常/驱动信息替换为"操作失败：数据校验不通过或数据库约束冲突"。
**前端问题分三层**：

| # | 级别 | 位置 | 问题 |
|---|---|---|---|
| E-01 | **严重** | 16 个 store 共 **30 处** `catch { set(...) }` | 列表/详情加载失败**完全静默**，界面呈现"空表"或"加载完成但没数据" |
| E-02 | **严重** | **9 处** `catch` 内只有空/注释：`Role/index.tsx:106`、`User/index.tsx:147`、`Task/Assign/index.tsx:33`、`Task/Formulation/index.tsx:32`、`Template/Add/index.tsx:117`、`useOverviewStore.ts:38,47`、`utils/download.ts:48`、`utils/request.ts:27` | 同类问题 |
| E-03 | **严重** | 大量 `catch { msg.error('删除失败') }` 式**硬编码文案**（`Role:77`、`User:98`、`Task/Formulation:85,95,105`、`Template/List:51,61`、`Datasource:79`、`DataApply/Apply:66`、`Approve:64,77`、`Fill/Audit:84`、`Fill/Task:61`、`Menu:61`、`Dept:123`、`Project:149`） | **把后端返回的精确原因（"该角色已关联用户，不能删除"/"用户名已存在"）完全丢弃**，用户只看到"删除失败"，无法自助排查 |
| E-04 | 中 | `request.ts:100` `Promise.reject(new Error(res.msg))` | 只抛 `message`，丢掉 `code/response`，页面无法区分 401/403/业务错 |
| E-05 | 中 | `utils/download.ts:39-55` | 已正确识别"HTTP 200 + JSON blob"的伪装错误 ✅ 做得好，可作为其他地方的模板 |

**修复建议（全局统一抽取）**：
```ts
// src/utils/error.ts —— 新增
import { isAxiosError } from 'axios';
export function errMsg(e: unknown, fallback = '操作失败'): string {
  if (isAxiosError(e)) {
    const d = e.response?.data as { msg?: string } | undefined;
    if (e.response?.status === 401) return '登录已过期，请重新登录';
    if (e.response?.status === 403) return '无权限执行该操作';
    if (e.code === 'ECONNABORTED') return '请求超时，请稍后重试';
    return d?.msg || fallback;         // ← 透传后端已脱敏的 msg
  }
  return (e as Error)?.message || fallback;
}
```
```ts
// store 里不要再吞异常，改为向上抛 + 页面统一提示
fetchList: async (params) => {
  set({ loading: true });
  try { /* ... */ } finally { set({ loading: false }); }   // 不 catch
},
```
```ts
// 页面
try { await remove(id); msg.success('删除成功'); doFetch(); }
catch (e) { msg.error(errMsg(e, '删除失败')); }            // ← 带上后端原因
```

### 4.2 loading 状态遗漏 → 重复提交 —— 🟡 中

| # | 级别 | 位置 | 问题 |
|---|---|---|---|
| E-06 | 中 | **所有** 22 个页面的 `<Modal onOk={handleModalOk}>`（如 `Role:214`、`User:247`、`Task/Formulation:225`、`Template/List`、`Datasource:200`） | Modal 的 OK 按钮 **没有 `okButtonProps={{loading}}`**，`handleModalOk` 是 async，用户**双击就会发两次 POST**（新增两个同名角色/两个任务） |
| E-07 | 中 | `Task/Assign:169`、`DeptAudit:142`、`TaskAudit:142`、`Correct:155,166`、`Todo:157` | 同上 |
| E-08 | 轻 | `Fill/Task/index.tsx:143-144` | 「保存草稿」与「提交数据」**共用同一个 `submitting`**，但草稿保存后不关弹窗也不提示状态，用户会以为没点 |
| E-09 | 轻 | `Datasource/List:83-101` | `handleModalOk` 里 `update/create` 无 loading，且「测试连接」按钮与「确定」可同时点 |
| E-10 | 轻 | `Login/index.tsx:241` | ✅ 有 `loading={loading}` —— 正确示范 |

**修复**：
```tsx
const [submitting, setSubmitting] = useState(false);
const handleModalOk = async () => {
  setSubmitting(true);
  try { /* ... */ } finally { setSubmitting(false); }
};
<Modal okButtonProps={{ loading: submitting }} confirmLoading={submitting} ... />
```

### 4.3 表单校验是否充分 —— 🟡 中

| # | 级别 | 位置 | 缺失校验 |
|---|---|---|---|
| E-11 | 中 | `Login/index.tsx` | 用户名/密码/验证码只有 required，无长度、无验证码 4 位校验 |
| E-12 | 中 | `UserCenter/index.tsx:26` | 前端只校验"≥6 位"，后端 `PasswordPolicy` 有复杂度要求 → 用户填 6 位简单密码被后端拒，提示不明 |
| E-13 | 中 | `System/User/index.tsx:267` | 手机号无格式校验；`gender` 字段后端根本不存在（F-07） |
| E-14 | **严重** | `System/User/index.tsx:259-263` | 新增用户时密码 **required 但没有复杂度规则**，后端 `PasswordPolicy.check` 拒绝后，页面 `catch` 里 `msg.error(error?.message)` 能显示——但因为是 `res.code=500` 走 `Promise.reject(new Error(res.msg))`，**能显示**，勉强可接受 |
| E-15 | 中 | `Task/Formulation/index.tsx:250-255` | 开始/结束日期**无先后顺序校验**（可以结束早于开始），`toTaskPayload` 直接转 `fillDeadline` |
| E-16 | 中 | `Task/Formulation` 表单 | `period`（期数）自由文本，无选项约束；`year` 与 `period` 拼成 `belongTime="2026-Q1"`，后端按 `belongTime` **精确匹配**查询（`RptTaskService:eq(belongTime)`）→ 格式不统一会查不到 |
| E-17 | 中 | `Datasource/List:217-222` | host 无 IP/域名格式校验；port 有 min/max ✅ |
| E-18 | 中 | `DataRange/index.tsx:243` | SQL 只做 required，**无"仅允许 SELECT"的前端预检**（后端有，但用户要等一次失败） |
| E-19 | 轻 | `Template/Wizard` | ✅ 校验最完整（编码正则、重复检测、物化前置检查），可作为标准 |
| E-20 | 轻 | `System/Menu:216-218` | 路径无格式校验（可填任意字符串，导致菜单点了没反应，见 §7.2） |

### 4.4 "点了没反应"的路径清单 —— 🔴 严重（共 11 条）

| # | 页面 | 操作 | 表现 | 根因 |
|---|---|---|---|---|
| R-01 | `Fill/Task` | 点「填报」 | 无反应/报"获取填报详情失败" | `assignId` 不存在 → `/fill/task/undefined`（F-03） |
| R-02 | `Fill/Task` | 点「保存草稿」「提交数据」 | 失败但提示"操作失败" | body 格式错（F-03） |
| R-03 | `Task/Assign` | 点「确定」分派 | "分派失败"，无原因 | 缺 `assignType` → 400（F-01） |
| R-04 | `Task/Formulation` | 搜索任务名称 | 列表不变 | 参数名 `name` vs `taskName`（F-08） |
| R-05 | `System/Role` | 分配菜单权限 → 确定 | **提示"权限分配成功"但实际没保存** | `menuIds` 没发（F-05） |
| R-06 | `System/User` | 分配角色 → 确定 | 静默失败 | body 应为裸数组（F-04） |
| R-07 | `UserCenter` | 修改密码 | 永远"原密码错误" | RSA+@RequestParam（F-02） |
| R-08 | `Template/Add` | 上传 Excel / 完成创建 | 失败或创建出无法使用的模板 | `Date.now()` 假 ID（F-15） |
| R-09 | `Template/Detail` | 点「编辑模板」 | 跳到 Wizard，改完保存 | 走的是 `updateTemplateApi` + `saveTemplateFieldsApi` ✅ 可用；但「版本」显示 `vundefined` |
| R-10 | `Datasource/List` | 点「测试连接」 | 恒"连接失败" | 字段名 `host/databaseName` vs `ip/dbName`（F-06） |
| R-11 | `DataApply/Apply` | 新增申请 → 确定 | 创建失败 | 后端要求 `taskId`，前端不传（F-10） |
| R-12 | `Todo` | 「发起催办」填任务 ID | 无反应（如无权限） | 403 被当登录失效 → 直接踢回登录页（S-09） |
| R-13 | `System/OnlineUser` | 「踢下线」 | 弹窗一闪，未踢 | `Popconfirm` 里 `setKickTarget` 后立刻 `form.resetFields()`，而 Modal 的 `open={!!kickTarget}` 与 Popconfirm 的确认气泡**叠加**；见 §4.5 |
| R-14 | `File/List` | 「选择多个分卷上传」 | 每组都触发一次 | `beforeUpload={() => false}` 后 `onChange` **每选一个文件就触发一次** `uploadVolumes(files)`，多选 N 个文件会重复上传 N 组 |
| R-15 | `File/List` | 「打包下载」 | 下载失败无原因（有 `e?.message` ✅ 部分可接受） | 后端 `/{groupKey}/download` 正常 |

### 4.5 其他 UI 反模式

| 位置 | 问题 |
|---|---|
| `DataApply/Apply/index.tsx:118-126` | 编辑按钮**渲染两次**（`status===0` 一个可点、`status!==0` 一个 disabled），可点时不 disabled → 两列编辑 |
| `DataApply/Apply/index.tsx:139-141`、`Approve:147-149` | 状态筛选用 **`<Input placeholder="0-待审批 1-通过">`** 而不是 Select |
| `System/Role/index.tsx:173-184` | 搜索表单字段名 `name/code` 与后端 `roleName` 不匹配（后端 `RoleController:38` 只认 `roleName`）→ 搜索无效 |
| `System/User/index.tsx:207-209` | 搜索字段 `nickname`，后端 `UserController:25` 只认 `username/deptId/status` → 昵称搜索无效；且后端用 `removeIf` 在内存里过滤 status，**分页总数会错** |
| `Datasource/List/index.tsx:159-171` | 搜索字段 `name/type/host`，后端只认 `dsName/dsType` → 全部无效 |
| `Task/Assign/index.tsx:135-140` | 搜索字段 `taskName/deptName`，后端 `CompatController.assignPage` 只认 `taskId/assignType` → 全部无效 |
| `Fill/Audit/index.tsx:225-237` | 搜索字段 `taskName/deptName/status`，后端 `/audit/myTasks` 只认 `auditStatus` → **全部无效**（且 `auditType` 也只是前端状态，后端同一接口不区分通道） |
| `Todo/index.tsx:132-140` | Tabs 的 `items` 被 `map` 掉了 `children`，`children: null` 是死数据 |
| `Correct/index.tsx:41-43` | `useEffect(() => {}, [])` 空实现 + 注释"等用户输入任务ID"，是死代码 |
| `Overview/index.tsx:24-27,50-56,75-78,98-106` | **生产环境展示假数据**：无数据时兜底成 `发改局/财政局…`、`{totalTasks:48, auditPassRate:85.5}`。用户无法分辨真假 |
| `Template/Wizard/index.tsx:365` | 文案"未物化（可在模板列表中随时物化）"，但**模板列表没有物化入口**（只有发布/删除）→ 成了谎话 |
| `PageLoading.tsx:13` | 运行时出现 antd 5 警告：`Spin tip` 在非嵌套场景无效（tip 在 antd 5 要求包在内容里），实际不显示"加载中"文字 |
| `Login/index.tsx:3` | 同时 `import { message }` 静态方法与组件内 `App.useApp()` —— 本项目 17 个页面都这么写，静态 `message` **在 `ConfigProvider` 主题外**，会出现主题不一致警告 |

---

## 5. 性能

### 5.1 大依赖全量引入 —— 🔴 严重（echarts），antd ✅ 正常

| 依赖 | 引入方式 | 体积 | 结论 |
|---|---|---|---|
| **echarts** | `Overview/index.tsx:7` `import ReactEChartsCore from 'echarts-for-react'` —— 名字叫 Core，**但没有配 `echarts/core` + `use([...])` 按需注册**，`echarts-for-react` 默认 import 整个 `echarts` | **1,033.3 KB** | 🔴 **全量**。只用了 pie/bar/line 三种图，按需引入可降到约 150-200KB |
| antd | `import { Table, Button, ... } from 'antd'` | 1416.7 KB（主 chunk，含公共层） | ✅ Vite tree-shake 生效 |
| dayjs | 22 个文件 `import dayjs` | 公共 chunk | ✅ 仅 core，未引入 locale/plugin |
| @ant-design/icons | `SiderMenu.tsx:4` **`import * as Icons from '@ant-design/icons'`** | 主 chunk 内含 836 个图标导出 | 🔴 **全量引入图标包**（为了动态取图标）。改为白名单映射表可省几百 KB |
| jsencrypt | 仅 `UserCenter` 一处 | 约 40KB | 🟡 且该功能本来就是坏的——**删掉可省 40KB** |
| bcryptjs | **0 引用** | 0（tree-shake 掉了） | 🟡 从 package.json 移除 |

### 5.2 打包体积分析（实测 dist）

```
总 JS：68 个文件，3,461.8 KB；CSS 仅 1.5 KB
├─ index-BdxASciG.js      1,416.7 KB  ← 全局 chunk（react-dom + router + antd 公共层 + 全量图标）
├─ index-C4cpMVvc.js      1,033.3 KB  ← echarts 全量
├─ Table-CY8kIjiP.js        183.4 KB
├─ index-NLHUW0A9.js        109.2 KB  （dayjs + icons 补充）
├─ index-DGD_mCvi.js         81.2 KB  （icons 补充）
└─ 其余 63 个               ~638 KB
```
✅ **已核验**：全部大 chunk **均为压缩产物**（无 sourcemap、无未压缩源码残留）。
🔴 **可优化点**：
1. **没有 gzip/brotli 预压缩产物**（dist 下 0 个 .gz/.br），nginx 若未开 `gzip_static`，实际传输 1.4MB+1MB。开 brotli 后主 chunk 约 320KB、echarts 约 320KB。
2. **antd 未做 manualChunks 拆分**：1.4MB 主 chunk 里混着 react-dom、router、antd，首屏必须全部下载。
3. **Overview 页在首屏路由 `/overview`**，echarts 1MB 是首屏强依赖（虽是 lazy，但 `/` 就重定向到 `/overview`）→ 首屏必然加载 1MB。
4. `index.html` 引用了 **`/vite.svg`，但 dist 里不存在**（404，无害但脏）。

### 5.3 不必要的重渲染 / useEffect 依赖 —— 🟡 中

| # | 级别 | 位置 | 问题 |
|---|---|---|---|
| P-01 | 中 | `App.tsx:64`、`MainLayout.tsx:24`、`Login/index.tsx:10` | **`const { token, initialize, ... } = useAuthStore()` 整对象订阅**（无 selector）→ store 任何字段变化都触发重渲染；MainLayout 里还会因为 `todoStore.summary` 每 60s 刷新而连带重渲染整个 Layout+侧边栏 |
| P-02 | 中 | **23/24 个列表页**（除 Ledger） | `const columns: ColumnsType<T> = [...]` 定义在组件体内，**每次渲染重建列定义对象**，antd Table 会认为 columns 变了 → 重新计算列布局。合计 24 处，只有 `Template/Wizard` 用了 `useMemo` |
| P-03 | 中 | `DeptAudit/index.tsx:45`、`TaskAudit:44`、`Todo:36`、`Dict:55`、`DataRange:56`、`Ledger:30`、`OperLog:37`、`Trajectory:47`、`OnlineUser:42` | **`// eslint-disable-next-line react-hooks/exhaustive-deps` 共 9 处** + 空依赖数组，故意绕开依赖检查 → `load/page` 闭包陈旧风险 |
| P-04 | 中 | `OperLog/index.tsx:19-33` | `useCallback` 依赖 `[page, pageSize, params, msg]`，但 `load` 内部又**只读不写**这些 → `load` 每次渲染都重建；且 `useEffect` 空依赖，`load` 稳定引用无效 |
| P-05 | 轻 | `OnlineUser/index.tsx:21-43` | `load` 依赖 `[keyword, msg]`，但 `useEffect` 用空依赖调用 `load('')` → 每次输入框变化都会重建 `load`，但 interval 里调用的是**首次闭包的 `load`**（keyword 恒为 ''）→ 轮询**永远不带搜索词**（功能 bug） |
| P-06 | 轻 | `MainLayout.tsx:31-42` | 每 60s 轮询 `/todo/summary`（✅ 已清理定时器），但**没有做页面可见性判断**（`document.hidden` 时仍轮询），后台标签页持续消耗 |
| P-07 | 轻 | `Overview/index.tsx:19,47,72` | 3 个 `useMemo` ✅ 正确；但 `chartData/trendData` 为空时生成同一个假数据对象，`useMemo` 命中率无意义 |

### 5.4 内存泄漏 —— ✅ 已检查，未发现

- `setInterval`：2 处，**均有 clearInterval**（`MainLayout:37-41`、`OnlineUser:40-41`）✅
- `setTimeout`：**0 处**
- `addEventListener`：1 处（`request.ts:36` pagehide），未 remove —— 但它是**模块级全局单例，生命周期 = 页面**，不构成泄漏 ✅
- `AbortController`：`request.ts` 的在途请求登记表有 `add/delete/abortAll` 闭环 ✅（`releaseController` 在成功/失败/取消三支都调用）
- echarts 实例：`echarts-for-react` 自动 dispose ✅

---

## 6. 代码质量

### 6.1 `any` 类型滥用 —— 🟡 中（186 次）

**分布（Top 12）**：

| 文件 | any | as any |
|---|---|---|
| `services/api.ts` | 30 | 0 |
| `pages/Template/Add/index.tsx` | 19 | 1 |
| `pages/Template/Wizard/index.tsx` | 11 | 0 |
| `pages/System/User/index.tsx` | 8 | 0 |
| `pages/DataRange/index.tsx` | 7 | 0 |
| `pages/File/List/index.tsx` | 7 | 2 |
| `pages/Fill/Audit/index.tsx` | 6 | 1 |
| `pages/System/Role/index.tsx` | 6 | 0 |
| `pages/Task/Formulation/index.tsx` | 5 | 0 |
| `pages/Datasource/List/index.tsx` | 5 | 0 |
| `pages/System/Menu` / `StatisticsRule` / `Task/Assign` / `Template/List` / `Todo` / `Task/Ledger` / `OperLog` | 各 4 | — |

**典型反模式**：
```ts
// request.ts:22, 49, 64, 74 —— 全局拦截器里 4 处 any
const p: Record<string, any> = { ...config.params };
(config as any).__abortController = controller;

// api.ts:590, 785, 816, 863 ... —— 归一化层用 AnyRec = Record<string, any>
type AnyRec = Record<string, any>;

// Template/Add:24,25,91-93,154 —— 表格 render 参数全 any
render: (_: any, __: any, index: number) => ...
const handleFieldChange = (index: number, key: string, value: any) => { (newFields[index] as any)[key] = value; };
```
**影响**：字段名打错（本项目正是 F-01~F-14 的温床）编译器完全无感。

**修复建议**：
- `AnyRec` 换成 `Record<string, unknown>` + 收窄函数；
- `Template/Add` 的 `TemplateField` 补齐 `fieldCode/dbColumn` 等（目前是旧模型），或用 `Partial<Record<keyof TemplateField, unknown>>`；
- 打开 `noUnusedLocals/noUnusedParameters`（见 6.5）。

### 6.2 重复代码 —— 🟡 中

| 重复模式 | 出现次数 | 示例 |
|---|---|---|
| `doFetch = useCallback((params={}) => fetchList({current:1,size:10,...params}), [fetchList])` | **14 个页面** | Role/User/Task/Formulation/Task/Assign/Datasource/Template/List/DataApply×2/Fill×2/Dept/Project |
| `handleSearch → setSearchParams(values); doFetch(values)` | **14 处**，几乎逐字相同 | 同上 |
| `handleReset → resetFields(); setSearchParams({}); doFetch()` | **14 处** | 同上 |
| `handleModalOk`（validateFields → create/update → success → close → refetch） | **11 份**，仅字段名不同 | Role:81, User:112, Task/Formulation:109, Datasource:83, Template/List, DataApply/Apply:70, Dept:127, Project:153, Menu:65, Dict:71, StatisticsRule:56 |
| `handleDelete`（try remove → success → refetch / catch error） | **10 份** | Role:71, User:92, Task:79, Datasource:73, Template/List:45, DataApply:60, Dept:111, Project:136, Dict:89, DataRange:92 |
| `statusMap: Record<number,{color,text}>` | **9 份**，内容高度重叠（0/1/2 语义反复定义） | Task/Formulation、Task/Assign、Fill/Task、Fill/Audit、DataApply×2、Template/List、Template/Detail、Dict |
| `auditTag(v)` 组件 | **2 份完全相同** | DeptAudit:15-20 = TaskAudit:15-20 |
| `flattenTree` | **3 份**（Menu:86、Dept:29、User:34 的 deptOptions） | |
| `buildTreeSelectData` | **2 份**（Menu:100、Dept:54） | |
| 双通道审核页 95% 相同 | DeptAudit(159行) vs TaskAudit(159行) | 仅按钮 disabled 条件与调用 API 不同 |
| `ColumnDef` + CRUD Modal | 每页 1 份 | — |

**修复建议**：抽 `src/hooks/useCrudTable.ts` + `src/components/CrudPage.tsx` + `src/constants/status.ts`：
```ts
// src/hooks/useCrudTable.ts
export function useCrudTable<T>(fetcher: (p: PageParams) => Promise<void>, defaults = { current: 1, size: 10 }) {
  const [searchParams, setSearchParams] = useState<Record<string, unknown>>({});
  const doFetch = useCallback((p = {}) => fetcher({ ...defaults, ...p }), [fetcher]);
  const onSearch = (v: Record<string, unknown>) => { setSearchParams(v); doFetch(v); };
  const onReset = (form: FormInstance) => { form.resetFields(); setSearchParams({}); doFetch(); };
  return { searchParams, doFetch, onSearch, onReset };
}
```

### 6.3 硬编码 —— 🟡 中

| 类别 | 数量 | 位置 |
|---|---|---|
| 内联 `style={{...}}` | **282 处** | 全页面，样式完全内联，无 CSS Module |
| 十六进制颜色字面量 | **89 处** | 如 `#1677ff` 重复出现 20+ 次，`#52c41a`、`#ff4d4f`、`#f6f8fa` 各出现 5-10 次 |
| 路由字符串字面量 | **~60 处** | `navigate('/template/list')`、`navigate('/audit/trajectory?taskId=')` 等散落在 15 个页面，无 `ROUTES` 常量 |
| 业务文案 | 全部 | 无 i18n，中文硬编码在 JSX（可接受，但改文案要动代码） |
| 尺寸魔法数字 | 大量 | `width: 240`、`scroll={{x:1400}}`、`padding:24` |
| API 基址 | `request.ts:4` `baseURL: '/api'` | ✅ 与后端 `context-path: /api` 一致 |

**修复建议**：颜色走 `antd` 的 `theme.useToken()`；路由抽 `src/router/paths.ts`；`App.tsx` 的 28 个 `<Route>` 用 `ROUTES` 生成。

### 6.4 未使用的导入/变量 & 死代码 —— 🟡 中

**未使用导出（12 个）**：
| 符号 | 文件 |
|---|---|
| `withAuth` | `components/AuthGuard.tsx:21` |
| `AuthGuard`（default export） | `components/AuthGuard.tsx:35`（App.tsx 未引用） |
| `getTemplateFieldsApi` | `services/api.ts:700` |
| `deployTemplateApi` | `services/api.ts:754` |
| `submitTemplateApi` | `services/api.ts:769` |
| `downloadTemplateApi` | `services/api.ts:758` |
| `getDataRangeApi` | `services/api.ts:1361` |
| `submitQualityApi` / `getQualityApi` / `getChannelStatusApi` | `services/api.ts:1535/1539/1543`（后端有接口，前端无页面） |
| `hasAnyPermission` / `hasAllPermissions` / `getCurrentUser` | `utils/auth.ts:18,25,32` |
| `md5Hex` | `utils/md5.ts:133` |

**未使用导入（12 处）**：
```
components/SiderMenu.tsx        -> MenuItem（type 未用）
components/TemplateAuthModal.tsx -> Space
pages/Audit/Correct/index.tsx    -> InputNumber
pages/Audit/DeptAudit/index.tsx  -> InputNumber
pages/Audit/TaskAudit/index.tsx  -> InputNumber
pages/Audit/Trajectory/index.tsx -> InputNumber
pages/DataRange/index.tsx        -> InputNumber
pages/Org/Dept/index.tsx         -> Switch
pages/Template/Add/index.tsx     -> Space
pages/Template/Detail/index.tsx  -> Select
pages/Template/List/index.tsx    -> Switch
pages/Template/Wizard/index.tsx  -> type TemplateSaveField
```

**未使用解构变量**：`setCurrentItem`（22 处）、`setCurrentApply`、`roleMenuIds`、`perspective`、`SubmitParams`…

**其他死代码**：`Correct/index.tsx:41-43` 空 useEffect、`Todo:110-114` 的 `children: null`、`useTaskStore.ledgerList` 整组、`Template/Add` 整个页面（F-15）、`AuthGuard` 组件。

### 6.5 TypeScript 绕过类型检查的写法 —— 🟡 中（项目级）

| 检查项 | 结果 |
|---|---|
| `@ts-ignore` / `@ts-expect-error` | **0 处** ✅ |
| `as any` | **12 处** |
| `as unknown as` | **16 处**（api.ts 归一化 13 处） |
| `<any>` 泛型旧语法 | 0 |
| `!` 非空断言 | 多处使用（如 `selectedUser!.id`、`kickTarget.sessionId!`、`steps[current].content`）——合理但未防护 |
| **`noUnusedLocals: false`** | `tsconfig.json` 关闭 → 6.4 的 12 处死导入无人发现 |
| **`noUnusedParameters: false`** | 关闭 |
| `strict: true` | ✅ 开启（这点不错） |
| `skipLibCheck: true` | 常见做法，可接受 |
| `tsc -b` 在 build 里 | ✅ `build: "tsc -b && vite build"`，类型错会阻断构建 |

### 6.6 `services/api.ts` 结构问题 —— 中

1. **1545 行单文件**承载全部接口、全部类型、全部归一化。应拆 `api/auth.ts / api/task.ts / ...`。
2. **归一化散落且不对称**：`normalizeTaskRow` 只处理"读"，`toTaskPayload` 只处理"任务写"；模板有 `normalizeTemplate` 但详情页仍读 `version`；数据源有读归一化但写没有。**建议统一 `adapters/` 层，读/写成对实现**。
3. `normalizeTaskRow` 用 `belongTime.split('-')` 猜 `period`：`belongTime="2026"` 时 `period` 为 `undefined` → `''`；`belongTime="2026-Q1"` → `period="Q1"`；而 `toTaskPayload` 又会拼回 `2026-Q1`。**若用户在期数里填了 `2026-Q1` 就会变成 `2026-2026-Q1`**（无长度/格式约束，E-16）。
4. `testDatasourceConnectionApi`（api.ts:814-823）里读 `d.ip / d.dbName ?? d.database`，但 `DatasourceFormParams` 定义的是 `host / databaseName` —— **类型与实现自相矛盾**（因为参数被 `as unknown as Record<string, any>` 抹掉了）。

### 6.7 其他代码质量点

- `Template/Wizard/index.tsx:41-42` 用 `Date.now()+seq` 生成 key ✅ 无碰撞问题。
- `utils/md5.ts` 自实现增量 MD5（148 行），**正确性风险**：未与标准实现对比测试，建议加单测；且**大文件计算 MD5 会阻塞主线程**（2MB 分片 `arrayBuffer()` 逐片，无 Worker）→ 500MB 文件会卡死 UI 数秒。
- `ChunkUpload.tsx:42` `chunkSize = 512*1024`，但 `application.yml` 已把 `max-file-size` 提到 50MB → 现在完全可以调到 5MB，**分片数量减少 10 倍**（当前配置过度保守，上传 1GB 要 2000 次请求）。

---

## 7. 路由与菜单

### 7.1 路由配置 vs 后端菜单对应关系

App.tsx 28 条路由 vs 后端种子菜单（`DataInitializer.java:66-99` + `report_2.0_upgrade4_audit_menus.sql:14-42`）：

| 后端菜单 path | App.tsx 路由 | 对应 |
|---|---|---|
| ✅ `/overview` `/system/user` `/system/role` `/system/menu` `/org/dept` `/org/project` `/template/list` `/template/add` `/datasource`?? | — | 见下 |
| `/datasource`（DataInitializer id=11） | `datasource/list` | 🔴 **不一致**：后端 `/datasource`，前端 `/datasource/list` → 点侧边栏跳 `/datasource` → 命中 `*` → 重定向 `/` |
| `/task/formulation` `/task/assign` `/task/ledger` `/fill/task` `/fill/audit` `/dataApply/apply` `/dataApply/approve` | ✅ | 一致 |
| `/system/log`（无种子菜单，但 App 有路由） | ✅ | 路由存在但菜单没有 → 只能手输 URL |
| `/audit/dept-audit` `/audit/task-audit` `/audit/trajectory` `/audit/correct` `/todo` `/file/list` `/system/dict` `/system/operLog` `/system/online` `/system/statisticsRule` `/dataRange` | ✅ | 一致 |
| — | `template/wizard`（无菜单） | 只能从 Template/List 的「向导新建」进入 |
| — | `template/detail/:id`、`userCenter` | 无菜单，合理 |
| `/datasource`（左列） | — | 🔴 见上 |

**结论**：**2 处路由断链**：①后端菜单 `/datasource` vs 前端 `/datasource/list`；②`fill/audit` 页面（旧版）与 `audit/dept-audit`（新版）**功能重复**，两套页面同时存在，`Fill/Audit` 的批量审核接口调用还是错的（F-12 + 见 §2.2）。

### 7.2 权限路由是否可能被直接 URL 访问绕过 —— 🔴 严重

| # | 问题 | 影响 |
|---|---|---|
| RT-01 | **所有已登录用户可直达任意路由**。`App.tsx:85` 只判断 `token` 存在，**没有任何权限/菜单校验**；`AuthGuard` 未使用（§1.3） | 普通用户手输 `/system/user` 就能打开"用户管理"页（数据由后端 403 拦住，但页面框架/操作按钮全部可见 → 配合 §4.1 的 `catch{msg.error('删除失败')}`，用户会看到一堆"删除失败"） |
| RT-02 | `SiderMenu` 只按 `menus` 渲染，但**路由不受 menus 限制** | 菜单隐藏 ≠ 路由不可达 |
| RT-03 | 前端有 `hasPermission`，但菜单项不按 `permission` 过滤（`SiderMenu.tsx:25` 只过滤 `visible` 和 `menuType==='F'`） | 只要后端给了菜单，前端就显示，不看 permission |
| RT-04 | 后端菜单 `visible` 语义 = 1 可见，`SiderMenu` 过滤 `m.visible !== 0` ✅ 一致 | — |

### 7.3 菜单图标全部失效 —— 🟡 中（已实测）

```tsx
// SiderMenu.tsx:14
const IconComp = (Icons as any)[iconName] || (Icons as any)[`${iconName}Outlined`];
```
后端种子菜单的 icon 值（`DataInitializer.java:66-99`）是**小写短名**：`dashboard/setting/apartment/database/file-text/schedule/audit/check-circle/user/team/menu/cluster/project/unordered-list/file-add/form/send/table/file-protect/carry-out/safety`（21 个）。
`upgrade4` 里的是 **PascalCase**：`AuditOutlined/BellOutlined/...`（11 个）。

**实测（node + 真实 @ant-design/icons 包）**：
```
解析成功: 11   解析失败: 21 -> dashboard, setting, apartment, database, file-text, schedule,
audit, check-circle, user, team, menu, cluster, project, unordered-list, file-add, form,
send, table, file-protect, carry-out, safety
```
→ **DataInitializer 建的 21 个菜单全部没有图标**，只有 upgrade4 补的 11 个正常。

**修复**：
```ts
// src/components/SiderMenu.tsx
const ICON_MAP: Record<string, React.ComponentType> = {
  dashboard: DashboardOutlined, setting: SettingOutlined, apartment: ApartmentOutlined,
  database: DatabaseOutlined, 'file-text': FileTextOutlined, schedule: ScheduleOutlined,
  audit: AuditOutlined, 'check-circle': CheckCircleOutlined, user: UserOutlined,
  team: TeamOutlined, menu: MenuOutlined, cluster: ClusterOutlined, project: ProjectOutlined,
  'unordered-list': UnorderedListOutlined, 'file-add': FileAddOutlined, form: FormOutlined,
  send: SendOutlined, table: TableOutlined, 'file-protect': FileProtectOutlined,
  'carry-out': CarryOutOutlined, safety: SafetyOutlined,
};
function getIcon(name?: string) {
  if (!name) return null;
  const C = ICON_MAP[name] ?? (Icons as Record<string, React.ComponentType>)[name];
  return C ? React.createElement(C) : null;
}
```
> 这一改同时**解决 §5.1 的 `import * as Icons` 全量引入问题**（改为具名导入 21 个图标）。

### 7.4 会话/断网统一处理

| # | 问题 | 修复 |
|---|---|---|
| RT-05 | `SiderMenu.tsx:58-61` 的 `selectedKeys` 计算逻辑：先取所有**一级** `item.key` 的 path 做 `startsWith` 匹配；三级菜单不在 `menuItems` 第一层，二级菜单（如 `/system/user`）能匹配 ✅，但 `/system` 目录项也会匹配 `/system/user` → **同时高亮"系统管理"和"用户管理"** | 只对 `menuType==='C'` 的项做匹配，且取最长匹配 |
| RT-06 | `findOpenKeys`（`SiderMenu.tsx:64-76`）逻辑有缺陷：`if (found.length > 0 \|\| parents.length > 0) return ...` —— 同层第一个不匹配的子项就会提前 return，**部分场景展开错误** | 用 antd `Menu` 的 `defaultOpenKeys` 时按扁平的 ancestors 计算 |
| RT-07 | `defaultOpenKeys` 是**非受控**的，折叠/展开后 `collapsed` 变化不会重置 | 用受控 `openKeys` |

---

## 8. 后端侧问题（前端无法单方面修复，需联动）

审计中顺带发现的 3 个后端缺陷（影响前端修复方案）：

1. `AuthController.java:168-170` —— `changePassword` 用 `@RequestParam` 且无 RSA 解密 → **要么改后端收 JSON，要么前端去掉加密**（推荐：后端改 `@RequestBody ChangePasswordDTO`）。
2. `TaskController.java:155` —— `@RequestParam String assignType` **缺 `required = false`**，导致前端漏传直接 400。建议后端补默认值 `defaultValue = "FILL"`。
3. `UserController.java:25-45` —— status 过滤在**内存里 `removeIf`**，分页 total 与列表不一致 → 前端翻页会看到空页。

---

## 9. 汇总

### 9.1 各级别问题分布

| 级别 | 数量 | 主要类别 |
|---|---|---|
| **严重** | **17** | 改密码全坏、任务分派必失败、Fill 模块全坏、角色授权假成功、用户角色接口错、403 踢登录、假数据（Overview 假报表 / Template/Add 假ID）、菜单权限无校验、错误全吞、echarts 全量、数据源写字段错、数据申请模块错、路由权限无守卫 |
| 中 | 28 | 字段不匹配 14 处、重复代码、any、表格重渲染、搜索参数无效 6 处、Modal 重复提交、localStorage 兜底缺失、图标全失效 |
| 轻 | 21 | 死代码/未使用导入、样式硬编码、vite.svg 404、Spin tip 告警、静态 message 主题 |

### 9.2 前端代码整体评级

> **C（合格线附近，可上线但存在 3 个"功能级不可用"的严重缺陷）**

- **安全性：B+** —— XSS 零风险、后端鉴权完备（70 处 `@PreAuthorize`）；但 token+权限全存 localStorage 且无兜底校验、无 CSP、前端权限完全没接线。
- **接口契约：D** —— 0 个 404 是最大亮点（CompatController 的功劳），但 **8 个模块的字段契约仍是错的**，任务模块的修复没有推广到其他模块，属于"打补丁式修复"。
- **状态管理：C** —— store 划分混乱（Fill/Task 三合一）、30 处静默 catch、无统一错误出口、菜单永不刷新。
- **错误处理/体验：D** —— 大量硬编码"删除失败"，9 处吞异常，22 个 Modal 无 loading，**至少 15 条"点了没反应"路径**，生产环境展示假数据。
- **性能：C-** —— echarts 全量 1MB + 图标全量 + 无 gzip 预压 + 23 页 columns 未 memo，首屏至少 1.4MB。
- **代码质量：C** —— 186 个 any、11 份重复 CRUD、1545 行 api.ts、12 处死导入、`noUnusedLocals` 关闭。
- **路由菜单：D+** —— 无权限守卫、2 处路由断链、21 个菜单图标全失效。

**亮点（应保留）**：
- ✅ `utils/download.ts` 处理"HTTP 200 + JSON 错误 blob"的设计很好；
- ✅ `services/api.ts` 的归一化思路（方向对，只是覆盖不全）；
- ✅ `Template/Wizard` 校验最完整（正则 + 重复检测 + 前置检查）可作为标准；
- ✅ `request.ts` 的 AbortController 在途请求管理与取消静默处理；
- ✅ `utils/md5.ts` 流式 MD5 避免整文件入内存；
- ✅ strict 模式开启、`tsc -b` 纳入 build；
- ✅ 后端鉴权做得扎实（不是前端的功劳但值得一提）。

### 9.3 最该修的 5 项（按 ROI 排序）

| 排序 | 问题 | 位置 | 为什么最优先 | 修复量 |
|---|---|---|---|---|
| **1** | **403 被当登录失效 → 用户被踢出登录** | `utils/request.ts:96-99,110-113` | **1 行代码级修复，影响所有用户所有页面**。当前只要打开一个无权限下拉（如审核人打开模板向导）就会被强制退出，这是最容易被用户感知的"系统坏了" | 极小（10 行） |
| **2** | **改密码 100% 不可用（RSA + @RequestParam）** | `UserCenter/index.tsx:8-13,32-44`；`AuthController.java:168` | **安全相关功能完全失效**，且用户认为是系统 bug。修复方式简单（去掉 JSEncrypt，或后端改 @RequestBody） | 小（前后端各 10 行） |
| **3** | **角色权限分配"假成功" + 用户角色分配失败** | `System/Role/index.tsx:118`；`services/api.ts:479` | **越权/权限错配是事故级**：管理员以为收回了菜单权限，实际全没生效。修复只需改两个请求体 | 小（2 处各 3 行） |
| **4** | **任务分派必失败（缺 assignType）** | `Task/Assign/index.tsx:79-82`；`services/api.ts:324-328` | 业务主流程（分派→填报）在第一步就断了，后续 Fill 模块也全坏（F-03）。这是"业务跑不通"的根源 | 小（补 assignType: 'FILL'） |
| **5** | **错误处理统一（30 处静默 catch + 9 处吞异常 + 硬编码失败文案）** | 16 个 store + 22 个页面的 catch | 上述 1-4 之所以"看起来是点了没反应"，**根因就是错误被吞**。修完这一项，其余问题的定位成本从"数小时"降到"一眼看到原因"，也让用户自助可解 | 中（新增 `utils/error.ts`，批量替换） |

> 紧随其后的第 6-8 项：**Fill 模块整体重写**（F-03，工时较大）、**echarts 按需引入 + gzip**（首屏降 800KB）、**路由级权限守卫**（接线 AuthGuard + hasPermission）。

---

## 附录 A：本次审计覆盖的文件清单（64 个，已在 `frontend2/src` 下全量核对）

**services(1)** api.ts
**stores(16)** useAudit/useAuth/useDataApply/useDatasource/useDept/useFile/useFill/useLedger/useMenu/useOverview/useProject/useRole/useTask/useTemplate/useTodo/useUser
**components(7)** AuthGuard/ChunkUpload/PageLoading/SiderMenu/TaskSelector/TemplateAuthModal
**layouts(1)** MainLayout
**utils(4)** auth/download/md5/request
**pages(25)** Login/Overview/UserCenter/System(User,Role,Menu,Log,Dict,OperLog,OnlineUser,StatisticsRule)/Org(Dept,Project)/Template(List,Add,Detail,Wizard)/Datasource/File/List/Task(Formulation,Assign,Ledger)/Fill(Task,Audit)/DataApply(Apply,Approve)/Audit(DeptAudit,TaskAudit,Trajectory,Correct)/Todo/DataRange
**根** App.tsx / main.tsx / vite-env.d.ts / vite.config.ts / package.json / tsconfig.json

## 附录 B：本次审计未覆盖 / 受限项

1. **无运行时验证**：未启动后端与浏览器，所有"必然失败"的结论均来自**源码静态推导**（参数签名、实体字段、Jackson 配置、SecurityConfig），逻辑链条已逐条给出。
2. **数据库真实数据未核**：菜单 id 空间、`rpt_*` 表实际列，依据 `schema.sql` / `report_2.0_upgrade*.sql` / `DataInitializer.java` 推断。
3. **打包体积为现有 dist 实测**，非重新构建；建议改动后重新 `npm run build` 复核。
