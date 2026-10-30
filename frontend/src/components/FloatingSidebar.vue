<template>
  <div class="floating-sidebar" :class="{ 'is-hidden': !showSidebar }">
    <div class="sidebar-inner">
      <div v-for="item in items" :key="item.key" class="sidebar-item" @click="item.action">
        <el-badge :value="item.badge" :hidden="!item.badge" :max="99">
          <el-icon :size="22"><component :is="item.icon" /></el-icon>
        </el-badge>
        <span class="sidebar-label">{{ item.label }}</span>
      </div>

      <div class="sidebar-divider" v-show="showBackTop"></div>

      <div class="sidebar-item" v-show="showBackTop" @click="scrollToTop">
        <el-icon :size="22"><Top /></el-icon>
        <span class="sidebar-label">顶部</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ShoppingCartFull,
  Star,
  List,
  Headset,
  Top
} from '@element-plus/icons-vue'
import { useCartStore } from '@/stores/cart'

const router = useRouter()
const route = useRoute()
const cartStore = useCartStore()

const cartCount = computed(() =>
  cartStore.totalCount > 0 && !cartStore.badgeSeen ? cartStore.totalCount : 0
)
const showBackTop = ref(false)

/** 首页顶部（Hero 区域）先隐藏快捷栏，等商品模块进入视口后再出现 */
const isHome = computed(() => route.path === '/')
const showHomeSidebar = ref(false)
const showSidebar = computed(() => !isHome.value || showHomeSidebar.value)
let syncTimer: number | undefined

function updateHomeSidebar() {
  if (!isHome.value) {
    showHomeSidebar.value = true
    return
  }
  const content = document.querySelector('.home-content') as HTMLElement | null
  if (!content) {
    showHomeSidebar.value = false
    return
  }
  // 商品模块进入视口下 1/4 后，快捷栏再淡入
  showHomeSidebar.value = content.getBoundingClientRect().top <= window.innerHeight * 0.25
}

function scheduleSync() {
  if (syncTimer) clearTimeout(syncTimer)
  syncTimer = window.setTimeout(updateHomeSidebar, 300)
}

const items = computed(() => [
  {
    key: 'cart',
    label: '购物车',
    icon: ShoppingCartFull,
    badge: cartCount.value || null,
    action: () => {
      cartStore.markBadgeSeen()
      router.push('/cart')
    }
  },
  {
    key: 'favorites',
    label: '我的收藏',
    icon: Star,
    badge: null,
    action: () => router.push('/favorites')
  },
  {
    key: 'orders',
    label: '我的订单',
    icon: List,
    badge: null,
    action: () => router.push('/order/list')
  },
  {
    key: 'service',
    label: 'AI助手',
    icon: Headset,
    badge: null,
    action: () => router.push('/rag')
  }
])

function scrollToTop() {
  // 减少动态效果偏好下退化为瞬时跳转
  const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  window.scrollTo({ top: 0, behavior: reduceMotion ? 'auto' : 'smooth' })
}

function onScroll() {
  showBackTop.value = window.scrollY > 400
  updateHomeSidebar()
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('resize', onScroll, { passive: true })
  // 移动端底部横条占据高度，通知页面留出底部空间
  document.body.classList.add('has-mobile-dock')
  onScroll()
  // 路由懒加载的 Home 可能晚于本组件挂载，延迟补一次同步
  scheduleSync()
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('resize', onScroll)
  document.body.classList.remove('has-mobile-dock')
  if (syncTimer) clearTimeout(syncTimer)
})

watch(() => route.path, () => {
  showHomeSidebar.value = false
  onScroll()
  scheduleSync()
})
</script>

<style scoped>
.floating-sidebar {
  position: fixed;
  right: 16px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 200;
  transition: opacity 0.24s var(--ease-out), transform 0.24s var(--ease-out), visibility 0.24s;
}
.floating-sidebar.is-hidden {
  opacity: 0;
  transform: translate(18px, -50%);
  visibility: hidden;
  pointer-events: none;
}

.sidebar-inner {
  display: flex;
  flex-direction: column;
  gap: 2px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-lg);
  padding: 6px;
  box-shadow: var(--glass-shadow), inset 0 1px 0 var(--glass-highlight);
  backdrop-filter: var(--backdrop-blur);
  -webkit-backdrop-filter: var(--backdrop-blur);
}

.sidebar-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  cursor: pointer;
  color: var(--text-secondary);
  transition: background var(--transition-color), color var(--transition-color), transform 160ms var(--ease-out);
  min-width: 60px;
}
.sidebar-item:hover {
  background: var(--glass-hover);
  color: var(--brand-primary);
}

.sidebar-label {
  font-size: 11px;
  white-space: nowrap;
  line-height: 1;
}

.sidebar-divider {
  height: 1px;
  background: var(--glass-border);
  margin: 4px 8px;
}

/* ===== 移动端：右侧悬浮栏改为底部横条，不遮挡竖向内容 ===== */
@media (max-width: 768px) {
  .floating-sidebar {
    right: 0;
    left: 0;
    top: auto;
    bottom: 0;
    transform: translateY(0);
  }
  .floating-sidebar.is-hidden {
    transform: translateY(100%);
  }
  .sidebar-inner {
    flex-direction: row;
    justify-content: space-around;
    gap: 0;
    border-radius: var(--radius-lg) var(--radius-lg) 0 0;
    padding: 6px 8px calc(6px + env(safe-area-inset-bottom));
    box-shadow: 0 -8px 32px rgba(0, 0, 0, 0.08), inset 0 1px 0 var(--glass-highlight);
  }
  .sidebar-item {
    flex: 1;
    flex-direction: row;
    gap: 6px;
    padding: 8px 4px;
    min-width: 0;
  }
  .sidebar-label {
    font-size: 12px;
  }
  .sidebar-divider {
    width: 1px;
    height: auto;
    margin: 8px 4px;
  }
}
</style>
