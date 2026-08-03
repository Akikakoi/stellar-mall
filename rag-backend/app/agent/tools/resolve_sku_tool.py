"""智能 SKU 解析工具：根据商品名称和规格描述，自动查找匹配的 SKU ID。

支持场景：
- 用户说"把星耀 X100 Pro Max的16+512版本加入购物车" → 自动搜索商品、获取 SKU 列表、匹配规格，返回 sku_id
"""
from __future__ import annotations

import re
from typing import Optional

from langchain_core.tools import tool

from app.agent.tools.mall_client import call_mall
from app.core.logger import logger


def _name_matches(search_name: str, found_name: str) -> bool:
    """检查搜索商品名与找到的商品名是否是同一个商品。

    规则：
    - 完全相同 → True
    - 搜索词是找到名的子串，但剩余部分 ≥3 个非空格字符 → False
      （如 "星域 Stellar X1" 搜到 "星域 Stellar X1 Pro"，剩余 "Pro" 3 字符 → 不是同一个）
    - 字符 Jaccard 相似度 ≥ 0.85 → True
    - 其他 → False
    """
    import re
    s = search_name.strip()
    f = found_name.strip()
    if not s or not f:
        return False
    if s == f:
        return True

    # 子串匹配 + 剩余部分检查
    if s in f:
        remainder = f[len(s):].strip()
        # 剩余部分只是空格、标点或 1-2 个字符（如括号、空格+1字母）→ 可接受
        if len(remainder.replace(" ", "")) >= 3:
            logger.info(f"[name_match] MISMATCH: '{s}' is substring of '{f}' but remainder '{remainder}' too long")
            return False
        return True

    if f in s:
        remainder = s[len(f):].strip()
        if len(remainder.replace(" ", "")) >= 3:
            logger.info(f"[name_match] MISMATCH: '{f}' is substring of '{s}' but remainder '{remainder}' too long")
            return False
        return True

    # 字符集 Jaccard
    set_s = set(s.lower().replace(" ", ""))
    set_f = set(f.lower().replace(" ", ""))
    if not set_s:
        return False
    jaccard = len(set_s & set_f) / len(set_s | set_f)
    return jaccard >= 0.85


def _extract_spec_pattern(query: str) -> str | None:
    """从用户 query 中提取规格描述，如 "16+512"、"12+256G"、"8+128GB"。

    匹配模式：
    - 数字+数字：16+512、12+256
    - 带单位的：16+512G、12+256GB、8G+128G
    - 斜杠分隔：16/512、12/256
    - 纯尺寸：512G、1TB
    """
    patterns = [
        r'(\d{1,3})\s*\+\s*(\d{1,4})\s*(?:G|GB)?',
        r'(\d{1,3})\s*/\s*(\d{1,4})',
        r'(\d{1,4})\s*(?:G|GB|TB)\b',
    ]
    for pat in patterns:
        m = re.search(pat, query)
        if m:
            return m.group(0).strip()
    return None


def _extract_numbers(text: str) -> set[str]:
    """提取文本中的所有数字 token。"""
    return set(re.findall(r'\d+', text))


def _match_sku(skus: list, spec_pattern: str | None, product_name: str) -> tuple[int | None, str]:
    """在 SKU 列表中匹配最佳 SKU。

    匹配优先级（由高到低）：
    1. SKU name 包含完整规格字符串（如 "16+512" 出现在 SKU 名中）
    2. SKU specs 中包含与规格模式相同的数字组合（需所有数字都匹配）
    3. 如果未提供规格描述，返回默认 SKU（按 sort 排序的第一个在售 SKU）

    **重要**：如果用户提供了规格描述但无法匹配，绝不返回不匹配的 SKU！
    这会错误地把错误规格的商品加入购物车。

    Args:
        skus: SKU 列表，每条包含 id, name, specs, price, stock, status, sort
        spec_pattern: 提取出的规格模式（如 "16+512"），None 表示未指定规格
        product_name: 商品名称

    Returns:
        (sku_id_or_None, match_reason)
    """
    if not skus:
        return None, "该商品暂时没有可购买的规格"

    # 按 sort 排序，有明确的默认顺序
    skus_sorted = sorted(skus, key=lambda s: (s.get("sort") if s.get("sort") is not None else 0))
    active_skus = [s for s in skus_sorted if s.get("status") in (1, None)]
    if not active_skus:
        return None, "该商品所有规格均已下架"

    # 没有指定规格 → 返回默认 SKU（sort 排序第一个在售的）
    if not spec_pattern or not spec_pattern.strip():
        fallback = active_skus[0]
        return fallback.get("id"), f"未指定规格，使用默认 (ID={fallback.get('id')}, {fallback.get('name', '')})"

    spec_clean = spec_pattern.strip()

    # 优先级 1：SKU name 包含完整规格字符串
    for sku in active_skus:
        sku_name = str(sku.get("name", "") or "")
        if spec_clean in sku_name:
            return sku.get("id"), f"匹配 SKU 名称包含「{spec_clean}」(ID={sku.get('id')})"

    # 优先级 2：数字组合匹配（必须所有数字都吻合）
    spec_numbers = _extract_numbers(spec_clean)
    if spec_numbers:
        best_match = None
        best_count = 0
        for sku in active_skus:
            sku_name = str(sku.get("name", "") or "")
            sku_specs = str(sku.get("specs", "") or "")
            sku_nums = _extract_numbers(sku_name + " " + sku_specs)
            matched = len(spec_numbers & sku_nums)
            if matched > best_count:
                best_count = matched
                best_match = sku

        # 只有所有数字都匹配才算成功（部分匹配太危险）
        if best_match and best_count == len(spec_numbers):
            return best_match.get("id"), (
                f"规格数字全匹配 {best_count}/{len(spec_numbers)} (ID={best_match.get('id')})"
            )

    # 匹配失败：列出可选规格供用户选择
    available = [str(s.get("name", "")) for s in active_skus[:8]]
    available_str = "、".join(available) if available else "无"
    return None, (
        f"未找到匹配「{spec_clean}」的规格。"
        f"「{product_name}」可选规格: {available_str}"
    )


@tool
def resolve_sku_tool(
    product_name: str,
    spec_description: Optional[str] = None,
    mall_token: Optional[str] = None,
    user_id: Optional[int] = None,
) -> dict:
    """智能解析 SKU：根据商品名称和规格描述，自动查找匹配的 SKU ID。

    适用场景：用户说"把 XX 的 YY 版本加入购物车"，需要先找到 YY 版本对应的 SKU。

    Args:
        product_name: 商品名称，如"星耀 X100 Pro Max"
        spec_description: 规格描述，如"16+512"、"256GB"（可选，不传则返回第一个可用 SKU）
        mall_token: 商城用户 token（可选）
        user_id: 用户 ID（可选）
    """
    try:
        # 步骤 1：搜索商品
        search_result = call_mall(
            "GET", "/user/spu/page",
            mall_token=mall_token,
            params={"name": product_name, "page": 1, "pageSize": 5},
        )
        if not search_result["ok"]:
            return {"success": False, "sku_id": None, "message": f"商品搜索失败: {search_result.get('message', '')}"}

        data = search_result.get("data") or {}
        if data.get("code") not in (1, 200):
            return {"success": False, "sku_id": None, "message": data.get("msg", "商品搜索失败")}

        inner = data.get("data", {})
        records = inner.get("records", []) if isinstance(inner, dict) else []
        if not records:
            return {"success": False, "sku_id": None, "message": f"未找到商品「{product_name}」"}

        spu = records[0]
        spu_id = spu.get("id")
        spu_name = spu.get("name", product_name)

        logger.info(
            f"[resolve_sku] 搜索「{product_name}」→ SPU #{spu_id} {spu_name}"
        )

        # 名称匹配度检查：用户搜"星域 Stellar X1"但返回"星域 Stellar X1 Pro"时
        # 应提示商品不存在，而不是悄悄用近似结果
        if not _name_matches(product_name, spu_name):
            top_matches = [r.get("name", "") for r in records[:3]]
            matches_str = "、".join(top_matches)
            return {
                "success": False, "sku_id": None,
                "message": (
                    f"没有找到「{product_name}」。"
                    f"商城中最接近的商品是「{matches_str}」，"
                    f"请问您指的是其中哪一款？"
                ),
            }
        logger.info(f"[resolve_sku] name match OK: '{product_name}' ≈ '{spu_name}'")

        # 步骤 2：获取 SPU 详情（含 SKU 列表）
        detail_result = call_mall("GET", f"/user/spu/{spu_id}", mall_token=mall_token)
        if not detail_result["ok"]:
            return {"success": False, "sku_id": None, "message": "获取商品详情失败"}

        detail_data = detail_result.get("data") or {}
        if detail_data.get("code") not in (1, 200):
            return {"success": False, "sku_id": None, "message": detail_data.get("msg", "获取商品详情失败")}

        spu_detail = detail_data.get("data", {})
        skus = spu_detail.get("skus") or spu_detail.get("skuList") or []

        # 步骤 3：匹配最佳 SKU
        # 优先使用显式传入的 spec_description，否则从 product_name 中提取
        spec = spec_description or _extract_spec_pattern(product_name)
        logger.info(f"[resolve_sku] SPU #{spu_id} 含 {len(skus)} 个 SKU, spec='{spec}'")

        sku_id, match_reason = _match_sku(skus, spec, spu_name)

        if sku_id is None:
            return {"success": False, "sku_id": None, "message": match_reason}

        matched_sku = next((s for s in skus if s.get("id") == sku_id), {})
        return {
            "success": True,
            "spu_id": spu_id,
            "spu_name": spu_name,
            "sku_id": str(sku_id),
            "sku_name": matched_sku.get("name", ""),
            "sku_price": str(matched_sku.get("price", "")),
            "sku_specs": matched_sku.get("specs", ""),
            "sku_stock": matched_sku.get("stock", 0),
            "match_reason": match_reason,
            "message": (
                f"已找到「{spu_name}」的 SKU #{sku_id}"
                f"({matched_sku.get('name', '')})"
            ),
        }
    except Exception as e:
        logger.error(f"[resolve_sku] 异常: {e}")
        return {"success": False, "sku_id": None, "message": f"SKU 解析出错: {e}"}
