/**
 * RAG（检索增强生成）模块 API 接口。
 * 包含对话管理、知识库管理、管理员后台等接口。
 * 所有请求通过 ragRequest 实例发送。
 */
import { ragRequest } from './request'

// =========================== 认证 ===========================

/** 获取当前 RAG 用户身份 */
export function apiAuthMe() {
  return ragRequest({
    url: '/ragapi/api/auth/me',
    method: 'get'
  })
}

// =========================== 对话管理 ===========================

/** 获取对话列表 */
export function apiListConversations() {
  return ragRequest({
    url: '/ragapi/api/conversations',
    method: 'get'
  })
}

/** 创建新对话 */
export function apiCreateConversation(data) {
  return ragRequest({
    url: '/ragapi/api/conversations',
    method: 'post',
    data
  })
}

/** 获取对话详情 */
export function apiGetConversation(id) {
  return ragRequest({
    url: `/ragapi/api/conversations/${id}`,
    method: 'get'
  })
}

/** 重命名对话 */
export function apiRenameConversation(id, title) {
  return ragRequest({
    url: `/ragapi/api/conversations/${id}`,
    method: 'put',
    data: { title }
  })
}

/** 删除对话 */
export function apiDeleteConversation(id) {
  return ragRequest({
    url: `/ragapi/api/conversations/${id}`,
    method: 'delete'
  })
}

/** 对消息提交反馈（点赞/点踩） */
export function apiFeedbackMessage(convId, msgId, feedback) {
  return ragRequest({
    url: `/ragapi/api/conversations/${convId}/messages/${msgId}/feedback`,
    method: 'post',
    data: { feedback }
  })
}

// =========================== 知识库 ===========================

/** 分页查询知识库文档列表 */
export function apiKbList(params) {
  return ragRequest({
    url: '/ragapi/api/kb/documents',
    method: 'get',
    params
  })
}

/** 查询知识库文档详情 */
export function apiKbGet(id) {
  return ragRequest({
    url: `/ragapi/api/kb/documents/${id}`,
    method: 'get'
  })
}

/** 删除知识库文档 */
export function apiKbDelete(id) {
  return ragRequest({
    url: `/ragapi/api/kb/documents/${id}`,
    method: 'delete'
  })
}

/** 更新知识库文档 */
export function apiKbUpdate(id, data) {
  return ragRequest({
    url: `/ragapi/api/kb/documents/${id}`,
    method: 'put',
    data
  })
}

/** 上传文件到知识库（支持进度回调） */
export function apiKbUpload(form, onProgress) {
  return ragRequest({
    url: '/ragapi/api/kb/documents',
    method: 'post',
    data: form,
    timeout: 600000,
    onUploadProgress: onProgress
      ? (e) => onProgress(Math.round((e.loaded / e.total) * 100))
      : undefined,
  })
}

/**
 * 上传文件到知识库（带自定义分块参数）。
 * @param {File} file - 上传的文件
 * @param {string} tags - 标签
 * @param {number} chunkSize - 分块大小
 * @param {number} chunkOverlap - 分块重叠大小
 * @param {Function} onProgress - 进度回调
 */
export function apiKbUploadWithChunks(file, tags, chunkSize, chunkOverlap, onProgress) {
  const fd = new FormData()
  fd.append('file', file)
  fd.append('tags', tags || '')
  if (chunkSize != null) fd.append('chunk_size', String(chunkSize))
  if (chunkOverlap != null) fd.append('chunk_overlap', String(chunkOverlap))
  return apiKbUpload(fd, onProgress)
}

/** 获取知识库统计信息 */
export function apiKbStats() {
  return ragRequest({
    url: '/ragapi/api/kb/stats',
    method: 'get'
  })
}

/** 预览文档分块结果（上传前预览） */
export function apiKbPreview(form, onProgress) {
  return ragRequest({
    url: '/ragapi/api/kb/preview',
    method: 'post',
    data: form,
    timeout: 120000,
    onUploadProgress: onProgress
      ? (e) => onProgress(Math.round((e.loaded / e.total) * 100))
      : undefined,
  })
}

/** 重新索引文档 */
export function apiKbReindex(id) {
  return ragRequest({
    url: `/ragapi/api/kb/documents/${id}/reindex`,
    method: 'post'
  })
}

/** 获取文档下载 URL */
export function apiKbDownloadUrl(id) {
  return `/api/kb/documents/${id}/download`
}

/** 下载文档（返回 Blob） */
export function apiKbDownload(id) {
  return ragRequest({
    url: `/ragapi/api/kb/documents/${id}/download`,
    method: 'get',
    responseType: 'blob',
  })
}

/** 获取文档分块列表 */
export function apiKbChunks(id, limit = 100) {
  return ragRequest({
    url: `/ragapi/api/kb/documents/${id}/chunks`,
    method: 'get',
    params: { limit }
  })
}

// =========================== 管理员后台 ===========================

/** 获取管理后台仪表盘数据 */
export function apiAdminDashboard() {
  return ragRequest({
    url: '/ragapi/api/admin/dashboard',
    method: 'get'
  })
}

/** 查询操作日志 */
export function apiAdminLogs(params) {
  return ragRequest({
    url: '/ragapi/api/admin/logs',
    method: 'get',
    params
  })
}

/** 获取系统设置 */
export function apiAdminSettingsGet() {
  return ragRequest({
    url: '/ragapi/api/admin/settings',
    method: 'get'
  })
}

/** 更新系统设置 */
export function apiAdminSettingsUpdate(data) {
  return ragRequest({
    url: '/ragapi/api/admin/settings',
    method: 'post',
    data
  })
}
