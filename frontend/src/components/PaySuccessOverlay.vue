<template>
  <Transition name="pay">
    <div v-if="show" class="pay-overlay" @click="finish">
      <canvas ref="canvasRef" class="pay-canvas" aria-hidden="true"></canvas>
      <div class="pay-card" role="status" aria-live="polite">
        <svg class="pay-check" viewBox="0 0 56 56" aria-hidden="true">
          <circle class="pay-check-ring" cx="28" cy="28" r="25" />
          <path class="pay-check-mark" d="M16 29.5 24 37.5 40 21" />
        </svg>
        <p class="pay-title">支付成功</p>
        <p v-if="Number(amount) > 0" class="pay-amount">¥{{ Number(amount).toFixed(2) }}</p>
        <p class="pay-hint">点击任意位置继续</p>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
/**
 * 支付成功的一次性反馈：对勾描边 + 纸屑。
 *
 * 约定（来自 ui-atlas 的成功纸屑）：
 * - 全站只在「真的付款成功」时出现一次，加购、收藏这类高频操作绝不撒；
 * - 纸屑用 canvas + 重力，出屏即剔除，粒子清空后 rAF 自动停；
 * - 系统开启「减少动态效果」时不撒纸屑，对勾直接到位；
 * - 面板可点击跳过，1.8 秒后自动收尾，绝不挡住后续操作。
 */
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  /** 是否展示 */
  show: boolean
  /** 实付金额，0 或空则不显示金额行 */
  amount?: number | string
  /** 自动收尾时间（毫秒） */
  duration?: number
}>(), {
  amount: 0,
  duration: 1800
})

const emit = defineEmits<{ (event: 'done'): void }>()

interface Confetti {
  x: number
  y: number
  vx: number
  vy: number
  w: number
  h: number
  rot: number
  vr: number
  color: string
}

const canvasRef = ref<HTMLCanvasElement | null>(null)
let particles: Confetti[] = []
let raf = 0
let doneTimer: number | undefined
/** 点击跳过与自动收尾可能同时发生，用一个标记保证只通知一次 */
let finished = false
let ctx: CanvasRenderingContext2D | null = null
let viewW = 0
let viewH = 0
let paused = false

const COLORS = ['#0071e3', '#34c759', '#ff9500', '#ff3b30', '#af52de', '#ffd60a']

function prefersReducedMotion(): boolean {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
}

/** 在面板上方炸开一簇纸屑：初速向上，重力把它们拉回屏幕外 */
function spawn() {
  const canvas = canvasRef.value
  if (!canvas) return
  const dpr = Math.min(window.devicePixelRatio || 1, 2)
  viewW = canvas.clientWidth
  viewH = canvas.clientHeight
  canvas.width = Math.round(viewW * dpr)
  canvas.height = Math.round(viewH * dpr)
  ctx = canvas.getContext('2d')
  ctx?.setTransform(dpr, 0, 0, dpr, 0, 0)

  const count = 90
  particles = Array.from({ length: count }, () => ({
    x: viewW / 2 + (Math.random() - 0.5) * 60,
    y: viewH * 0.42,
    vx: (Math.random() - 0.5) * 10,
    vy: -7 - Math.random() * 7,
    w: 4 + Math.random() * 5,
    h: 7 + Math.random() * 6,
    rot: Math.random() * Math.PI,
    vr: (Math.random() - 0.5) * 0.28,
    color: COLORS[Math.floor(Math.random() * COLORS.length)]!
  }))
  if (!raf) raf = requestAnimationFrame(tick)
}

function tick() {
  raf = 0
  if (!ctx) return
  ctx.clearRect(0, 0, viewW, viewH)
  const alive: Confetti[] = []
  for (const p of particles) {
    p.vy += 0.3
    p.vx *= 0.995
    p.x += p.vx
    p.y += p.vy
    p.rot += p.vr
    if (p.y < viewH + 40) alive.push(p)
    ctx.save()
    ctx.translate(p.x, p.y)
    ctx.rotate(p.rot)
    ctx.fillStyle = p.color
    ctx.fillRect(-p.w / 2, -p.h / 2, p.w, p.h)
    ctx.restore()
  }
  particles = alive
  // 全部出屏后停掉，别让几十个预览一起空转
  if (particles.length && !paused) raf = requestAnimationFrame(tick)
}

function onVisibility() {
  paused = document.hidden
  if (paused) {
    cancelAnimationFrame(raf)
    raf = 0
  } else if (particles.length) {
    raf = requestAnimationFrame(tick)
  }
}

function finish() {
  if (!props.show || finished) return
  finished = true
  emit('done')
}

function stop() {
  cancelAnimationFrame(raf)
  raf = 0
  particles = []
  ctx?.clearRect(0, 0, viewW, viewH)
  if (doneTimer) {
    clearTimeout(doneTimer)
    doneTimer = undefined
  }
  document.removeEventListener('visibilitychange', onVisibility)
}

watch(() => props.show, async (visible) => {
  if (visible) {
    finished = false
    await nextTick()
    if (!prefersReducedMotion()) {
      document.addEventListener('visibilitychange', onVisibility)
      spawn()
    }
    doneTimer = window.setTimeout(finish, props.duration)
  } else {
    stop()
  }
})

onBeforeUnmount(stop)
</script>

<style scoped>
.pay-overlay {
  position: fixed;
  inset: 0;
  z-index: 4000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.28);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}

.pay-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.pay-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  min-width: 240px;
  padding: 30px 34px 22px;
  border-radius: var(--radius-lg);
  background: var(--glass-bg);
  backdrop-filter: var(--backdrop-blur);
  -webkit-backdrop-filter: var(--backdrop-blur);
  border: 1px solid var(--glass-border);
  box-shadow: var(--glass-shadow), inset 0 1px 0 var(--glass-highlight);
  transition: transform var(--dur-3) var(--ease-enter);
}

.pay-check {
  width: 56px;
  height: 56px;
  margin-bottom: 6px;
}
/* 外圈先画，再画对勾：两段 dashoffset 归零就是「描边」动画 */
.pay-check-ring,
.pay-check-mark {
  fill: none;
  stroke: var(--status-success);
  stroke-linecap: round;
  stroke-linejoin: round;
}
.pay-check-ring {
  stroke-width: 2;
  opacity: 0.35;
  stroke-dasharray: 158;
  stroke-dashoffset: 158;
  animation: pay-ring var(--dur-5) var(--ease-out) forwards;
}
.pay-check-mark {
  stroke-width: 3.5;
  stroke-dasharray: 42;
  stroke-dashoffset: 42;
  animation: pay-tick var(--dur-4) var(--ease-out) 140ms forwards;
}
@keyframes pay-ring {
  to { stroke-dashoffset: 0; }
}
@keyframes pay-tick {
  to { stroke-dashoffset: 0; }
}

.pay-title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: var(--text-primary);
}
.pay-amount {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}
.pay-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--text-secondary);
  opacity: 0.75;
}

.pay-enter-active,
.pay-leave-active { transition: opacity var(--dur-2) var(--ease-standard); }
.pay-enter-from,
.pay-leave-to { opacity: 0; }
.pay-enter-from .pay-card { transform: scale(0.94); }
</style>
