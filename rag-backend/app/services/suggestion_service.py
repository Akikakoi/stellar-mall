"""AI 欢迎页动态提问示例服务。

思路：
  1. 从 Mall C 端商品接口随机抽取真实上架商品（随机页 + 随机采样）；
  2. 让 LLM 基于商品名/价格/SKU 数生成口语化问题，禁止编造参数；
  3. 生成结果写入 Redis 缓存池（Redis 不可用时降级进程内缓存），TTL 6 小时；
  4. 每次请求从池中随机抽取，保证每次打开欢迎页的示例都不一样；
  5. LLM 失败、超时或无商品时，回退到真实商品模板池/静态兜底，接口始终可用。
"""
from __future__ import annotations

import asyncio
import json
import math
import random
import time
from typing import Any, Dict, List, Optional

from app.agent.tools.mall_client import call_mall
from app.config import settings
from app.core.logger import logger
from app.rag.llm import get_langchain_chat

# 缓存池配置
_CACHE_KEY = "suggest:pool:v1"
_CACHE_TTL_SECONDS = 6 * 3600
# 池容量：9 个商品 × 2 个问题，抽 6 条时有足够组合空间
_PRODUCT_SAMPLE_SIZE = 9
_POOL_SIZE = 18
_PRODUCT_PAGE_SIZE = 20
_MAX_RANDOM_PAGE = 30
# 后台生成不阻塞首屏，超时可以放宽
_LLM_TIMEOUT_SECONDS = 30.0

# 固定保留的服务类问题（每次随机选各一条）
_ORDER_PROMPTS = [
    "帮我查一下最近订单的状态",
    "我的订单现在是什么状态？",
    "帮我看看最近的订单到哪一步了",
]
_AFTER_SALE_PROMPTS = [
    "我想申请售后退货，怎么操作？",
    "商品有问题怎么申请售后？",
    "退货流程和退款时间是怎样的？",
]

# Mall 接口不可用时的静态兜底（不包含具体商品名，避免展示不存在的商品）
_STATIC_PROMPTS = [
    "帮我推荐几款热门商品",
    "最近有哪些新品上架？",
    "怎么查看商品的规格参数？",
    "平台商品支持几天无理由退货？",
    "商品保修政策是怎么样的？",
    "有哪些优惠活动值得关注？",
]

# 商品类型 → 模板库；未命中走通用模板
_TEMPLATES_APPLIANCE = [
    "「{name}」现在多少钱？有哪些配置可选？",
    "「{name}」的保修和安装政策是什么？",
    "「{name}」的核心参数和尺寸是多少？",
    "「{name}」的耗电和噪音表现怎么样？",
]
_TEMPLATES_DIGITAL = [
    "「{name}」现在多少钱？有哪些版本可选？",
    "「{name}」的核心规格参数是什么？",
    "「{name}」的续航和接口配置怎么样？",
    "「{name}」和同价位产品比有什么优势？",
]
_TEMPLATES_GENERIC = [
    "「{name}」现在多少钱？有哪些配置可选？",
    "「{name}」的核心参数和规格是什么？",
    "「{name}」的保修和售后政策是什么？",
    "「{name}」适合什么使用场景？",
]
_APPLIANCE_HINTS = ("冰箱", "空调", "洗衣机", "电视", "油烟机", "洗碗机", "热水器")
_DIGITAL_HINTS = ("手机", "电脑", "平板", "耳机", "手表", "相机", "显示器", "音箱")

# Redis / 进程内缓存
_redis_client: Any = None
_redis_failed = False
_memory_pool: Optional[Dict[str, Any]] = None
_memory_pool_expire = 0.0
_lock = asyncio.Lock()
_llm_task: Optional[asyncio.Task] = None


async def get_suggestions(limit: int = 8, mall_token: Optional[str] = None) -> Dict[str, Any]:
    """返回欢迎页提问示例：product 问题在前，服务类问题在后。

    缓存未命中时先返回真实商品模板池（毫秒级），同时后台生成 LLM 问题池，
    下一次打开即可拿到更自然的问题，避免首屏被 LLM 延迟拖住。
    """
    limit = max(4, min(limit, 10))
    service_count = 2
    product_count = max(0, limit - service_count)

    pool = await _load_pool()
    if pool is None:
        async with _lock:
            # 双重检查：并发首屏请求只生成一次
            pool = await _load_pool()
            if pool is None:
                products = await asyncio.to_thread(_fetch_products, mall_token)
                if not products:
                    return _static_result(limit)
                pool = _build_template_pool(products)
                await _save_pool(pool)
                _schedule_llm_pool(products)

    items = _pick_product_items(pool.get("items") or [], product_count)
    items.append(random.choice(_ORDER_PROMPTS))
    items.append(random.choice(_AFTER_SALE_PROMPTS))

    return {
        "items": items[:limit],
        "source": pool.get("source", "template"),
        "generated_at": pool.get("generated_at"),
    }


# =========================== 真实商品取样 ===========================

def _fetch_products(mall_token: Optional[str]) -> List[Dict[str, Any]]:
    """同步调用 Mall C 端商品接口，随机页取样。

    先查总条数拿到总页数，再随机取一页，避免每次都取首页那几个商品。
    """
    try:
        first = call_mall(
            "GET", "/user/spu/page", mall_token=mall_token,
            params={"page": 1, "pageSize": 1, "status": 1},
        )
        if not first.get("ok"):
            logger.warning(f"[Suggest] 商品总数查询失败: {first.get('message')}")
            return []
        total = int(_inner_data(first).get("total") or 0)
        if total <= 0:
            return []

        last_page = max(1, math.ceil(total / _PRODUCT_PAGE_SIZE))
        page = random.randint(1, min(last_page, _MAX_RANDOM_PAGE))
        result = call_mall(
            "GET", "/user/spu/page", mall_token=mall_token,
            params={"page": page, "pageSize": _PRODUCT_PAGE_SIZE, "status": 1},
        )
        records = _inner_data(result).get("records") or []
        if not records and page != 1:
            # 随机页落空（商品被删/下架）时回退第一页
            result = call_mall(
                "GET", "/user/spu/page", mall_token=mall_token,
                params={"page": 1, "pageSize": _PRODUCT_PAGE_SIZE, "status": 1},
            )
            records = _inner_data(result).get("records") or []

        products: List[Dict[str, Any]] = []
        for item in records:
            name = str(item.get("name") or "").strip()
            if not name:
                continue
            products.append({
                "spu_id": item.get("id"),
                "name": name,
                "min_price": item.get("minPrice"),
                "max_price": item.get("maxPrice"),
                "sku_count": item.get("skuCount"),
            })
        random.shuffle(products)
        return products[:_PRODUCT_SAMPLE_SIZE]
    except Exception as e:  # noqa: 商品接口异常时不影响欢迎页
        logger.warning(f"[Suggest] 商品取样异常: {e}")
        return []


def _inner_data(payload: Dict[str, Any]) -> Dict[str, Any]:
    """拆解 Mall 统一响应 {code,msg,data:{total,records}}。"""
    data = payload.get("data") if isinstance(payload, dict) else None
    if not isinstance(data, dict) or data.get("code") not in (1, 200):
        return {}
    inner = data.get("data")
    return inner if isinstance(inner, dict) else {}


# =========================== 问题池生成 ===========================

async def _build_llm_pool(products: List[Dict[str, Any]]) -> Optional[Dict[str, Any]]:
    """调用 LLM 生成问题池；失败/超时返回 None 交给模板兜底。"""
    if not settings.LLM_MODEL_NAME or not settings.DASHSCOPE_API_KEY:
        return None
    try:
        llm = get_langchain_chat()
        from langchain_core.messages import HumanMessage, SystemMessage

        resp = await asyncio.wait_for(
            llm.ainvoke([
                SystemMessage(content=_build_system_prompt()),
                HumanMessage(content=_build_user_prompt(products)),
            ]),
            timeout=_LLM_TIMEOUT_SECONDS,
        )
        questions = _parse_questions(_content_to_text(resp.content))
        items = _validate_questions(questions, products)
        if len(items) < 12:
            logger.warning(f"[Suggest] LLM 产出有效问题不足（{len(items)} 条），使用模板兜底")
            return None
        return {"items": items[:_POOL_SIZE], "source": "llm", "generated_at": time.time()}
    except Exception as e:  # noqa: 超时 / Key 缺失 / 解析失败都走兜底
        logger.warning(f"[Suggest] LLM 生成失败，使用模板兜底: {e}")
        return None


def _schedule_llm_pool(products: List[Dict[str, Any]]) -> None:
    """后台生成 LLM 问题池并覆盖模板缓存，不阻塞当前请求。"""
    global _llm_task
    if _llm_task is not None and not _llm_task.done():
        return
    try:
        loop = asyncio.get_running_loop()
    except RuntimeError:
        return
    _llm_task = loop.create_task(_generate_and_cache(products))


async def _generate_and_cache(products: List[Dict[str, Any]]) -> None:
    try:
        pool = await _build_llm_pool(products)
        if pool is not None:
            await _save_pool(pool)
            logger.info(f"[Suggest] LLM 问题池已更新: {len(pool['items'])} 条")
    except Exception as e:  # noqa
        logger.warning(f"[Suggest] 后台生成问题池失败: {e}")


async def warm_pool() -> None:
    """服务启动时预热问题池（不阻塞启动）；已有 LLM 池则跳过。"""
    try:
        existing = await _load_pool()
        if existing is not None and existing.get("source") == "llm":
            return
        products = await asyncio.to_thread(_fetch_products, None)
        if not products:
            return
        pool = await _build_llm_pool(products)
        await _save_pool(pool or _build_template_pool(products))
    except Exception as e:  # noqa
        logger.warning(f"[Suggest] 预热问题池失败: {e}")


def start_warmup() -> Optional[asyncio.Task]:
    """把预热任务登记到 _llm_task，避免首屏请求再触发一次重复生成。"""
    global _llm_task
    try:
        loop = asyncio.get_running_loop()
    except RuntimeError:
        return None
    if _llm_task is None or _llm_task.done():
        _llm_task = loop.create_task(warm_pool())
    return _llm_task


def _build_system_prompt() -> str:
    return (
        "你是电商平台的导购助手。请基于给定的真实商品，生成用户在 AI 助手欢迎页"
        "可能点击的提问示例。\n"
        "必须遵守：\n"
        "1. 只输出 JSON 数组，不要 Markdown、不要解释；\n"
        "2. 每个问题必须完整包含给定商品名称（原样照抄，不要改写或简称）；\n"
        "3. 只能使用给出的商品名、价格区间、SKU 数，不得编造参数、库存、优惠或活动；\n"
        "4. 每个商品生成 2 个不同角度的问题，可从价格配置、规格参数、选购建议、"
        "保修售后、使用场景中选择；\n"
        "5. 口语化、简洁，8~24 个字，以问号结尾；\n"
        "6. 不得重复，不得用“这款商品”等代称，必须写明商品名。\n"
        f"7. 总共生成 {_POOL_SIZE} 个问题。"
    )


def _build_user_prompt(products: List[Dict[str, Any]]) -> str:
    lines = ["商品列表："]
    for idx, p in enumerate(products, 1):
        parts = [f"名称：{p['name']}"]
        mi, ma = p.get("min_price"), p.get("max_price")
        if mi is not None and ma is not None:
            parts.append(f"价格区间：¥{mi}~¥{ma}")
        elif mi is not None:
            parts.append(f"参考价：¥{mi}")
        if p.get("sku_count") is not None:
            parts.append(f"SKU 数：{p['sku_count']}")
        lines.append(f"{idx}. " + "；".join(parts))
    lines.append(f"\n请严格输出包含 {_POOL_SIZE} 个问题的 JSON 数组。")
    return "\n".join(lines)


def _content_to_text(content: Any) -> str:
    """兼容 str / list[dict] 两种 ChatModel 返回内容。"""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts = []
        for block in content:
            if isinstance(block, dict):
                text = block.get("text") or block.get("content")
                if text:
                    parts.append(str(text))
            elif isinstance(block, str):
                parts.append(block)
        return "".join(parts)
    return str(content or "")


def _parse_questions(text: str) -> List[str]:
    text = (text or "").strip()
    start, end = text.find("["), text.rfind("]")
    if start < 0 or end <= start:
        return []
    try:
        data = json.loads(text[start:end + 1])
    except Exception:
        return []
    if isinstance(data, dict):
        data = data.get("questions") or data.get("items") or []
    if not isinstance(data, list):
        return []
    return [str(x).strip() for x in data if isinstance(x, (str, int, float))]


def _validate_questions(questions: List[str], products: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """校验：必须包含真实商品名、长度合理、不重复，并回填 spu_id。"""
    names = sorted(
        [(str(p["name"]), p.get("spu_id")) for p in products],
        key=lambda x: len(x[0]), reverse=True,
    )
    seen = set()
    items: List[Dict[str, Any]] = []
    for q in questions:
        q = q.strip()
        if not (4 <= len(q) <= 60) or q in seen:
            continue
        spu_id = next((sid for name, sid in names if name in q), None)
        if spu_id is None:
            continue
        seen.add(q)
        items.append({"text": q, "spu_id": spu_id})
    return items


# =========================== 模板兜底池 ===========================

def _build_template_pool(products: List[Dict[str, Any]]) -> Dict[str, Any]:
    items: List[Dict[str, Any]] = []
    for p in products:
        for tpl in _pick_templates(p["name"]):
            items.append({"text": tpl.format(name=p["name"]), "spu_id": p.get("spu_id")})
    return {"items": items, "source": "template", "generated_at": time.time()}


def _pick_templates(name: str) -> List[str]:
    if any(h in name for h in _APPLIANCE_HINTS):
        bank = _TEMPLATES_APPLIANCE
    elif any(h in name for h in _DIGITAL_HINTS):
        bank = _TEMPLATES_DIGITAL
    else:
        bank = _TEMPLATES_GENERIC
    return random.sample(bank, min(2, len(bank)))


# =========================== 抽样与兜底 ===========================

def _pick_product_items(items: List[Dict[str, Any]], count: int) -> List[str]:
    """按商品去重后随机抽 count 条，尽量保证每条来自不同商品。"""
    if count <= 0:
        return []
    by_spu: Dict[Any, List[str]] = {}
    for item in items:
        text = str(item.get("text") or "").strip()
        if text:
            by_spu.setdefault(item.get("spu_id"), []).append(text)

    spu_ids = list(by_spu.keys())
    random.shuffle(spu_ids)
    picked = [random.choice(by_spu[sid]) for sid in spu_ids[:count]]
    if len(picked) < count:
        rest = [t for sid in spu_ids[count:] for t in by_spu[sid]]
        random.shuffle(rest)
        picked.extend(rest[:count - len(picked)])
    return picked


def _static_result(limit: int) -> Dict[str, Any]:
    items = list(_STATIC_PROMPTS)
    items.append(random.choice(_ORDER_PROMPTS))
    items.append(random.choice(_AFTER_SALE_PROMPTS))
    return {"items": items[:limit], "source": "static", "generated_at": time.time()}


# =========================== 缓存 ===========================

async def _get_redis() -> Any:
    global _redis_client, _redis_failed
    if _redis_client is not None:
        return _redis_client
    if _redis_failed or not settings.LLM_CACHE_REDIS_ENABLED:
        return None
    try:
        import redis.asyncio as aioredis
        client = aioredis.from_url(
            settings.LLM_CACHE_REDIS_URL,
            max_connections=4,
            socket_timeout=1,
            socket_connect_timeout=0.5,
            decode_responses=True,
        )
        await client.ping()
        _redis_client = client
        return client
    except Exception as e:  # noqa
        _redis_failed = True
        logger.warning(f"[Suggest] Redis 不可用，降级进程内缓存: {e}")
        return None


async def _load_pool() -> Optional[Dict[str, Any]]:
    client = await _get_redis()
    if client is not None:
        try:
            raw = await client.get(_CACHE_KEY)
            if raw:
                data = json.loads(raw)
                if isinstance(data, dict) and data.get("items"):
                    return data
        except Exception as e:  # noqa
            logger.warning(f"[Suggest] 读取缓存失败: {e}")
    if _memory_pool and time.time() < _memory_pool_expire:
        return _memory_pool
    return None


async def _save_pool(pool: Dict[str, Any]) -> None:
    global _memory_pool, _memory_pool_expire
    _memory_pool = pool
    _memory_pool_expire = time.time() + _CACHE_TTL_SECONDS
    client = await _get_redis()
    if client is None:
        return
    try:
        await client.setex(_CACHE_KEY, _CACHE_TTL_SECONDS, json.dumps(pool, ensure_ascii=False))
    except Exception as e:  # noqa
        logger.warning(f"[Suggest] 写入缓存失败: {e}")
