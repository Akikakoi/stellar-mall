<template>
  <div class="app-root">
    <NavHeader v-if="showHeader" />
    <div :class="{ 'page-bg': !isAdmin && !isRag, 'page-bg--rag': isRag }">
      <router-view />
    </div>
    <FloatingSidebar v-if="showFloatingSidebar" />
  </div>
</template>

<script setup lang="ts">
import { computed, watchEffect } from 'vue'
import { useRoute } from 'vue-router'
import NavHeader from '@/components/NavHeader.vue'
import FloatingSidebar from '@/components/FloatingSidebar.vue'
import { getSiteBg } from '@/api/mall'

const route = useRoute()
const hideHeaderPaths = ['/login', '/register', '/shop/search', '/me', '/me/messages', '/wallet', '/points']
const hideSidebarPaths = ['/login', '/register', '/shop/search', '/me', '/me/messages', '/wallet', '/points']
const isAdmin = computed(() => route.path.startsWith('/admin'))
// 商品详情页隐藏顶部导航，沉浸式浏览
const isSpuDetail = computed(() => route.path.startsWith('/spu/'))
const isRag = computed(() => route.path.startsWith('/rag'))
const showHeader = computed(() => !isAdmin.value && !isSpuDetail.value && !hideHeaderPaths.includes(route.path))
// AI 助手页面本身已有左侧会话栏，右侧浮动栏会遮挡聊天区，故隐藏；商品详情页同样隐藏
const showFloatingSidebar = computed(() =>
  showHeader.value && !isRag.value
)
// 弹层作用域标记：ElMessageBox/Drawer 等 JS 服务弹层 teleport 到 body，
// 无法用 DOM 后代选择器区分 C 端与后台 → 按路由给 body 挂 area-user/area-admin
// 标记，供 main.scss 里 MessageBox 等 C 端玻璃化样式限定作用域（后台保持实底）。
// 商城主页背景图：进入 C 端路由时拉取配置并注入 html 根 CSS 变量 --page-bg-image
// （.page-bg::before 图源由它驱动），带 15s 节流避免每路由跳转都请求；
// 管理端装修保存后切回前台即可看到新背景。接口匿名放行 + 内部静默，任何异常
// 保持默认背景（变量未设置即 CSS fallback 默认图），绝不影响页面渲染。
let lastBgFetch = 0
async function applySiteBg() {
  try {
    const cfg = await getSiteBg()
    const url = cfg?.bgImage || ''
    if (url) {
      document.documentElement.style.setProperty('--page-bg-image', `url("${url}")`)
    } else {
      document.documentElement.style.removeProperty('--page-bg-image')
    }
  } catch {
    // 静默：后端不可用/接口未部署时保持默认背景
  }
}
watchEffect(() => {
  const admin = route.path.startsWith('/admin')
  document.body.classList.toggle('area-admin', admin)
  document.body.classList.toggle('area-user', !admin)
  if (!admin && !route.path.startsWith('/rag')) {
    const now = Date.now()
    if (now - lastBgFetch > 15000) {
      lastBgFetch = now
      applySiteBg()
    }
  }
})
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  height: 100%;
  width: 100%;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

.app-root {
  min-height: 100%;
}

a {
  text-decoration: none;
  color: inherit;
}

/* 全站背景（用户端页面）。
 * 背景图放到 ::before / ::after 这两个独立的 position:fixed 图层上，
 * 而非挂在 .page-bg 自身并用 background-attachment: fixed。
 * 这样背景是稳定、独立的合成层：弹窗遮罩层开合时浏览器不会重绘该层，
 * 从根本上避免关闭弹窗瞬间整页闪烁，且视觉上仍是固定背景。 */
.page-bg {
  min-height: 100vh;
  background-color: var(--bg-base);
  position: relative;
}
.page-bg::before,
.page-bg::after {
  content: '';
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
}
.page-bg::before {
  /* 背景图源由 CSS 变量驱动：后台装修保存后注入 var(--page-bg-image) 即可即时换图；
     变量未设置/被移除时自动回落默认背景图（= 恢复默认）。 */
  background-image: var(--page-bg-image, url('/images/background-light.webp'));
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
}
.page-bg::after {
  background: var(--bg-base);
  opacity: 0.55;
}
.page-bg > * {
  position: relative;
  z-index: 1;
}

/* AI 助手页面：NavHeader 64px + 布局区 = 100vh，避免底部空白 */
.page-bg--rag {
  min-height: auto;
  height: calc(100vh - 64px);
}
</style>
