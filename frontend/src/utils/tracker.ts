/**
 * 用户行为埋点工具（轻量、尽力而为、零依赖）。
 *
 * 设计：
 *   - 事件先入内存缓冲，每 5 秒或攒满 30 条批量 POST /user/behavior/track；
 *   - 页面隐藏（切走/关页）时立刻冲刷（keepalive 尽力送达）；
 *   - 直接 fetch 上报，不经过 axios 拦截器 → 失败不弹错、不触发 401 登出流程；
 *   - 设备 ID 由 localStorage 生成，登录前后一致（游客/登录用户同源归因）；
 *   - 已登录时带上 authentication/Authorization 头，后端可选解析 userId。
 *
 * 事件类型与后端 stellar_user_behavior 表一致：
 *   view_item_list / view_item / search / add_to_cart / order_placed / favorite
 */

import { storage } from './storage'

const DEVICE_KEY = 'stellar_device_id'
const TOKEN_KEY = 'stellar_user_token'
const BATCH_URL = '/user/behavior/track'
const FLUSH_INTERVAL_MS = 5000
const MAX_BUFFER = 30

export interface BehaviorPayload {
  spuId?: number | null
  skuId?: number | null
  categoryId?: number | null
  keyword?: string | null
  scene?: string | null
  position?: number | null
  amount?: number | null
  durationMs?: number | null
  extra?: Record<string, unknown> | null
}

let deviceIdCache = ''
let flushing = false
const buffer: Array<{ eventType: string } & BehaviorPayload> = []
let timer: number | null = null

function genUuid(): string {
  try {
    if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID()
  } catch { /* ignore */ }
  // 降级：随机串
  return 'd' + Date.now().toString(36) + Math.random().toString(36).slice(2, 12)
}

export function getDeviceId(): string {
  if (deviceIdCache) return deviceIdCache
  let id = ''
  try {
    id = storage.local.get(DEVICE_KEY) || ''
  } catch { /* ignore */ }
  if (!id) {
    id = genUuid()
    try {
      storage.local.set(DEVICE_KEY, id)
    } catch { /* ignore */ }
  }
  deviceIdCache = id
  return id
}

/** 记录一条行为事件（不抛异常，失败静默）。 */
export function track(eventType: string, payload: BehaviorPayload = {}): void {
  try {
    const ev = { eventType, ...payload }
    // 过滤空值，减小体积
    for (const k of Object.keys(ev)) {
      const v = (ev as Record<string, unknown>)[k]
      if (v === undefined || v === null) delete (ev as Record<string, unknown>)[k]
    }
    buffer.push(ev as typeof ev)
    if (buffer.length >= MAX_BUFFER) {
      flush()
    } else if (timer === null) {
      timer = window.setTimeout(flush, FLUSH_INTERVAL_MS)
    }
  } catch { /* 埋点永不影响业务 */ }
}

/** 立即冲刷缓冲（隐藏页/离开页时调用）。 */
export function flush(): void {
  if (timer !== null) {
    window.clearTimeout(timer)
    timer = null
  }
  if (buffer.length === 0 || flushing) return
  flushing = true
  const batch = buffer.splice(0, buffer.length)
  try {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    const token = storage.local.get(TOKEN_KEY) || ''
    if (token) {
      headers['authentication'] = token
      headers['Authorization'] = 'Bearer ' + token
    }
    fetch(BATCH_URL, {
      method: 'POST',
      headers,
      body: JSON.stringify({ deviceId: getDeviceId(), events: batch }),
      keepalive: true // 页面关闭时尽量送达
    }).catch(() => { /* 静默 */ })
  } catch { /* 静默 */ } finally {
    flushing = false
  }
}

// 页面隐藏（切后台 / 关闭标签）时冲刷，防丢最后一批
if (typeof document !== 'undefined') {
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') flush()
  })
  window.addEventListener('pagehide', flush)
}

export default track
