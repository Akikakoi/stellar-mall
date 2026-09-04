import { ref, watch, computed, onMounted, getCurrentInstance, type ComputedRef, type Ref } from 'vue'
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

function readInitialTheme(): Theme {
  if (typeof window === 'undefined') return THEMES.DARK
  const saved = storage.local.get(STORAGE_KEY)
  if (saved === THEMES.DARK || saved === THEMES.LIGHT) return saved
  const prefersDark = window.matchMedia?.('(prefers-color-scheme: dark)').matches
  return prefersDark ? THEMES.DARK : THEMES.LIGHT
}

/**
 * 将指定的主题应用到 DOM 上。
 * 通过操作 document.documentElement 的 class 和 style 来实现暗色/亮色模式的切换,
 * 并加入过渡锁防止切换时出现闪烁动画。
 */
export function applyThemeToDOM(value: Theme) {
  if (typeof document === 'undefined') return
  const root = document.documentElement
  const isDark = value === THEMES.DARK

  // 1) 先加锁,强制浏览器提交这一帧——此时所有过渡已冻结
  root.classList.add('theme-transitioning')
  if (transitionLockTimer) clearTimeout(transitionLockTimer)

  // 2) 在下一帧执行实际主题切换,确保锁先生效
  requestAnimationFrame(() => {
    root.classList.toggle('theme-dark', isDark)
    root.classList.toggle('theme-light', !isDark)
    root.classList.toggle('dark', isDark)
    root.style.colorScheme = isDark ? 'dark' : 'light'
    document.body?.setAttribute('data-theme', value)

    // 3) 等 CSS 变量全部挂载后再解锁,恢复统一过渡
    transitionLockTimer = setTimeout(() => {
      root.classList.remove('theme-transitioning')
    }, 0)
  })
}

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
  toggleTheme: () => void
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

  /** 在暗色和亮色模式之间切换 */
  function toggleTheme() {
    theme.value = theme.value === THEMES.DARK ? THEMES.LIGHT : THEMES.DARK
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
