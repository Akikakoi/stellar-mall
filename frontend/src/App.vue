<template>
  <div class="app-root">
    <NavHeader v-if="showHeader" />
    <div :class="{ 'page-bg': !isAdmin && !isRag, 'page-bg--rag': isRag }">
      <router-view />
    </div>
    <FloatingSidebar v-if="showFloatingSidebar" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import NavHeader from '@/components/NavHeader.vue'
import FloatingSidebar from '@/components/FloatingSidebar.vue'

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
  background-image: url('/images/background-light.webp');
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
