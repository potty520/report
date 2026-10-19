#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成代码审计总报告（HTML）"""
import os
import time

ROOT = r"E:\C++\report_src\report-master"
OUT = os.path.join(ROOT, "代码审计报告.html")

# 四维度评级
GRADES = [
    ("安全与鉴权", "C", "认证骨架合格（JWT 签名/过期/验证码一次性均正确），"
                       "但<b>数据归属校验系统性缺失</b>，26 个 Controller 几乎无一处校验「这条数据是不是我的」，"
                       "且存在 CORS 通配、SQL 注入通道、多处 fail-open", "bad"),
    ("业务逻辑", "C-", "主链路可跑通，「已完成」是死状态，分派可复活已关闭任务，"
                       "双通道审核两套字段互相打架，台账部门口径错误", "bad"),
    ("并发与事务", "D+", "<b>无任何锁/版本号/唯一约束</b>，关键操作全是 check-then-act；"
                        "定时任务多实例必重复；无测试覆盖", "worse"),
    ("数据一致性", "D", "删除无引用保护（已产生 55 条线上孤儿）、软硬删混用、"
                       "外键全缺、4 个关键唯一键全缺", "worse"),
    ("代码质量", "C+", "结构清晰可读，但重复代码多（121 处 catch 模板）、上帝类、"
                      "魔法数字 47 处、无 DTO、无测试", "mid"),
    ("前端", "C", "接口路径 0 个 404（契约基本对得上），但<b>4 个核心功能页面完全不可用</b>、"
                 "字段名 8 个模块错位、403 误判导致踢人", "bad"),
]

# 线上实测验证（我亲自验证的，可证伪）
VERIFIED = [
    ("CORS 任意源放行", "严重", "config/CorsConfig.java:11-16", "✅ 已实测确认",
     "curl 带 Origin: https://evil.example.com 返回<br>"
     "<code>Access-Control-Allow-Origin: https://evil.example.com</code><br>"
     "<code>Access-Control-Allow-Credentials: true</code>", "bad"),
    ("物化表导出无归属校验", "高", "FillController.java:218-240", "✅ 代码确认",
     "exportTaskData / queryTargetRows 只校验任务与模板存在，<b>完全不校验当前用户权限</b>。"
     "当前环境因模板未配数据源而未能实拉到数据，属<b>潜伏漏洞</b>，一旦配好数据源即可越权导出", "warn"),
    ("数据范围 SQL 黑名单可绕过", "高", "DataRangeService.java:39-41,136-146", "✅ 已实测确认",
     "黑名单字符串带尾随空格（<code>\"delete \"</code>），且用<b>大写 LIMIT</b> 可躲过包裹判断。"
     "实测 admin 写入 <code>select id,username,password from sys_user LIMIT 2</code> "
     "<b>保存成功且 user 调用 preview 时真的执行了</b>（因目标库凭据无效才失败）", "bad"),
    ("登出后 token 仍有效", "高", "JwtAuthFilter / AuthController", "❌ 实测否定",
     "实测：logout 后同一 token 访问返回 <b>HTTP 401「该会话已下线」</b>。"
     "代码中 login 用 <code>getSessionId(token)</code> 登记，与 token 的 jti 一致，链路自洽。"
     "<b>该问题在当前代码中不存在</b>", "ok"),
    ("会话表不可用时 fail-open", "中", "JwtAuthFilter.java:180-184", "⚠️ 代码确认",
     "sessionState 查询异常时 <code>return \"ONLINE\"</code>，"
     "表不可用/DB 抖动时被踢下线的 token 会复活。属防御性缺陷", "warn"),
    ("模板删除无引用校验", "严重", "RptTemplateService.java:264-269", "✅ 线上已发生",
     "生产库 55 条任务全部引用已删除的 template_id=2，"
     "这些任务的填报功能<b>完全不可用</b>（报「模板不存在」）", "bad"),
    ("分派接口缺 assignType 参数", "严重", "TaskController.java:155 + Task Assign/index.tsx:79", "⚠️ 代码确认",
     "前端只传 <code>{taskId, deptIds}</code>，后端 <code>@RequestParam String assignType</code> "
     "无 <code>required=false</code> → <b>必然 400</b>，前端 catch 吞成「分派失败」", "bad"),
    ("改密码功能不可用", "严重", "UserCenter/index.tsx:8-13 + AuthController.java:168", "⚠️ 代码确认",
     "前端 RSA 加密后提交，<b>后端无任何解密逻辑</b>（全仓 grep 无 PrivateKey/decrypt）→ "
     "改密永远报「原密码错误」", "bad"),
    ("填报模块前后端全错位", "严重", "Fill/Task/index.tsx:29 + FillController.java:92", "⚠️ 代码确认",
     "前端把 <code>record.assignId</code>（后端不返回）当 taskId → 请求 <code>/fill/task/undefined</code>；"
     "提交发 <code>{assignId, fields[]}</code>，后端要 <code>@RequestParam taskId + dataJson</code> → "
     "<b>填报保存/提交完全不可用</b>", "bad"),
    ("403 被当登录失效", "严重", "utils/request.ts:96-99", "⚠️ 代码确认",
     "对 <code>code===403</code> 执行 <code>localStorage.clear()</code> + 跳登录。"
     "打开一个无权限的下拉（如非超管访问 <code>/datasource/page</code>）就会<b>被踢出登录并清空全部本地状态</b>", "bad"),
]

# Top 问题清单
TOP_ISSUES = [
    ("1", "CORS 允许任意源携带凭证", "严重", "安全",
     "config/CorsConfig.java:11", "任意站点可跨域读写全部接口",
     "改为显式白名单（配置化），禁止 * + credentials"),
    ("2", "数据归属校验系统性缺失", "严重", "安全",
     "FillController:218/231、AuditController:50、LedgerController:25、FileController:103、CompatController:175-206",
     "任一登录用户可越权读写他人/他部门数据",
     "所有 by-id / by-taskId 接口补 AuthzService 归属校验"),
    ("3", "CompatController 三个端点零鉴权", "严重", "安全",
     "CompatController.java:175,185-206", "任意登录用户可读/改/删任何人的数据申请",
     "补 @PreAuthorize + 归属校验（PUT 禁止改 taskId）"),
    ("4", "数据范围 SQL 黑名单可绕过", "严重", "安全",
     "DataRangeService.java:39,136-146", "DEPT_ADMIN 可在目标库执行任意 SELECT，可拖库",
     "改词法白名单 + 强制参数化 + 只允许单表查询"),
    ("5", "模板/部门/用户删除无引用保护", "严重", "数据",
     "RptTemplateService:264、SysDeptService:116、SysUserService:210",
     "删除产生不可恢复的孤儿数据（线上已发生 55 条）",
     "删除前检查下游引用 + 改软删除"),
    ("6", "填报/审核无并发控制", "严重", "并发",
     "RptTaskDataService:61-141,195-240,640-693", "双击/重试产生重复数据，之后 TooManyResults 永久锁死",
     "加唯一键 + 状态流转改条件 UPDATE"),
    ("7", "周期任务重复生成", "严重", "并发",
     "RptTaskService:352-438 + TaskScheduleJob:39", "多实例每次各跑一遍，子任务成倍冗余",
     "唯一键 + 捕获 DuplicateKey + ShedLock"),
    ("8", "前端 4 个核心功能完全不可用", "严重", "前端",
     "Task/Assign:79、UserCenter:8、Fill/Task:29、Template/Add:37",
     "分派、改密、填报、模板新建都跑不通", "按后端契约修正字段与方法"),
    ("9", "403 误判踢人", "严重", "前端",
     "utils/request.ts:96-99", "打开无权限下拉即被登出，体验灾难",
     "403 只提示不跳转，仅 401 才登出"),
    ("10", "AuthzService fail-open", "高", "安全",
     "AuthzService.java:72,92,100", "无分派记录时全站可填可审，等于没有兜底",
     "改 fail-closed（无分派即拒绝，超管除外）"),
    ("11", "文件秒传按 md5 全局复用", "高", "安全",
     "FileStorageService:128-144,217-223", "拿到 md5 即可下载/软删他人附件",
     "秒传结果按上传人隔离；下载补归属校验"),
    ("12", "分派逻辑破坏性写", "高", "业务",
     "RptTaskService:87-134", "传 AUDIT 会清空全部 FILL 分派；已关闭任务被复活；无效用户静默跳过",
     "按类型分别删 + 状态守卫 + 无效用户报错"),
    ("13", "台账部门口径错误", "高", "业务",
     "ReportStatsService:209-219", "按「下发部门」而非「填报部门」统计，部门考核失真",
     "改按 rpt_task_assign.dept_id / fill_user 的 dept_id"),
    ("14", "审核双通道字段打架", "高", "业务",
     "RptTaskDataService:195-240 vs 640-693", "已审通过的数据仍挂在待审列表；旧通道数据永远进不了任务方审核",
     "二选一，删除旧单级 audit()"),
    ("15", "填报截止后仍可改数据", "高", "业务",
     "RptTaskDataService:78-95,367-467", "importExcel 完全绕开状态与截止校验，还能覆盖已审核数据",
     "importExcel 补授权+状态+截止校验"),
    ("16", "登录限流可绕过", "中", "安全",
     "SysUserService:127-133 等 3 处", "IP 取 X-Forwarded-For 最左值，换头即可无限撞库",
     "只信 getRemoteAddr()，由代理固定覆盖 XFF"),
    ("17", "无测试覆盖", "中", "质量",
     "src/test 不存在", "124 个文件 0 个测试，任何改动都无回归保障",
     "先补 5 个核心模块的单测"),
    ("18", "121 处 catch 吞异常", "中", "质量",
     "全 Controller", "业务失败包装成 HTTP 200 + code 500，可观测性差",
     "Controller 去掉 catch，统一交 GlobalExceptionHandler"),
]

# 修复路线
ROADMAP = [
    ("第一阶段（上线前必须，1-2 天）", "bad", [
        "CORS 改白名单（配置化）",
        "CompatController 三端点补鉴权",
        "AuthzService fail-open → fail-closed",
        "所有 by-id/by-taskId 接口补归属校验（fill/audit/ledger/file）",
        "数据范围 SQL 改词法白名单 + 参数化",
        "前端：修 4 个不可用功能 + 403 误判",
        "改掉 admin/user 弱密码",
    ]),
    ("第二阶段（上线前强烈建议，3-5 天）", "warn", [
        "模板/部门/用户/项目/数据源删除补引用检查（改软删除）",
        "加 4 个唯一约束（需先清存量重复数据）",
        "填报/审核改条件 UPDATE，消除 check-then-act",
        "周期任务加 ShedLock + 唯一键 + 复制 AUDIT 分派",
        "分派逻辑重写（按类型删/状态守卫/无效用户报错）",
        "台账口径改按填报部门统计",
    ]),
    ("第三阶段（持续改进）", "ok", [
        "双通道审核二选一，清理死状态（任务 status=3）",
        "状态常量统一 enum，修 filling_cycle 两套编码",
        "schema.sql 补齐 10 张缺失表",
        "Controller 去掉 121 处 catch，统一异常处理",
        "补单元测试（目标：核心 Service 覆盖率 > 60%）",
        "前端 echarts 按需引入（-1033KB），columns 加 useMemo",
    ]),
]

REPORTS = [
    ("backend/SECURITY_AUDIT_REPORT.md", "安全专项审计", "584 行 · 20 项（严重 2 / 高 6 / 中 7 / 低 5）"),
    ("docs/业务逻辑与数据一致性审计报告.md", "业务逻辑与数据一致性", "约 470 行 · 状态机/并发/一致性/规则漏洞"),
    ("docs/代码质量与架构审计报告.md", "代码质量与架构", "565 行 · 严重 7 / 中 10 / 轻多项"),
    ("frontend2/AUDIT_REPORT.md", "前端代码审计", "623 行 · 量化指标 + 契约核对 + 性能"),
]


def sev_color(s):
    return {"严重": "sev-1", "高": "sev-2", "中": "sev-3", "低": "sev-4"}.get(s, "sev-4")


def build():
    now = time.strftime("%Y-%m-%d %H:%M")

    grade_rows = "".join(
        f'<tr><td><b>{dim}</b></td><td class="grade {cls}">{g}</td><td>{desc}</td></tr>'
        for dim, g, desc, cls in GRADES)

    verified_rows = "".join(
        f'<tr><td><span class="pill {cls}">{sev}</span></td><td><b>{name}</b></td>'
        f'<td class="path">{loc}</td><td>{res}</td><td class="detail">{ev}</td></tr>'
        for name, sev, loc, res, ev, cls in VERIFIED)

    issue_rows = "".join(
        f'<tr><td class="num-i">{i}</td><td><span class="pill {sev_color(sev)}">{sev}</span></td>'
        f'<td class="cat">{cat}</td><td><b>{title}</b></td>'
        f'<td class="path">{loc}</td><td class="impact">{impact}</td><td class="fix">{fix}</td></tr>'
        for i, title, sev, cat, loc, impact, fix in TOP_ISSUES)

    roadmap_html = "".join(
        f'<div class="stage {cls}"><h3>{title}</h3><ul>'
        + "".join(f"<li>{it}</li>" for it in items) + "</ul></div>"
        for title, cls, items in ROADMAP)

    report_rows = "".join(
        f'<tr><td class="path">{p}</td><td>{name}</td><td style="color:#8b949e">{desc}</td></tr>'
        for p, name, desc in REPORTS)

    return f"""<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>填报系统 · 代码审计报告</title>
<style>
*{{box-sizing:border-box}}
body{{margin:0;background:#0e1117;color:#c9d1d9;font:14px/1.7 "Microsoft YaHei","PingFang SC",-apple-system,sans-serif}}
.wrap{{max-width:1400px;margin:0 auto;padding:32px 28px 80px}}
h1{{font-size:27px;margin:0 0 6px;color:#e6edf3;font-weight:600}}
.sub{{color:#8b949e;font-size:13px;margin-bottom:24px}}
h2{{font-size:18px;color:#e6edf3;margin:40px 0 14px;padding-left:11px;border-left:4px solid #58a6ff}}
h3{{font-size:15px;color:#e6edf3;margin:18px 0 10px}}
.kpis{{display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:14px;margin:20px 0 8px}}
.kpi{{background:#161b22;border:1px solid #30363d;border-radius:10px;padding:15px 17px}}
.kpi .v{{font-size:26px;font-weight:700;color:#e6edf3;line-height:1.15}}
.kpi .l{{font-size:12px;color:#8b949e;margin-top:4px}}
.kpi.r .v{{color:#f85149}} .kpi.y .v{{color:#d29922}} .kpi.b .v{{color:#58a6ff}}
.kpi.g .v{{color:#3fb950}}
table{{width:100%;border-collapse:collapse;background:#161b22;border:1px solid #30363d;border-radius:8px;overflow:hidden;font-size:13px;margin-bottom:10px}}
th{{background:#1c2128;color:#8b949e;text-align:left;padding:9px 11px;font-weight:600;border-bottom:1px solid #30363d;white-space:nowrap;font-size:12px}}
td{{padding:9px 11px;border-bottom:1px solid #21262d;vertical-align:top}}
tr:last-child td{{border-bottom:none}}
tr:hover{{background:#1c2128}}
.num-i{{width:34px;color:#6e7681;font-family:Consolas,monospace}}
.path{{font-family:Consolas,monospace;color:#79c0ff;font-size:11.5px;word-break:break-all}}
.detail{{font-size:12px;color:#9da7b3;line-height:1.55}}
.impact{{color:#d29922;font-size:12.5px}}
.fix{{color:#8b949e;font-size:12px}}
.cat{{font-size:11px;color:#8b949e;white-space:nowrap}}
.grade{{font-size:17px;font-weight:700;text-align:center;width:64px}}
.grade.bad{{color:#f85149}} .grade.worse{{color:#da3633}}
.grade.mid{{color:#d29922}} .grade.ok{{color:#3fb950}}
.pill{{font-size:11px;padding:2px 8px;border-radius:10px;font-weight:700;white-space:nowrap}}
.pill.bad{{background:#f85149;color:#fff}}
.pill.warn{{background:#d29922;color:#1a1a00}}
.pill.ok{{background:#238636;color:#fff}}
.sev-1{{background:#da3633;color:#fff}}
.sev-2{{background:#f85149;color:#fff}}
.sev-3{{background:#d29922;color:#1a1a00}}
.sev-4{{background:#30363d;color:#c9d1d9}}
.note{{background:#161b22;border:1px solid #30363d;border-left:4px solid #3fb950;border-radius:8px;padding:14px 18px;font-size:13px;color:#9da7b3;margin-bottom:16px}}
.note.warn{{border-left-color:#d29922}}
.note.bad{{border-left-color:#f85149}}
.note b{{color:#e6edf3}}
.note ul{{margin:8px 0 0;padding-left:20px}} .note li{{margin:4px 0}}
code{{background:#21262d;padding:1px 5px;border-radius:4px;font-family:Consolas,monospace;color:#79c0ff;font-size:12px}}
.stage{{background:#161b22;border:1px solid #30363d;border-left-width:4px;border-radius:8px;padding:14px 20px;margin-bottom:14px}}
.stage.bad{{border-left-color:#f85149}}
.stage.warn{{border-left-color:#d29922}}
.stage.ok{{border-left-color:#3fb950}}
.stage h3{{margin:0 0 8px}}
.stage ul{{margin:6px 0 0;padding-left:22px;font-size:13px;color:#9da7b3}}
.stage li{{margin:4px 0}}
.verdict{{background:#2a1215;border:1px solid #f85149;border-radius:10px;padding:20px 24px;margin:20px 0}}
.verdict h3{{margin:0 0 10px;color:#f85149;font-size:17px}}
.verdict p{{margin:6px 0}}
.foot{{margin-top:44px;padding-top:16px;border-top:1px solid #30363d;color:#6e7681;font-size:12px}}
</style></head><body><div class="wrap">

<h1>填报系统 · 代码审计报告</h1>
<div class="sub">
审计对象 <code>E:\\C++\\report_src\\report-master</code>　|　
后端 124 个 Java 文件 / 11,200 行　前端 64 个 ts·tsx / 10,300 行　|　
审计时间 {now} CST　|　方式：4 路并行静态审计 + 线上实测验证
</div>

<div class="verdict">
<h3>结论：不建议按当前状态上线生产</h3>
<p>功能层面此前已验证可跑通（业务闭环 14/14），但本次代码审计发现 <b>代码层面的安全与数据一致性存在系统性缺陷</b>——
这些缺陷不会在功能测试中暴露，只会在<b>真实多人使用、误操作、并发、或恶意访问</b>时爆发。</p>
<p style="margin-top:10px"><b>最危险的三个组合：</b></p>
<p>① CORS 通配 + 物化表导出无归属校验 → 任意员工可把全公司数据导出到外部站点</p>
<p>② SQL 黑名单可绕过 + 数据源可指向主库 → 部门管理员可拖库（密码哈希）</p>
<p>③ 删除操作全无引用保护 → 已造成线上 55 条孤儿任务，且会持续产生</p>
<p style="margin-top:10px;color:#d29922">建议：先完成第一阶段整改（1-2 天），再做一次双账号跨部门越权回归，才具备上线条件。</p>
</div>

<div class="kpis">
<div class="kpi r"><div class="v">60+</div><div class="l">发现问题总数</div></div>
<div class="kpi r"><div class="v">8</div><div class="l">严重级</div></div>
<div class="kpi y"><div class="v">14</div><div class="l">高危级</div></div>
<div class="kpi b"><div class="v">4</div><div class="l">审计维度</div></div>
<div class="kpi r"><div class="v">4</div><div class="l">前端不可用功能</div></div>
<div class="kpi y"><div class="v">0</div><div class="l">单元测试</div></div>
</div>

<h2>一、各维度评级</h2>
<table>
<thead><tr><th>维度</th><th>评级</th><th>说明</th></tr></thead>
<tbody>{grade_rows}</tbody></table>

<div class="note bad">
<b>整体评级：C-（不合格）</b>　认证骨架合格、代码结构可读，但<b>授权层的数据归属校验</b>与
<b>并发/一致性保障</b>两块几乎空白。这两块不修，任何一次双击、任何一次多实例发布、任何一次误删，
都会产生不可自动恢复的数据污染。
</div>

<h2>二、线上实测验证（含对审计结论的修正）</h2>
<div class="note">
本节是我对子代理审计结论的<b>逐条实测复核</b>。生产环境 103.85.25.169 实测，
所有操作均为只读或可回滚，测试数据已清理回基线。
</div>
<table>
<thead><tr><th style="width:70px">级别</th><th style="width:180px">问题</th><th style="width:210px">位置</th><th style="width:110px">验证结论</th><th>证据</th></tr></thead>
<tbody>{verified_rows}</tbody></table>

<div class="note warn">
<b>审计修正说明：</b>子代理安全报告将「登出后 token 仍有效」列为高危（H4），
但<b>实测与源码复核均证明该问题在当前代码中不存在</b>——
<code>AuthController.login</code> 用 <code>jwtTokenUtil.getSessionId(token)</code> 登记的 jti 与 token 内一致，
登出后立即返回 401。已从高危清单降级。
但同一处代码的 <b>fail-open</b>（会话表异常时返回 ONLINE）仍然成立，列为中危。
</div>

<h2>三、Top 18 问题清单</h2>
<table>
<thead><tr><th>#</th><th>级别</th><th>类别</th><th>问题</th><th>位置</th><th>影响</th><th>修复要点</th></tr></thead>
<tbody>{issue_rows}</tbody></table>

<h2>四、修复路线图</h2>
{roadmap_html}

<h2>五、量化基线</h2>
<table>
<thead><tr><th>指标</th><th>数值</th><th>说明</th></tr></thead>
<tbody>
<tr><td>Controller / Service / Model</td><td>26 / 27 / 22</td><td>后端结构</td></tr>
<tr><td><code>catch</code> 块 / 其中 <code>return Result.error(e.getMessage())</code></td><td>284 / <b>121</b></td><td>异常被大量吞掉</td></tr>
<tr><td><code>throw new RuntimeException</code></td><td>144</td><td>无自定义异常类型</td></tr>
<tr><td><code>@Transactional</code> 带 <code>rollbackFor</code></td><td>61 / <b>0</b></td><td>未显式声明回滚</td></tr>
<tr><td>方法级 <code>@PreAuthorize</code></td><td>72</td><td>覆盖率约 90%（写接口）</td></tr>
<tr><td>魔法数字（状态值 0/1/2/4）</td><td><b>47 处</b></td><td>散落各 Service</td></tr>
<tr><td>DTO 数量 / 实体直接作 <code>@RequestBody</code></td><td><b>0</b> / 13</td><td>无 DTO 层</td></tr>
<tr><td>日志条数 / 覆盖类数</td><td>47 / 24（共 124）</td><td>Service/Controller 几乎零日志</td></tr>
<tr><td>单元测试</td><td><b>0</b></td><td><code>src/test</code> 不存在</td></tr>
<tr><td>前端 <code>any</code> / <code>as any</code></td><td>186 / 12</td><td><code>@ts-ignore</code> 为 0（✓）</td></tr>
<tr><td>前端 API 路径 404 数</td><td><b>0</b></td><td>契约路径基本对齐（✓）</td></tr>
<tr><td>前端字段不匹配模块</td><td><b>8 个</b></td><td>模板/数据源/角色/用户/申请/字典/审核/台账</td></tr>
<tr><td>主 chunk 体积</td><td>1416.7 KB</td><td>echarts 全量引入占 1033 KB</td></tr>
<tr><td>列表页 columns 未 useMemo</td><td>23 / 24</td><td>每次渲染重建</td></tr>
</tbody></table>

<h2>六、已验证无问题的项（避免误以为漏审）</h2>
<div class="note">
<ul>
<li><b>SQL 注入</b>：MyBatis <code>${{}}</code> 0 处；JdbcTemplate 拼接仅限自增 Long 与 int，不可控（<code>DataRangeService</code> 的 SQL 试跑除外）</li>
<li><b>路径穿越</b>：UUID 文件名 + safeName + 分片 sanitize，无原文件名拼路径</li>
<li><b>XSS</b>：前端 <code>dangerouslySetInnerHTML</code>/<code>eval</code>/<code>innerHTML</code> 均 0 处</li>
<li><b>反序列化 / SpEL / 命令执行</b>：全仓库 0 处</li>
<li><b>JWT 签名与密钥强度</b>：启动强校验 ≥32 字节，拒绝历史泄漏值</li>
<li><b>验证码</b>：一次性消费、uuid 不可预测、固定码默认关闭</li>
<li><b>密码字段回显</b>：11 处入口全覆盖</li>
<li><b>AES/GCM 的 IV</b>：SecureRandom 每次随机，无复用</li>
<li><b>Content-Disposition 头注入</b>：全部 URLEncoder 编码</li>
<li><b>自审拦截</b>：<code>assertCanAudit</code> 正确拦截「审核人 == 填报人」</li>
<li><b>代填拦截</b>：<code>fillData</code> 强制本人</li>
<li><b>CSRF</b>：token 走 Header 无 Cookie 鉴权，禁用合理</li>
<li><b>前端权限</b>：不是「按钮隐藏」式假拦截，后端 <code>@PreAuthorize</code> 覆盖全部写接口</li>
<li><b>定时器/事件监听泄漏</b>：2 个 setInterval 均有清理</li>
</ul>
</div>

<h2>七、详细报告索引</h2>
<table>
<thead><tr><th>文件</th><th>主题</th><th>规模</th></tr></thead>
<tbody>{report_rows}</tbody></table>

<h2>八、可复用的审计与验证脚本</h2>
<table>
<thead><tr><th>脚本</th><th>用途</th></tr></thead>
<tbody>
<tr><td class="path">deploy/preflight_check.py</td><td>上线前健康检查（13 项，只读）</td></tr>
<tr><td class="path">deploy/audit_flow.py</td><td>全新数据业务闭环验证（14 步）</td></tr>
<tr><td class="path">deploy/probe_h5.py</td><td>SQL 注入黑名单绕过验证</td></tr>
<tr><td class="path">deploy/cleanup_audit_data.py</td><td>审计测试数据清理 + 基线校验</td></tr>
<tr><td class="path">deploy/cleanup_process.py</td><td>孤儿进程记录清理（含备份）</td></tr>
</tbody></table>

<div class="foot">
报告由 4 路并行审计 + 线上实测复核生成　|　
子报告：安全专项 / 业务逻辑与数据一致性 / 代码质量与架构 / 前端审计　|　
所有测试数据已清理，生产数据零损伤
</div>

</div></body></html>
"""


def main():
    html = build()
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(html)
    print(f"✅ 报告已生成: {OUT} ({os.path.getsize(OUT)/1024:.1f} KB)")


if __name__ == "__main__":
    main()
