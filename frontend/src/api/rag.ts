/**
 * RAG(检索增强生成)模块 API 接口。
 * 包含对话管理、知识库管理、管理员后台等接口。
 * 所有请求通过 ragRequest 实例发送。
 *
 * 类型说明:ragRequest 拦截器对 code===0 返回整个 {code,message,data} envelope,
 * 因此这里统一用 RagResponse<T> 描述返回。
 */
import { ragRequest } from './request'
import type {
  RagResponse, Conversation,
  KbDocument, KbPage,
} from '@/types/models'

// =========================== 认证 ===========================

/** 获取当前 RAG 用户身份 */
export function apiAuthMe(): Promise<RagResponse<any>> {
  return ragRequest({
    url: '/ragapi/api/auth/me',
    method: 'get'
  })
}

// =========================== 对话管理 ===========================

/** 获取对话列表 */
export function apiListConversations(): Promise<RagResponse<Conversation[]>> {
  return ragRequest({
    url: '/ragapi/api/conversations',
    method: 'get'
  })
}

/** 创建新对话 */
export function apiCreateConversation(data: { title: string }): Promise<RagResponse<Conversation>> {
  return ragRequest({
    url: '/ragapi/api/conversations',
    method: 'post',
    data
  })
}

/** 获取对话详情 */
export function apiGetConversation(id: number): Promise<RagResponse<Conversation>> {
  return ragRequest({
    url: `/ragapi/api/conversations/${id}`,
    method: 'get'
  })
}

/** 重命名对话 */
export function apiRenameConversation(id: number, title: string): Promise<RagResponse<Conversation>> {
  return ragRequest({
    url: `/ragapi/api/conversations/${id}`,
    method: 'put',
    data: { title }
  })
}

/** 删除对话 */
export function apiDeleteConversation(id: number): Promise<RagResponse<any>> {
  return ragRequest({
    url: `/ragapi/api/conversations/${id}`,
    method: 'delete'
  })
}

/** 对消息提交反馈(点赞/点踩) */
export function apiFeedbackMessage(convId: number, msgId: number, feedback: string): Promise<RagResponse<any>> {
  return ragRequest({
    url: `/ragapi/api/conversations/${convId}/messages/${msgId}/feedback`,
    method: 'post',
    data: { feedback }
  })
}

// =========================== 知识库 ===========================

/** 分页查询知识库文档列表 */
export function apiKbList(params: Record<string, any>): Promise<RagResponse<KbPage>> {
  return ragRequest({
    url: '/ragapi/api/kb/documents',
    method: 'get',
    params
  })
}

/** 查询知识库文档详情 */
export function apiKbGet(id: number): Promise<RagResponse<KbDocument>> {
  return ragRequest({
    url: `/ragapi/api/kb/documents/${id}`,
    method: 'get'
  })
}

/** 删除知识库文档 */
export function apiKbDelete(id: number): Promise<RagResponse<any>> {
  return ragRequest({
    url: `/ragapi/api/kb/documents/${id}`,
    method: 'delete'
  })
}

/** 更新知识库文档 */
export function apiKbUpdate(id: number, data: any): Promise<RagResponse<any>> {
  return ragRequest({
    url: `/ragapi/api/kb/documents/${id}`,
    method: 'put',
    data
  })
}

/** 上传文件到知识库(支持进度回调) */
export function apiKbUpload(
  form: FormData,
  onProgress?: (percent: number) => void
): Promise<RagResponse<any>> {
  return ragRequest({
    url: '/ragapi/api/kb/documents',
    method: 'post',
    data: form,
    timeout: 600000,
    onUploadProgress: onProgress
      ? (e) => onProgress(e.total ? Math.round((e.loaded / e.total) * 100) : 0)
      : undefined,
  })
}

/**
 * 上传文件到知识库(带自定义分块参数)。
 * @param file 上传的文件
 * @param tags 标签
 * @param chunkSize 分块大小
 * @param chunkOverlap 分块重叠大小
 * @param onProgress 进度回调
 */
export function apiKbUploadWithChunks(
  file: File,
  tags: string,
  chunkSize?: number,
  chunkOverlap?: number,
  onProgress?: (percent: number) => void
): Promise<RagResponse<any>> {
  const fd = new FormData()
  fd.append('file', file)
  fd.append('tags', tags || '')
  if (chunkSize != null) fd.append('chunk_size', String(chunkSize))
  if (chunkOverlap != null) fd.append('chunk_overlap', String(chunkOverlap))
  return apiKbUpload(fd, onProgress)
}

/** 获取知识库统计信息 */
export function apiKbStats(): Promise<RagResponse<any>> {
  return ragRequest({
    url: '/ragapi/api/kb/stats',
    method: 'get'
  })
}

/** 预览文档分块结果(上传前预览) */
export function apiKbPreview(
  form: FormData,
  onProgress?: (percent: number) => void
): Promise<RagResponse<any>> {
  return ragRequest({
    url: '/ragapi/api/kb/preview',
    method: 'post',
    data: form,
    timeout: 120000,
    onUploadProgress: onProgress
      ? (e) => onProgress(e.total ? Math.round((e.loaded / e.total) * 100) : 0)
      : undefined,
  })
}

/** 重新索引文档 */
export function apiKbReindex(id: number): Promise<RagResponse<any>> {
  return ragRequest({
    url: `/ragapi/api/kb/documents/${id}/reindex`,
    method: 'post'
  })
}

/** 获取文档下载 URL */
export function apiKbDownloadUrl(id: number): string {
  return `/api/kb/documents/${id}/download`
}

/** 下载文档(返回 Blob,responseType: 'blob' 时拦截器不包 envelope) */
export function apiKbDownload(id: number): Promise<Blob> {
  return ragRequest({
    url: `/ragapi/api/kb/documents/${id}/download`,
    method: 'get',
    responseType: 'blob',
  })
}

/** 获取文档分块列表 */
export function apiKbChunks(id: number, limit = 100): Promise<RagResponse<any>> {
  return ragRequest({
    url: `/ragapi/api/kb/documents/${id}/chunks`,
    method: 'get',
    params: { limit }
  })
}

// =========================== 管理员后台 ===========================

/** 获取管理后台仪表盘数据 */
export function apiAdminDashboard(): Promise<RagResponse<any>> {
  return ragRequest({
    url: '/ragapi/api/admin/dashboard',
    method: 'get'
  })
}

/** 获取系统设置 */
export function apiAdminSettingsGet(): Promise<RagResponse<any>> {
  return ragRequest({
    url: '/ragapi/api/admin/settings',
    method: 'get'
  })
}

/** 更新系统设置 */
export function apiAdminSettingsUpdate(data: any): Promise<RagResponse<any>> {
  return ragRequest({
    url: '/ragapi/api/admin/settings',
    method: 'post',
    data
  })
}
