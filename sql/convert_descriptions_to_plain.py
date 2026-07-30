"""
将 stellar_spu 表中所有 markdown 格式的 description_md 和 description 转换为纯文本，
并更新数据库。
"""
import pymysql
import re

DB_CONFIG = {
    "host": "127.0.0.1",
    "port": 3306,
    "user": "stellar",
    "password": "123456",
    "database": "stellar_mall",
    "charset": "utf8mb4",
}


def markdown_to_plain(md_text: str) -> str:
    """将 markdown 转为纯文本段落。"""
    if not md_text:
        return ""

    lines = md_text.strip().split("\n")
    result = []
    in_table = False

    for line in lines:
        stripped = line.strip()

        # 跳过表格分隔线
        if re.match(r"^\|[-:| ]+\|$", stripped):
            in_table = False
            continue

        # 跳过空行
        if not stripped:
            if result and result[-1] != "":
                result.append("")
            continue

        # 跳过纯标题行（无实际内容）
        if re.match(r"^#{1,4}\s", stripped):
            continue

        # 去掉加粗标记
        stripped = re.sub(r"\*\*(.+?)\*\*", r"\1", stripped)

        # 去掉行内代码
        stripped = re.sub(r"`([^`]+)`", r"\1", stripped)

        # 表格行 → 逗号分隔
        if stripped.startswith("|") and stripped.endswith("|"):
            cells = [c.strip() for c in stripped.strip("|").split("|")]
            plain = "：".join(cells) if len(cells) == 2 else " | ".join(cells)
            result.append(plain)
            in_table = True
            continue

        # 列表项去掉前导符号
        stripped = re.sub(r"^[-*+]\s+", "", stripped)

        # 去掉链接，保留文字
        stripped = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", stripped)

        # 去掉 HTML 标签
        stripped = re.sub(r"<[^>]+>", "", stripped)

        if stripped:
            result.append(stripped)

    # 合并多余空行
    text = "\n".join(result)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def main():
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    cursor.execute("SELECT id, name, description, description_md FROM stellar_spu ORDER BY id")
    rows = cursor.fetchall()

    updated = 0
    skipped = 0

    for spu_id, name, desc, desc_md in rows:
        has_html = (desc or "").strip().startswith("<")
        has_md = (desc_md or "").strip().startswith("#")

        if not has_html and not has_md:
            skipped += 1
            # 可能已经是纯文本，仍然做一次清理
            continue

        new_desc = desc
        new_desc_md = desc_md

        if has_md and desc_md:
            new_desc_md = markdown_to_plain(desc_md)

        if has_html and desc:
            new_desc = markdown_to_plain(desc)

        # 如果 description 为空或太短，用 desc_md 的纯文本填充
        if not desc or len(desc.strip()) < 20:
            if desc_md:
                new_desc = markdown_to_plain(desc_md)

        changed = (new_desc != desc) or (new_desc_md != desc_md)
        if not changed:
            skipped += 1
            continue

        cursor.execute(
            "UPDATE stellar_spu SET description = %s, description_md = %s WHERE id = %s",
            (new_desc, new_desc_md, spu_id),
        )
        updated += 1
        print(f"  [{spu_id}] {name}  ✓")

    conn.commit()
    cursor.close()
    conn.close()

    print(f"\n完成：更新 {updated} 个商品，跳过 {skipped} 个。")


if __name__ == "__main__":
    main()
