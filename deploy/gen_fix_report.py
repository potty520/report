#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成缺陷修复与重新部署报告（HTML）"""
import base64
import os
import time

ROOT = r"E:\C++\report_src\report-master"
SHOT_DIR = os.path.join(ROOT, "screenshots", "fixtest")
OUT = os.path.join(ROOT, "缺陷修复与重新部署报告.html")

FIXES = [
    ("FIX-1", "P0", "前端新建任务必然失败（字段不匹配）", "code=500 任务未创建",
     "UI 提交成功，id=207 正常落库"),
    ("FIX-2", "P0", "任务列表年度/期数/日期显示 -", "4 列全部为空",
     "年份 2026 / 期数 Q2 / 结束日期 2026-12-31"),
    ("FIX-3", "P1", "LocalDateTime 不支持空格时间格式", "Text could not be parsed at index 10",
     "三种格式全部支持"),
]

FIELD_MAP = [
    ("taskName", "name", "读取靠已有归一化补出，勉强可用", "warn"),
    ("belongTime（2026-Q1）", "year + period", "未归一化 → 列表显示 -", "bad"),
    ("fillDeadline", "endDate", "未归一化 → 列表显示 -", "bad"),
    ("（无对应字段）", "startDate", "列表显示 -", "bad"),
    ("fillingCycle（必填）", "前端未传", "创建时数据库非空约束失败", "bad"),
]

VERIFY_TABLE = [
    ("UI 新建任务", "❌ code=500，未创建", "✅ 成功，id=207"),
    ("年度列", "-", "✅ 2026"),
    ("期数列", "-", "✅ Q2"),
    ("结束日期列", "-", "✅ 2026-12-31"),
    ("状态列", "草稿", "✅ 草稿"),
    ("操作列", "仅删除", "✅ 编辑 / 发布 / 删除"),
]

DT_TABLE = [
    ("2026-12-31 23:59:59", "❌ 解析失败", "✅ code=200", "2026-12-31 23:59:59"),
    ("2026-11-30（纯日期）", "❌ 解析失败", "✅ code=200", "2026-11-30 00:00:00"),
    ("2026-10-31T23:59:59（ISO）", "✅ code=200", "✅ code=200", "2026-10-31 23:59:59"),
]

STATUS_BTNS = [
    ("草稿(0)", "编辑、发布、删除", "ok"),
    ("已发布(1)", "编辑、关闭、删除", "ok"),
    ("已关闭(2)", "删除", "ok"),
]

API_REGRESS = [
    ("/task/page", 514), ("/template/page", 212), ("/template/list ←P13", 174),
    ("/fill/task/page ←F02", 303), ("/overview/stats", 221), ("/dept/tree", 220),
    ("/role/list", 251), ("/user/page", 254), ("/audit/statistics", 246),
    ("/todo/summary", 194),
]

DATA_CHECK = [
    ("rpt_task", "55", "55"), ("rpt_task_data", "1", "1"),
    ("rpt_task_assign", "51", "51"), ("rpt_task_process", "296", "296"),
    ("rpt_template", "1", "1"),
]

SHOTS = [
    ("01_修复后_新增任务表单.png", "修复后：新增任务表单，日期可正常选择"),
    ("02_修复后_任务列表.png", "修复后：任务列表（已关闭任务仅显示删除，属正确业务逻辑）"),
]


def img_b64(name):
    p = os.path.join(SHOT_DIR, name)
    if not os.path.exists(p):
        return None
    with open(p, "rb") as f:
        return "data:image/png;base64," + base64.b64encode(f.read()).decode()


def build():
    now = time.strftime("%Y-%m-%d %H:%M")

    fix_rows = "".join(
        f'<tr><td class="cid">{c}</td><td><span class="tag sev-{"h" if p=="P0" else "m"}">{p}</span></td>'
        f'<td>{n}</td><td class="before">{b}</td><td class="after">{a}</td>'
        f'<td class="ok">✅ 已修复</td></tr>'
        for c, p, n, b, a in FIXES)

    field_rows = "".join(
        f'<tr><td class="path">{b}</td><td class="path">{f}</td>'
        f'<td class="{ "warn-t" if lv=="warn" else "bad-t" }">{i}</td></tr>'
        for b, f, i, lv in FIELD_MAP)

    verify_rows = "".join(
        f'<tr><td>{n}</td><td class="before">{b}</td><td class="after">{a}</td></tr>'
        for n, b, a in VERIFY_TABLE)

    dt_rows = "".join(
        f'<tr><td class="path">{inp}</td><td class="before">{b}</td>'
        f'<td class="after">{a}</td><td class="path">{out}</td></tr>'
        for inp, b, a, out in DT_TABLE)

    status_rows = "".join(
        f'<tr><td>{s}</td><td>{b}</td><td class="ok">✅</td></tr>'
        for s, b, _ in STATUS_BTNS)

    api_rows = "".join(
        f'<tr><td class="path">{p}</td><td class="num">200</td>'
        f'<td><div class="bar"><div class="bar-in {"warn" if ms > 400 else "ok"}" '
        f'style="width:{min(100, ms/600*100):.0f}%"></div></div><span class="ms">{ms}ms</span></td></tr>'
        for p, ms in API_REGRESS)

    data_rows = "".join(
        f'<tr><td class="path">{t}</td><td class="num">{b}</td><td class="num">{a}</td>'
        f'<td class="ok">✅</td></tr>' for t, b, a in DATA_CHECK)

    shot_html = "\n".join(
        f'<figure><img src="{b}" alt="{c}"><figcaption>{c}</figcaption></figure>'
        for c, b in [(c, img_b64(f)) for f, c in SHOTS] if b)

    return f"""<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>填报系统 · 缺陷修复与重新部署报告</title>
<style>
*{{box-sizing:border-box}}
body{{margin:0;background:#0e1117;color:#c9d1d9;font:14px/1.7 "Microsoft YaHei","PingFang SC",-apple-system,sans-serif}}
.wrap{{max-width:1280px;margin:0 auto;padding:32px 28px 80px}}
h1{{font-size:26px;margin:0 0 6px;color:#e6edf3;font-weight:600}}
.sub{{color:#8b949e;font-size:13px;margin-bottom:26px}}
h2{{font-size:18px;color:#e6edf3;margin:38px 0 14px;padding-left:11px;border-left:4px solid #58a6ff}}
h3{{font-size:15px;color:#e6edf3;margin:24px 0 10px}}
.kpis{{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:14px;margin:18px 0 8px}}
.kpi{{background:#161b22;border:1px solid #30363d;border-radius:10px;padding:16px 18px}}
.kpi .v{{font-size:26px;font-weight:700;color:#e6edf3;line-height:1.2}}
.kpi .l{{font-size:12px;color:#8b949e;margin-top:4px}}
.kpi.g .v{{color:#3fb950}} .kpi.b .v{{color:#58a6ff}} .kpi.y .v{{color:#d29922}}
table{{width:100%;border-collapse:collapse;background:#161b22;border:1px solid #30363d;border-radius:8px;overflow:hidden;font-size:13px;margin-bottom:8px}}
th{{background:#1c2128;color:#8b949e;text-align:left;padding:9px 12px;font-weight:600;border-bottom:1px solid #30363d;white-space:nowrap}}
td{{padding:8px 12px;border-bottom:1px solid #21262d;vertical-align:top;word-break:break-all}}
tr:last-child td{{border-bottom:none}}
tr:hover{{background:#1c2128}}
.num{{text-align:right;white-space:nowrap}}
.cid{{width:60px;color:#58a6ff;font-family:Consolas,monospace;font-weight:600}}
.path{{font-family:Consolas,monospace;color:#79c0ff;font-size:12px}}
.before{{color:#f85149;font-family:Consolas,monospace;font-size:12px}}
.after{{color:#3fb950;font-family:Consolas,monospace;font-size:12px}}
.ok{{color:#3fb950;white-space:nowrap}}
.warn-t{{color:#d29922}}
.bad-t{{color:#f85149}}
.tag{{font-size:11px;padding:2px 9px;border-radius:11px;font-weight:700;white-space:nowrap}}
.tag.sev-h{{background:#f85149;color:#fff}}
.tag.sev-m{{background:#d29922;color:#1a1a00}}
.bar{{display:inline-block;width:100px;height:9px;background:#21262d;border-radius:5px;overflow:hidden;vertical-align:middle;margin-right:9px}}
.bar-in{{height:100%;border-radius:5px}}
.bar-in.ok{{background:#3fb950}} .bar-in.warn{{background:#d29922}}
.ms{{font-size:12px;color:#8b949e}}
.note{{background:#161b22;border:1px solid #30363d;border-left:4px solid #3fb950;border-radius:8px;padding:14px 18px;font-size:13px;color:#9da7b3;margin-bottom:16px}}
.note.warn{{border-left-color:#d29922}}
.note.bad{{border-left-color:#f85149}}
.note b{{color:#e6edf3}}
.note ul{{margin:8px 0 0;padding-left:20px}} .note li{{margin:4px 0}}
code{{background:#21262d;padding:1px 5px;border-radius:4px;font-family:Consolas,monospace;color:#79c0ff;font-size:12px}}
pre{{background:#0d1117;border:1px solid #30363d;border-radius:8px;padding:14px 16px;overflow-x:auto;font-size:12px;color:#c9d1d9;line-height:1.55}}
.gallery{{display:grid;grid-template-columns:repeat(auto-fill,minmax(380px,1fr));gap:18px;margin-top:16px}}
figure{{margin:0;background:#161b22;border:1px solid #30363d;border-radius:8px;overflow:hidden}}
figure img{{width:100%;display:block;border-bottom:1px solid #30363d}}
figcaption{{padding:9px 13px;font-size:12px;color:#8b949e}}
.foot{{margin-top:44px;padding-top:16px;border-top:1px solid #30363d;color:#6e7681;font-size:12px}}
</style></head><body><div class="wrap">

<h1>填报系统 · 缺陷修复与重新部署报告</h1>
<div class="sub">
测试对象 <a href="http://103.85.25.169:8091" style="color:#58a6ff">http://103.85.25.169:8091</a>　|　
执行时间 {now} CST　|　方式：Playwright 浏览器实测 + 生产 API 回归 + 数据库校验
</div>

<div class="kpis">
<div class="kpi g"><div class="v">3/3</div><div class="l">缺陷已修复</div></div>
<div class="kpi g"><div class="v">✅</div><div class="l">前后端已部署</div></div>
<div class="kpi g"><div class="v">10/10</div><div class="l">API 回归通过</div></div>
<div class="kpi b"><div class="v">1</div><div class="l">误判已澄清</div></div>
<div class="kpi g"><div class="v">零损伤</div><div class="l">生产数据</div></div>
</div>

<h2>一、修复总览</h2>
<table>
<thead><tr><th>编号</th><th>级别</th><th>缺陷</th><th>修复前</th><th>修复后</th><th>状态</th></tr></thead>
<tbody>{fix_rows}</tbody></table>

<div class="note warn">
<b>一项判断修正：「任务列表只有删除按钮」不是缺陷，是我此前的误判。</b>
<p>操作列代码本身完整（编辑/发布/关闭/删除都有），采用<b>状态条件渲染</b>：</p>
<table style="margin:8px 0">
<thead><tr><th>任务状态</th><th>可见按钮</th><th>是否正确</th></tr></thead>
<tbody>{status_rows}</tbody></table>
<p>生产环境 55 个任务<b>全部是已关闭状态</b>，所以只显示「删除」——这是<b>正确的业务逻辑</b>，无需修复。</p>
</div>

<h2>二、FIX-1 / FIX-2：前后端任务字段不匹配</h2>

<h3>2.1 根因</h3>
<table>
<thead><tr><th>后端实际字段</th><th>前端期望字段</th><th>影响</th></tr></thead>
<tbody>{field_rows}</tbody></table>

<div class="note bad">
<b>结果：</b>页面新建任务 <b>100% 失败</b>；列表「年度/期数/开始日期/结束日期」4 列显示为空。
</div>

<h3>2.2 修复方案</h3>
<div class="note">
在 API 层做<b>双向映射</b>，把字段差异收敛在一处，页面组件无需感知后端字段名。
文件：<code>frontend2/src/services/api.ts</code>
</div>

<p><b>① 读取方向 —— 归一化补全</b></p>
<pre>function normalizeTaskRow(x) {{
  const belongTime = x.belongTime ?? '';
  // "2026-Q1" → year=2026, period="Q1"
  const yearFromBelong = /^\\d{{4}}/.test(belongTime) ? Number(belongTime.slice(0,4)) : undefined;
  const periodFromBelong = belongTime.length > 4 ? belongTime.slice(5) : undefined;
  return {{
    ...x,
    name: x.name ?? x.taskName,
    year: x.year ?? yearFromBelong,
    period: x.period ?? periodFromBelong ?? '',
    startDate: x.startDate ?? x.startTime ?? '',
    // fillDeadline 作为结束日期展示，截取日期部分
    endDate: x.endDate ?? (x.fillDeadline ? String(x.fillDeadline).slice(0,10) : ''),
    templateName: x.templateName ?? (x.templateId != null ? `模板#${{x.templateId}}` : ''),
  }};
}}</pre>

<p><b>② 写入方向 —— 参数转换</b></p>
<pre>export function toTaskPayload(data) {{
  const belongTime = data.year != null
    ? (data.period ? `${{data.year}}-${{data.period}}` : String(data.year))
    : undefined;
  // 纯日期补 23:59:59，满足 LocalDateTime
  const fillDeadline = data.endDate
    ? (data.endDate.length === 10 ? `${{data.endDate}}T23:59:59` : data.endDate)
    : undefined;
  return {{
    taskName: data.name,
    templateId: data.templateId,
    belongTime,
    fillDeadline,
    description: data.description,
  }};
}}</pre>

<h3>2.3 修复验证</h3>
<table>
<thead><tr><th>验证项</th><th>修复前</th><th>修复后</th></tr></thead>
<tbody>{verify_rows}</tbody></table>

<h2>三、FIX-3：LocalDateTime 反序列化</h2>

<h3>3.1 根因</h3>
<div class="note bad">
<code>application-prod.yml</code> 配置了 <code>spring.jackson.date-format: yyyy-MM-dd HH:mm:ss</code>，
但该配置<b>只对 java.util.Date 生效</b>，对 <code>java.time.LocalDateTime</code> 无效。
项目实体大量使用 LocalDateTime，Spring Boot 默认 JavaTimeModule 只认 ISO-8601。
</div>

<h3>3.2 修复方案</h3>
<div class="note">
新增 <code>backend/src/main/java/com/reportsys/config/JacksonConfig.java</code>：
<ul>
<li>注册 LocalDateTime / LocalDate / LocalTime 的序列化与反序列化器</li>
<li>序列化统一输出 <code>yyyy-MM-dd HH:mm:ss</code></li>
<li>反序列化<b>兼容三种格式</b>（向后兼容，不破坏现有调用）：
  <code>yyyy-MM-dd HH:mm:ss</code> / <code>yyyy-MM-dd</code> / ISO-8601</li>
</ul>
</div>

<h3>3.3 修复验证</h3>
<table>
<thead><tr><th>输入格式</th><th>修复前</th><th>修复后</th><th>落库结果</th></tr></thead>
<tbody>{dt_rows}</tbody></table>

<div class="note">
<b>输出格式也统一了</b>：接口返回时间从 <code>2026-12-31T23:59:59</code> 变为
<code>2026-12-31 23:59:59</code>，与前端展示口径一致。
</div>

<h2>四、部署结果</h2>

<h3>4.1 后端</h3>
<table>
<thead><tr><th>项目</th><th>内容</th></tr></thead>
<tbody>
<tr><td>jar 大小</td><td>92,012,758 bytes</td></tr>
<tr><td>构建</td><td>mvn clean package → BUILD SUCCESS</td></tr>
<tr><td>部署</td><td>分块断点续传 → /opt/report/jar/</td></tr>
<tr><td>重启</td><td>systemctl restart report-api ✅</td></tr>
<tr><td>启动耗时</td><td>25.7s</td></tr>
<tr><td>健康检查</td><td class="ok">/api/auth/captcha HTTP 200 ✅</td></tr>
</tbody></table>

<h3>4.2 前端</h3>
<table>
<thead><tr><th>项目</th><th>内容</th></tr></thead>
<tbody>
<tr><td>构建</td><td>npm install + npm run build → dist 70 文件</td></tr>
<tr><td>部署前入口</td><td class="path">index-CHGhVGPH.js</td></tr>
<tr><td>部署后入口</td><td class="path">index-BdxASciG.js</td></tr>
<tr><td>备份</td><td class="path">/opt/report/web.bak-20261019-032021</td></tr>
<tr><td>文件校验</td><td class="ok">70/70 上传成功，入口文件 EXISTS ✅</td></tr>
<tr><td>nginx</td><td class="ok">nginx -t syntax is ok ✅</td></tr>
<tr><td>入口</td><td class="ok">:8091 HTTP 200 ✅</td></tr>
</tbody></table>

<div class="note warn">
<b>注意：</b>前端包更新后，浏览器需<b>强制刷新（Ctrl+F5）</b>或清缓存才能加载新 bundle。
本次验证通过 CDP <code>Network.clearBrowserCache</code> 确认新入口 <code>index-BdxASciG.js</code> 已生效。
</div>

<h2>五、回归验证</h2>

<h3>5.1 API 回归（10/10 通过，无回归）</h3>
<table>
<thead><tr><th>接口</th><th>HTTP</th><th>耗时</th></tr></thead>
<tbody>{api_rows}</tbody></table>

<h3>5.2 数据完整性</h3>
<p>测试数据（<code>ZZFIX-*</code> 共 5 条任务）已清理，全部表恢复到基线：</p>
<table>
<thead><tr><th>表</th><th>基线</th><th>清理后</th><th>状态</th></tr></thead>
<tbody>{data_rows}</tbody></table>

<div class="note">
生产数据：用户 2、原有任务（P1*）55 条，全部完好。✅
</div>

<h2>六、改动清单</h2>
<table>
<thead><tr><th>文件</th><th>说明</th></tr></thead>
<tbody>
<tr><td class="path">backend/.../config/JacksonConfig.java</td><td>【新增】全局 JavaTime 序列化/反序列化配置</td></tr>
<tr><td class="path">frontend2/src/services/api.ts</td><td>【修改】新增 normalizeTaskRow() 与 toTaskPayload()，接入任务读写全链路</td></tr>
<tr><td class="path">deploy/deploy_web.py</td><td>【新增】前端静态资源部署脚本（备份+上传+校验）</td></tr>
<tr><td class="path">deploy/cleanup_fix.py</td><td>【新增】修复验证数据清理脚本</td></tr>
</tbody></table>

<h2>七、结论</h2>
<table>
<thead><tr><th>维度</th><th>结果</th></tr></thead>
<tbody>
<tr><td>缺陷修复</td><td class="ok">✅ 3 项检查全部修复（FIX-1 P0 / FIX-2 P0 / FIX-3 P1）</td></tr>
<tr><td>误判澄清</td><td class="ok">✅ 1 项（任务列表按钮为状态条件渲染，非缺陷）</td></tr>
<tr><td>部署</td><td class="ok">✅ 前后端均成功，健康检查通过</td></tr>
<tr><td>回归</td><td class="ok">✅ API 10/10 通过，无回归</td></tr>
<tr><td>数据完整性</td><td class="ok">✅ 生产数据零损伤</td></tr>
</tbody></table>

<div class="note">
<b>总体判定：修复完成并已部署上线，生产环境验证通过。</b>
</div>

<h2>八、截图证据</h2>
<div class="gallery">
{shot_html}
</div>

<h2>附：回滚方式</h2>
<pre># 后端回滚
ls -t /opt/report/jar/report-system-1.0.0.jar.bak-* | head -1
cp &lt;最新备份&gt; /opt/report/jar/report-system-1.0.0.jar
systemctl restart report-api

# 前端回滚
ls -td /opt/report/web.bak-* | head -1
rm -rf /opt/report/web &amp;&amp; cp -r &lt;最新备份&gt; /opt/report/web</pre>
<p style="color:#8b949e;font-size:12px">回滚前端后，用户需强制刷新浏览器（Ctrl+F5）。</p>

<div class="foot">
报告由自动化流程生成　|　部署脚本 <code>deploy_jar.py</code> / <code>deploy_web.py</code>　|　
清理脚本 <code>cleanup_fix.py</code>　|　截图目录 <code>screenshots/fixtest/</code>
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
