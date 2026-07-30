import { ragRequest } from './request'

export function apiAuthMe() {
  return ragRequest({
    url: '/ragapi/api/auth/me',
    method: 'get'
  })
}

export function apiListConversations() {
  return ragRequest({
    url: '/ragapi/api/conversations',
    method: 'get'
  })
}

export function apiCreateConversation(data) {
  return ragRequest({
    url: '/ragapi/api/conversations',
    method: 'post',
    data
  })
}

export function apiGetConversation(id) {
  return ragRequest({
    url: `/ragapi/api/conversations/${id}`,
    method: 'get'
  })
}

export function apiRenameConversation(id, title) {
  return ragRequest({
    url: `/ragapi/api/conversations/${id}`,
    method: 'put',
    data: { title }
  })
}

export function apiDeleteConversation(id) {
  return ragRequest({
    url: `/ragapi/api/conversations/${id}`,
    method: 'delete'
  })
}

export function apiFeedbackMessage(convId, msgId, feedback) {
  return ragRequest({
    url: `/ragapi/api/conversations/${convId}/messages/${msgId}/feedback`,
    method: 'post',
    data: { feedback }
  })
}

export function apiKbList(params) {
  return ragRequest({
    url: '/ragapi/api/kb/documents',
    method: 'get',
    params
  })
}

export function apiKbGet(id) {
  return ragRequest({
    url: `/ragapi/api/kb/documents/${id}`,
    method: 'get'
  })
}

export function apiKbDelete(id) {
  return ragRequest({
    url: `/ragapi/api/kb/documents/${id}`,
    method: 'delete'
  })
}

export function apiKbUpdate(id, data) {
  return ragRequest({
    url: `/ragapi/api/kb/documents/${id}`,
    method: 'put',
    data
  })
}

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

export function apiKbUploadWithChunks(file, tags, chunkSize, chunkOverlap, onProgress) {
  const fd = new FormData()
  fd.append('file', file)
  fd.append('tags', tags || '')
  if (chunkSize != null) fd.append('chunk_size', String(chunkSize))
  if (chunkOverlap != null) fd.append('chunk_overlap', String(chunkOverlap))
  return apiKbUpload(fd, onProgress)
}

export function apiKbStats() {
  return ragRequest({
    url: '/ragapi/api/kb/stats',
    method: 'get'
  })
}

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

export function apiKbReindex(id) {
  return ragRequest({
    url: `/ragapi/api/kb/documents/${id}/reindex`,
    method: 'post'
  })
}

export function apiKbDownloadUrl(id) {
  return `/api/kb/documents/${id}/download`
}

export function apiKbDownload(id) {
  return ragRequest({
    url: `/ragapi/api/kb/documents/${id}/download`,
    method: 'get',
    responseType: 'blob',
  })
}

export function apiKbChunks(id, limit = 100) {
  return ragRequest({
    url: `/ragapi/api/kb/documents/${id}/chunks`,
    method: 'get',
    params: { limit }
  })
}

export function apiAdminDashboard() {
  return ragRequest({
    url: '/ragapi/api/admin/dashboard',
    method: 'get'
  })
}

export function apiAdminLogs(params) {
  return ragRequest({
    url: '/ragapi/api/admin/logs',
    method: 'get',
    params
  })
}

export function apiAdminSettingsGet() {
  return ragRequest({
    url: '/ragapi/api/admin/settings',
    method: 'get'
  })
}

export function apiAdminSettingsUpdate(data) {
  return ragRequest({
    url: '/ragapi/api/admin/settings',
    method: 'post',
    data
  })
}
