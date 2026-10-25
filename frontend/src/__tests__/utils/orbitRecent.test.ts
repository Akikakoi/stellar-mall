/**
 * orbitRecent 单元测试
 *
 * 覆盖：空历史、记录/去重/上限、脏数据过滤、新展示排在最前、localStorage 持久化。
 */
import { describe, it, expect, beforeEach } from 'vitest'
import { loadOrbitRecent, rememberOrbitRecent, ORBIT_RECENT_MAX } from '@/utils/orbitRecent'

const KEY = 'stellar_orbit_recent'

describe('orbitRecent', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('没有历史时返回空数组', () => {
    expect(loadOrbitRecent()).toEqual([])
  })

  it('记录展示过的商品并持久化到 localStorage', () => {
    const result = rememberOrbitRecent([1, 2, 3])
    expect(result).toEqual([1, 2, 3])
    expect(loadOrbitRecent()).toEqual([1, 2, 3])
    expect(JSON.parse(localStorage.getItem(KEY) || '[]')).toEqual([1, 2, 3])
  })

  it('新展示的商品排在前面且不会重复', () => {
    rememberOrbitRecent([1, 2, 3])
    const result = rememberOrbitRecent([3, 4, 5])
    expect(result).toEqual([3, 4, 5, 1, 2])
  })

  it('历史超过上限时只保留最近的最多 ORBIT_RECENT_MAX 个', () => {
    rememberOrbitRecent(Array.from({ length: ORBIT_RECENT_MAX }, (_, i) => i + 1))
    const result = rememberOrbitRecent([1000])
    expect(result).toHaveLength(ORBIT_RECENT_MAX)
    // 新展示的排最前，末尾最旧的 40 被挤出
    expect(result[0]).toBe(1000)
    expect(result).not.toContain(40)
  })

  it('过滤 0/负数/非数字等脏数据', () => {
    localStorage.setItem(KEY, JSON.stringify([1, 0, -2, 'abc', null, 3, '4', 1.5]))
    // 数字字符串 '4' 按宽松策略视为有效 id；0/负数/NaN/非数字/小数会过滤
    expect(loadOrbitRecent()).toEqual([1, 3, 4])
    expect(rememberOrbitRecent([0, -1, Number.NaN, 5])).toContain(5)
  })

  it('localStorage 数据不是数组时安全返回空数组', () => {
    localStorage.setItem(KEY, JSON.stringify({ foo: 'bar' }))
    expect(loadOrbitRecent()).toEqual([])
  })
})
