<template>
  <el-container class="admin-layout">
    <el-aside :width="collapsed ? '64px' : '220px'" class="sidebar">
      <div class="brand" @click="router.push('/admin/dashboard')">
        <div class="brand-logo">S</div>
        <span v-if="!collapsed" class="brand-text">{{ siteTitle }}</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        router
        class="side-menu"
      >
        <el-menu-item index="/admin/dashboard">
          <el-icon><DataBoard /></el-icon>
          <template #title>控制台</template>
        </el-menu-item>
        <!-- ===== 管理类（连续排列） ===== -->
        <el-menu-item index="/admin/spu">
          <el-icon><Goods /></el-icon>
          <template #title>商品管理</template>
        </el-menu-item>
        <el-menu-item index="/admin/category">
          <el-icon><Menu /></el-icon>
          <template #title>分类管理</template>
        </el-menu-item>
        <el-menu-item index="/admin/kb">
          <el-icon><Document /></el-icon>
          <template #title>知识库管理</template>
        </el-menu-item>
        <el-menu-item index="/admin/inventory">
          <el-icon><Box /></el-icon>
          <template #title>库存管理</template>
        </el-menu-item>
        <el-menu-item index="/admin/orders">
          <el-icon><Tickets /></el-icon>
          <template #title>订单管理</template>
        </el-menu-item>
        <el-menu-item index="/admin/aftersale">
          <el-icon><Warning /></el-icon>
          <template #title>售后管理</template>
        </el-menu-item>
        <el-menu-item index="/admin/coupon">
          <el-icon><Discount /></el-icon>
          <template #title>优惠券管理</template>
        </el-menu-item>
        <el-menu-item index="/admin/points-rules">
          <el-icon><Star /></el-icon>
          <template #title>积分规则管理</template>
        </el-menu-item>
        <el-menu-item index="/admin/points-products">
          <el-icon><Goods /></el-icon>
          <template #title>积分商城管理</template>
        </el-menu-item>
        <el-menu-item index="/admin/review">
          <el-icon><ChatLineSquare /></el-icon>
          <template #title>评价管理</template>
        </el-menu-item>
        <el-menu-item index="/admin/employee">
          <el-icon><User /></el-icon>
          <template #title>员工管理</template>
        </el-menu-item>
        <el-menu-item index="/admin/banner">
          <el-icon><Picture /></el-icon>
          <template #title>轮播图管理</template>
        </el-menu-item>
        <el-menu-item index="/admin/home-module">
          <el-icon><Stamp /></el-icon>
          <template #title>首页装修</template>
        </el-menu-item>
        <!-- ===== 工具类 ===== -->
        <el-menu-item index="/admin/recycle">
          <el-icon><Delete /></el-icon>
          <template #title>商品回收站</template>
        </el-menu-item>
        <el-menu-item index="/admin/rag-sync">
          <el-icon><Refresh /></el-icon>
          <template #title>RAG 同步队列</template>
        </el-menu-item>
        <el-menu-item index="/admin/logs">
          <el-icon><Monitor /></el-icon>
          <template #title>操作日志</template>
        </el-menu-item>
        <el-menu-item index="/admin/settings">
          <el-icon><Setting /></el-icon>
          <template #title>系统设置</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="topbar">
        <div class="left">
          <el-button text @click="collapsed = !collapsed" style="font-size: 18px;">
            <el-icon :size="20"><Fold v-if="!collapsed" /><Expand v-else /></el-icon>
          </el-button>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentPageTitle">{{ currentPageTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="right">
          <el-button class="theme-btn" @click="toggleTheme" :title="isDark ? '切换亮色' : '切换暗色'">
            <el-icon :size="18"><Sunny v-if="isDark" /><Moon v-else /></el-icon>
          </el-button>
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="32" style="background: var(--brand-primary); color: #fff; margin-right: 8px;">
                {{ (adminStore.username || 'A').charAt(0).toUpperCase() }}
              </el-avatar>
              <span class="username">{{ adminStore.name || adminStore.username || '管理员' }}</span>
              <el-icon style="margin-left: 4px;"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">账号信息</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAdminStore } from '@/stores/admin'
import { useSiteTitle } from '@/composables/useSiteTitle'
import { useTheme } from '@/composables/useTheme'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  DataBoard, Goods, Menu, Refresh, User,
  Fold, Expand, ArrowDown, Document, Monitor, Setting, Tickets,
  Discount, ChatLineSquare, Box, Delete, Picture, Warning, Sunny, Moon, Stamp, Star
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const adminStore = useAdminStore()
const { siteTitle } = useSiteTitle()
const { isDark, toggleTheme } = useTheme()

const collapsed = ref(false)
const activeMenu = computed(() => route.path)

const TITLE_MAP = {
  '/admin/dashboard': '控制台',
  '/admin/spu': '商品管理',
  '/admin/category': '分类管理',
  '/admin/kb': '知识库管理',
  '/admin/inventory': '库存管理',
  '/admin/orders': '订单管理',
  '/admin/aftersale': '售后管理',
  '/admin/coupon': '优惠券管理',
  '/admin/points-rules': '积分规则管理',
  '/admin/points-products': '积分商城���理',
  '/admin/review': '评价管理',
  '/admin/employee': '员工管理',
  '/admin/banner': '轮播图管理',
  '/admin/home-module': '首页装修',
  '/admin/recycle': '商品回收站',
  '/admin/rag-sync': 'RAG 同步队列',
  '/admin/logs': '操作日志',
  '/admin/settings': '系统设置'
}
const currentPageTitle = computed(() => TITLE_MAP[route.path] || '')

async function handleCommand(cmd) {
  if (cmd === 'profile') {
    ElMessage.info(`账号：${adminStore.username || '-'}`)
  } else if (cmd === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
      adminStore.logout()
      ElMessage.success('已退出')
      router.push('/admin/login')
    } catch (e) {}
  }
}

onMounted(async () => {
  if (adminStore.token && !adminStore.name) {
    try { await adminStore.fetchProfile() } catch (e) {}
  }
})
</script>

<style scoped>
.admin-layout { height: 100vh; }
.sidebar {
  background: var(--bg-sidebar);
  border-right: 1px solid var(--border-base);
  transition: width 0.2s;
  overflow: hidden;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 18px;
  height: 60px;
  border-bottom: 1px solid var(--border-base);
  cursor: pointer;
}
.brand-logo {
  width: 36px; height: 36px;
  background: var(--brand-primary);
  color: #fff;
  font-weight: 700;
  border-radius: var(--radius-sm);
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.brand-text { color: var(--text-primary); font-size: 16px; font-weight: 600; white-space: nowrap; }
.side-menu {
  border-right: none;
  height: calc(100vh - 60px);
  background: transparent;
}
:deep(.el-menu) {
  border-right: none;
  background: transparent;
}
:deep(.el-menu-item) {
  color: var(--text-secondary);
  border-radius: var(--radius-md);
  margin: 4px 10px;
  height: 44px;
  line-height: 44px;
}
:deep(.el-menu-item:hover) {
  background: var(--bg-hover);
  color: var(--text-primary);
}
:deep(.el-menu-item.is-active) {
  background: var(--brand-primary-soft);
  color: var(--brand-primary);
}

.topbar {
  background: var(--glass-bg);
  backdrop-filter: var(--backdrop-blur);
  -webkit-backdrop-filter: var(--backdrop-blur);
  border-bottom: 1px solid var(--glass-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 60px;
}
.left { display: flex; align-items: center; gap: 18px; }
.user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 0 6px;
  color: var(--text-primary);
}
.username { font-size: 14px; color: var(--text-primary); font-weight: 500; }

.theme-btn {
  width: 34px; height: 34px; padding: 0;
  border: 1px solid var(--border-base);
  border-radius: 8px;
  background: var(--bg-card);
  color: var(--text-secondary);
  box-shadow: var(--shadow-sm);
}
.theme-btn:hover {
  border-color: var(--brand-primary);
  color: var(--brand-primary);
}

.main-content {
  background: var(--bg-base);
  padding: 28px;
  overflow: auto;
}

.fade-enter-active, .fade-leave-active {
  transition: opacity 0.18s ease;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}
</style>
