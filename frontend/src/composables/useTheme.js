import { ref, watch, computed, onMounted, getCurrentInstance } from 'vue'
import { storage } from '@/utils/storage'

const STORAGE_KEY = 'app-theme-preference'

const THEMES = Object.freeze({
  DARK: 'dark',
  LIGHT: 'light',
})

const theme = ref(/** @type {'dark'|'light'} */ (THEMES.DARK))
let initialized = false
let mountedSyncRegistered = false
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

  // 切换前临时禁用所有过渡，防止不同元素过渡时长不一致导致闪烁
  root.classList.add('theme-transitioning')
  if (transitionLockTimer) clearTimeout(transitionLockTimer)

  root.classList.toggle('theme-dark', isDark)
  root.classList.toggle('theme-light', !isDark)
  root.classList.toggle('dark', isDark)
  root.style.colorScheme = isDark ? 'dark' : 'light'

  document.body?.setAttribute('data-theme', value)

  // 浏览器应用新主题后，继续保持统一过渡类直到完整过渡结束
  if (typeof requestAnimationFrame !== 'undefined') {
    requestAnimationFrame(() => {
      transitionLockTimer = setTimeout(() => {
        root.classList.remove('theme-transitioning')
      }, 800)
    })
  } else {
    root.classList.remove('theme-transitioning')
  }
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

    watch(
      theme,
      (val) => {
        applyThemeToDOM(val)
        storage.local.set(STORAGE_KEY, val)
      },
      { immediate: false },
    )
  }

  if (!mountedSyncRegistered && getCurrentInstance()) {
    mountedSyncRegistered = true
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
