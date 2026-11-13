/**
 * 数字滚动：把一个不断变化的值补间成"看得见涨落"的动画数。
 *
 * 后台看板用：KPI 直接跳数看不出量级变化，滚一下能感知"从 3 单变 47 单"。
 * 只用于展示层数字，绝不参与判断逻辑（判断仍读原始值）。
 *
 * 约定：
 * - 首次挂载与数值变化都会滚动；
 * - 数值相同不重播，避免每次刷新都闪；
 * - 系统要求减少动效时直接落到终值；
 * - 返回值已取整，调用方按需再格式化。
 */
import { computed, onBeforeUnmount, ref, watch, type Ref } from 'vue'

function prefersReducedMotion(): boolean {
  return typeof window !== 'undefined' &&
    (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false)
}

/**
 * @param source 目标值（通常是一个 computed）
 * @param duration 补间时长，默认 700ms
 */
export function useCountUp(source: Ref<number> | (() => number), duration = 700) {
  const current = ref(0)
  let raf = 0

  function stop() {
    if (raf) cancelAnimationFrame(raf)
    raf = 0
  }

  function run(to: number) {
    const target = Number.isFinite(to) ? to : 0
    const from = Number(current.value) || 0
    if (from === target) {
      current.value = target
      return
    }
    if (prefersReducedMotion() || duration <= 0) {
      current.value = target
      return
    }
    stop()
    const started = performance.now()
    const step = (now: number) => {
      const t = Math.min((now - started) / duration, 1)
      // ease-out cubic：起步快、收尾稳，避免像秒表一样匀速跳
      const eased = 1 - Math.pow(1 - t, 3)
      current.value = from + (target - from) * eased
      if (t < 1) {
        raf = requestAnimationFrame(step)
      } else {
        current.value = target
        raf = 0
      }
    }
    raf = requestAnimationFrame(step)
  }

  watch(source, (value) => run(Number(value) || 0), { immediate: true })
  onBeforeUnmount(stop)

  return computed(() => Math.round(current.value))
}
