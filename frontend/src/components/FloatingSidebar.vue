<template>
  <div class="floating-sidebar">
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

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  ShoppingCartFull,
  Star,
  List,
  Headset,
  Top
} from '@element-plus/icons-vue'
import { useCartStore } from '@/stores/cart'

const router = useRouter()
const cartStore = useCartStore()

const cartCount = computed(() => cartStore.totalCount || 0)
const showBackTop = ref(false)

const items = computed(() => [
  {
    key: 'cart',
    label: '购物车',
    icon: ShoppingCartFull,
    badge: cartCount.value || null,
    action: () => router.push('/cart')
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
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function onScroll() {
  showBackTop.value = window.scrollY > 400
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
})
</script>

<style scoped>
.floating-sidebar {
  position: fixed;
  right: 16px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 200;
}

.sidebar-inner {
  display: flex;
  flex-direction: column;
  gap: 2px;
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-lg);
  padding: 6px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  backdrop-filter: blur(10px);
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
  transition: all 0.2s ease;
  min-width: 60px;
}
.sidebar-item:hover {
  background: var(--brand-primary-soft);
  color: var(--brand-primary);
}

.sidebar-label {
  font-size: 11px;
  white-space: nowrap;
  line-height: 1;
}

.sidebar-divider {
  height: 1px;
  background: var(--border-subtle);
  margin: 4px 8px;
}
</style>
