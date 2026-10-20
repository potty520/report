#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""修正版：把 schema_patch.sql 转成 H2 兼容 DDL 追加到 schema.sql（先清理旧段）"""
import re

SRC = r"E:\C++\report_src\report-master\backend\src\main\resources\schema_patch.sql"
DST = r"E:\C++\report_src\report-master\backend\src\main\resources\schema.sql"
MARK = "-- >>> AUTO-GENERATED: MISSING TABLES"


def split_blocks(text: str):
    """按行扫描，切出每个 CREATE TABLE 块（以分号结束）"""
    lines = text.splitlines()
    blocks, cur, capturing = [], [], False
    for ln in lines:
        if re.match(r"\s*CREATE TABLE\s+\w+\s*\(", ln) or re.match(r"\s*CREATE TABLE\s+`", ln):
            if cur:
                blocks.append("\n".join(cur).strip())
            cur, capturing = [ln], True
            continue
        if capturing:
            cur.append(ln)
            if ln.rstrip().endswith(";"):
                blocks.append("\n".join(cur).strip())
                cur, capturing = [], False
    if cur:
        blocks.append("\n".join(cur).strip())
    return [b for b in blocks if b.rstrip().endswith(";")]


def convert_block(ddl: str) -> str:
    ddl = ddl.replace("`", "")
    ddl = re.sub(r"\s+COMMENT\s+'[^']*'", "", ddl)
    m = re.search(r"CREATE TABLE\s+(\w+)", ddl)
    if not m:
        return ""
    table = m.group(1)
    ddl = re.sub(r"CREATE TABLE\s+\w+\s*\(",
                 f"CREATE TABLE IF NOT EXISTS {table} (", ddl, count=1)
    ddl = re.sub(r"\s*AUTO_INCREMENT=\d+", "", ddl)
    ddl = re.sub(r"\)\s*ENGINE=.*$", ")", ddl, flags=re.S).strip()

    # 内联 KEY / UNIQUE KEY → 独立 CREATE INDEX
    index_stmts = []
    def key_repl(mo):
        unique = "UNIQUE " if mo.group(1).strip().upper() == "UNIQUE" else ""
        index_stmts.append(
            f"CREATE {unique}INDEX IF NOT EXISTS {mo.group(2)} ON {table}({mo.group(3)});")
        return ""
    ddl = re.sub(r",?\s*(UNIQUE\s+KEY|KEY)\s+(\w+)\s*\(([^)]*)\)", key_repl, ddl)

    if not ddl.rstrip().endswith(";"):
        ddl = ddl.rstrip() + ";"
    if index_stmts:
        ddl += "\n" + "\n".join(index_stmts)
    return ddl


def main():
    patch = open(SRC, encoding="utf-8").read()
    schema = open(DST, encoding="utf-8").read()

    # 1) 清掉之前错误追加的段落
    if MARK in schema:
        schema = schema[:schema.index(MARK)]
        # 去掉残留的标题边框行
        schema = re.sub(r"\n-- =+\s*$", "\n", schema.rstrip()) + "\n"
        print("已移除旧的自动生成段")

    blocks = split_blocks(patch)
    print(f"从 patch 解析出 {len(blocks)} 个 CREATE TABLE 块")

    converted, names = [], []
    for b in blocks:
        c = convert_block(b)
        if not c:
            continue
        nm = re.search(r"CREATE TABLE IF NOT EXISTS (\w+)", c)
        if not nm:
            continue
        # 过滤误匹配的注释行
        if not re.search(r"CREATE TABLE IF NOT EXISTS \w+ \(", c):
            continue
        converted.append(c)
        names.append(nm.group(1))

    print(f"转换成功 {len(converted)} 张：{', '.join(names)}")

    header = f"""
-- ============================================================
{MARK}
-- 来源：生产库 SHOW CREATE TABLE（MySQL），已转成 H2/MySQL 双兼容语法
-- 解决：原 schema.sql 只建 13 张表，代码读写的下列表未定义，
--       缺失时写操作被 catch(Exception ignored) 吞掉 → 功能静默失效
-- ============================================================
"""
    content = schema.rstrip() + "\n" + header + "\n\n".join(converted) + "\n"
    open(DST, "w", encoding="utf-8").write(content)
    print(f"✅ schema.sql 现在 {len(content.splitlines())} 行")


if __name__ == "__main__":
    main()
