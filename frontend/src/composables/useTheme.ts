import { ref, watch, computed, onMounted, getCurrentInstance, nextTick, type ComputedRef, type Ref } from 'vue'
import { storage } from '@/utils/storage'

const STORAGE_KEY = 'app-theme-preference'

const THEMES = Object.freeze({
  DARK: 'dark',
  LIGHT: 'light',
})

type Theme = 'dark' | 'light'

const theme = ref<Theme>(THEMES.DARK)
let initialized = false
let mediaQueryListener: ((e: MediaQueryListEvent) => void) | null = null
let transitionLockTimer: ReturnType<typeof setTimeout> | null = null

/** 圆形擦除：扩散与淡出的时长（与 --dur-4 / --dur-3 同一档） */
const WIPE_SPREAD_MS = 480
const WIPE_FADE_MS = 240
/** 擦除进行中：挡住连点，避免同时叠两层遮罩 */
let wipeRunning = false

function prefersReducedMotion(): boolean {
  return typeof window !== 'undefined' &&
    (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false)
}

function readInitialTheme(): Theme {
  if (typeof window === 'undefined') return THEMES.DARK
  const saved = storage.local.get(STORAGE_KEY)
  if (saved === THEMES.DARK || saved === THEMES.LIGHT) return saved
  const prefersDark = window.matchMedia?.('(prefers-color-scheme: dark)').matches
  return prefersDark ? THEMES.DARK : THEMES.LIGHT
}

/**
 * 将指定的主题应用到 DOM 上。
 * 通过操作 document.documentElement 的 class 和 style 来实现暗色/亮色模式的切换。
 * 切换瞬间加 html.theme-switching 锁全局禁用过渡（与主题 class 同一次样式计算生效），
 * 所有部件直接变色、无渐变动画；绘制完成后（双 rAF）移除锁，恢复 hover 反馈。
 */
export function applyThemeToDOM(value: Theme) {
  if (typeof document === 'undefined') return
  const root = document.documentElement
  const isDark = value === THEMES.DARK

  // 0) DOM 已是目标主题时直接跳过：避免每个组件挂载时都重放
  //    「加锁→切换→解锁」流程（会对全站过渡做一次无意义干预）
  const alreadyApplied =
    root.classList.contains('theme-dark') === isDark &&
    root.classList.contains('theme-light') === !isDark &&
    root.style.colorScheme === (isDark ? 'dark' : 'light')
  if (alreadyApplied) return

  // 1) 加锁（禁用全站过渡）并强制回流，确保锁先于主题变更提交到计算样式
  root.classList.add('theme-switching')
  void root.offsetWidth
  if (transitionLockTimer) clearTimeout(transitionLockTimer)

  // 2) 翻转主题：锁与主题 class 同帧生效 → 直接变色
  root.classList.toggle('theme-dark', isDark)
  root.classList.toggle('theme-light', !isDark)
  root.classList.toggle('dark', isDark)
  root.style.colorScheme = isDark ? 'dark' : 'light'
  document.body?.setAttribute('data-theme', value)

  // 3) 双 rAF（翻转已绘制）后解锁，恢复常规 hover 过渡
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      root.classList.remove('theme-switching')
    })
  })
}

/**
 * 十六进制颜色转 rgba；非 hex（如 rgb()/变量求值失败）时原样返回
 */
function withAlpha(color: string, alpha: number): string {
  const hex = color.trim().replace('#', '')
  if (!/^[0-9a-fA-F]{3}$|^[0-9a-fA-F]{6}$/.test(hex)) return color
  const full = hex.length === 3 ? hex.split('').map((c) => c + c).join('') : hex
  const n = parseInt(full, 16)
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${alpha})`
}

/**
 * 读取目标主题的「页面底色 + 背景图 + 压暗强度」。
 * 主题变量都挂在 html.theme-dark / html.theme-light 上，没法用一个临时 div 探针求值，
 * 所以底色单独由 --wipe-surface-* 提供（与两种主题的 --bg-base 同值），
 * 压暗强度与背景图则与当前主题无关，直接从根节点读。
 */
function readTargetSurface(target: Theme): { base: string; dim: number; image: string } {
  const rootStyle = getComputedStyle(document.documentElement)
  const base =
    rootStyle.getPropertyValue(`--wipe-surface-${target}`).trim() ||
    (target === THEMES.DARK ? '#000000' : '#f5f5f7')
  const dimValue = Number(rootStyle.getPropertyValue('--page-bg-dim').trim())
  const rawImage = rootStyle.getPropertyValue('--page-bg-image').trim()
  // 变量为空 = 没配置过，用 .page-bg 的默认背景图；显式 'none' = 后台选了纯色背景，照实不放图
  const image = rawImage || 'url("/images/background-light.webp")'
  return {
    base,
    dim: Number.isFinite(dimValue) && dimValue > 0 ? dimValue : 0.55,
    image
  }
}

/**
 * 铺出「目标主题页面」的遮罩层：底色 + 背景图 + 压暗层，
 * 三层叠出来的观感与切换完成后的页面一致，遮罩散去时不会突然从纯色跳成图片。
 */
function buildWipeLayer(target: Theme): HTMLDivElement {
  const { base, dim, image } = readTargetSurface(target)
  const layer = document.createElement('div')
  layer.className = 'theme-wipe-layer'
  layer.setAttribute('aria-hidden', 'true')
  layer.style.background = base

  if (image && image !== 'none') {
    const photo = document.createElement('div')
    photo.className = 'theme-wipe-photo'
    photo.style.backgroundImage = image
    layer.appendChild(photo)

    const tint = document.createElement('div')
    tint.className = 'theme-wipe-tint'
    tint.style.background = withAlpha(base, dim)
    layer.appendChild(tint)
  }
  return layer
}

/**
 * 圆形擦除切换主题：
 * 1) 目标主题底色先铺在固定层上，clip-path 从按钮中心 circle(0) 扩到最远角；
 * 2) 完全遮住后才翻转主题 class（此时露不出来，也就不会闪白）；
 * 3) 遮罩淡出，露出已经换好的页面。
 * 用 Web Animations API 而不是 CSS transition：applyThemeToDOM 会给全站加
 * `transition: none !important` 的锁，CSS 过渡会被这条规则吃掉。
 * @returns 是否已接管本次切换（false 表示调用方应直接瞬切）
 */
function runThemeWipe(next: Theme, origin: { x: number; y: number }): boolean {
  if (wipeRunning || typeof document === 'undefined' || !document.body) return false
  const layer = buildWipeLayer(next)
  const x = Math.round(origin.x)
  const y = Math.round(origin.y)
  // 半径取到视口最远角，保证任意点击位置都能盖满
  const radius = Math.ceil(Math.hypot(Math.max(x, window.innerWidth - x), Math.max(y, window.innerHeight - y)))
  layer.style.clipPath = `circle(0px at ${x}px ${y}px)`
  document.body.appendChild(layer)
  wipeRunning = true

  const grow = layer.animate(
    [
      { clipPath: `circle(0px at ${x}px ${y}px)` },
      { clipPath: `circle(${radius}px at ${x}px ${y}px)` }
    ],
    // 曲线偏中性：强 ease-out 会在头一两帧就把圆扩到 70% 以上，
    // 看起来像「闪一下」而不是「擦过去」，所以这里不用 --ease-standard
    { duration: WIPE_SPREAD_MS, easing: 'cubic-bezier(0.35, 0, 0.15, 1)', fill: 'forwards' }
  )

  grow.finished
    .then(async () => {
      theme.value = next
      await nextTick()
      const fade = layer.animate(
        [{ opacity: 1 }, { opacity: 0 }],
        { duration: WIPE_FADE_MS, easing: 'ease-out', fill: 'forwards' }
      )
      await fade.finished
      layer.remove()
      wipeRunning = false
    })
    .catch(() => {
      layer.remove()
      wipeRunning = false
    })

  return true
}

/** 跟随系统配色：用户没有手动选过主题时，系统切换即跟随 */
function bindSystemPreference() {
  if (typeof window === 'undefined') return
  const mql = window.matchMedia('(prefers-color-scheme: dark)')
  mediaQueryListener = (e: MediaQueryListEvent) => {
    const saved = storage.local.get(STORAGE_KEY)
    if (saved !== THEMES.DARK && saved !== THEMES.LIGHT) {
      theme.value = e.matches ? THEMES.DARK : THEMES.LIGHT
    }
  }
  if (mql.addEventListener) mql.addEventListener('change', mediaQueryListener)
  else if (mql.addListener) mql.addListener(mediaQueryListener)
}

export interface ThemeComposable {
  theme: Ref<Theme>
  isDark: ComputedRef<boolean>
  isLight: ComputedRef<boolean>
  /**
   * 切换主题；传入点击位置时从该点做圆形擦除，
   * 不传（或系统要求减少动效）则直接瞬切
   */
  toggleTheme: (origin?: { x: number; y: number }) => void
  setTheme: (val: Theme) => void
  resetToSystem: () => void
  THEMES: Readonly<{ DARK: 'dark', LIGHT: 'light' }>
}

/**
 * 主题切换管理 composable。
 * 提供暗色/亮色模式的切换、持久化和系统偏好跟随功能。
 * 初始化时优先读取本地存储的偏好,其次跟随系统配色方案,默认使用暗色模式。
 * 主题变更会自动同步到 DOM 并持久化到 localStorage。
 */
export function useTheme(): ThemeComposable {
  if (!initialized) {
    initialized = true
    const domTheme =
      typeof document !== 'undefined' &&
      document.documentElement.classList.contains('theme-light')
        ? THEMES.LIGHT
        : typeof document !== 'undefined' &&
            document.documentElement.classList.contains('theme-dark')
          ? THEMES.DARK
          : null
    theme.value = domTheme || readInitialTheme()
    applyThemeToDOM(theme.value)
    bindSystemPreference()
  }

  // 每个调用组件都注册自己的 onMounted,确保组件挂载时重新应用主题
  if (getCurrentInstance()) {
    onMounted(() => applyThemeToDOM(theme.value))
  }

  const isDark = computed(() => theme.value === THEMES.DARK)
  const isLight = computed(() => theme.value === THEMES.LIGHT)

  /**
   * 在暗色和亮色模式之间切换。
   * @param origin 触发按钮的中心坐标（视口坐标）；有坐标且未开启减少动效时走圆形擦除
   */
  function toggleTheme(origin?: { x: number; y: number }) {
    // 擦除未结束前忽略连点：否则会叠出两层遮罩
    if (wipeRunning) return
    const next = theme.value === THEMES.DARK ? THEMES.LIGHT : THEMES.DARK
    if (origin && !prefersReducedMotion()) {
      if (runThemeWipe(next, origin)) return
    }
    theme.value = next
  }

  /** 设置主题为指定值,仅接受 'dark' 或 'light' */
  function setTheme(val: Theme) {
    if (val === THEMES.DARK || val === THEMES.LIGHT) theme.value = val
  }

  /** 重置主题偏好,清除本地存储并恢复为跟随系统配色方案 */
  function resetToSystem() {
    storage.local.remove(STORAGE_KEY)
    const prefersDark =
      typeof window !== 'undefined' &&
      window.matchMedia?.('(prefers-color-scheme: dark)').matches
    theme.value = prefersDark ? THEMES.DARK : THEMES.LIGHT
  }

  return {
    theme,
    isDark,
    isLight,
    toggleTheme,
    setTheme,
    resetToSystem,
    THEMES,
  }
}

// 模块级 watch,不绑定任何组件生命周期,确保主题切换在组件卸载后仍生效
watch(
  theme,
  (val) => {
    applyThemeToDOM(val)
    storage.local.set(STORAGE_KEY, val)
  },
  { immediate: false },
)
