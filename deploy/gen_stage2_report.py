#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成「第二阶段：并发加固与工程质量修复报告」HTML"""
import os
import time

OUT = r"E:\C++\report_src\report-master\第二阶段修复报告.html"

FIXED = [
    ("C1", "严重", "状态流转全是 check-then-act（先查后改）",
     "提交 / 部门审核 / 任务方审核 / 审核通过驳回 / 任务发布 / 任务关闭 / 保存草稿 全部改为"
     "「带前置状态条件的原子 UPDATE」，用受影响行数判断是否抢占成功；抢不到直接报「状态已变更」",
     "✅ 并发 8 线程实测：只 1 次成功，其余 7 次被正确拦截"),
    ("C2", "严重", "已提交数据可被「保存草稿」静默打回",
     "fillData 增加状态守卫 + 条件更新：status=1 时拒绝保存草稿；并发的保存/提交不再互相踩踏",
     "✅ 实测返回「该数据已提交，不能直接保存草稿」"),
    ("C3", "严重", "定时任务无跨实例互斥",
     "新增 ScheduleLockService：基于 rpt_schedule_lock 表唯一键做 INSERT 抢占式加锁"
     "（30 分钟超时自动清理崩溃残留锁），无需引入 ShedLock 等新依赖；"
     "同时把「超期扫描」从周期生成中解耦，各自独立加锁",
     "✅ 锁表在任务结束后自动清空（实测记录数 0）"),
    ("C4", "高", "周期任务生成靠 selectCount 预判，并发失效",
     "插入改为捕获 DuplicateKeyException 即跳过，依赖唯一约束 uk_periodic 兜底；"
     "子任务同步复制 AUDIT 分派（原实现只复制 FILL，导致子任务没有审核人）",
     "✅ 并发 8 线程生成后子任务零重复"),
    ("S1", "严重", "schema.sql 只建 13 张表，10 张在用表未定义",
     "从生产库 SHOW CREATE TABLE 导出 10 张表的真实 DDL（转 H2/MySQL 双兼容）补齐；"
     "另新增 rpt_schedule_lock",
     "✅ 纯净库首次启动成功（H2 实测 Started in 5.7s）"),
    ("S2", "高", "schema.sql 与生产库列不一致",
     "对比 information_schema 逐列核对，补齐 rpt_task(+10 列)、rpt_task_data(+15 列)、"
     "rpt_template(+2 列)",
     "✅ 逐表比对：7 张核心表列数/列名与生产 100% 一致"),
    ("T1", "高", "零单元测试",
     "引入 spring-boot-starter-test；新增 74 个测试：密码策略 14、IP 解析 11、"
     "SQL 校验 49（覆盖写操作/UNION/敏感表/元数据/危险函数/多语句/合法放行）",
     "✅ 74/74 通过"),
    ("T2", "高", "SQL 校验误杀字符串字面量（测试发现）",
     "校验前先掩码 '...' / \"...\" 内容：既避免 remark='please delete this' 这类正常值被误杀，"
     "也避免字符串里的分号被误判为多语句；字符串外的关键字仍可检出",
     "✅ 49 个 SQL 校验用例全通过（含 10 个合法放行用例）"),
]

VERIFY = [
    ("C3 并发发布", "8 线程 → 1 成功 / 7 拒绝", "✅", "只允许草稿状态发布"),
    ("C1 并发提交", "8 线程 → 1 成功 / 7 拒绝", "✅", "「数据已提交或状态已变更」"),
    ("C2 并发部门审核", "8 线程 → 1 成功 / 7 拒绝", "✅", "「数据状态已变更，可能已被他人审核」"),
    ("C2b 自审拦截", "403 拒绝", "✅", "「不能审核自己提交的数据」"),
    ("C5 已提交打回草稿", "拒绝", "✅", "「请等待审核驳回」"),
    ("C4 并发周期任务", "子任务零重复", "✅", "并发 8 线程生成后 DB 校验无重复"),
    ("锁表清理", "记录数 0", "✅", "任务结束后锁自动释放"),
    ("业务闭环回归", "14 / 14 通过", "✅", "安全加固未破坏正常流程"),
    ("单元测试", "74 / 74 通过", "✅", "mvn test 全绿"),
    ("H2 纯净库启动", "Started in 5.7s", "✅", "schema.sql 完整可用"),
    ("生产数据", "零损伤", "✅", "task 55 / data 1 / assign 51 / process 296"),
]

PENDING = [
    ("双通道审核字段二选一",
     "deptAuditStatus/taskAuditStatus（双级）与 auditStatus（单级）两套口径仍在并存。"
     "本次已给两者都加了并发保护与状态守卫，但「业务上到底走单级还是双级」必须由业务方确认，"
     "代码层面无法替业务决定。"),
    ("台账统计口径",
     "当前台账按「下发部门（t.dept_id）」统计，而业务语义可能是「填报部门」。"
     "已加部门隔离（非超管只看本部门），但口径变更会改变历史报表数字，需业务确认后再改。"),
    ("状态常量收敛为枚举",
     "任务/数据状态仍是裸数字（0/1/2/3/4）。改动会扩散到 100+ 处比较与前端，"
     "建议与前端常量一起治理，单独一轮。"),
    ("Controller 吞异常（121 处）",
     "catch(Exception e) { return Result.error(e.getMessage()) } 会把内部异常细节透出给前端，"
     "已用 Result.sanitizeMessage 兜底部分接口。全量治理会改变前端错误处理契约，需前端配合。"),
    ("多实例部署的其他前提",
     "定时任务已做互斥，但文件存储仍是本地磁盘（/opt/report/files），多实例需换共享存储或对象存储。"),
]


def sev_class(s):
    return {"严重": "sev-1", "高": "sev-2", "中": "sev-3"}.get(s, "sev-4")


def build():
    now = time.strftime("%Y-%m-%d %H:%M")

    fixed_rows = "".join(
        f'<tr><td class="cid">{c}</td><td><span class="pill {sev_class(s)}">{s}</span></td>'
        f'<td><b>{p}</b></td><td class="fix">{f}</td><td class="ok">{v}</td></tr>'
        for c, s, p, f, v in FIXED)

    verify_rows = "".join(
        f'<tr><td>{n}</td><td>{r}</td><td class="ok">{i}</td><td class="fix">{d}</td></tr>'
        for n, r, i, d in VERIFY)

    pending_rows = "".join(
        f'<tr><td><b>{n}</b></td><td class="fix">{d}</td></tr>' for n, d in PENDING)

    return f"""<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>填报系统 · 第二阶段修复报告（并发加固与工程质量）</title>
<style>
*{{box-sizing:border-box}}
body{{margin:0;background:#0e1117;color:#c9d1d9;font:14px/1.7 "Microsoft YaHei","PingFang SC",-apple-system,sans-serif}}
.wrap{{max-width:1400px;margin:0 auto;padding:32px 28px 80px}}
h1{{font-size:26px;margin:0 0 6px;color:#e6edf3;font-weight:600}}
.sub{{color:#8b949e;font-size:13px;margin-bottom:24px}}
h2{{font-size:18px;color:#e6edf3;margin:40px 0 14px;padding-left:11px;border-left:4px solid #58a6ff}}
.kpis{{display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:14px;margin:20px 0 8px}}
.kpi{{background:#161b22;border:1px solid #30363d;border-radius:10px;padding:15px 17px}}
.kpi .v{{font-size:25px;font-weight:700;color:#e6edf3;line-height:1.15}}
.kpi .l{{font-size:12px;color:#8b949e;margin-top:4px}}
.kpi.g .v{{color:#3fb950}} .kpi.b .v{{color:#58a6ff}} .kpi.y .v{{color:#d29922}}
table{{width:100%;border-collapse:collapse;background:#161b22;border:1px solid #30363d;border-radius:8px;overflow:hidden;font-size:13px;margin-bottom:10px}}
th{{background:#1c2128;color:#8b949e;text-align:left;padding:9px 11px;font-weight:600;border-bottom:1px solid #30363d;white-space:nowrap;font-size:12px}}
td{{padding:9px 11px;border-bottom:1px solid #21262d;vertical-align:top}}
tr:last-child td{{border-bottom:none}}
tr:hover{{background:#1c2128}}
.cid{{width:52px;color:#58a6ff;font-family:Consolas,monospace;font-weight:600;white-space:nowrap}}
.fix{{color:#9da7b3;font-size:12.5px;line-height:1.55}}
.ok{{color:#3fb950}}
.pill{{font-size:11px;padding:2px 8px;border-radius:10px;font-weight:700;white-space:nowrap}}
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
.verdict{{background:#0f2a1a;border:1px solid #238636;border-radius:10px;padding:20px 24px;margin:20px 0}}
.verdict h3{{margin:0 0 10px;color:#3fb950;font-size:17px}}
.verdict p{{margin:6px 0}}
.code{{background:#0d1117;border:1px solid #30363d;border-radius:8px;padding:14px 16px;font-family:Consolas,monospace;font-size:12px;color:#8b949e;overflow-x:auto;margin:10px 0}}
.code .kw{{color:#ff7b72}} .code .cm{{color:#8b949e}} .code .st{{color:#a5d6ff}}
.foot{{margin-top:44px;padding-top:16px;border-top:1px solid #30363d;color:#6e7681;font-size:12px}}
</style></head><body><div class="wrap">

<h1>填报系统 · 第二阶段修复报告</h1>
<div class="sub">
并发加固 · 定时任务互斥 · Schema 补全 · 单元测试　|　
项目 <a href="https://github.com/potty520/report" style="color:#58a6ff">potty520/report</a>　|　
生产 <a href="http://103.85.25.169:8091" style="color:#58a6ff">103.85.25.169:8091</a>　|　
{now} CST
</div>

<div class="verdict">
<h3>第二阶段技术项已完成（8 项修复，全部经过实测验证）</h3>
<p>第一阶段的「上线前必须项」清完后，本轮处理审计报告里的<b>并发与工程质量</b>类别：
状态流转的 check-then-act、定时任务多实例重复执行、schema 与生产不一致、零单元测试。</p>
<p>关键结果：<b>并发 8 线程下所有状态流转只允许 1 次成功</b>，<b>业务闭环 14/14 仍全通过</b>，
<b>74 个单元测试全绿</b>，生产数据零损伤。</p>
<p style="color:#d29922;margin-top:10px">剩下 5 项<b>需要业务决策或跨端配合</b>，不在本轮范围，见文末。</p>
</div>

<div class="kpis">
<div class="kpi g"><div class="v">8</div><div class="l">本轮修复项</div></div>
<div class="kpi g"><div class="v">6/6</div><div class="l">并发验证通过</div></div>
<div class="kpi g"><div class="v">74</div><div class="l">单元测试</div></div>
<div class="kpi g"><div class="v">14/14</div><div class="l">业务闭环</div></div>
<div class="kpi g"><div class="v">零损伤</div><div class="l">生产数据</div></div>
</div>

<h2>一、修复清单</h2>
<table>
<thead><tr><th>编号</th><th>级别</th><th>问题</th><th>修复方式</th><th>验证</th></tr></thead>
<tbody>{fixed_rows}</tbody></table>

<h2>二、核心改动：check-then-act → 条件 UPDATE</h2>
<div class="note">
<b>问题</b>：原来所有状态流转都是「先 SELECT 判断，再 UPDATE 写入」。两次操作之间有窗口期，
两个并发请求都读到「待审」，就都会执行更新——后写的覆盖先写的，审核结果的「谁审的、什么结论」会错乱。
</div>
<div class="code">
<span class="cm">// 修复前（有并发窗口）</span><br>
if (data.getStatus() != 1) <span class="kw">throw</span> ...;　　<span class="cm">// 读</span><br>
data.setAuditStatus(1);<br>
mapper.updateById(data);　　　　　　　　<span class="cm">// 写（覆盖他人结果）</span><br><br>
<span class="cm">// 修复后（原子抢占）</span><br>
<span class="kw">int</span> claimed = mapper.update(<span class="kw">null</span>, Wrappers.&lt;RptTaskData&gt;lambdaUpdate()<br>
　　.set(RptTaskData::getAuditStatus, 1)<br>
　　.eq(RptTaskData::getId, dataId)<br>
　　.eq(RptTaskData::getStatus, 1)　　　　　<span class="cm">// 前置条件进 WHERE</span><br>
　　.and(w -&gt; w.isNull(...).or().eq(..., 0)));<br>
<span class="kw">if</span> (claimed == 0) <span class="kw">throw new</span> RuntimeException(<span class="st">"状态已变更，请刷新后重试"</span>);
</div>
<div class="note">
覆盖范围：<b>提交填报、审核通过/驳回、部门审核、任务方审核、任务发布、任务关闭、保存草稿</b>共 7 个流转点。
</div>

<h2>三、定时任务互斥（不引入新依赖）</h2>
<div class="note">
原实现 <code>@EnableScheduling</code> 裸跑，多实例部署时每个实例每天各执行一遍，配合 check-then-insert 会重复生成子任务。
</div>
<div class="code">
<span class="cm">// 数据库唯一键抢占，无需 ShedLock</span><br>
<span class="kw">DELETE</span> FROM rpt_schedule_lock <span class="kw">WHERE</span> job_name=? <span class="kw">AND</span> locked_at &lt; NOW()-<span class="kw">INTERVAL</span> 30 <span class="kw">MINUTE</span>;<br>
<span class="kw">INSERT INTO</span> rpt_schedule_lock (job_name, instance, locked_at) <span class="kw">VALUES</span> (?,?,NOW());<br>
<span class="cm">// 抛 DuplicateKeyException 即说明其他实例正在执行 → 直接跳过</span><br>
<span class="cm">// finally {{ DELETE ... }} 释放；崩溃残留由 30 分钟超时清理</span>
</div>
<div class="note">
另外把「超期扫描」从周期生成里拆出来独立加锁——原来生成失败会把超期提醒一起跳过。
</div>

<h2>四、验证结果</h2>
<table>
<thead><tr><th>验证项</th><th>结果</th><th>状态</th><th>说明</th></tr></thead>
<tbody>{verify_rows}</tbody></table>

<h2>五、单元测试覆盖</h2>
<table>
<thead><tr><th>测试类</th><th>用例数</th><th>覆盖重点</th></tr></thead>
<tbody>
<tr><td><code>PasswordPolicyTest</code></td><td>14</td>
<td>长度边界（8/64）、字符类别、纯数字、弱口令黑名单（含大小写绕过 Admin123）</td></tr>
<tr><td><code>IpUtilsTest</code></td><td>11</td>
<td>公网对端伪造 XFF 必须忽略、内网代理才采信、X-Real-IP 优先、IPv6 归一化、超长截断</td></tr>
<tr><td><code>DataRangeServiceSqlGuardTest</code></td><td>49</td>
<td>写操作/注释拼接绕过/UNION/敏感表/元数据库/危险函数/多语句 + 10 个合法 SQL 防误杀用例</td></tr>
</tbody></table>
<div class="note">
测试过程中<b>发现并修复了一个真实缺陷</b>：原 SQL 校验会把字符串字面量里的关键字当注入，
<code>where remark = 'please delete this'</code> 这种正常业务值会被拒绝。
已改为「先掩码字符串内容再校验」，既防误杀又不降低安全性。
</div>

<h2>六、schema.sql 补全</h2>
<div class="note">
原 <code>schema.sql</code> 只建 13 张表，而代码读写了 23 张——缺失表的写操作全被
<code>catch(Exception ignored)</code> 吞掉，表现为「流转轨迹丢失、定时任务无记录、在线会话不生效」等静默失效。
</div>
<table>
<thead><tr><th>项</th><th>内容</th></tr></thead>
<tbody>
<tr><td>新增表</td><td><code>rpt_task_process</code>、<code>rpt_correct_task</code>、<code>rpt_schedule_log</code>、
<code>rpt_oper_log</code>、<code>rpt_quality_report</code>、<code>rpt_sms_log</code>、<code>rpt_volume_file</code>、
<code>rpt_statistics_rule</code>、<code>rpt_template_auth</code>、<code>rpt_online_user</code>、
<code>rpt_schedule_lock</code>（共 11 张）</td></tr>
<tr><td>补列</td><td><code>rpt_task</code> +10 列、<code>rpt_task_data</code> +15 列、<code>rpt_template</code> +2 列</td></tr>
<tr><td>一致性</td><td>与生产库逐列比对，7 张核心表列数/列名 100% 一致</td></tr>
<tr><td>可用性</td><td>H2 纯净库首次启动实测成功（Started in 5.7s）</td></tr>
</tbody></table>

<h2>七、剩余项（需业务决策或跨端配合）</h2>
<div class="note warn">
以下问题<b>代码层面无法单方面解决</b>，需要你或业务方拍板，或需要前后端同一轮治理：
</div>
<table>
<thead><tr><th>问题</th><th>说明</th></tr></thead>
<tbody>{pending_rows}</tbody></table>

<h2>八、部署与数据</h2>
<table>
<thead><tr><th>项</th><th>内容</th></tr></thead>
<tbody>
<tr><td>后端</td><td>重新构建部署，健康检查 200</td></tr>
<tr><td>数据库</td><td>新增 <code>rpt_schedule_lock</code> 表（已建）；3 个唯一约束沿用第一阶段</td></tr>
<tr><td>测试数据</td><td>清理 13 个测试任务 + 7 个测试模板，生产表全部回到基线</td></tr>
<tr><td>基线</td><td>task 55 / task_data 1 / task_assign 51 / process 296 / template 1 / data_range 26</td></tr>
</tbody></table>

<div class="foot">
报告由自动化流程生成　|　第二阶段：并发加固 + 工程质量　|　
验证脚本：<code>deploy/concurrency_test.py</code>、<code>deploy/audit_flow.py</code>、<code>deploy/compare_schema.py</code>、<code>deploy/cleanup_stage2.py</code>
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
