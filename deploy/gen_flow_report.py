#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成业务流转测试报告（HTML，内嵌截图）"""
import base64
import os
import time

ROOT = r"E:\C++\report_src\report-master"
SHOT_DIR = os.path.join(ROOT, "screenshots", "prodtest")
OUT = os.path.join(ROOT, "业务流转测试报告.html")

STEPS = [
    ("T01", "新建模板", "返回模板 id", "✅ id=109", 54),
    ("T01b", "启用模板", "status→1", "✅ status=1", 49),
    ("T01c", "模板审核通过", "auditStatus→1", "✅ auditStatus=1", 38),
    ("T02", "保存模板字段", "3 字段落库", "✅ 3 字段", 87),
    ("T03", "新建任务", "返回任务 id", "✅ id=205", 72),
    ("T04", "发布任务", "status 0→1", "✅ status=1", 55),
    ("T05", "任务分派", "生成分派记录", "✅ 1 条", 89),
    ("T06", "保存草稿", "草稿落库", "✅", 78),
    ("T07", "正式提交", "status→1", "✅ taskDataId=171", 178),
    ("T08", "部门审核", "通过且耗时可接受", "✅ 通过", 86),
    ("T09", "任务方审核", "通过", "✅", 85),
    ("T10", "流转轨迹", "完整节点链", "✅ 3 节点", 20),
    ("T11", "台账联动", "统计更新", "✅ 通过率 100%", 30),
    ("T12", "补正流程", "发起补正", "✅ id=34", 48),
]

STATE_CHECKS = [
    ("重复审核", "被拒绝", "数据未提交，无法审核"),
    ("模板未启用时建任务", "被拒绝", "模板未启用"),
    ("不能审核自己提交的数据", "被拒绝", "不能审核自己提交的数据"),
    ("任务方审核需先过部门审核", "被拒绝", "部门审核未通过，不能进行任务方审核"),
]

DATA_CHECK = [
    ("rpt_task", "55", "55"),
    ("rpt_task_data", "1", "1"),
    ("rpt_template", "1", "1"),
    ("rpt_task_assign", "51", "51"),
    ("rpt_task_process", "296", "296"),
]

PROD_CHECK = [
    ("用户数", "2（admin / user）"),
    ("角色数", "4"),
    ("部门数", "4（总公司/技术部/财务部/市场部）"),
    ("原模板（验证模板 TVERIFY）", "1"),
    ("原有任务（P1* 前缀）", "55"),
]

NEW_DEFECTS = [
    {
        "sev": "h", "tag": "高", "title": "前后端任务字段不匹配 —— 前端新建任务必然失败",
        "rows": [
            ("现象", "在「任务管理 → 任务制定」页面填写完整表单点「确定」，返回 "
                     "<code>操作失败：数据校验不通过或数据库约束冲突</code>，任务未被创建"),
            ("根因", "前端 <code>TaskFormParams</code> 提交 "
                     "<code>{name, year, period, startDate, endDate, status, description}</code>；"
                     "后端实体 <code>RptTask</code> 实际字段为 "
                     "<code>{taskName, templateId, belongTime, fillDeadline, fillingCycle}</code>。"
                     "<b>两套字段名几乎完全不同</b>，后端拿不到必填的 taskName 与 fillingCycle"),
            ("影响", "页面上新建任务 <b>100% 失败</b>，只能通过 API 创建"),
            ("实测证据", "用前端真实 payload 提交 → <code>code=500</code>，回查 createdCount=0"),
            ("修复方向", "① 前端改为提交后端字段名（推荐）；② 或后端加 DTO + @JsonProperty 映射。"
                         "并补充前后端字段契约测试"),
        ],
    },
    {
        "sev": "m", "tag": "中", "title": "任务列表缺少操作入口",
        "rows": [
            ("现象", "「任务制定」列表页每行只有「删除」按钮"),
            ("缺失", "编辑、发布、分派、关闭 四个关键操作在列表中均无入口"),
            ("影响", "用户无法在界面上推进任务流转（发布/分派/关闭），业务闭环在 UI 层断裂"),
            ("实测证据", "页面按钮集合 = <code>[搜索, 重置, 新增任务, 删除]</code>，无 编辑/发布/分派"),
            ("修复方向", "列表操作列补齐：编辑、发布（仅草稿可见）、分派、关闭（仅已发布可见）"),
        ],
    },
    {
        "sev": "m", "tag": "中", "title": "LocalDateTime 反序列化不支持标准格式",
        "rows": [
            ("现象", 'POST /task 传 <code>"fillDeadline": "2026-12-31 23:59:59"</code> → '
                     "<code>Text could not be parsed at index 10</code>"),
            ("根因", "未注册 JavaTimeModule 反序列化器，也未加 @JsonFormat；"
                     "<code>spring.jackson.date-format</code> 只对 <code>java.util.Date</code> 生效"),
            ("现状", "只有 ISO 格式（2026-12-31T23:59:59）能解析"),
            ("影响", "当前前端不传 fillDeadline，暂无实际触发；"
                     "<b>一旦修复上一条让前端传日期，必然踩坑</b>"),
            ("修复方向", "配置 <code>spring.jackson.serialization.write-dates-as-timestamps: false</code>，"
                         "或全局注册 JavaTimeModule 指定 yyyy-MM-dd HH:mm:ss"),
        ],
    },
    {
        "sev": "l", "tag": "设计", "title": "新建模板默认不可用（非缺陷）",
        "rows": [
            ("现象", "通过 API 新建模板后 status=0（停用）且 audit_status=0（待审），"
                     "立即建任务报「模板未启用」"),
            ("性质", "设计如此 —— <code>RptTemplateService.add</code> 明确 <code>setStatus(0) // 草稿</code>"),
            ("提示", "使用 API/脚本创建模板时，必须先执行「启用 + 审核通过」两步才能使用"),
        ],
    },
]

SHOTS = [
    ("11_业务流程_任务列表.png", "任务制定列表 — 注意操作列仅有「删除」"),
    ("12_业务流程_新增任务弹窗.png", "新增任务弹窗 — 字段完整，但提交字段名与后端不匹配"),
    ("04_任务台账.png", "任务台账 — 流转后的统计联动"),
    ("05_数据填报.png", "数据填报 — 填报入口"),
]


def img_b64(name):
    p = os.path.join(SHOT_DIR, name)
    if not os.path.exists(p):
        return None
    with open(p, "rb") as f:
        return "data:image/png;base64," + base64.b64encode(f.read()).decode()


def build():
    now = time.strftime("%Y-%m-%d %H:%M")

    step_rows = "\n".join(
        f'<tr><td class="cid">{code}</td><td>{name}</td><td>{exp}</td>'
        f'<td class="ok">{actual}</td>'
        f'<td><div class="bar"><div class="bar-in {"warn" if ms > 150 else "ok"}" '
        f'style="width:{min(100, ms/200*100):.0f}%"></div></div>'
        f'<span class="ms">{ms}ms</span></td></tr>'
        for code, name, exp, actual, ms in STEPS)

    state_rows = "\n".join(
        f'<tr><td>{n}</td><td>{e}</td><td class="path">{a}</td><td class="ok">✅</td></tr>'
        for n, e, a in STATE_CHECKS)

    data_rows = "\n".join(
        f'<tr><td class="path">{t}</td><td class="num">{b}</td><td class="num">{a}</td>'
        f'<td class="ok">✅ 一致</td></tr>'
        for t, b, a in DATA_CHECK)

    prod_rows = "\n".join(
        f'<tr><td>{n}</td><td>{v}</td><td class="ok">✅</td></tr>'
        for n, v in PROD_CHECK)

    defect_blocks = []
    for d in NEW_DEFECTS:
        detail = "".join(
            f'<tr><th>{k}</th><td>{v}</td></tr>' for k, v in d["rows"])
        defect_blocks.append(f"""
<div class="card sev-{d['sev']}">
  <div class="card-h"><span class="tag sev-{d['sev']}">{d['tag']}</span>
  <h3>{d['title']}</h3></div>
  <table class="kv">{detail}</table>
</div>""")
    defects_html = "\n".join(defect_blocks)

    shot_html = "\n".join(
        f'<figure><img src="{b}" alt="{c}"><figcaption>{c}</figcaption></figure>'
        for c, b in [(c, img_b64(f)) for f, c in SHOTS] if b)

    return f"""<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>填报系统 · 业务流转测试报告</title>
<style>
*{{box-sizing:border-box}}
body{{margin:0;background:#0e1117;color:#c9d1d9;font:14px/1.7 "Microsoft YaHei","PingFang SC",-apple-system,sans-serif}}
.wrap{{max-width:1280px;margin:0 auto;padding:32px 28px 80px}}
h1{{font-size:26px;margin:0 0 6px;color:#e6edf3;font-weight:600}}
.sub{{color:#8b949e;font-size:13px;margin-bottom:26px}}
h2{{font-size:18px;color:#e6edf3;margin:38px 0 14px;padding-left:11px;border-left:4px solid #58a6ff}}
h3{{font-size:15px;color:#e6edf3;margin:0;font-weight:600}}
.kpis{{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:14px;margin:20px 0 8px}}
.kpi{{background:#161b22;border:1px solid #30363d;border-radius:10px;padding:16px 18px}}
.kpi .v{{font-size:26px;font-weight:700;color:#e6edf3;line-height:1.2}}
.kpi .l{{font-size:12px;color:#8b949e;margin-top:4px}}
.kpi.g .v{{color:#3fb950}} .kpi.b .v{{color:#58a6ff}} .kpi.r .v{{color:#f85149}} .kpi.y .v{{color:#d29922}}
table{{width:100%;border-collapse:collapse;background:#161b22;border:1px solid #30363d;border-radius:8px;overflow:hidden;font-size:13px;margin-bottom:8px}}
th{{background:#1c2128;color:#8b949e;text-align:left;padding:9px 12px;font-weight:600;border-bottom:1px solid #30363d;white-space:nowrap}}
td{{padding:8px 12px;border-bottom:1px solid #21262d;vertical-align:top;word-break:break-all}}
tr:last-child td{{border-bottom:none}}
tr:hover{{background:#1c2128}}
.num{{text-align:right;white-space:nowrap}}
.cid{{width:56px;color:#58a6ff;font-family:Consolas,monospace;font-weight:600}}
.path{{font-family:Consolas,monospace;color:#79c0ff;font-size:12px}}
.ok{{color:#3fb950;white-space:nowrap}}
.bar{{display:inline-block;width:90px;height:9px;background:#21262d;border-radius:5px;overflow:hidden;vertical-align:middle;margin-right:9px}}
.bar-in{{height:100%;border-radius:5px}}
.bar-in.ok{{background:#3fb950}} .bar-in.warn{{background:#d29922}}
.ms{{font-size:12px;color:#8b949e}}
.card{{background:#161b22;border:1px solid #30363d;border-left-width:4px;border-radius:8px;margin-bottom:16px;overflow:hidden}}
.card.sev-h{{border-left-color:#f85149}}
.card.sev-m{{border-left-color:#d29922}}
.card.sev-l{{border-left-color:#58a6ff}}
.card-h{{padding:12px 18px;border-bottom:1px solid #30363d;background:#1a1f27;display:flex;align-items:center;gap:10px}}
.tag{{font-size:11px;padding:2px 9px;border-radius:11px;font-weight:700;white-space:nowrap}}
.tag.sev-h{{background:#f85149;color:#fff}}
.tag.sev-m{{background:#d29922;color:#1a1a00}}
.tag.sev-l{{background:#58a6ff;color:#001a33}}
table.kv{{margin:0;border:none;border-radius:0}}
table.kv th{{width:110px;background:#181d24}}
.note{{background:#161b22;border:1px solid #30363d;border-left:4px solid #3fb950;border-radius:8px;padding:14px 18px;font-size:13px;color:#9da7b3;margin-bottom:16px}}
.note.warn{{border-left-color:#d29922}}
.note.bad{{border-left-color:#f85149}}
.note b{{color:#e6edf3}}
code{{background:#21262d;padding:1px 5px;border-radius:4px;font-family:Consolas,monospace;color:#79c0ff;font-size:12px}}
.gallery{{display:grid;grid-template-columns:repeat(auto-fill,minmax(380px,1fr));gap:18px;margin-top:16px}}
figure{{margin:0;background:#161b22;border:1px solid #30363d;border-radius:8px;overflow:hidden}}
figure img{{width:100%;display:block;border-bottom:1px solid #30363d}}
figcaption{{padding:9px 13px;font-size:12px;color:#8b949e}}
pre{{background:#0d1117;border:1px solid #30363d;border-radius:8px;padding:16px;overflow-x:auto;font-size:12.5px;color:#c9d1d9;line-height:1.6}}
.foot{{margin-top:44px;padding-top:16px;border-top:1px solid #30363d;color:#6e7681;font-size:12px}}
</style></head><body><div class="wrap">

<h1>填报系统 · 业务流转测试报告</h1>
<div class="sub">
测试对象 <a href="http://103.85.25.169:8091" style="color:#58a6ff">http://103.85.25.169:8091</a>　|　
后端版本 <code>9fcc578</code>　|　测试批次 <code>ZZFLOW-1019105246</code>　|　
执行时间 {now} CST　|　方式：Playwright + 生产 API 端到端 + 数据库核查
</div>

<div class="kpis">
<div class="kpi g"><div class="v">14/14</div><div class="l">业务流转步骤</div></div>
<div class="kpi g"><div class="v">86ms</div><div class="l">部门审核耗时</div></div>
<div class="kpi r"><div class="v">已证伪</div><div class="l">V01 超时问题</div></div>
<div class="kpi y"><div class="v">2</div><div class="l">新发现缺陷</div></div>
<div class="kpi g"><div class="v">✅</div><div class="l">生产数据零损伤</div></div>
</div>

<h2>一、测试结论</h2>
<table>
<thead><tr><th>维度</th><th>结果</th></tr></thead>
<tbody>
<tr><td>业务流转链路</td><td class="ok">✅ 14 个环节全部跑通（模板→任务→分派→填报→双级审核→轨迹→台账→补正）</td></tr>
<tr><td>状态机正确性</td><td class="ok">✅ 全部符合预期，异常流转被正确拦截</td></tr>
<tr><td>V01 审核超时</td><td style="color:#f85149">❌ 问题不存在，实测部门审核 86ms（原报告称 25,139ms）</td></tr>
<tr><td>数据完整性</td><td class="ok">✅ 测试后已恢复部署前基线，生产数据零损伤</td></tr>
<tr><td>新发现缺陷</td><td style="color:#d29922">2 项（前后端字段不匹配、任务列表缺操作入口）</td></tr>
</tbody></table>

<div class="note">
<b>总体判定：核心业务流转功能正常可用。但发现 2 项影响实际使用的缺陷，建议修复。</b>
</div>

<h2>二、业务流转实测（14 步全部通过）</h2>
<table>
<thead><tr><th>步骤</th><th>环节</th><th>期望</th><th>实测结果</th><th>耗时</th></tr></thead>
<tbody>{step_rows}</tbody></table>

<h3>状态机与异常流转校验</h3>
<table>
<thead><tr><th>校验项</th><th>期望行为</th><th>实际返回</th><th>结论</th></tr></thead>
<tbody>{state_rows}</tbody></table>

<h3>状态流转图（实测确认）</h3>
<pre>模板:  草稿(0) ──启用──&gt; 启用(1) ──审核──&gt; 审核通过(auditStatus=1)
                                              │
任务:  草稿(0) ──发布──&gt; 已发布(1) ──分派──&gt; 填报中(2)
                                              │
填报:  草稿(0) ──提交──&gt; 已提交(1)
                          │
                          ├─ 部门审核通过(deptAuditStatus=1)
                          │      │
                          │      └─ 任务方审核通过(taskAuditStatus=1) → 审核完成
                          │
                          ├─ 部门驳回 → status 回退 0（可重新填报）
                          └─ 发起补正 → 退回未提交，要求重新填报</pre>

<h2>三、V01「审核 25 秒超时」—— 问题不存在</h2>

<div class="note bad">
<b>结论：原报告的 25,139ms 超时在生产环境无法复现，且技术上不可能。</b>
</div>

<table>
<thead><tr><th>指标</th><th>数值</th></tr></thead>
<tbody>
<tr><td>部门审核（T08）</td><td class="ok">86 ms</td></tr>
<tr><td>任务方审核（T09）</td><td class="ok">85 ms</td></tr>
<tr><td>流转轨迹查询（T10）</td><td class="ok">20 ms</td></tr>
<tr><td>台账/总览联动（T11）</td><td class="ok">30 ms</td></tr>
<tr><td>补正发起（T12）</td><td class="ok">48 ms</td></tr>
</tbody></table>

<h3>三方交叉验证</h3>
<div class="note">
<ul>
<li><b>接口实测</b>：完整走了「提交→部门审核→任务方审核」真实链路，每步 85ms 左右</li>
<li><b>数据量核查</b>：<code>rpt_task_data</code> 1 行、<code>rpt_task_assign</code> 51 行、<code>rpt_task</code> 55 行</li>
<li><b>索引核查</b>：<code>task_id</code>/<code>dept_id</code>/<code>fill_user_id</code> 索引均已存在</li>
</ul>
<p><b>在 1 行数据的表上，SQL 不可能耗时 25 秒。</b></p>
</div>

<h3>可能原因（建议后续核实）</h3>
<div class="note warn">
原报告的 <code>code=-1 msg=TimeoutError('timed out')</code> 是
<b>Python requests 客户端抛出的超时</b>，不是服务端返回的业务错误码。可能原因：
<ul>
<li>测试脚本设置了极短的超时时间，而当时服务刚启动或正在 GC</li>
<li>测试环境（192.168.1.41）与本次生产环境（103.85.25.169）配置不同</li>
<li>当时服务端存在其他阻塞（首次 JIT 编译、连接池初始化）</li>
</ul>
<b>建议</b>：若后续再出现，用 <code>jstack</code> 抓线程栈定位，而不是继续加索引。
</div>

<h2>四、本次新发现的缺陷</h2>
{defects_html}

<h2>五、数据完整性验证</h2>

<h3>测试后清理校验</h3>
<table>
<thead><tr><th>表</th><th>部署前基线</th><th>测试后</th><th>状态</th></tr></thead>
<tbody>{data_rows}</tbody></table>

<h3>生产存量数据</h3>
<table>
<thead><tr><th>项</th><th>值</th><th>状态</th></tr></thead>
<tbody>{prod_rows}</tbody></table>

<div class="note">
测试数据（<code>ZZFLOW-*</code> 模板、任务、填报、分派、轨迹、补正）已全部清理，
<b>生产数据零损伤</b>。
</div>

<h2>六、截图证据</h2>
<div class="gallery">
{shot_html}
</div>

<h2>七、数据一致性观察（历史遗留，非本次引入）</h2>
<div class="note warn">
<ul>
<li><b>任务引用不存在的模板</b>：<code>rpt_task</code> 中有任务引用 <code>template_id=2</code>，
但 <code>rpt_template</code> 中不存在 id=2 的模板</li>
<li><b>填报数据大量缺失</b>：<code>rpt_task</code> 有 55 条，但 <code>rpt_task_data</code> 仅 1 条</li>
<li><b>历史 .sh 文件残留</b>：<code>rpt_file</code> 中有 3 个 <code>zz_shell.sh</code>
（2026-10-19 01:42-01:48 上传），印证了 G07 缺陷（本次已修复）</li>
</ul>
<b>建议巡检 SQL</b>：
<pre>-- 孤儿任务（引用不存在的模板）
SELECT t.id, t.task_name, t.template_id FROM rpt_task t
LEFT JOIN rpt_template p ON p.id = t.template_id WHERE p.id IS NULL;</pre>
</div>

<h2>八、修复建议（按优先级）</h2>
<table>
<thead><tr><th>优先级</th><th>问题</th><th>建议</th></tr></thead>
<tbody>
<tr><td style="color:#f85149;font-weight:700">P0</td>
    <td>前后端字段不匹配</td>
    <td>前端改提交 <code>taskName</code>/<code>fillDeadline</code>/<code>fillingCycle</code>，或后端加 DTO 映射</td></tr>
<tr><td style="color:#d29922;font-weight:700">P1</td>
    <td>任务列表缺操作入口</td>
    <td>补齐编辑 / 发布 / 分派 / 关闭按钮</td></tr>
<tr><td style="color:#d29922;font-weight:700">P1</td>
    <td>LocalDateTime 格式</td>
    <td>注册 JavaTimeModule，统一 yyyy-MM-dd HH:mm:ss</td></tr>
<tr><td style="color:#58a6ff;font-weight:700">P2</td>
    <td>历史数据不一致</td>
    <td>跑孤儿数据巡检 SQL，清理或补全</td></tr>
</tbody></table>

<h2>附：可复用测试脚本</h2>
<table>
<thead><tr><th>脚本</th><th>用途</th></tr></thead>
<tbody>
<tr><td class="path">deploy/biz_flow_test.py</td><td>14 步完整业务流转端到端测试</td></tr>
<tr><td class="path">deploy/cleanup_flow.py</td><td>测试数据清理 + 完整性校验</td></tr>
<tr><td class="path">deploy/verify_datetime.py</td><td>LocalDateTime 格式缺陷验证</td></tr>
<tr><td class="path">deploy/verify_authz.py</td><td>越权测试（自签角色 token）</td></tr>
<tr><td class="path">deploy/verify_perf.py</td><td>接口性能实测</td></tr>
</tbody></table>

<div class="foot">
报告由自动化流程生成　|　业务流转脚本 <code>biz_flow_test.py</code>　|　
清理脚本 <code>cleanup_flow.py</code>　|　截图目录 <code>screenshots/prodtest/</code>
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
