import { storage } from '@/utils/storage'

(function initThemeBeforeMount() {
  const STORAGE_KEY = 'app-theme-preference'
  const saved = storage.local.get(STORAGE_KEY)
  let theme = 'dark'
  if (saved === 'dark' || saved === 'light') {
    theme = saved
  } else if (window.matchMedia) {
    theme = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  }
  const isDark = theme === 'dark'
  const root = document.documentElement
  root.classList.toggle('theme-dark', isDark)
  root.classList.toggle('theme-light', !isDark)
  root.classList.toggle('dark', isDark)
  root.style.colorScheme = isDark ? 'dark' : 'light'
  document.body?.setAttribute('data-theme', theme)
})()

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/theme-chalk/dark/css-vars.css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import App from './App.vue'
import router from './router'
import './assets/main.scss'

const SVG_NO_IMAGE =
  'data:image/svg+xml;utf8,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 300">' +
    '<rect width="100%" height="100%" fill="#f2f3f5"/>' +
    '<text x="50%" y="50%" text-anchor="middle" dominant-baseline="middle" ' +
    'fill="#c0c4cc" font-family="Arial, sans-serif" font-size="22">暂无图片</text></svg>'
  )
window.__PH = SVG_NO_IMAGE

const app = createApp(App)
app.config.errorHandler = (err, instance, info) => {
  console.error('[Vue error]', info, err)
}
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')