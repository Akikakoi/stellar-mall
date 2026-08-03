import { ref, watch, computed, onMounted, getCurrentInstance } from 'vue'
import { storage } from '@/utils/storage'

const STORAGE_KEY = 'app-theme-preference'

const THEMES = Object.freeze({
  DARK: 'dark',
  LIGHT: 'light',
})

const theme = ref(/** @type {'dark'|'light'} */ (THEMES.DARK))
let initialized = false
let mediaQueryListener = null
let transitionLockTimer = null

function readInitialTheme() {
  if (typeof window === 'undefined') return THEMES.DARK
  const saved = storage.local.get(STORAGE_KEY)
  if (saved === THEMES.DARK || saved === THEMES.LIGHT) return saved
  const prefersDark = window.matchMedia?.('(prefers-color-scheme: dark)').matches
  return prefersDark ? THEMES.DARK : THEMES.LIGHT
}

export function applyThemeToDOM(value) {
  if (typeof document === 'undefined') return
  const root = document.documentElement
  const isDark = value === THEMES.DARK

  // 1) 先加锁，强制浏览器提交这一帧——此时所有过渡已冻结
  root.classList.add('theme-transitioning')
  if (transitionLockTimer) clearTimeout(transitionLockTimer)

  // 2) 在下一帧执行实际主题切换，确保锁先生效
  requestAnimationFrame(() => {
    root.classList.toggle('theme-dark', isDark)
    root.classList.toggle('theme-light', !isDark)
    root.classList.toggle('dark', isDark)
    root.style.colorScheme = isDark ? 'dark' : 'light'
    document.body?.setAttribute('data-theme', value)

    // 3) 等 CSS 变量全部挂载后再解锁，恢复过渡
    transitionLockTimer = setTimeout(() => {
      root.classList.remove('theme-transitioning')
    }, 200)
  })
}

function bindSystemPreference() {
  if (typeof window === 'undefined') return
  const mql = window.matchMedia('(prefers-color-scheme: dark)')
  mediaQueryListener = (e) => {
    const saved = storage.local.get(STORAGE_KEY)
    if (saved !== THEMES.DARK && saved !== THEMES.LIGHT) {
      theme.value = e.matches ? THEMES.DARK : THEMES.LIGHT
    }
  }
  if (mql.addEventListener) mql.addEventListener('change', mediaQueryListener)
  else if (mql.addListener) mql.addListener(mediaQueryListener)
}

export function useTheme() {
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

  // 每个调用组件都注册自己的 onMounted，确保组件挂载时重新应用主题
  if (getCurrentInstance()) {
    onMounted(() => applyThemeToDOM(theme.value))
  }

  const isDark = computed(() => theme.value === THEMES.DARK)
  const isLight = computed(() => theme.value === THEMES.LIGHT)

  function toggleTheme() {
    theme.value = theme.value === THEMES.DARK ? THEMES.LIGHT : THEMES.DARK
  }

  function setTheme(val) {
    if (val === THEMES.DARK || val === THEMES.LIGHT) theme.value = val
  }

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

// 模块级 watch，不绑定任何组件生命周期，确保主题切换在组件卸载后仍生效
watch(
  theme,
  (val) => {
    applyThemeToDOM(val)
    storage.local.set(STORAGE_KEY, val)
  },
  { immediate: false },
)
