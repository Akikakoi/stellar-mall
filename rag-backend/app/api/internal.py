"""Mall ↔ RAG 内部同步桥接路由。

仅限 Mall 后端调用，使用共享密钥 X-Stellar-Rag-Sync-Secret 头鉴权，
不走 JWT，不走前端登录鉴权链路。
"""
from __future__ import annotations

from typing import Any, Dict, List, Optional

from fastapi import APIRouter, Depends, Header, HTTPException, status
from pydantic import BaseModel, Field

from app.config import settings
from app.rag.vector_store import ChromaVectorStore, get_vector_store

router = APIRouter()


# ---------- 请求/响应模型 ----------
class SyncSpuRequest(BaseModel):
    spu_id: int = Field(..., gt=0)
    name: str = Field(..., min_length=1, max_length=200)
    subtitle: Optional[str] = Field(default=None, max_length=300)
    category_path: Optional[str] = Field(default=None, max_length=300)
    main_image: Optional[str] = Field(default=None, max_length=500)
    description_md: str = Field(..., min_length=1)
    spec_table_markdown: Optional[str] = Field(default=None)
    tags: Optional[List[str]] = Field(default=None)
    min_price: Optional[float] = Field(default=None, ge=0)
    sku_count: Optional[int] = Field(default=None, ge=0)
    status: int = Field(default=1, ge=0, le=1)  # 1=on shelf, 0=off shelf


class SyncDocRequest(BaseModel):
    doc_id: str = Field(..., min_length=1, max_length=100)
    title: str = Field(..., min_length=1, max_length=300)
    doc_type: str = Field(default="DOC", pattern=r"^[A-Z0-9_]{2,32}$")
    content_md: str = Field(..., min_length=1)
    tags: Optional[List[str]] = Field(default=None)
    status: int = Field(default=1, ge=0, le=1)


class SyncResponse(BaseModel):
    ok: bool
    biz_type: str
    biz_id: str
    processed_chunk_count: int = 0
    message: str = ""


# ---------- 共享密钥鉴权依赖 ----------
def _require_sync_secret(
    x_stellar_rag_sync_secret: Optional[str] = Header(default=None),
) -> None:
    expected = settings.STELLAR_RAG_INTERNAL_SYNC_SECRET
    if not expected:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Server not configured: STELLAR_RAG_INTERNAL_SYNC_SECRET is empty",
        )
    if not x_stellar_rag_sync_secret:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing X-Stellar-Rag-Sync-Secret header",
        )
    # 常量时间比对，避免时序攻击
    import hmac
    if not hmac.compare_digest(x_stellar_rag_sync_secret, expected):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid X-Stellar-Rag-Sync-Secret",
        )


# ---------- 辅助：Markdown 分块 ----------
_CHUNK_SIZE = 1024
_CHUNK_OVERLAP = 200


def _split_markdown_smart(markdown: str, chunk_size: int = _CHUNK_SIZE,
                           overlap: int = _CHUNK_OVERLAP) -> List[str]:
    """尽量在段落 / 标题 / 表格边界拆分，保留语义。"""
    if not markdown:
        return []
    md = markdown.replace("\r\n", "\n")
    lines = md.split("\n")
    blocks: List[str] = []
    buf: List[str] = []
    buf_len = 0

    def flush() -> None:
        nonlocal buf, buf_len
        if buf:
            blocks.append("\n".join(buf).strip())
            buf = []
            buf_len = 0

    for line in lines:
        # 标题、表格分隔、空行 → 自然断点
        natural_break = (
            not line.strip()
            or line.startswith("#")
            or (line.startswith("|") and line.endswith("|"))
            or line.strip().startswith("---")
        )
        line_len = len(line) + 1
        if buf_len + line_len > chunk_size and buf:
            flush()
        buf.append(line)
        buf_len += line_len
        if natural_break and buf_len >= chunk_size // 2:
            flush()
    flush()

    if not blocks:
        return [md[:chunk_size]] if md else []

    # 重叠拼接：相邻块末尾 overlap 字符重叠
    merged: List[str] = []
    for i, b in enumerate(blocks):
        if i == 0:
            merged.append(b)
            continue
        prev = merged[-1][-overlap:] if overlap > 0 else ""
        merged.append(prev + b if prev else b)
    return merged


def _build_spu_document_text(req: SyncSpuRequest) -> str:
    parts: List[str] = []
    parts.append(f"# 商品：{req.name}")
    if req.subtitle:
        parts.append(f"> {req.subtitle}")
    meta_line = []
    if req.spu_id:
        meta_line.append(f"商品ID(SPU_ID)：{req.spu_id}")
    if req.category_path:
        meta_line.append(f"分类：{req.category_path}")
    if req.min_price is not None:
        meta_line.append(f"参考最低价：¥{req.min_price:.2f}")
    if req.sku_count is not None:
        meta_line.append(f"SKU 规格数：{req.sku_count}")
    if req.tags:
        meta_line.append("标签：" + "、".join(req.tags))
    if req.status == 0:
        meta_line.append("【商品已下架】")
    if meta_line:
        parts.append("**基本信息**：" + " | ".join(meta_line))
    parts.append("")
    parts.append("## 详情描述")
    parts.append(req.description_md)
    if req.spec_table_markdown:
        parts.append("\n## 规格参数")
        parts.append(req.spec_table_markdown)
    return "\n".join(parts)


def _build_doc_document_text(req: SyncDocRequest) -> str:
    parts = [f"# {req.title}"]
    meta = [f"文档ID：{req.doc_id}", f"文档类型：{req.doc_type}"]
    if req.tags:
        meta.append("标签：" + "、".join(req.tags))
    if req.status == 0:
        meta.append("【文档已停用】")
    parts.append("**元数据**：" + " | ".join(meta))
    parts.append("")
    parts.append(req.content_md)
    return "\n".join(parts)


# ---------- 路由 ----------
@router.post(
    "/sync_spu",
    response_model=SyncResponse,
    summary="Mall → RAG 同步 SPU 商品（SPU 新增/上下架/更新时调用）",
)
def sync_spu(
    req: SyncSpuRequest,
    _auth: None = Depends(_require_sync_secret),
) -> SyncResponse:
    # 先做「鉴权通过 + 返回假 chunk=0」，G2 再实现真实向量入库
    # 但为了让 metadata 用例一次性通过，这里直接实现完整的分块+upsert 逻辑：
    full_text = _build_spu_document_text(req)
    chunks = _split_markdown_smart(full_text)
    chunks = [c for c in chunks if c.strip()]

    biz_id = str(req.spu_id)
    metadata_base: Dict[str, Any] = {
        "source": "mall_spu",
        "biz_type": "SPU",
        "biz_id": biz_id,
        "spu_id": int(req.spu_id),
        "name": req.name,
        "status": int(req.status),
    }
    if req.category_path:
        metadata_base["category_path"] = req.category_path
    if req.min_price is not None:
        metadata_base["min_price"] = float(req.min_price)
    if req.tags:
        metadata_base["tags"] = ",".join(req.tags)
    if req.main_image:
        metadata_base["main_image"] = req.main_image

    chunk_count = 0
    if chunks:
        mgr: ChromaVectorStore = get_vector_store()
        try:
            # 先按 biz_type+biz_id 删除旧分块（幂等），再重新写入
            mgr.delete_by_metadata_filter(
                {"source": "mall_spu", "biz_id": biz_id, "biz_type": "SPU"}
            )
            metadatas = [dict(metadata_base, chunk_index=i, total_chunks=len(chunks))
                         for i in range(len(chunks))]
            ids = [f"mall_spu_{biz_id}_{i}" for i in range(len(chunks))]
            mgr.add_texts(texts=chunks, metadatas=metadatas, ids=ids)
            chunk_count = len(chunks)
        except Exception as e:  # pragma: no cover - 异常时返回 500，让 Mall 端重试
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"Vector store failed: {e}",
            ) from e

    return SyncResponse(
        ok=True, biz_type="SPU", biz_id=biz_id,
        processed_chunk_count=chunk_count,
        message=(
            f"upsert SPU {biz_id} {chunk_count} chunks"
            if chunk_count else f"upsert SPU {biz_id} 0 chunks (empty)"
        ),
    )


@router.post(
    "/sync_doc",
    response_model=SyncResponse,
    summary="Mall → RAG 同步文档（政策/公告/优惠券规则/帮助文档）",
)
def sync_doc(
    req: SyncDocRequest,
    _auth: None = Depends(_require_sync_secret),
) -> SyncResponse:
    full_text = _build_doc_document_text(req)
    chunks = [c for c in _split_markdown_smart(full_text) if c.strip()]

    biz_id = str(req.doc_id)
    metadata_base: Dict[str, Any] = {
        "source": "mall_doc",
        "biz_type": req.doc_type or "DOC",
        "biz_id": biz_id,
        "doc_id": biz_id,
        "title": req.title,
        "status": int(req.status),
    }
    if req.tags:
        metadata_base["tags"] = ",".join(req.tags)

    chunk_count = 0
    if chunks:
        mgr: ChromaVectorStore = get_vector_store()
        try:
            mgr.delete_by_metadata_filter(
                {"source": "mall_doc", "biz_id": biz_id, "biz_type": metadata_base["biz_type"]}
            )
            metadatas = [dict(metadata_base, chunk_index=i, total_chunks=len(chunks))
                         for i in range(len(chunks))]
            ids = [f"mall_doc_{biz_id}_{i}" for i in range(len(chunks))]
            mgr.add_texts(texts=chunks, metadatas=metadatas, ids=ids)
            chunk_count = len(chunks)
        except Exception as e:  # pragma: no cover
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"Vector store failed: {e}",
            ) from e

    return SyncResponse(
        ok=True, biz_type=metadata_base["biz_type"], biz_id=biz_id,
        processed_chunk_count=chunk_count,
        message=f"upsert DOC {biz_id} {chunk_count} chunks",
    )
