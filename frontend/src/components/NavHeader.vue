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
          <span class="nav-label">{{ item.label }}</span>
        </router-link>
        <span class="nav-indicator" :style="indicatorStyle"></span>
      </nav>
      <div class="search-box" ref="searchBoxRef">
        <el-autocomplete
          v-model="searchKeyword"
          :fetch-suggestions="querySearch"
          :trigger-on-focus="true"
          placeholder="搜索商品..."
          clearable
          class="nav-search"
          popper-class="nav-search-popper is-panel-hidden"
          @keyup.enter="doSearch"
          @focus="navPanelOpen = true"
          @blur="navPanelOpen = false"
          :debounce="300"
        >
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
        <!-- 搜索面板标签云：空输入展示搜索历史，有输入展示搜索建议（与商城搜索页同一套样式）
             Teleport 到 body：.top-bar 自带 backdrop-filter + transform，会成为 backdrop root，
             面板留在里面就取样不到页面内容、磨砂失效，必须挪出顶栏才能真的糊到背后内容 -->
        <Teleport to="body">
          <div class="nav-history-panel" v-if="navPanelVisible" :style="panelStyle">
            <div class="nav-history-head">
              <span class="nav-history-title">{{ isSuggestMode ? '搜索建议' : '搜索历史' }}</span>
              <button
                v-if="!isSuggestMode"
                type="button"
                class="nav-history-clear"
                @mousedown.stop.prevent="clearHistoryAll"
              >清空搜索历史</button>
            </div>
            <div class="nav-history-tags">
              <button
                v-for="t in panelTags"
                :key="t"
                type="button"
                class="nav-history-tag"
                :title="t"
                @mousedown.prevent="pickTag(t)"
              >{{ t }}</button>
            </div>
          </div>
        </Teleport>
      </div>
      <div class="user-area">
        <ThemeToggle />
        <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="msg-bell">
          <el-button circle class="bell-btn" @click="goMessages">
            <el-icon :size="18"><Bell /></el-icon>
          </el-button>
        </el-badge>
        <span class="nickname">{{ userStore.nickname || '用户' }}</span>
        <el-dropdown popper-class="user-menu-popper" @command="handleCommand">
          <el-button class="user-menu-btn">我的 ▾</el-button>
          <template #dropdown>
            <el-dropdown-menu class="user-menu-dropdown">
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item command="browse">浏览记录</el-dropdown-item>
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
  Search
} from '@element-plus/icons-vue'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import { getUnreadCount, suggestSpu } from '@/api/mall'
import { useUnreadBadge } from '@/composables/useUnreadBadge'
import { useSearchHistory } from '@/composables/useSearchHistory'
import { track } from '@/utils/tracker'
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

// ===== 搜索历史 / 搜索建议（历史与商城搜索页共享同一份数据，按用户隔离） =====
const { searchHistory, addToHistory, clearHistory } = useSearchHistory()

// 搜索框是否聚焦
const navPanelOpen = ref(false)
// 远程搜索建议（输入非空时展示），由 querySearch 填充
const suggestions = ref<string[]>([])
// 是否处于「搜索建议」态（输入框有内容）
const isSuggestMode = computed(() => !!searchKeyword.value.trim())
// 当前面板要展示的标签（空输入→历史，有输入→建议）
const panelTags = computed(() => (isSuggestMode.value ? suggestions.value : searchHistory.value))
// 有标签可展示时才浮出面板
const navPanelVisible = computed(() => navPanelOpen.value && panelTags.value.length > 0)

// 面板被 Teleport 到 body（脱离顶栏的 backdrop root），改为 fixed 定位，
// 需要按搜索框实际位置实时校准
const SEARCH_PANEL_WIDTH = 440
const searchBoxRef = ref<HTMLElement | null>(null)
const panelStyle = ref<Record<string, string>>({})

/**
 * 按搜索框的位置计算面板的 fixed 坐标（右对齐搜索框、向下 10px，并限制在视口内）
 */
function syncPanelPosition() {
  const el = searchBoxRef.value
  if (!el || !navPanelVisible.value) return
  const rect = el.getBoundingClientRect()
  const maxLeft = window.innerWidth - SEARCH_PANEL_WIDTH - 8
  const left = Math.max(8, Math.min(rect.right - SEARCH_PANEL_WIDTH, maxLeft))
  const next = {
    top: `${Math.round(rect.bottom + 10)}px`,
    left: `${Math.round(left)}px`
  }
  // 位置没变就不写回，避免滚动过程中反复触发渲染
  if (next.top === panelStyle.value.top && next.left === panelStyle.value.left) return
  panelStyle.value = next
}

// 面板出现时先定位一次；滚动/缩放时跟随搜索框（顶栏会随滚动收起，位置会变）
watch(navPanelVisible, (visible) => {
  if (visible) nextTick(syncPanelPosition)
})
onMounted(() => {
  window.addEventListener('scroll', syncPanelPosition, { passive: true })
  window.addEventListener('resize', syncPanelPosition)
})
onUnmounted(() => {
  window.removeEventListener('scroll', syncPanelPosition)
  window.removeEventListener('resize', syncPanelPosition)
})

/**
 * 搜索建议查询回调函数，供 el-autocomplete 使用
 * 原生下拉恒被 is-panel-hidden 隐藏，结果改存 suggestions 由自定义标签云面板渲染
 * @param {string} queryString - 用户输入的搜索关键词
 * @param {Function} cb - 回调函数，接收建议项数组（这里恒传空，避免原生下拉渲染）
 */
function querySearch(queryString: string, cb: any) {
  if (!queryString || queryString.trim().length < 1) {
    suggestions.value = []
    cb([])
    return
  }
  fetchSuggestions(queryString, cb)
}

/**
 * 选中一个标签：填入输入框并立即搜索
 * @param {string} tag - 历史关键词或搜索建议
 */
function pickTag(tag: string) {
  searchKeyword.value = tag
  navPanelOpen.value = false
  doSearch()
}

/**
 * 清空全部搜索历史（确认弹窗在 composable 内）
 */
async function clearHistoryAll() {
  const ok = await clearHistory()
  if (ok) navPanelOpen.value = false
}

/**
 * 调用远程接口获取搜索建议（SPU 补全）
 * @param {string} queryString - 搜索关键词
 * @param {Function} cb - 回调函数，接收建议项数组（恒传空，渲染由自定义面板负责）
 */
async function fetchSuggestions(queryString: string, cb: any) {
  if (!queryString || queryString.trim().length < 1) {
    suggestions.value = []
    cb([])
    return
  }
  try {
    const res = await suggestSpu(queryString.trim())
    const data: any = res || {}
    suggestions.value = (data.completions || []) as string[]
    cb([])
  } catch (e: any) {
    suggestions.value = []
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
  track('search', { keyword: kw, scene: 'header' })
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
let unreadTimer: ReturnType<typeof setInterval> | null = null
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
  else if (cmd === 'browse') window.open('/me/browse', '_blank')
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

/* 顶栏搜索框 —— 磨砂玻璃质感（与搜索面板、全站玻璃规范同一套材质）。
 * 注意：class="nav-search" 会被 el-autocomplete 透传到内部 el-input 上，
 * 那层节点没有本组件的 scope 属性，scoped 选择器勾不到，故用 .search-box 作锚点。 */
.search-box :deep(.el-input__wrapper) {
  background: var(--glass-bg, rgba(255, 255, 255, 0.72)) !important;
  backdrop-filter: var(--backdrop-blur, blur(20px)) saturate(160%);
  -webkit-backdrop-filter: var(--backdrop-blur, blur(20px)) saturate(160%);
  border-radius: 999px;
  padding: 0 12px;
  box-shadow:
    0 0 0 1px var(--glass-border, rgba(0, 0, 0, 0.06)) inset,
    inset 0 1px 0 var(--glass-highlight, rgba(255, 255, 255, 0.6)),
    var(--glass-shadow, 0 8px 32px rgba(0, 0, 0, 0.06)) !important;
  transition: box-shadow 0.2s;
}
.search-box :deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 1px var(--brand-primary, #0071e3) inset,
    inset 0 1px 0 var(--glass-highlight, rgba(255, 255, 255, 0.6)),
    0 0 0 3px var(--brand-primary-soft, rgba(0, 113, 227, 0.1)) !important;
}
.search-box :deep(.el-input__inner) {
  background: transparent;
  color: var(--text-primary);
}
.search-box :deep(.el-input__inner::placeholder) {
  color: var(--text-muted);
}

/* 搜索历史 / 搜索建议 标签云面板（Teleport 到 body，fixed 定位，与商城搜索页同一套样式） */
.nav-history-panel {
  --nav-history-tag-bg: rgba(0, 0, 0, 0.08);
  --nav-history-tag-bg-hover: var(--brand-primary-soft, rgba(0, 113, 227, 0.1));

  position: fixed;
  width: 440px;
  max-width: calc(100vw - 16px);
  background: var(--glass-popover-bg, rgba(255, 255, 255, 0.6));
  backdrop-filter: var(--glass-popover-blur, blur(28px) saturate(180%));
  -webkit-backdrop-filter: var(--glass-popover-blur, blur(28px) saturate(180%));
  border: 1px solid var(--glass-border, rgba(0, 0, 0, 0.08));
  border-radius: 14px;
  box-shadow:
    var(--glass-shadow, 0 8px 32px rgba(0, 0, 0, 0.06)),
    inset 0 1px 0 var(--glass-highlight, rgba(255, 255, 255, 0.6));
  padding: 14px 16px 16px;
  overflow: hidden;
  z-index: 99;
  animation: nav-history-in 0.16s ease-out;
}
@keyframes nav-history-in {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}
html.theme-dark .nav-history-panel {
  --nav-history-tag-bg: rgba(255, 255, 255, 0.12);
  --nav-history-tag-bg-hover: rgba(255, 255, 255, 0.2);
}
.nav-history-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.nav-history-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}
.nav-history-clear {
  border: none;
  background: transparent;
  padding: 0;
  font-size: 13px;
  color: var(--text-muted);
  cursor: pointer;
  transition: color 0.15s;
}
.nav-history-clear:hover { color: var(--brand-primary); }
.nav-history-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 12px;
  max-height: 268px;
  overflow-y: auto;
  overscroll-behavior: contain;
}
.nav-history-tag {
  max-width: 100%;
  padding: 9px 18px;
  border: none;
  border-radius: 10px;
  background: var(--nav-history-tag-bg);
  font-size: 14px;
  color: var(--text-primary);
  line-height: 1.25;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: background 0.15s, color 0.15s, transform 0.15s;
}
.nav-history-tag:hover {
  background: var(--nav-history-tag-bg-hover);
  color: var(--brand-primary);
}
.nav-history-tag:active { transform: scale(0.97); }
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

  /* 输入框为空时由历史标签云面板接管，隐藏原生建议下拉 */
  &.is-panel-hidden {
    display: none !important;
  }

  /* ===== 磨砂玻璃质感：弹层根节点即视觉容器 ===== */
  &.el-popper.is-light {
    padding: 0;
    background: var(--glass-bg, rgba(255, 255, 255, 0.72));
    backdrop-filter: var(--backdrop-blur, blur(20px)) saturate(160%);
    -webkit-backdrop-filter: var(--backdrop-blur, blur(20px)) saturate(160%);
    border: 1px solid var(--glass-border, rgba(0, 0, 0, 0.08));
    border-radius: var(--radius-md);
    box-shadow:
      var(--glass-shadow, 0 8px 32px rgba(0, 0, 0, 0.06)),
      inset 0 1px 0 var(--glass-highlight, rgba(255, 255, 255, 0.6));
    overflow: hidden;
  }

  /* element-plus 默认以不透明 --el-fill-color-light 填充行 hover/键盘高亮，
     在玻璃面上改用半透明层，保持整块通透质感 */
  .el-autocomplete-suggestion li:hover,
  .el-autocomplete-suggestion li.highlighted {
    background: var(--glass-hover, rgba(0, 0, 0, 0.05));
  }
}

/* 「我的」下拉菜单：磨砂玻璃质感 + 去 focus 蓝色描边 */
.user-menu-popper {
  &.el-popper.is-light {
    padding: 0;
    background: var(--glass-bg, rgba(255, 255, 255, 0.72));
    backdrop-filter: var(--backdrop-blur, blur(20px)) saturate(160%);
    -webkit-backdrop-filter: var(--backdrop-blur, blur(20px)) saturate(160%);
    border: 1px solid var(--glass-border, rgba(0, 0, 0, 0.08));
    border-radius: var(--radius-md);
    box-shadow:
      var(--glass-shadow, 0 8px 32px rgba(0, 0, 0, 0.06)),
      inset 0 1px 0 var(--glass-highlight, rgba(255, 255, 255, 0.6));
    overflow: hidden;
  }

  .el-dropdown-menu {
    background: transparent;
    border: none;
    border-radius: 0;
    box-shadow: none;
  }

  .el-dropdown-menu__item {
    outline: none !important;
    color: var(--text-primary);
    transition: background 0.15s, color 0.15s;

    &:hover {
      background: var(--glass-hover, rgba(0, 0, 0, 0.05));
      color: var(--text-primary);
    }

    &:focus,
    &:focus-visible {
      outline: none !important;
      box-shadow: none !important;
    }
  }
}

</style>
