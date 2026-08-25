import { ref, type Ref } from 'vue'

const count = ref(0)

export interface UnreadBadgeComposable {
  count: Ref<number>
  set: (n: number) => void
  dec: (n?: number) => void
}

/**
 * 共享未读消息角标状态管理 composable。
 * 提供模块级的未读计数 ref,NavHeader 和 Messages 等组件共用同一个计数源。
 */
export function useUnreadBadge(): UnreadBadgeComposable {
  return {
    count,
    /** 设置未读消息数量 */
    set(n: number) { count.value = n },
    /** 减少未读消息数量,不会低于 0 */
    dec(n = 1) { count.value = Math.max(0, count.value - n) }
  }
}
