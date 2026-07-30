"""
商品搜索 Embedding API — 供 Java 后端调用，将商品文本转为语义向量。
内部接口，无需鉴权。
"""
from __future__ import annotations

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from app.rag.embeddings import get_embeddings
from app.core.logger import logger

router = APIRouter()


class EmbedRequest(BaseModel):
    texts: list[str] = Field(..., min_length=1, max_length=100, description="待向量化的文本列表")


class EmbedResponse(BaseModel):
    embeddings: list[list[float]] = Field(..., description="向量列表，每条对应一个输入文本")
    dim: int = Field(..., description="向量维度")


@router.post("/embed", response_model=EmbedResponse, tags=["Embedding"])
async def embed_texts(req: EmbedRequest):
    """将商品名称/描述文本转为语义向量。"""
    try:
        emb = get_embeddings()
        vectors = emb.embed_documents(req.texts)
        dim = len(vectors[0]) if vectors else 0
        logger.debug(f"Generated {len(vectors)} embeddings, dim={dim}")
        return EmbedResponse(embeddings=vectors, dim=dim)
    except Exception as e:
        logger.error(f"Embedding failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))
