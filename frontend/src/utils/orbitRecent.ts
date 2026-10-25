/**
 * 首屏环绕商品的「最近展示」历史。
 *
 * 目的：首页每次刷新/重新进入时，把最近展示过的商品从随机池里排除，
 * 让连续多次刷新尽量看到不同的商品（配合后端 /user/spu/orbit 的 exclude 参数）。
 * 使用 localStorage 持久化：整页刷新后历史仍在，才能真正做到「连续刷新不重样」。
 *
 * 数量上限按「商品池足够大时留足候选」来定：默认 80 个商品，
 * 记住最近 40 个后仍有 40 个候选可选；若商品库更小，后端会自动放宽排除补足。
 */
import { storage } from '@/utils/storage'

const STORAGE_KEY = 'stellar_orbit_recent'

/** 最多记住最近展示过的商品数 */
export const ORBIT_RECENT_MAX = 40

/** 读取最近展示过的商品 id（过滤脏数据/重复） */
export function loadOrbitRecent(): number[] {
  const raw = storage.local.getObject<unknown[]>(STORAGE_KEY, [])
  if (!Array.isArray(raw)) return []
  const ids = raw
    .map((v) => Number(v))
    .filter((v) => Number.isInteger(v) && v > 0)
  return [...new Set(ids)].slice(0, ORBIT_RECENT_MAX)
}

/**
 * 记录本轮展示的商品（新展示的排前面），并截断到上限。
 *
 * @param ids 本轮展示的商品 id
 * @returns 更新后的历史列表
 */
export function rememberOrbitRecent(ids: number[]): number[] {
  const fresh = ids
    .map((v) => Number(v))
    .filter((v) => Number.isInteger(v) && v > 0)
  const merged = [...new Set([...fresh, ...loadOrbitRecent()])].slice(0, ORBIT_RECENT_MAX)
  storage.local.setObject(STORAGE_KEY, merged)
  return merged
}
