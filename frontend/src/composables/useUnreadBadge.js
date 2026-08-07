import { ref } from 'vue'

const count = ref(0)

/**
 * 共享未读消息角标状态管理 composable。
 * 提供模块级的未读计数 ref，NavHeader 和 Messages 等组件共用同一个计数源。
 *
 * @returns {{ count: import('vue').Ref<number>, set: (n: number) => void, dec: (n?: number) => void }}
 *          返回未读计数、设置计数和减少计数的方法。
 */
export function useUnreadBadge() {
  return {
    count,
    /**
     * 设置未读消息数量。
     * @param {number} n - 新的未读数量
     */
    set(n) { count.value = n },
    /**
     * 减少未读消息数量，不会低于 0。
     * @param {number} [n=1] - 要减少的数量，默认为 1
     */
    dec(n = 1) { count.value = Math.max(0, count.value - n) }
  }
}
