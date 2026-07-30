import { ref, onUnmounted } from 'vue'

/**
 * 订单倒计时：计算从 createTime 开始的过期倒计时。
 * @param {string} createTime - 订单创建时间（ISO 或后端格式字符串）
 * @param {number} expireMinutes - 过期分钟数，默认 15
 * @returns {{ remaining: import('vue').Ref<number>, remainingText: import('vue').Ref<string>, expired: import('vue').Ref<boolean> }}
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

  function start() {
    tick()
    timer = setInterval(tick, 1000)
  }

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
