"""Pydantic 校验 schemas（请求/响应 DTO）。"""
from datetime import datetime
from typing import Optional, List, Any
from pydantic import BaseModel, EmailStr, Field, ConfigDict


# ================ 通用 ================
class RespModel(BaseModel):
    code: int = 0
    message: str = "success"
    data: Any = None


# ================ 用户 / 认证 ================
class UserRegisterReq(BaseModel):
    username: str = Field(..., min_length=3, max_length=32, description="用户名")
    password: str = Field(..., min_length=6, max_length=64, description="密码")
    email: Optional[EmailStr] = Field(None, description="邮箱（可选）")
    nickname: Optional[str] = Field(None, max_length=64, description="昵称")


class UserLoginReq(BaseModel):
    username: str = Field(..., description="用户名/邮箱")
    password: str = Field(..., description="密码")


class UserLoginResp(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    user_info: "UserInfo"


class ChangePasswordReq(BaseModel):
    old_password: str = Field(..., min_length=6, max_length=64)
    new_password: str = Field(..., min_length=6, max_length=64)


class UserInfo(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    username: str
    nickname: Optional[str]
    email: Optional[str]
    role: str
    is_active: bool
    created_at: datetime
    last_login_at: Optional[datetime]


class RefreshTokenReq(BaseModel):
    refresh_token: str


# ================ 会话 ================
class ConversationCreateReq(BaseModel):
    title: Optional[str] = Field(None, max_length=128)


class ConversationRenameReq(BaseModel):
    title: str = Field(..., min_length=1, max_length=128)


class ConversationInfo(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    title: str
    summary: Optional[str]
    pinned: bool
    created_at: datetime
    updated_at: datetime


class MessageInfo(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    conversation_id: int
    role: str
    content: str
    sources: Optional[List[Any]]
    feedback: int
    created_at: datetime


class ConversationDetail(ConversationInfo):
    messages: List[MessageInfo] = []


# ================ 问答 ================
class ChatReq(BaseModel):
    conversation_id: Optional[int] = Field(None, description="会话ID，不传则新建会话")
    query: str = Field(..., min_length=1, max_length=2000, description="用户问题")
    stream: bool = Field(True, description="是否流式输出")
    use_rewrite: Optional[bool] = Field(None)
    top_k: Optional[int] = Field(None, ge=1, le=50)
    tags_filter: Optional[List[str]] = Field(None, description="按文档标签过滤")
    use_agent: Optional[bool] = Field(None, description="是否使用智能体模式，不填则用系统默认配置")


class SourceRef(BaseModel):
    id: int = 0
    doc_name: str
    chunk_index: int
    page: Optional[int] = None
    content: str
    score: float
    tags: Optional[str]


class MessageFeedbackReq(BaseModel):
    feedback: int = Field(..., ge=-1, le=1)


# ================ 知识库 ================
class KbDocInfo(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    filename: str
    file_ext: str
    file_size: int
    chunk_count: int
    tags: Optional[str]
    status: str
    error_msg: Optional[str]
    created_by: int
    created_at: datetime
    updated_at: datetime
    # 计算字段：向量化是否成功（status == ready）
    indexed: Optional[bool] = None

    @classmethod
    def model_validate(cls, obj, *args, **kwargs):
        inst = super().model_validate(obj, *args, **kwargs)
        if inst.indexed is None:
            inst.indexed = (inst.status == "ready")
        return inst


class KbDocUpdateReq(BaseModel):
    tags: Optional[str] = Field(None, max_length=512)
    filename: Optional[str] = Field(None, max_length=255)


class KbPreviewResp(BaseModel):
    total_chunks: int
    estimated_chunk_count: Optional[int] = None
    chunks: list


# ================ 管理员：系统设置 ================
class SystemSettingsReq(BaseModel):
    llm_model_name: Optional[str] = None
    llm_temperature: Optional[float] = Field(None, ge=0, le=2)
    llm_max_tokens: Optional[int] = Field(None, ge=64, le=8192)
    retriever_top_k: Optional[int] = Field(None, ge=1, le=100)
    rerank_top_k: Optional[int] = Field(None, ge=1, le=20)
    similarity_threshold: Optional[float] = Field(None, ge=0, le=1)
    chunk_size: Optional[int] = Field(None, ge=128, le=4096)
    chunk_overlap: Optional[int] = Field(None, ge=0, le=1024)
    query_rewrite_enabled: Optional[bool] = None
    agent_enabled: Optional[bool] = None


class DashboardResp(BaseModel):
    user_count: int
    conversation_count: int
    message_count: int
    kb_doc_count: int
    kb_chunk_count: int
    last_24h_chat_count: int
    top_questions: List[dict] = []


# 响应里的 UserInfo 用到，需要更新引用
UserLoginResp.model_rebuild()
