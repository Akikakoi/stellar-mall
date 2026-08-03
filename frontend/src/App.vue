<template>
  <div class="app-root">
    <NavHeader v-if="showHeader" />
    <div :class="{ 'page-bg': showHeader || route.path === '/shop/search' || route.path === '/points' }">
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
const showHeader = computed(() => !isAdmin.value && !isSpuDetail.value && !hideHeaderPaths.includes(route.path))
// AI 助手页面本身已有左侧会话栏，右侧浮动栏会遮挡聊天区，故隐藏；商品详情页同样隐藏
const showFloatingSidebar = computed(() =>
  showHeader.value && !route.path.startsWith('/rag')
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

/* 全站背景图（用户端页面） */
.page-bg {
  min-height: 100vh;
  background-color: var(--bg-base);
  background-image: url('/images/background-light.webp');
  background-size: cover;
  background-position: center;
  background-attachment: fixed;
  background-repeat: no-repeat;
  position: relative;
}
.page-bg::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background: var(--bg-base);
  opacity: 0.55;
}
.page-bg > * {
  position: relative;
  z-index: 1;
}
</style>
