/**
 * useOrderCountdown 单元测试
 *
 * 覆盖：正常倒计时、过期判定、createTime 非法输入、
 * 启动/停止控制、时间格式化。
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { useOrderCountdown } from '@/utils/countdown'

describe('useOrderCountdown', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    // 固定"当前时间"为 2026-08-08 12:00:00 UTC+8
    vi.setSystemTime(new Date('2026-08-08T12:00:00+08:00'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  // ── 基础倒计时 ──
  it('start 后立即计算剩余时间', () => {
    const { remaining, remainingText, expired, start, stop } = useOrderCountdown(
      '2026-08-08T12:00:00+08:00',
      15
    )

    start()

    expect(remaining.value).toBe(15 * 60)
    expect(remainingText.value).toBe('15:00')
    expect(expired.value).toBe(false)

    stop()
  })

  it('已经过去 10 分钟时剩余 5 分钟', () => {
    const { remaining, remainingText, expired, start, stop } = useOrderCountdown(
      '2026-08-08T11:50:00+08:00',
      15
    )

    start()

    expect(remaining.value).toBe(5 * 60)
    expect(remainingText.value).toBe('05:00')
    expect(expired.value).toBe(false)

    stop()
  })

  it('超过过期时间时 expired = true', () => {
    const { remaining, remainingText, expired, start, stop } = useOrderCountdown(
      '2026-08-08T11:30:00+08:00',
      15
    )

    start()

    expect(remaining.value).toBe(0)
    expect(remainingText.value).toBe('已超时')
    expect(expired.value).toBe(true)

    stop()
  })

  it('每秒 tick 递减剩余时间', () => {
    const { remaining, remainingText, start, stop } = useOrderCountdown(
      '2026-08-08T12:00:00+08:00',
      15
    )

    start()
    expect(remaining.value).toBe(900)

    vi.advanceTimersByTime(10000)

    expect(remaining.value).toBe(890)
    expect(remainingText.value).toBe('14:50')

    stop()
  })

  it('倒计时到 0 后自动标记过期', () => {
    const { remaining, expired, remainingText, start, stop } = useOrderCountdown(
      '2026-08-08T12:00:00+08:00',
      1
    )

    start()
    expect(remaining.value).toBe(60)

    vi.advanceTimersByTime(61000)

    expect(remaining.value).toBe(0)
    expect(expired.value).toBe(true)
    expect(remainingText.value).toBe('已超时')

    stop()
  })

  // ── 格式化 ──
  it('剩余 1 分 5 秒时格式化为 "01:05"', () => {
    // 当前时间 12:00:00，创建于 11:58:55 → 还剩 13:55（15min 过期）
    // 改为创建于 11:59:55，1 分钟过期 → 到期 12:00:55，此刻还有 55 秒
    // 再改：创建于 11:58:55，设过期 1min + 自己设的秒...
    // 最简单：设创建时间 = 55 秒前，过期 2 分钟 → 剩余 = 120 - 55 = 65 秒
    const { remainingText, start, stop } = useOrderCountdown(
      '2026-08-08T11:59:05+08:00', // 55 秒前
      2 // 2 分钟过期 → 剩余 65 秒
    )

    start()
    expect(remainingText.value).toBe('01:05')

    stop()
  })

  it('刚过期 1 秒显示"已超时"', () => {
    const { remainingText, expired, start, stop } = useOrderCountdown(
      '2026-08-08T11:59:59+08:00',
      1 / 60 // 1 秒过期
    )

    start()
    expect(expired.value).toBe(true)
    expect(remainingText.value).toBe('已超时')

    stop()
  })

  // ── 非法输入 ──
  it('createTime 为 undefined 时 remain 全为 0', () => {
    const { remaining, remainingText, expired, start, stop } = useOrderCountdown(undefined, 15)

    start()

    expect(remaining.value).toBe(0)
    expect(remainingText.value).toBe('')
    expect(expired.value).toBe(false)

    stop()
  })

  it('createTime 为 null 时 remain 全为 0', () => {
    const { remaining, remainingText, expired, start, stop } = useOrderCountdown(null, 15)

    start()

    expect(remaining.value).toBe(0)
    expect(remainingText.value).toBe('')
    expect(expired.value).toBe(false)

    stop()
  })

  it('createTime 为非法字符串时 remain 全为 0', () => {
    const { remaining, remainingText, expired, start, stop } = useOrderCountdown('not-a-date', 15)

    start()

    expect(remaining.value).toBe(0)
    expect(remainingText.value).toBe('')
    expect(expired.value).toBe(false)

    stop()
  })

  it('使用时间戳作为 createTime', () => {
    const now = Date.now()
    const { remaining, start, stop } = useOrderCountdown(now, 15)

    start()

    expect(remaining.value).toBe(900)
    stop()
  })

  // ── start/stop 控制 ──
  it('不调 start 时不会自动启动', () => {
    vi.useRealTimers()
    const { remaining, remainingText } = useOrderCountdown(
      '2026-08-08T12:00:00+08:00',
      15
    )

    return new Promise((resolve) => {
      setTimeout(() => {
        expect(remaining.value).toBe(0)
        expect(remainingText.value).toBe('')
        resolve()
      }, 1100)
    })
  })

  it('stop 后不再更新', () => {
    const { remaining, start, stop } = useOrderCountdown(
      '2026-08-08T12:00:00+08:00',
      15
    )

    start()
    expect(remaining.value).toBe(900)

    stop()

    vi.advanceTimersByTime(30000)

    expect(remaining.value).toBe(900)

    stop()
  })
})
