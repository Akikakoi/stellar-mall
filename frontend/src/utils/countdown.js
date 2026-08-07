import { ref, onUnmounted } from 'vue'

/**
 * 订单倒计时组合式函数。
 * 根据订单创建时间和过期时长，计算剩余时间并自动更新。
 * 需要手动调用返回的 start() 方法启动倒计时，组件卸载时自动停止。
 * @param {string|number} createTime - 订单创建时间（ISO 字符串或时间戳）
 * @param {number} [expireMinutes=15] - 过期分钟数
 * @returns {{
 *   remaining: import('vue').Ref<number>,
 *   remainingText: import('vue').Ref<string>,
 *   expired: import('vue').Ref<boolean>,
 *   start: () => void,
 *   stop: () => void
 * }} 倒计时相关状态与控制方法
 *   - remaining: 剩余秒数
 *   - remainingText: 格式化后的剩余时间文本（如 "14:32"），超时时显示"已超时"
 *   - expired: 是否已超时
 *   - start: 启动倒计时
 *   - stop: 停止倒计时
 */
export function useOrderCountdown(createTime, expireMinutes = 15) {
  const remaining = ref(0)         // 剩余秒数
  const remainingText = ref('')    // 格式化文本 "14:32"
  const expired = ref(false)
  let timer = null

  function tick() {
    if (!createTime) {
      remaining.value = 0
      remainingText.value = ''
      expired.value = false
      return
    }
    const created = typeof createTime === 'string' ? new Date(createTime).getTime() : createTime
    if (isNaN(created)) {
      remaining.value = 0
      remainingText.value = ''
      expired.value = false
      return
    }
    const deadline = created + expireMinutes * 60 * 1000
    const diff = Math.max(0, deadline - Date.now())
    remaining.value = Math.floor(diff / 1000)
    if (diff <= 0) {
      remainingText.value = '已超时'
      expired.value = true
    } else {
      const m = Math.floor(remaining.value / 60)
      const s = remaining.value % 60
      remainingText.value = `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
      expired.value = false
    }
  }

  /**
   * 启动倒计时（立即执行一次 tick，然后每秒更新）。
   */
  function start() {
    tick()
    timer = setInterval(tick, 1000)
  }

  /**
   * 停止倒计时，清除定时器。
   */
  function stop() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  // 需要手动调 start() 启动，不自动启动
  onUnmounted(stop)

  return { remaining, remainingText, expired, start, stop }
}
