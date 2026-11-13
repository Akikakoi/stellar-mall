<template>
  <div ref="rootRef" class="sliding-tabs" role="group" :aria-label="label">
    <button
      v-for="item in items"
      :key="String(item.value)"
      type="button"
      class="stab"
      :class="{ 'is-active': item.value === modelValue }"
      :aria-pressed="item.value === modelValue"
      @click="pick(item.value)"
      @mouseenter="hoverSwitch && emit('hover', item.value)"
    >{{ item.label }}</button>
    <span ref="indicatorRef" class="stab-indicator" aria-hidden="true"></span>
  </div>
</template>

<script setup lang="ts">
/**
 * 滑动下划线标签组（分类子标签用）
 *
 * 与「点一个换一个颜色」的区别：下划线是从上一个位置滑过去的，
 * 用户能看出层级关系而不是重新认识一排按钮。
 *
 * 实现要点：
 * - 下划线是容器里的绝对定位元素，用 translate + scaleX 定位到当前按钮，
 *   只动 transform，不触发布局；
 * - 允许换行：同时写 x 与 y，所以标签折到第二行时下划线也跟着上去；
 * - resize、字体加载完成、items 变化后都要重算一次，否则字宽变了会错位。
 */
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

export interface SlideTabItem {
  /** 提交给 modelValue 的值，null 常用于「全部」 */
  value: string | number | null
  label: string
}

const props = withDefaults(defineProps<{
  items: SlideTabItem[]
  modelValue: string | number | null
  /** 分组无障碍名 */
  label?: string
  /** 悬停即切换（首页兜底分区就是这样用的） */
  hoverSwitch?: boolean
}>(), {
  label: '分类筛选',
  hoverSwitch: false
})

const emit = defineEmits<{
  (event: 'update:modelValue', value: string | number | null): void
  (event: 'hover', value: string | number | null): void
}>()

const rootRef = ref<HTMLElement | null>(null)
const indicatorRef = ref<HTMLElement | null>(null)
let observer: ResizeObserver | null = null

/** 把下划线摆到当前按钮底下：位移用 translate，宽度用 scaleX（基准宽 1px） */
function syncIndicator() {
  const root = rootRef.value
  const indicator = indicatorRef.value
  if (!root || !indicator) return
  const active = root.querySelector<HTMLElement>('.stab.is-active')
  if (!active) {
    indicator.style.opacity = '0'
    return
  }
  indicator.style.opacity = '1'
  indicator.style.transform =
    `translate(${active.offsetLeft}px, ${active.offsetTop + active.offsetHeight - 2}px) scaleX(${active.offsetWidth})`
}

function scheduleSync() {
  nextTick(syncIndicator)
}

function pick(value: string | number | null) {
  if (value === props.modelValue) return
  emit('update:modelValue', value)
}

watch(() => [props.modelValue, props.items], scheduleSync)

onMounted(() => {
  scheduleSync()
  // 字体加载完后字宽会变，必须补算一次
  document.fonts?.ready.then(scheduleSync).catch(() => {})
  if (typeof ResizeObserver !== 'undefined' && rootRef.value) {
    observer = new ResizeObserver(scheduleSync)
    observer.observe(rootRef.value)
  }
})

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
})
</script>

<style scoped>
.sliding-tabs {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  gap: 22px;
}

.stab {
  /* 按钮重置：外观完全交给下面的排版规则 */
  appearance: none;
  border: none;
  background: none;
  margin: 0;
  font-family: inherit;
  font-size: 15px;
  line-height: 1.4;
  color: var(--text-secondary);
  cursor: pointer;
  padding: 0 0 4px;
  border-bottom: 2px solid transparent;
  user-select: none;
  transition: color var(--transition-color);
}

.stab:hover { color: var(--brand-primary); }
.stab:focus-visible {
  outline: 2px solid var(--brand-primary);
  outline-offset: 3px;
  border-radius: 4px;
}
.stab.is-active { color: var(--brand-primary); font-weight: 600; }

.stab-indicator {
  position: absolute;
  top: 0;
  left: 0;
  width: 1px;
  height: 2px;
  border-radius: 1px;
  background: var(--brand-primary);
  transform-origin: left top;
  opacity: 0;
  pointer-events: none;
  transition:
    transform var(--dur-3) var(--ease-standard),
    opacity var(--dur-1) var(--ease-standard);
}

@media (max-width: 480px) {
  .sliding-tabs { gap: 14px; }
  .stab { font-size: 13px; }
}
</style>
