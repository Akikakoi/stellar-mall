import { ref } from 'vue'

const count = ref(0)

/** 共享未读消息角标，NavHeader 和 Messages 共用 */
export function useUnreadBadge() {
  return {
    count,
    set(n) { count.value = n },
    dec(n = 1) { count.value = Math.max(0, count.value - n) }
  }
}
