<template>
  <header class="top-bar" :class="{ hidden: isHidden }">
    <div class="container">
      <div
        class="logo"
        :class="{ clickable: logoClickable || !!$attrs.onLogoClick }"
        @click="handleLogoClick"
      >
        星耀商城
      </div>
      <nav class="nav-links" ref="navRef">
        <router-link
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="nav-item"
          active-class="active"
        >
          <component :is="item.icon" class="nav-icon" />
          <span class="nav-label" v-html="item.label"></span>
        </router-link>
        <span class="nav-indicator" :style="indicatorStyle"></span>
      </nav>
      <div class="search-box">
        <el-autocomplete
          v-model="searchKeyword"
          :fetch-suggestions="querySearch"
          :trigger-on-focus="true"
          placeholder="搜索商品..."
          clearable
          class="nav-search"
          popper-class="nav-search-popper"
          @keyup.enter="doSearch"
          @select="onSelectSuggestion"
          :debounce="300"
        >
          <template #default="{ item }">
            <div class="suggestion-item" :class="{ 'is-history': item.isHistory }">
              <el-icon v-if="item.isHistory" class="history-clock"><Clock /></el-icon>
              <el-icon v-else class="sug-icon"><Search /></el-icon>
              <span class="suggestion-text">{{ item.value }}</span>
              <span
                v-if="item.isHistory"
                class="history-del"
                title="删除"
                @click.stop="removeHistory(item.value)"
              ><Close /></span>
            </div>
          </template>
          <template #prefix>
            <el-icon :size="15"><Search /></el-icon>
          </template>
          <template #suffix>
            <el-button
              class="search-btn"
              :icon="Search"
              circle
              size="small"
              @click="doSearch"
              :disabled="!searchKeyword.trim()"
            />
          </template>
        </el-autocomplete>
      </div>
      <div class="user-area">
        <ThemeToggle />
        <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="msg-bell">
          <el-button circle class="bell-btn" @click="goMessages">
            <el-icon :size="18"><Bell /></el-icon>
          </el-button>
        </el-badge>
        <span class="nickname">{{ userStore.nickname || '用户' }}</span>
        <el-dropdown @command="handleCommand">
          <el-button class="user-menu-btn">我的 ▾</el-button>
          <template #dropdown>
            <el-dropdown-menu class="user-menu-dropdown">
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item command="messages">我的消息</el-dropdown-item>
              <el-dropdown-item command="aftersale">我的售后</el-dropdown-item>
              <el-dropdown-item command="wallet">我的钱包</el-dropdown-item>
              <el-dropdown-item command="address">收货地址</el-dropdown-item>
              <el-dropdown-item command="points">积分商城</el-dropdown-item>
              <el-dropdown-item command="favorites">我的收藏</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed, getCurrentInstance, ref, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  House,
  Ticket,
  ChatDotRound,
  Bell,
  List as ListIcon,
  Search,
  Clock,
  Close
} from '@element-plus/icons-vue'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import { getUnreadCount, suggestSpu } from '@/api/mall'
import { useUnreadBadge } from '@/composables/useUnreadBadge'
import { useSearchHistory } from '@/composables/useSearchHistory'
import ThemeToggle from '@/components/ThemeToggle.vue'

const props = defineProps({
  logoClickable: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['logo-click'])
const route = useRoute()
const router = useRouter()
const instance = getCurrentInstance()
const hasLogoClickListener = computed(() => !!instance?.vnode.props?.onLogoClick)

const navRef = ref<any>(null)
const indicatorStyle = ref<any>({
  transform: 'translateX(0px)',
  width: '0px',
  opacity: 0
})

// 滚动方向感知：向下滑动收起导航栏，向上滑动展开
const isHidden = ref(false)
let lastScrollY = 0
let ticking = false
const SCROLL_THRESHOLD = 10

/**
 * 滚动事件处理：根据滚动方向控制导航栏的显示/隐藏
 * 向下滚动超过阈值时收起导航栏，向上滚动时展开
 */
function handleScroll() {
  if (!ticking) {
    window.requestAnimationFrame(() => {
      const currentScrollY = window.scrollY || window.pageYOffset
      const delta = currentScrollY - lastScrollY

      if (Math.abs(delta) > SCROLL_THRESHOLD) {
        if (delta > 0 && currentScrollY > 0) {
          // 向下滚动且不在顶部：收起
          isHidden.value = true
        } else if (delta < 0) {
          // 向上滚动：展开
          isHidden.value = false
        }
      }

      // 回到顶部时强制展开
      if (currentScrollY <= 0) {
        isHidden.value = false
      }

      lastScrollY = currentScrollY
      ticking = false
    })
    ticking = true
  }
}

/**
 * 计算当前激活的导航项索引，根据当前路由路径匹配
 * @returns {number} 激活的导航项索引，未匹配时返回 -1
 */
const activeIndex = computed(() => {
  const items = navItems.value
  const path = route.path
  if (path === '/') return 0
  return items.findIndex((item: any) =>
    item.to !== '/' && (path === item.to || path.startsWith(item.to + '/'))
  )
})

/**
 * 更新导航栏激活指示器的位置和样式
 * 根据当前激活的导航项计算指示器的偏移量和宽度
 */
function updateIndicator() {
  nextTick(() => {
    if (!navRef.value) return
    const items = navRef.value.querySelectorAll('.nav-item')
    const activeEl = items[activeIndex.value]
    if (!activeEl) {
      indicatorStyle.value.opacity = 0
      return
    }
    const navRect = navRef.value.getBoundingClientRect()
    const itemRect = activeEl.getBoundingClientRect()
    const indicatorWidth = 20
    const left = itemRect.left - navRect.left + (itemRect.width - indicatorWidth) / 2
    indicatorStyle.value = {
      transform: `translateX(${left}px)`,
      width: `${indicatorWidth}px`,
      opacity: 1
    }
  })
}

watch(() => route.path, updateIndicator)
onMounted(() => {
  updateIndicator()
  lastScrollY = window.scrollY || window.pageYOffset
  window.addEventListener('resize', updateIndicator)
  window.addEventListener('scroll', handleScroll, { passive: true })
})
onUnmounted(() => {
  window.removeEventListener('resize', updateIndicator)
  window.removeEventListener('scroll', handleScroll)
})

const cartStore = useCartStore()
const userStore = useUserStore()
const { count: unreadCount } = useUnreadBadge()
const searchKeyword = ref('')

// ===== 搜索历史（与商城搜索页共享同一份历史数据，按用户隔离） =====
const { searchHistory, addToHistory, removeHistory } = useSearchHistory()

/**
 * 搜索建议查询回调函数，供 el-autocomplete 使用
 * 无输入时显示搜索历史，有输入时调用远程搜索建议接口
 * @param {string} queryString - 用户输入的搜索关键词
 * @param {Function} cb - 回调函数，接收建议项数组
 */
function querySearch(queryString: string, cb: any) {
  if (!queryString || queryString.trim().length < 1) {
    const items = searchHistory.value.map((h: any) => ({ value: h, isHistory: true }))
    cb(items)
    return
  }
  fetchSuggestions(queryString, cb)
}

/**
 * 选中搜索建议项时的回调
 * 填充搜索关键词并立即执行搜索
 * @param {Object} item - 选中的建议项，包含 value 属性
 */
function onSelectSuggestion(item: any) {
  searchKeyword.value = item.value
  doSearch()
}

/**
 * 调用远程接口获取搜索建议（SPU 补全）
 * @param {string} queryString - 搜索关键词
 * @param {Function} cb - 回调函数，接收建议项数组
 */
async function fetchSuggestions(queryString: string, cb: any) {
  if (!queryString || queryString.trim().length < 1) {
    cb([])
    return
  }
  try {
    const res = await suggestSpu(queryString.trim())
    const data: any = res || {}
    const items = (data.completions || []).map((s: any) => ({ value: s }))
    cb(items)
  } catch (e: any) {
    cb([])
  }
}

/**
 * 执行搜索：将关键词加入历史记录，跳转到搜索结果页
 * 当前已在搜索页时使用 replace 替换，否则在新标签页打开
 */
function doSearch() {
  const kw = searchKeyword.value.trim()
  if (!kw) return
  addToHistory(kw)
  const query: Record<string, any> = { keyword: kw }
  if (route.path === '/shop/search') {
    router.replace({ query })
  } else {
    // 从其他页面搜索 → 新标签页打开搜索结果
    const resolved = router.resolve({ path: '/shop/search', query })
    window.open(resolved.href, '_blank')
  }
}

/**
 * 获取未读消息数量，仅在用户已登录时调用
 */
async function fetchUnreadCount() {
  if (!userStore.token) return
  try {
    const res: any = await getUnreadCount()
    const d = res?.data || res || {}
    unreadCount.value = d.count || 0
  } catch (e: any) { /* ignore */ }
}

/**
 * 跳转到消息页面
 */
function goMessages() {
  router.push('/me/messages')
}

// 每 30 秒拉一次未读数
let unreadTimer: number | null = null
onMounted(() => {
  fetchUnreadCount()
  unreadTimer = setInterval(fetchUnreadCount, 30000)
})
onUnmounted(() => {
  if (unreadTimer) clearInterval(unreadTimer)
})

/**
 * 处理用户下拉菜单命令
 * 支持跳转到个人中心、消息、订单、售后、钱包、地址、收藏、优惠券、积分商城、AI 助手等页面，以及退出登录
 * @param {string} cmd - 菜单命令标识
 */
function handleCommand(cmd: string) {
  if (cmd === 'profile') window.open('/me', '_blank')
  else if (cmd === 'messages') window.open('/me/messages', '_blank')
  else if (cmd === 'aftersale') window.open('/aftersale/list', '_blank')
  else if (cmd === 'wallet') window.open('/wallet', '_blank')
  else if (cmd === 'coupons') router.push('/coupons')
  else if (cmd === 'points') window.open('/points', '_blank')
  else if (cmd === 'address') router.push('/address')
  else if (cmd === 'favorites') router.push('/favorites')
  else if (cmd === 'cart') router.push('/cart')
  else if (cmd === 'rag') router.push('/rag')
  else if (cmd === 'logout') {
    userStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}

/**
 * 导航菜单项配置
 * @returns {Array<{to: string, label: string, icon: Component}>} 导航项数组
 */
const navItems = computed(() => [
  { to: '/', label: '首页', icon: House },
  { to: '/coupons', label: '优惠券', icon: Ticket },
  { to: '/order/list', label: '我的订单', icon: ListIcon },
  { to: '/rag', label: 'AI助手', icon: ChatDotRound }
])

/**
 * 处理 Logo 点击事件
 * 若有自定义点击监听器则触发事件，否则跳转到首页
 */
function handleLogoClick() {
  if (!props.logoClickable) return
  if (hasLogoClickListener.value) {
    emit('logo-click')
  } else {
    router.push('/')
  }
}
</script>

<style scoped>
.top-bar {
  background: var(--glass-bg);
  backdrop-filter: var(--backdrop-blur);
  -webkit-backdrop-filter: var(--backdrop-blur);
  border-bottom: 1px solid var(--glass-border);
  position: sticky;
  top: 0;
  z-index: 100;
  transform: translateY(0);
  transition: transform 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}
.top-bar.hidden {
  transform: translateY(-100%);
}
.top-bar .container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  align-items: center;
  height: 64px;
}
.logo {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
  margin-right: 48px;
  letter-spacing: -0.02em;
  user-select: none;
}
.logo.clickable {
  cursor: pointer;
}
.nav-links {
  position: relative;
  display: flex;
  gap: 32px;
  flex: 1;
}
.nav-indicator {
  position: absolute;
  bottom: 0;
  left: 0;
  height: 3px;
  border-radius: 2px;
  background: var(--brand-primary);
  pointer-events: none;
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1),
              width 0.3s cubic-bezier(0.4, 0, 0.2, 1),
              opacity 0.2s ease;
}
.nav-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--text-muted);
  font-size: 15px;
  font-weight: 500;
  text-decoration: none;
  transition: color 0.25s ease;
  padding: 6px 0;
}
.nav-item:hover {
  color: var(--text-primary);
}
.nav-item.active {
  color: var(--brand-primary);
}
.nav-icon {
  width: 16px;
  height: 16px;
  transition: color 0.25s ease, transform 0.25s ease;
}
.nav-item:hover .nav-icon {
  transform: translateY(-1px);
}
.nav-item.active .nav-icon {
  transform: scale(1.1);
}
.user-area {
  display: flex;
  align-items: center;
  gap: 12px;
}
.msg-bell { margin-right: 0; }
.bell-btn {
  border: none;
  background: transparent;
  color: var(--text-secondary);
  transition: color var(--transition-base);
}
.bell-btn:hover { color: var(--brand-primary); }

.search-box { margin: 0 12px; position: relative; }
.nav-search { width: 260px; }
.search-btn {
  border: none;
  background: transparent;
  color: var(--text-muted);
  transition: color 0.2s;
  padding: 0;
}
.search-btn:hover:not(:disabled) {
  color: var(--brand-primary);
}
.search-btn:disabled {
  color: var(--text-muted);
  opacity: 0.4;
  cursor: not-allowed;
}
</style>

<style lang="scss">
.nav-search-popper {
  margin-top: 6px !important;

  .el-autocomplete-suggestion__wrap {
    padding: 4px 0;
    max-height: 320px;
  }

  .suggestion-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 16px;
    cursor: pointer;
    font-size: 13px;
    color: var(--text-primary);

    &:hover {
      background: var(--bg-hover);
    }

    &.is-history {
      .suggestion-text {
        color: var(--text-secondary);
      }
    }
  }

  .history-clock,
  .sug-icon {
    flex-shrink: 0;
    width: 14px;
    height: 14px;
    color: var(--text-muted);
  }

  .suggestion-text {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .history-del {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 18px;
    height: 18px;
    border-radius: 50%;
    font-size: 12px;
    color: var(--text-muted);
    cursor: pointer;
    transition: all 0.15s;
    opacity: 0;

    &:hover {
      background: var(--bg-hover);
      color: var(--status-danger, #F56C6C);
      opacity: 1;
    }
  }

  .suggestion-item:hover .history-del {
    opacity: 1;
  }
}
</style>
