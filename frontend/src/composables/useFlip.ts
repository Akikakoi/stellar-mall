/**
 * FLIP 网格重排动画（First / Last / Invert / Play）
 *
 * 模块职责：
 * - 数据变化前记录每张卡的旧位置，渲染后按「旧位置 - 新位置」的差值做反向位移，
 *   再归零 —— 元素看起来是从原处滑到新处，而不是瞬间跳过去。
 * - 只动 transform / opacity，不触发重排；用 Web Animations API 直接跑，
 *   不往元素上挂 class，animationend 后自动释放。
 *
 * 使用约定：
 * 1. 容器内参与动画的元素必须带 `data-flip-key="唯一键"`，键在两次渲染间保持稳定；
 * 2. 数据变化**之前**调用 capture()，`await nextTick()` 之后调用 play()；
 * 3. 首次渲染没有旧位置，capture() 记录为空，play() 直接跳过（不会把首屏卡片做成入场动画）。
 *
 * 注意（来自 ui-atlas 的踩坑记录）：
 * - 用 display:none 隐藏旧卡会丢掉旧 rect，必须先量再藏；
 * - 动画期间不要改网格列数，否则新旧坐标不在同一坐标系；
 * - 系统开启「减少动态效果」时直接跳过，不做位移也不做淡入。
 */
import { onBeforeUnmount, type Ref } from 'vue'

interface FlipOptions {
  /** 位移时长，默认 480ms（--dur-4） */
  duration?: number
  /** 位移曲线，默认 ease-standard */
  easing?: string
  /** 新出现元素的淡入时长 */
  enterDuration?: number
  /** 新元素之间的错峰间隔，单次最多累计 200ms */
  stagger?: number
  /** 参与动画的元素选择器 */
  selector?: string
}

interface FlipApi {
  /** 数据变化前调用：记录当前每张卡相对容器的位置 */
  capture: () => void
  /** 渲染完成后调用：执行位移与淡入 */
  play: () => void
}

/** 系统是否要求减少动画 */
function prefersReducedMotion(): boolean {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
}

export function useFlip(container: Ref<HTMLElement | null>, options: FlipOptions = {}): FlipApi {
  const {
    duration = 480,
    easing = 'cubic-bezier(0.2, 0.8, 0.2, 1)',
    enterDuration = 260,
    stagger = 24,
    selector = '[data-flip-key]'
  } = options

  /** 上一次的位置（相对容器左上角），空 Map 表示还没记录过 */
  let previous = new Map<string, { left: number; top: number }>()
  /** 正在跑的动画，卸载时要主动取消，避免回调打到已销毁的节点上 */
  const running: Animation[] = []

  /** 读取容器内所有参与动画的元素及其稳定键 */
  function readNodes(): Array<{ el: HTMLElement; key: string }> {
    const root = container.value
    if (!root) return []
    return Array.from(root.querySelectorAll<HTMLElement>(selector))
      .map((el) => ({ el, key: el.dataset.flipKey || '' }))
      .filter((item) => item.key !== '')
  }

  function cancelRunning() {
    while (running.length) running.pop()?.cancel()
  }

  function capture() {
    const root = container.value
    if (!root) {
      previous = new Map()
      return
    }
    const base = root.getBoundingClientRect()
    const next = new Map<string, { left: number; top: number }>()
    for (const { el, key } of readNodes()) {
      const rect = el.getBoundingClientRect()
      next.set(key, { left: rect.left - base.left, top: rect.top - base.top })
    }
    previous = next
  }

  function play() {
    const root = container.value
    if (!root || previous.size === 0) return
    if (prefersReducedMotion()) {
      previous = new Map()
      return
    }
    cancelRunning()
    const base = root.getBoundingClientRect()
    let entering = 0

    for (const { el, key } of readNodes()) {
      if (typeof el.animate !== 'function') continue
      const rect = el.getBoundingClientRect()
      const last = { left: rect.left - base.left, top: rect.top - base.top }
      const first = previous.get(key)

      if (first) {
        const dx = first.left - last.left
        const dy = first.top - last.top
        // 位移小于 1px 视为没动，避免给静止卡片挂空动画
        if (Math.abs(dx) < 1 && Math.abs(dy) < 1) continue
        running.push(el.animate(
          [
            { transform: `translate3d(${dx}px, ${dy}px, 0)` },
            { transform: 'translate3d(0, 0, 0)' }
          ],
          { duration, easing }
        ))
      } else {
        running.push(el.animate(
          [
            { opacity: 0, transform: 'scale(0.96)' },
            { opacity: 1, transform: 'scale(1)' }
          ],
          {
            duration: enterDuration,
            easing,
            delay: Math.min(entering * stagger, 200)
          }
        ))
        entering += 1
      }
    }

    previous = new Map()
  }

  onBeforeUnmount(cancelRunning)

  return { capture, play }
}
