<template>
  <el-container class="admin-layout">
    <el-aside :width="collapsed ? '64px' : '208px'" class="sidebar">
      <div class="brand" @click="router.push('/admin/dashboard')">
        <div class="brand-logo">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="#fff">
              <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/>
            </svg>
          </div>
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
        <el-menu-item index="/admin/chatbi">
          <el-icon><TrendCharts /></el-icon>
          <template #title>智能查数</template>
        </el-menu-item>
        <!-- ===== 管理类（连续排列） ===== -->
        <el-menu-item index="/admin/spu">
          <el-icon><Goods /></el-icon>
          <template #title>商品管理</template>
        </el-menu-item>
        <el-menu-item index="/admin/inventory">
          <el-icon><Box /></el-icon>
          <template #title>出入库日志</template>
        </el-menu-item>
        <el-menu-item index="/admin/category">
          <el-icon><Menu /></el-icon>
          <template #title>分类管理</template>
        </el-menu-item>
        <el-menu-item index="/admin/kb">
          <el-icon><Document /></el-icon>
          <template #title>知识库管理</template>
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
        <el-menu-item index="/admin/points">
          <el-icon><Star /></el-icon>
          <template #title>积分管理</template>
        </el-menu-item>
        <el-menu-item index="/admin/review">
          <el-icon><ChatLineSquare /></el-icon>
          <template #title>评价管理</template>
        </el-menu-item>
        <el-menu-item index="/admin/employee">
          <el-icon><User /></el-icon>
          <template #title>员工管理</template>
        </el-menu-item>
        <el-menu-item index="/admin/user">
          <el-icon><User /></el-icon>
          <template #title>用户管理</template>
        </el-menu-item>
        <el-menu-item index="/admin/home-module">
          <el-icon><Stamp /></el-icon>
          <template #title>首页装修</template>
        </el-menu-item>
        <el-menu-item index="/admin/service">
          <el-icon><Umbrella /></el-icon>
          <template #title>保障服务</template>
        </el-menu-item>
        <!-- ===== 工具类 ===== -->
        <el-menu-item index="/admin/settings">
          <el-icon><Setting /></el-icon>
          <template #title>系统设置</template>
        </el-menu-item>
      </el-menu>
      <!-- ⌘K 搜索入口：贴底、发丝线顶部分隔，折叠时只剩图标 -->
      <div class="side-search" :class="{ collapsed }" @click="paletteVisible = true">
        <el-icon :size="14"><Search /></el-icon>
        <span v-if="!collapsed" class="side-search-text">搜索</span>
        <span v-if="!collapsed" class="kbd">{{ isMac ? '⌘' : 'Ctrl' }} K</span>
      </div>
    </el-aside>

    <el-container>
      <el-header class="topbar">
        <div class="left">
          <el-button text @click="collapsed = !collapsed" style="font-size: 18px;">
            <el-icon :size="18"><Fold v-if="!collapsed" /><Expand v-else /></el-icon>
          </el-button>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentPageTitle">{{ currentPageTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="right">
          <el-button class="theme-btn" @click="toggleTheme" :title="isDark ? '切换亮色' : '切换暗色'">
            <el-icon :size="16"><Sunny v-if="isDark" /><Moon v-else /></el-icon>
          </el-button>
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="28" style="background: var(--brand-primary); color: #fff; margin-right: 8px;">
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

    <AdminCommandPalette
      :items="paletteItems"
      v-model:visible="paletteVisible"
    />
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAdminStore } from '@/stores/admin'
import { useSiteTitle } from '@/composables/useSiteTitle'
import { useTheme } from '@/composables/useTheme'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import AdminCommandPalette from '@/components/AdminCommandPalette.vue'
// 方案 D 全局覆盖层：统一压平各管理子页的卡片/表格（详见 admin-d.scss 头注释）
import '@/assets/admin-d.scss'
import {
  DataBoard, Goods, Menu, User,
  Fold, Expand, ArrowDown, Document, Setting, Tickets,
  Discount, ChatLineSquare, Warning, Sunny, Moon, Stamp, Star, TrendCharts, Box, Umbrella
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const adminStore = useAdminStore()
const { siteTitle } = useSiteTitle()
const { isDark, toggleTheme } = useTheme()

const collapsed = ref(false)
const paletteVisible = ref(false)
const activeMenu = computed(() => route.path)
const isMac = /Mac|iPhone|iPad/.test(navigator.platform || '')

const TITLE_MAP: Record<string, string> = {
  '/admin/dashboard': '控制台',
  '/admin/spu': '商品管理',
  '/admin/inventory': '出入库日志',
  '/admin/category': '分类管理',
  '/admin/kb': '知识库管理',
  '/admin/orders': '订单管理',
  '/admin/aftersale': '售后管理',
  '/admin/coupon': '优惠券管理',
  '/admin/points': '积分管理',
  '/admin/review': '评价管理',
  '/admin/employee': '员工管理',
  '/admin/home-module': '首页装修',
  '/admin/service': '保障服务管理',
  '/admin/settings': '系统设置'
}
const currentPageTitle = computed(() => TITLE_MAP[route.path] || '')

/** 命令面板数据源：与 TITLE_MAP 同源，保证新增菜单只改一处 */
const paletteItems = Object.entries(TITLE_MAP).map(([path, title]) => ({ path, title }))

async function handleCommand(cmd: string) {
  if (cmd === 'profile') {
    ElMessage.info(`账号：${adminStore.username || '-'}`)
  } else if (cmd === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
      adminStore.logout()
      ElMessage.success('已退出')
      router.push('/admin/login')
    } catch (e: any) {}
  }
}

onMounted(async () => {
  if (adminStore.token && !adminStore.name) {
    try { await adminStore.fetchProfile() } catch (e: any) {}
  }
})
</script>

<style scoped>
/* ===== 方案 D：Linear/Vercel 极简工程风 =====
 * 仅在 .admin-layout 子树内覆写设计令牌（CSS 变量沿 DOM 继承，子组件全部生效），
 * 不动 main.scss 全局值 —— C 端商城不受影响。
 * 圆角整体收小、投影全部拍平（弹窗 teleport 到 body 之外，不受影响）。 */
.admin-layout {
  height: 100vh;
  --radius-sm: 6px;
  --radius-md: 8px;
  --radius-lg: 10px;
  --radius-xl: 12px;
  --shadow-sm: none;
  --shadow-md: none;
}
.sidebar {
  background: var(--bg-card);
  border-right: 1px solid var(--border-base);
  display: flex;
  flex-direction: column;
  /* 折叠是结构性切换：宽度瞬切，避免 200ms 内反复重排右侧表格/图表 */
  overflow: hidden;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  height: 56px;
  border-bottom: 1px solid var(--border-base);
  cursor: pointer;
  flex-shrink: 0;
}
.brand-logo {
  width: 28px; height: 28px;
  background: var(--brand-primary);
  color: #fff;
  font-weight: 700;
  border-radius: var(--radius-sm);
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.brand-text { color: var(--text-primary); font-size: 14px; font-weight: 600; white-space: nowrap; }
.side-menu {
  border-right: none;
  flex: 1;
  overflow-y: auto;
  background: transparent;
}
:deep(.el-menu) {
  border-right: none;
  background: transparent;
  padding: 10px;
}
/* 折叠态下菜单只剩 64px：取消左右内边距，
 * 否则 EP 的基础左内边距会把图标推到右侧、不再居中 */
:deep(.el-menu--collapse) {
  padding: 10px 0;
}
:deep(.el-menu-item) {
  color: var(--text-secondary);
  border-radius: var(--radius-sm);
  margin: 2px 0;
  height: 40px;
  line-height: 40px;
  font-size: 13px;
}
:deep(.el-menu-item .el-icon) { font-size: 16px; }
:deep(.el-menu-item:hover) {
  background: var(--bg-hover);
  color: var(--text-primary);
}
/* 激活态用中性灰高亮而非品牌色铺底：Linear 风格的"位置感"靠灰阶，
 * 品牌色只留给操作按钮和数据 */
:deep(.el-menu-item.is-active) {
  background: var(--bg-hover);
  color: var(--text-primary);
  font-weight: 500;
}

/* 侧栏底部 ⌘K 搜索入口 */
.side-search {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 8px;
  padding: 9px 12px;
  border: 1px solid var(--border-base);
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  font-size: 13px;
  cursor: pointer;
  transition: background var(--transition-color), color var(--transition-color);
}
.side-search:hover { background: var(--bg-hover); color: var(--text-primary); }
.side-search.collapsed { justify-content: center; padding: 9px 0; }
.side-search-text { flex: 1; }
.kbd {
  font-size: 11px;
  line-height: 1;
  padding: 3px 5px;
  border: 1px solid var(--border-base);
  border-radius: 4px;
  color: var(--text-muted);
  font-family: var(--font-mono, monospace);
}

.topbar {
  background: var(--bg-card);
  border-bottom: 1px solid var(--border-base);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  height: 56px;
}
.left { display: flex; align-items: center; gap: 14px; }
.user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 0 6px;
  color: var(--text-primary);
}
.username { font-size: 13px; color: var(--text-primary); font-weight: 500; }

.theme-btn {
  width: 30px; height: 30px; padding: 0;
  border: 1px solid var(--border-base);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-secondary);
  box-shadow: none;
}
.theme-btn:hover {
  border-color: var(--border-secondary, var(--border-base));
  color: var(--text-primary);
  background: var(--bg-hover);
}

.main-content {
  background: var(--bg-base);
  /* 纵向留白比横向多给：宽表格（订单/商品）已经没有余量，横向只在卡片内留白 */
  padding: 26px 24px 32px;
  overflow: auto;
}

.fade-enter-active, .fade-leave-active {
  transition: opacity 0.15s ease;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}
</style>
