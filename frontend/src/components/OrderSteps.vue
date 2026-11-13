<template>
  <ol class="order-steps" :aria-label="label">
    <!-- 进度轨道：只画在首尾圆点之间，填充用 scaleX，避免动 width -->
    <div class="ostep-track" aria-hidden="true"
      :style="{ left: `${100 / (2 * steps.length)}%`, width: `${(100 * (steps.length - 1)) / steps.length}%` }">
      <span class="ostep-track-fill" :style="{ transform: `scaleX(${progress})` }"></span>
    </div>

    <li
      v-for="(step, i) in steps"
      :key="step"
      class="ostep"
      :class="{ 'is-done': i < current - 1, 'is-current': i === current - 1 }"
      :aria-current="i === current - 1 ? 'step' : undefined"
    >
      <span class="ostep-dot">
        <svg v-if="i < current - 1" viewBox="0 0 16 16" width="10" height="10" aria-hidden="true">
          <path d="M3 8.4 6.2 11.5 13 4.5" fill="none" stroke="currentColor" stroke-width="2"
            stroke-linecap="round" stroke-linejoin="round" />
        </svg>
        <template v-else>{{ i + 1 }}</template>
      </span>
      <span class="ostep-label">{{ step }}</span>
    </li>
  </ol>
</template>

<script setup lang="ts">
/**
 * 结算流程进度条（不是可跳转的分步表单）
 *
 * 只做一件事：告诉用户「现在走到第几步、还差几步」。
 * 因此没有点击态——真正的导航由页面自身的按钮负责，
 * 步数也刻意压到 3 步，超过 5 步反而劝退。
 */
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  /** 当前步（从 1 开始） */
  current: number
  /** 步骤文案 */
  steps?: string[]
  /** 无障碍分组名 */
  label?: string
}>(), {
  steps: () => ['选择商品', '确认订单', '支付'],
  label: '下单进度'
})

/** 已完成段数占比：3 步走到第 2 步 = 0.5 */
const progress = computed(() => {
  const total = props.steps.length
  if (total < 2) return 0
  return Math.min(Math.max(props.current - 1, 0), total - 1) / (total - 1)
})
</script>

<style scoped>
.order-steps {
  position: relative;
  display: flex;
  align-items: flex-start;
  list-style: none;
  margin: 0 0 20px;
  padding: 4px 0 0;
}

.ostep-track {
  position: absolute;
  top: 17px; /* 与 28px 圆点的垂直中心对齐 */
  height: 2px;
  border-radius: 1px;
  background: var(--border-base);
  overflow: hidden;
}
.ostep-track-fill {
  display: block;
  width: 100%;
  height: 100%;
  border-radius: inherit;
  background: var(--brand-primary);
  transform-origin: left center;
  transition: transform var(--dur-4) var(--ease-standard);
}

.ostep {
  position: relative;
  z-index: 1;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.ostep-dot {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--text-secondary);
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  transition:
    background-color var(--dur-2) var(--ease-standard),
    color var(--dur-2) var(--ease-standard),
    border-color var(--dur-2) var(--ease-standard);
}

.ostep-label {
  font-size: 13px;
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}

/* 已完成：实心，勾由 SVG 画 */
.ostep.is-done .ostep-dot {
  background: var(--brand-primary);
  border-color: var(--brand-primary);
  color: #fff;
}
.ostep.is-done .ostep-label { color: var(--text-primary); }

/* 当前步：白底 + 品牌描边 + 加粗，不填充颜色，和「已完成」区分开 */
.ostep.is-current .ostep-dot {
  background: var(--bg-card);
  border-color: var(--brand-primary);
  color: var(--brand-primary);
  box-shadow: 0 0 0 4px var(--brand-primary-soft);
}
.ostep.is-current .ostep-label {
  color: var(--text-primary);
  font-weight: 600;
}

@media (max-width: 480px) {
  .ostep-label { font-size: 12px; }
}
</style>
