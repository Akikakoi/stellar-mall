<template>
  <div class="home-page">
    <!-- Hero 区域 -->
    <section ref="heroRef" class="hero" :class="{ 'hero-idle': heroIdle }">
      <!-- 环绕轨道：圆形旋转环，矩形卡片底边始终朝向圆心；图片在框内保持直立。
           槽位始终占位（未取到商品/图片未解码时显示骨架卡），环与标题同一拍出现，
           数据到达后只替换卡片内容，环的角度不重排、不重播入场。 -->
      <div class="hero-orbit" :class="{ paused: orbitPaused }">
        <div class="orbit-ring">
          <div
            v-for="(p, i) in orbitSlots"
            :key="i"
            class="orbit-slot"
            :style="{
              '--slot': `${(i * 360) / orbitSlots.length}deg`
            }"
          >
            <a
              v-if="p"
              class="orbit-card"
              :class="{ 'is-loading': !orbitImageLoaded.has(p.id) }"
              :href="`/spu/${p.id}`"
              target="_blank"
              rel="noopener"
              :aria-label="`查看商品：${p.name}`"
              @mouseenter="orbitPaused = true"
              @mouseleave="orbitPaused = false"
              @focus="orbitPaused = true"
              @blur="orbitPaused = false"
            >
              <span class="orbit-photo">
                <img
                  :src="ossThumb(p.mainImage) || __PH"
                  :alt="p.name"
                  decoding="async"
                  v-placeholder
                  @load="markOrbitImageLoaded(p.id)"
                />
              </span>
            </a>
            <span v-else class="orbit-card orbit-card--skeleton" aria-hidden="true"></span>
          </div>
        </div>
      </div>
      <div class="container hero-inner">
        <p class="hero-eyebrow">今夜 · 星光上新</p>
        <h1 class="hero-title">星耀商城</h1>
        <p class="hero-subtitle">
          <span class="typewriter-text">{{ subtitleText }}</span>
          <span class="typewriter-cursor" :class="{ blink: showCursor }"></span>
        </p>
        <p class="hero-desc">
          全球好物像星星一样缀满货架，指尖轻点，你喜欢的光就会送到门口。
          安心正品、光速配送——购物，也可以是一场浪漫的摘星。
        </p>
        <div class="hero-actions">
          <el-button type="primary" size="large" class="hero-cta" @click="scrollToContent">即刻，开始摘星</el-button>
        </div>
      </div>

      <!-- 右侧竖向分类滚动条：两段相同内容循环上移，点击进入对应分类商品页 -->
      <nav v-if="tickerCategories.length > 0" class="hero-ticker" aria-label="商品分类快捷入口">
        <div class="ticker-track">
          <div class="ticker-group">
            <button
              v-for="cat in tickerCategories"
              :key="cat.id"
              type="button"
              class="ticker-tag"
              @click="openShopSearch({ categoryId: cat.id })"
            >{{ cat.name }}</button>
          </div>
          <div class="ticker-group" aria-hidden="true">
            <button
              v-for="cat in tickerCategories"
              :key="`${cat.id}-dup`"
              type="button"
              class="ticker-tag"
              tabindex="-1"
              @click="openShopSearch({ categoryId: cat.id })"
            >{{ cat.name }}</button>
          </div>
        </div>
      </nav>
    </section>

    <!-- 动态模块区域 -->
    <div ref="contentRef" class="home-content">
      <template v-if="!filterMode">
        <template v-for="mod in modules" :key="mod.id">
          <!-- 分类展示：滚动到视口附近再加载对应分类商品 -->
          <template v-if="mod.type === 'CATEGORY_SHOWCASE'">
            <section
              v-if="mod._loading || !mod._loaded || mod._products.length > 0"
              :data-product-lazy="`module-${mod.id}`"
              class="container main-content cat-section"
              :class="{ 'product-pending': !mod._loaded }"
            >
              <div class="cat-header">
                <div class="cat-title">{{ mod.title || getCatName(mod) }}</div>
                <div class="cat-side">
                  <div v-if="getSubCats(mod).length > 0" class="sub-tabs">
                    <span v-if="!getCfg(mod).categoryIds" class="sub-tab" :class="{ active: !mod._activeSub }" @click="switchModuleSub(mod, null)">全部</span>
                    <span v-for="sub in getSubCats(mod)" :key="sub.id" class="sub-tab" :class="{ active: mod._activeSub === sub.id }" @click="switchModuleSub(mod, sub.id)">{{ sub.name }}</span>
                  </div>
                  <a v-else class="view-more" @click="viewMoreModule(mod)">查看更多 <svg class="arrow-icon" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/></svg></a>
                </div>
              </div>
              <div class="cat-grid" v-loading="mod._loading">
                <div v-if="mod._products[0]" class="cat-banner" :style="{ gridRow: mod._products.length > 5 ? '1 / 3' : '1 / 2' }" @click="goDetail(mod._products[0].id)">
                  <img :src="ossThumb(mod._products[0].mainImage, 480) || __PH" :alt="mod._products[0].name" loading="lazy" v-placeholder />
                  <div class="banner-text">
                    <h3>{{ mod._products[0].name }}</h3>
                    <p>¥{{ Number(mod._products[0].minPrice || 0).toFixed(2) }} 起</p>
                  </div>
                </div>
                <div v-for="p in mod._products.slice(1, 8)" :key="p.id" class="p-card" @click="goDetail(p.id)">
                  <img :src="ossThumb(p.mainImage, 480) || __PH" :alt="p.name" loading="lazy" v-placeholder />
                  <div class="p-name">{{ p.name }}</div>
                  <div class="p-desc">已售 {{ p.saleCount || 0 }} · 评论 {{ p.commentCount || 0 }}</div>
                  <div class="p-price"><span class="cur">¥{{ Number(p.minPrice || 0).toFixed(2) }}</span></div>
                </div>
                <div class="more-card" @click="viewMoreModule(mod)" v-if="mod._total > 8">
                  <div class="more-inner">
                    <div class="more-txt">浏览更多</div>
                    <div class="more-sub">{{ mod._activeSubLabel || mod.title }}</div>
                    <svg class="more-arrow" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><polyline points="9 18 15 12 9 6"></polyline></svg>
                  </div>
                </div>
              </div>
            </section>
          </template>

          <!-- 热门推荐 / 新品上市 / 精选商品：同样按滚动位置懒加载 -->
          <template v-else-if="mod.type === 'HOT_PRODUCTS' || mod.type === 'NEW_PRODUCTS' || mod.type === 'PRODUCT_GRID'">
            <section
              v-if="mod._loading || !mod._loaded || mod._products.length > 0"
              :data-product-lazy="`module-${mod.id}`"
              class="container main-content product-section"
              :class="{ 'product-pending': !mod._loaded }"
            >
              <div class="cat-header">
                <div class="cat-title">{{ mod.title }}</div>
                <a class="view-more" @click="openShopSearch()">查看更多 <svg class="arrow-icon" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/></svg></a>
              </div>
              <div class="spu-grid" v-loading="mod._loading">
                <div v-for="spu in mod._products" :key="spu.id" class="spu-card" @click="goDetail(spu.id)">
                  <div class="spu-image">
                    <img :src="ossThumb(spu.mainImage, 640) || __PH" :alt="spu.name" loading="lazy" v-placeholder />
                    <button class="fav-btn" :class="{ active: favSet.has(spu.id) }" @click.stop="toggleFav(spu)">{{ favSet.has(spu.id) ? '♥' : '♡' }}</button>
                  </div>
                  <div class="spu-info">
                    <h3 class="spu-name">{{ spu.name }}</h3>
                    <div class="spu-bottom">
                      <div class="spu-price">¥{{ Number(spu.minPrice || 0).toFixed(2) }}</div>
                      <el-button size="small" type="primary" class="add-cart-btn" @click.stop="handleAddToCart(spu)">加入购物车</el-button>
                    </div>
                  </div>
                </div>
              </div>
            </section>
          </template>

          <!-- 领券入口 -->
          <section v-else-if="mod.type === 'COUPON_ENTRY'" class="container main-content">
            <div class="coupon-entry-card" @click="router.push('/coupons')">
              <div class="coupon-entry-inner">
                <div class="coupon-entry-icon">🎫</div>
                <div class="coupon-entry-text">
                  <div class="coupon-entry-title">{{ mod.title || '领券中心' }}</div>
                  <div class="coupon-entry-desc">超值优惠券等你来领</div>
                </div>
                <div class="coupon-entry-arrow">›</div>
              </div>
            </div>
          </section>

          <!-- 单图广告 -->
          <section v-else-if="mod.type === 'SINGLE_IMAGE' && getCfg(mod).imageUrl" class="container main-content">
            <a :href="getCfg(mod).linkUrl || 'javascript:;'" :target="getCfg(mod).linkUrl ? '_blank' : '_self'">
              <img :src="getCfg(mod).imageUrl" :alt="mod.title" style="width:100%;border-radius:12px;display:block;" loading="lazy" />
            </a>
          </section>
        </template>

        <!-- 无模块或加载失败回退 -->
        <template v-if="!modulesLoaded || modules.length === 0">
          <div class="banner" v-if="banners.length > 0">
            <el-carousel :interval="4000" height="320px" indicator-position="outside">
              <el-carousel-item v-for="b in banners" :key="b.id">
                <a :href="b.linkUrl || 'javascript:;'" :target="b.linkUrl ? '_blank' : '_self'">
                  <img :src="b.imageUrl" :alt="b.title" loading="lazy" style="width:100%;height:100%;object-fit:cover;" />
                  <div class="banner-caption">{{ b.title }}</div>
                </a>
              </el-carousel-item>
            </el-carousel>
          </div>

          <main class="container main-content">
            <section
              v-for="sec in sections"
              :key="sec.id"
              v-show="sec.loading || !sec.loaded || sec.products.length > 0"
              :data-product-lazy="`section-${sec.id}`"
              class="cat-section"
              :class="{ 'product-pending': !sec.loaded }"
            >
              <div class="cat-header">
                <div class="cat-title">{{ sec.name }}</div>
                <div class="cat-side">
                  <div v-if="sec.subs.length > 0" class="sub-tabs">
                    <span class="sub-tab" :class="{ active: sec.activeSubId === null }" @click="switchSub(sec, null)" @mouseenter="switchSub(sec, null)">全部</span>
                    <span v-for="sub in sec.subs" :key="sub.id" class="sub-tab" :class="{ active: sec.activeSubId === sub.id }" @click="switchSub(sec, sub.id)" @mouseenter="switchSub(sec, sub.id)">{{ sub.name }}</span>
                  </div>
                  <a v-else class="view-more" @click="viewMore(sec)">查看更多 <svg class="arrow-icon" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/></svg></a>
                </div>
              </div>
              <div class="cat-grid" v-loading="sec.loading">
                <div v-if="sec.products[0]" class="cat-banner" :style="{ gridRow: sec.products.length > 5 ? '1 / 3' : '1 / 2' }" @click="goDetail(sec.products[0].id)">
                  <img :src="ossThumb(sec.products[0].mainImage, 480) || __PH" :alt="sec.products[0].name" loading="lazy" v-placeholder />
                  <div class="banner-text"><h3>{{ sec.products[0].name }}</h3><p>¥{{ Number(sec.products[0].minPrice || 0).toFixed(2) }} 起</p></div>
                </div>
                <div v-for="p in sec.products.slice(1, 8)" :key="p.id" class="p-card" @click="goDetail(p.id)">
                  <img :src="ossThumb(p.mainImage, 480) || __PH" :alt="p.name" loading="lazy" v-placeholder />
                  <div class="p-name">{{ p.name }}</div><div class="p-desc">已售 {{ p.saleCount || 0 }} · 评论 {{ p.commentCount || 0 }}</div>
                  <div class="p-price"><span class="cur">¥{{ Number(p.minPrice || 0).toFixed(2) }}</span></div>
                </div>
                <div class="more-card" @click="viewMore(sec)" v-if="sec.total > 8">
                  <div class="more-inner"><div class="more-txt">浏览更多</div><div class="more-sub">{{ activeSubName(sec) }}</div><svg class="more-arrow" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><polyline points="9 18 15 12 9 6"></polyline></svg></div>
                </div>
              </div>
            </section>
            <el-empty v-if="sections.every((s: any) => s.loaded && s.products.length === 0)" class="glass-empty" description="暂无商品" />
          </main>
        </template>
      </template>

      <!-- 筛选模式：平铺网格 -->
      <div v-if="filterMode" class="spu-grid container main-content" v-loading="loading" v-infinite-scroll="loadMore" :infinite-scroll-disabled="!hasMore || loading || loadingMore" :infinite-scroll-distance="120" :infinite-scroll-immediate="false">
        <div v-for="spu in spus" :key="spu.id" class="spu-card" @click="goDetail(spu.id)">
          <div class="spu-image">
            <img :src="ossThumb(spu.mainImage, 640) || __PH" :alt="spu.name" loading="lazy" v-placeholder />
            <button class="fav-btn" :class="{ active: favSet.has(spu.id) }" @click.stop="toggleFav(spu)">{{ favSet.has(spu.id) ? '♥' : '♡' }}</button>
          </div>
          <div class="spu-info">
            <h3 class="spu-name">{{ spu.name }}</h3>
            <div class="spu-meta">已售 {{ spu.saleCount || 0 }} · 评论 {{ spu.commentCount || 0 }}</div>
            <div class="spu-bottom">
              <div class="spu-price">¥{{ Number(spu.minPrice || 0).toFixed(2) }}</div>
              <el-button size="small" type="primary" class="add-cart-btn" @click.stop="handleAddToCart(spu)">加入购物车</el-button>
            </div>
          </div>
        </div>
        <el-empty v-if="!loading && spus.length === 0" class="glass-empty" description="暂无商品" />
        <div v-if="loadingMore" class="load-more-tip"><el-icon class="is-loading"><Loading /></el-icon><span>加载更多商品中...</span></div>
        <div v-else-if="!hasMore && spus.length > 0" class="load-more-tip no-more">已经到底啦</div>
      </div>
    </div>

    <!-- SKU 选择弹窗：选型号界面统一复用详情页 SkuSpecSelector（规格分组按钮 + 数量控件） -->
    <el-dialog class="glass-dialog" modal-class="glass-overlay" v-model="skuDialogVisible" :title="`选择规格 - ${currentSpu?.name || ''}`" width="520px" destroy-on-close>
      <SkuSpecSelector
        v-if="currentSkus.length"
        :spu="currentSpu"
        :skus="currentSkus"
        v-model="cartQty"
        @sku-change="onSkuChange"
      />
      <template #footer>
        <el-button @click="skuDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="addingCart" :disabled="!selectedSku" @click="confirmAddToCart">加入购物车</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts">
/**
 * 首屏环绕商品的会话级缓存。
 * 必须放在普通 <script>（模块作用域）：<script setup> 的顶层变量跟随组件实例，
 * 路由离开再回到首页会重新初始化，缓存就失效了。
 * TTL 只保留 60s：快速前进/后退时复用同一批卡片，避免闪烁；正常逛一会再回来
 * 会重新抽取（配合 orbitRecent 的近期去重，尽量看到不同商品）。
 */
const ORBIT_CACHE_TTL = 60 * 1000
let orbitCache: { products: any[]; at: number } | null = null
</script>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed, nextTick } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'
import { listSpu, listCategory, addFavorite, removeFavorite, batchCheckFavorites, getSpu, addCart, getSiteOrbit, getRandomSpus, getOrbitSpus } from '@/api/mall'
import { userRequest } from '@/api/request'
import { track } from '@/utils/tracker'
import { ossThumb } from '@/utils/imgThumb'
import { loadOrbitRecent, rememberOrbitRecent } from '@/utils/orbitRecent'
import { ElMessage } from 'element-plus'
import SkuSpecSelector from '@/components/SkuSpecSelector.vue'

const __PH = window.__PH
const router = useRouter()
const userStore = useUserStore()
const cartStore = useCartStore()

const contentRef = ref<any>(null)
const loading = ref(false)
const loadingMore = ref(false)
const filterMode = ref(false)
const isFiltering = computed(() => false)
/** 筛选模式无限滚动回调(当前 filterMode 恒为 false,保留占位实现供模板引用) */
const loadMore = () => {}

// Hero
const subtitleText = ref('')
const showCursor = ref(true)
const fullSubtitle = '把星河，装入购物车'
let typewriterTimer: any = null

/** 每屏环绕展示的商品数量（与参考站一致，8 张） */
const ORBIT_DISPLAY_COUNT = 8

/** Hero 环绕轨道商品（管理端配置候选池；未配置时每次刷新从整个商品库随机抽取） */
const orbitProducts = ref<any[]>([])
/** 悬停/聚焦商品卡时暂停整圈公转，避免只剩单卡停转而打乱队形 */
const orbitPaused = ref(false)
/** 首轮取数是否已结束：结束且无商品时撤掉骨架环，避免空环一直转 */
const orbitSettled = ref(false)
/** 已加载完成的商品主图 id：未完成前卡片保持骨架底，避免图未到先露白 */
const orbitImageLoaded = reactive(new Set<number>())

/**
 * 环绕槽位：数据未就绪时用 null 占位，保证环的结构首帧就成立；
 * 数据到达后槽位数量与角度一次性确定，图片再逐张淡入。
 */
const orbitSlots = computed<(any | null)[]>(() => {
  if (orbitProducts.value.length > 0) return orbitProducts.value
  return orbitSettled.value ? [] : new Array(ORBIT_DISPLAY_COUNT).fill(null)
})

function markOrbitImageLoaded(id: number) {
  if (id != null) orbitImageLoaded.add(id)
}

// ========== 持续旋转的暂停策略 ==========
/** 页面切到后台（其他标签/窗口）时暂停 */
const pageHidden = ref(typeof document !== 'undefined' ? document.hidden : false)
/** Hero 是否还在视口内：滚到下面商品区后暂停旋转，省电省 GPU */
const heroInView = ref(true)
const heroRef = ref<HTMLElement | null>(null)
/** 空闲暂停：后台标签页 / 首屏已滚出视口时，环、卡片高光、右侧分类条一并停转 */
const heroIdle = computed(() => pageHidden.value || !heroInView.value)
let heroVisibilityHandler: (() => void) | null = null
let heroObserver: IntersectionObserver | null = null

/**
 * 注册「离开可见区域就停转」的监听。
 * 路由切到其他页面时组件会卸载，动画本身会随之消失；这里额外覆盖
 * 「切到其他浏览器标签/窗口」和「在首页向下滚出首屏」两种不可见场景。
 */
function setupHeroIdleWatchers() {
  heroVisibilityHandler = () => { pageHidden.value = document.hidden }
  document.addEventListener('visibilitychange', heroVisibilityHandler)
  if (typeof IntersectionObserver === 'undefined' || !heroRef.value) return
  heroObserver = new IntersectionObserver((entries) => {
    heroInView.value = entries.some((entry) => entry.isIntersecting)
  }, { threshold: 0 })
  heroObserver.observe(heroRef.value)
}

/**
 * 启动 Hero 区域打字机动画效果，逐字显示副标题文本
 */
function startTypewriter() {
  subtitleText.value = ''
  showCursor.value = true
  let i = 0
  clearInterval(typewriterTimer)
  typewriterTimer = setInterval(() => {
    if (i < fullSubtitle.length) { subtitleText.value += fullSubtitle[i]; i++ }
    else { clearInterval(typewriterTimer); typewriterTimer = null; setTimeout(() => { showCursor.value = false }, 3000) }
  }, 120)
}

function scrollToContent() { contentRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }) }

// ========== 动态模块 ==========
const modules = ref<any[]>([])
const modulesLoaded = ref(false)
const banners = ref<any[]>([])
const flatCategories = ref<any[]>([])
const favSet = reactive(new Set())

function getCfg(mod: any) {
  try { return JSON.parse(mod.config || '{}') } catch { return {} }
}

function getCatName(mod: any) {
  const cfg = getCfg(mod)
  return cfg.categoryName || ''
}

/**
 * 获取分类信息（id + level）。
 * 优先取 config.categoryId；
 * 缺失时从 flatCategories 按名称匹配（防御模块配置不完整的情况）。
 * @param {Object} cfg - 模块配置对象
 * @returns {Object|null} 分类信息节点或 null
 */
function resolveCategoryInfo(cfg: any) {
  // 支持 categoryIds 数组配置（多分类聚合展示）
  if (cfg.categoryIds && cfg.categoryIds.length > 0) {
    const firstId = cfg.categoryIds[0]
    const node = flatCategories.value.find((c: any) => c.id === firstId)
    if (node) return { id: node.id, level: node.level, categoryIds: cfg.categoryIds }
    return { id: firstId, level: 1, categoryIds: cfg.categoryIds }
  }
  if (cfg.categoryId) {
    const node = flatCategories.value.find((c: any) => c.id === cfg.categoryId)
    if (node) return { id: node.id, level: node.level }
  }
  if (!cfg.categoryName || !flatCategories.value.length) return null
  const node = flatCategories.value.find((c: any) => c.name === cfg.categoryName)
  if (!node) return null
  return { id: node.id, level: node.level }
}

/**
 * 获取模块关联分类下的子分类列表
 * @param {Object} mod - 模块对象
 * @returns {Array} 子分类列表
 */
function getSubCats(mod: any) {
  const cfg = getCfg(mod)
  // 支持 categoryIds 数组配置（多分类聚合展示）
  if (cfg.categoryIds && cfg.categoryIds.length > 0) {
    return cfg.categoryIds
      .map((id: any) => flatCategories.value.find((c: any) => c.id === id) || null)
      .filter(Boolean)
  }
  return []
}

/**
 * 加载首页动态模块配置，为每个模块附加运行时状态字段
 */
async function loadModules() {
  try {
    const res: any = await userRequest({ url: '/user/home-module/list', method: 'get', __silent: true })
    const list = Array.isArray(res) ? res : (res?.data || [])
    // 为每个模块附加运行时状态
    modules.value = list.map((m: any) => {
      let cfg: Record<string, any> = {}
      try { cfg = JSON.parse(m.config || '{}') } catch {}
      // categoryIds 模式默认选中第一个分类
      const defaultSub = (cfg.categoryIds && cfg.categoryIds.length > 0) ? cfg.categoryIds[0] : null
      return {
        ...m,
        _products: [],
        _total: 0,
        _loading: false,
        _loaded: false,
        _activeSub: defaultSub,
        _activeSubLabel: ''
      }
    })
  } catch (e: any) {
    modules.value = []
  } finally {
    modulesLoaded.value = true
  }
}

/**
 * 根据模块类型加载对应的商品数据，支持 CATEGORY_SHOWCASE、HOT_PRODUCTS、NEW_PRODUCTS 和 PRODUCT_GRID
 * @param {Object} mod - 模块对象
 */
async function loadModuleProducts(mod: any) {
  mod._loading = true
  const cfg = getCfg(mod)
  /** 将 bannerSpuId 指定的商品置顶 */
  async function applyBanner() {
    if (!cfg.bannerSpuId || mod._products.length === 0) return
    try {
      const banner = await getSpu(cfg.bannerSpuId)
      if (banner) {
        mod._products = mod._products.filter((p: any) => p.id !== cfg.bannerSpuId)
        mod._products.unshift(banner)
        mod._total = Math.max(mod._total, mod._products.length)
      }
    } catch { /* 获取失败不影响正常展示 */ }
  }
  try {
    if (mod.type === 'CATEGORY_SHOWCASE') {
      const catNode = resolveCategoryInfo(cfg)
      if (!catNode) { mod._products = []; mod._total = 0; mod._loading = false; return }
      const params: Record<string, any> = { page: 1, pageSize: cfg.displayCount || 8 }
      if (catNode.categoryIds) {
        // 多分类聚合模式（categoryIds）：按选中的子分类或并发加载全部分类
        if (mod._activeSub) {
          params.categoryId = mod._activeSub
        } else {
          const results = await Promise.allSettled(
            catNode.categoryIds.map((id: any) => listSpu({ ...params, categoryId: id }))
          )
          const allProducts: any[] = []
          results.forEach((r: any) => {
            if (r.status === 'fulfilled' && r.value) {
              const products = r.value?.records || r.value?.list || (Array.isArray(r.value) ? r.value : [])
              allProducts.push(...products)
            }
          })
          mod._products = allProducts.slice(0, params.pageSize)
          mod._total = allProducts.length
          await applyBanner()
          mod._loading = false
          return
        }
      } else if (mod._activeSub) {
        params.categoryId = mod._activeSub
      } else {
        params.categoryId = catNode.id
      }
      const res = await listSpu(params)
      mod._products = res?.records || (res as any)?.list || (Array.isArray(res) ? res : [])
      mod._total = res?.total || mod._products.length
      await applyBanner()
    } else if (mod.type === 'HOT_PRODUCTS') {
      const res = await listSpu({ page: 1, pageSize: cfg.displayCount || 10, sortBy: 'saleCount', sortOrder: 'desc' })
      mod._products = res?.records || (res as any)?.list || (Array.isArray(res) ? res : [])
      mod._total = res?.total || mod._products.length
    } else if (mod.type === 'NEW_PRODUCTS') {
      const res = await listSpu({ page: 1, pageSize: cfg.displayCount || 10, sortBy: 'createTime', sortOrder: 'desc' })
      mod._products = res?.records || (res as any)?.list || (Array.isArray(res) ? res : [])
      mod._total = res?.total || mod._products.length
    } else if (mod.type === 'PRODUCT_GRID' && cfg.spuIds?.length) {
      const results = await Promise.allSettled(cfg.spuIds.map((id: any) => getSpu(id)))
      mod._products = results
        .filter((r: any) => r.status === 'fulfilled' && r.value)
        .map((r: any) => r.value)
        .filter(Boolean)
      mod._total = mod._products.length
    }
  } catch (e: any) {
    mod._products = []
  } finally {
    mod._loading = false
    mod._loaded = true
    void syncFavoriteState(mod._products)
    reportProductExposure(mod)
  }
}

/**
 * 切换模块的子分类筛选并重新加载商品
 * @param {Object} mod - 模块对象
 * @param {number|null} subId - 子分类 ID，null 表示全部
 */
async function switchModuleSub(mod: any, subId: any) {
  if (mod._activeSub === subId) return
  mod._activeSub = subId
  mod._activeSubLabel = subId ? (getSubCats(mod).find((s: any) => s.id === subId)?.name || '') : mod.title
  await loadModuleProducts(mod)
}

/**
 * 在新标签页打开商城搜索页
 * @param {Object} [query={}] - 查询参数
 */
function openShopSearch(query = {}) {
  const route = router.resolve({ path: '/shop/search', query })
  window.open(route.href, '_blank')
}

/**
 * 查看模块更多商品，解析分类信息后跳转到商城搜索页
 * @param {Object} mod - 模块对象
 */
function viewMoreModule(mod: any) {
  const cfg = getCfg(mod)
  const catNode = resolveCategoryInfo(cfg)
  if (!catNode) return
  const query: Record<string, any> = {}
  if (catNode.categoryIds) {
    // 多分类聚合模式
    query.categoryId = mod._activeSub || catNode.categoryIds[0]
  } else if (mod._activeSub) {
    query.categoryId = mod._activeSub
  } else {
    query.categoryId = catNode.id
  }
  openShopSearch(query)
}

/**
 * 批量检查当前加载商品的收藏状态
 */
async function syncFavoriteState(products: any[]) {
  const ids = (products || []).map((p: any) => p?.id).filter(Boolean)
  if (ids.length === 0 || !userStore.token) return
  try {
    const res = await batchCheckFavorites(ids)
    if (Array.isArray(res)) res.forEach((id: any) => favSet.add(id))
  } catch (e: any) {}
}

/**
 * 商品模块加载完成后的曝光埋点
 */
function reportProductExposure(mod: any) {
  const ids = Array.from(new Set((mod._products || []).map((p: any) => p?.id).filter(Boolean)))
  if (ids.length > 0) {
    track('view_item_list', {
      scene: 'home',
      extra: { moduleType: mod.type, spuIds: ids.slice(0, 50) }
    })
  }
}

// ========== 商品模块滚动懒加载 ==========
let lazyProductObserver: IntersectionObserver | null = null
const lazyProductTargets = new Map<string, Element>()

/**
 * 执行某个商品区块的首次加载；重复进入视口或切换子分类不受影响
 */
async function runLazyProductSection(key: string) {
  if (key.startsWith('module-')) {
    const mod = modules.value.find((m: any) => `module-${m.id}` === key)
    if (!mod || mod._loaded || mod._loading) return
    await loadModuleProducts(mod)
    return
  }
  if (key.startsWith('section-')) {
    const sec = sections.value.find((s: any) => `section-${s.id}` === key)
    if (!sec || sec.loaded || sec.loading) return
    await loadSectionProducts(sec)
  }
}

/**
 * 监听商品区块进入视口附近，再请求对应分类/模块商品。
 * rootMargin 预留 180px，让用户滚动到标题附近时数据已经开始加载。
 */
function ensureLazyProductObserver() {
  if (lazyProductObserver || typeof IntersectionObserver === 'undefined') return
  lazyProductObserver = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (!entry.isIntersecting) return
      const key = entry.target.getAttribute('data-product-lazy')
      lazyProductObserver?.unobserve(entry.target)
      if (key) void runLazyProductSection(key)
    })
  }, { rootMargin: '180px 0px', threshold: 0.05 })
}

/**
 * 扫描当前商品区块并注册滚动监听；DOM 变化后可重复调用
 */
function refreshLazyProductSections() {
  void nextTick(() => {
    const nodes = Array.from(document.querySelectorAll('[data-product-lazy]'))
    if (typeof IntersectionObserver === 'undefined') {
      nodes.forEach((el) => {
        const key = el.getAttribute('data-product-lazy')
        if (key) void runLazyProductSection(key)
      })
      return
    }
    ensureLazyProductObserver()
    const activeKeys = new Set<string>()
    nodes.forEach((el) => {
      const key = el.getAttribute('data-product-lazy')
      if (!key || !lazyProductObserver) return
      activeKeys.add(key)
      const previous = lazyProductTargets.get(key)
      if (previous !== el) {
        if (previous) lazyProductObserver.unobserve(previous)
        lazyProductTargets.set(key, el)
        lazyProductObserver.observe(el)
      }
    })
    lazyProductTargets.forEach((el, key) => {
      if (activeKeys.has(key) || !lazyProductObserver) return
      lazyProductObserver.unobserve(el)
      lazyProductTargets.delete(key)
    })
  })
}

// ========== 首屏环绕商品 ==========
/**
 * 加载首屏环绕商品（独立启动，不阻塞首屏其它请求）：
 * 1. 优先请求后端合并接口 /user/spu/orbit（候选池 + 随机兜底一次算完，轻量字段）；
 * 2. 旧后端没有该接口时回退：读候选池配置 → 拉候选详情 / 调用随机接口；
 * 3. 接口全部异常 → 回退首页已加载商品。
 * 数据一到就挂卡（槽位已由骨架占住），单张图片由卡片自己加载淡入；
 * 不再等全部图片 decode 后整圈一次性出现，避免始终比标题慢一拍。
 */
async function loadOrbitProducts() {
  // 同一会话内（如从商品详情返回首页）直接复用上次结果：图片走浏览器缓存，基本瞬间出图
  if (orbitCache && Date.now() - orbitCache.at < ORBIT_CACHE_TTL) {
    orbitImageLoaded.clear()
    orbitProducts.value = orbitCache.products
    orbitSettled.value = true
    return
  }
  let pool: any[] = []
  // 优先走后端合并接口：一次 RTT 拿到最终卡片数据（弱网下省掉 2~9 次串行请求）；
  // 同时把最近展示过的商品 id 传给后端，让它优先从没出现过的商品里抽
  try {
    const list = await getOrbitSpus(ORBIT_DISPLAY_COUNT, loadOrbitRecent())
    pool = Array.isArray(list) ? list : []
  } catch (e: any) {}
  // 回退：旧后端未部署 /user/spu/orbit（接口 404）或请求异常时，仍走旧的两步流程
  if (pool.length === 0) {
    try {
      const cfg: any = await getSiteOrbit()
      const ids: number[] = Array.isArray(cfg?.spuIds) ? cfg.spuIds : []
      if (ids.length > 0) {
        const results = await Promise.allSettled(
          ids.map((id: number) => userRequest({ url: `/user/spu/${id}`, method: 'get', __silent: true }))
        )
        pool = results
          .filter((r: any) => r.status === 'fulfilled' && r.value)
          .map((r: any) => r.value)
      }
    } catch (e: any) {}
  }
  if (pool.length === 0) {
    try {
      const list = await getRandomSpus(ORBIT_DISPLAY_COUNT)
      pool = Array.isArray(list) ? list : []
    } catch (e: any) {}
  }
  if (pool.length === 0) pool = pickDefaultOrbitProducts()
  const chosen = shuffle(pool).slice(0, ORBIT_DISPLAY_COUNT)
  orbitSettled.value = true
  if (chosen.length === 0) {
    // 全部兜底失败：撤掉骨架环，别让空环一直转
    orbitProducts.value = []
    return
  }
  orbitImageLoaded.clear()
  orbitProducts.value = chosen
  // 记录本轮展示，下一次抽取时排除，保证连续刷新尽量不重样
  rememberOrbitRecent(chosen.map((p: any) => p.id).filter(Boolean))
  orbitCache = { products: chosen, at: Date.now() }
}

/**
 * 随机打乱数组（Fisher-Yates），保证每次刷新抽到不同商品。
 */
function shuffle<T>(list: T[]): T[] {
  const arr = [...list]
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    const tmp = arr[i]!
    arr[i] = arr[j]!
    arr[j] = tmp
  }
  return arr
}

/**
 * 接口全部失败时的兜底：从首页已加载商品中取有主图的商品。
 */
function pickDefaultOrbitProducts() {
  const pools = [
    ...modules.value.flatMap((m: any) => m._products || []),
    ...sections.value.flatMap((s: any) => s.products || [])
  ]
  const seen = new Set<number>()
  const picked: any[] = []
  for (const p of pools) {
    if (p?.id && p?.mainImage && !seen.has(p.id)) {
      seen.add(p.id)
      picked.push(p)
    }
  }
  return picked
}

// ========== 回退：分类分区 ==========
const sections = ref<any[]>([])
/** 右侧竖排滚动条内容：取顶部一级分类 */
const tickerCategories = computed(() => sections.value.map((s: any) => ({ id: s.id, name: s.name })))

/**
 * 根据分类树构建回退分区列表，每个分类对应一个展示区域
 * @param {Array} tree - 分类树
 */
function buildSections(tree: any) {
  sections.value = (tree || []).map((c: any) => ({
    id: c.id, name: c.name,
    subs: Array.isArray(c.children) ? c.children : [],
    activeSubId: null, products: [], total: 0, loading: false, loaded: false
  }))
  sections.value.sort((a: any, b: any) => a.name === '智能手机' ? -1 : b.name === '智能手机' ? 1 : 0)
}

/**
 * 加载指定分类分区的商品列表，支持子分类筛选
 * @param {Object} sec - 分区对象
 */
async function loadSectionProducts(sec: any) {
  sec.loading = true
  try {
    const params: Record<string, any> = { page: 1, pageSize: 8, categoryId: sec.activeSubId || sec.id }
    const res = await listSpu(params)
    sec.products = res?.records || (res as any)?.list || (Array.isArray(res) ? res : [])
    sec.total = res?.total || sec.products.length
  } catch (e: any) { sec.products = [] } finally {
    sec.loading = false
    sec.loaded = true
  }
}

/**
 * 切换分类分区的子分类，带防抖延迟后重新加载商品
 * @param {Object} sec - 分区对象
 * @param {number|null} subId - 子分类 ID
 */
function switchSub(sec: any, subId: any) {
  if (sec.activeSubId === subId) return
  clearTimeout(sec._hoverTimer)
  sec._hoverTimer = setTimeout(() => { sec.activeSubId = subId; loadSectionProducts(sec) }, 200)
}

function activeSubName(sec: any) {
  return sec.name
}

/**
 * 查看分区更多商品，跳转到商城搜索页
 * @param {Object} sec - 分区对象
 */
function viewMore(sec: any) {
  openShopSearch({ categoryId: sec.activeSubId || sec.id })
}

// ========== 分类数据 ==========
/**
 * 加载商品分类数据，构建平铺列表并构建回退分区
 */
async function loadCategory() {
  try {
    const res = await listCategory()
    flatCategories.value = flattenCats(res || [])
    buildSections(res || [])
  } catch (e: any) {}
}

/**
 * 将分类树平铺为一维数组
 * @param {Array} tree - 分类树
 * @returns {Array} 平铺后的分类列表
 */
function flattenCats(tree: any) {
  const result: any[] = []
  function traverse(list: any) {
    if (!list) return
    for (const item of list) {
      result.push({ ...item })
      traverse(item.children)
    }
  }
  traverse(tree)
  return result
}

// ========== 筛选模式 + Banner ==========
const spus = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const hasMore = computed(() => spus.value.length < total.value)

/**
 * 加载首页 Banner 轮播图数据
 */
async function loadBanners() {
  try {
    const res: any = await userRequest({ url: '/user/banner/list', method: 'get', __silent: true })
    banners.value = Array.isArray(res) ? res : (res?.data || [])
  } catch (e: any) {}
}

// ========== 通用方法 ==========
/**
 * 在新标签页打开商品详情页
 * @param {number|string} id - 商品 SPU ID
 */
function goDetail(id: any) {
  const route = router.resolve(`/spu/${id}`)
  window.open(route.href, '_blank')
}

/**
 * 切换商品收藏状态，未登录时跳转登录页
 * @param {Object} spu - 商品对象
 */
async function toggleFav(spu: any) {
  if (!userStore.token) { ElMessage.warning('请先登录'); router.push('/login'); return }
  try {
    if (favSet.has(spu.id)) { await removeFavorite(spu.id); favSet.delete(spu.id); track('favorite', { spuId: spu.id, scene: 'home', extra: { fav: 0 } }); ElMessage.success('已取消收藏') }
    else { await addFavorite(spu.id); favSet.add(spu.id); track('favorite', { spuId: spu.id, scene: 'home', extra: { fav: 1 } }); ElMessage.success('已添加收藏') }
  } catch (e: any) { ElMessage.error('操作失败') }
}

// SKU dialog（选型号统一复用详情页 SkuSpecSelector：仅规格 + 数量，不含保障服务）
const skuDialogVisible = ref(false)
const currentSpu = ref<any>(null)
const currentSkus = ref<any[]>([])
const cartQty = ref(1)          // 数量，由 SkuSpecSelector 通过 v-model 同步
const addingCart = ref(false)
const selectedSku = ref<any>(null)   // 由 SkuSpecSelector 的 sku-change 事件填充
function onSkuChange(sku: any) { selectedSku.value = sku }

/**
 * 处理加入购物车：获取商品 SKU 列表，单个 SKU 直接加入，多个 SKU 弹出规格选择弹窗
 * @param {Object} spu - 商品对象
 */
async function handleAddToCart(spu: any) {
  if (!userStore.token) { ElMessage.warning('请先登录'); router.push('/login'); return }
  try {
    const res = await getSpu(spu.id)
    const skuList = res?.skuList || (res as any)?.skuListVo || []
    if (!skuList.length) { ElMessage.warning('该商品暂无规格可选'); return }
    if (skuList.length === 1) {
      track('add_to_cart', { spuId: spu.id, skuId: skuList[0].id, scene: 'home' })
      await doAddCart(skuList[0].id)
      return
    }
    currentSpu.value = res
    currentSkus.value = skuList.filter((s: any) => s.status !== 0)
    cartQty.value = 1
    selectedSku.value = null
    skuDialogVisible.value = true
  } catch (e: any) { ElMessage.error(e?.response?.data?.msg || e?.message || '加载商品规格失败') }
}

/**
 * 执行加入购物车操作，调用接口并刷新购物车数据
 * @param {number} skuId - SKU ID
 */
async function doAddCart(skuId: any, qty = 1) {
  if (!skuId) { ElMessage.warning('请选择商品规格'); return }
  addingCart.value = true
  try {
    await addCart({ skuId, qty })
    ElMessage.success('已加入购物车')
    skuDialogVisible.value = false
    await cartStore.load()
  } catch (e: any) { ElMessage.error(e?.response?.data?.msg || e?.message || '加入购物车失败') } finally { addingCart.value = false }
}

function confirmAddToCart() {
  track('add_to_cart', { spuId: currentSpu.value?.id, skuId: selectedSku.value?.id, scene: 'home' })
  doAddCart(selectedSku.value?.id, cartQty.value)
}

// ========== 生命周期 ==========
onMounted(() => {
  startTypewriter()
  setupHeroIdleWatchers()
  // 首屏各请求互不阻塞：环绕轨道立即启动（骨架环同帧已渲染），Banner / 分类 / 模块并行加载
  void loadBanners()
  void loadOrbitProducts()
  // 模块与分类都就绪后再注册滚动懒加载：回退分区依赖分类树，模块列表依赖接口返回
  void Promise.all([loadCategory(), loadModules()]).finally(() => refreshLazyProductSections())
  if (userStore.token && !userStore.nickname) {
    void userStore.fetchProfile().catch(() => {})
  }
  void cartStore.load().catch(() => {})
})

onUnmounted(() => {
  clearInterval(typewriterTimer)
  if (heroVisibilityHandler) {
    document.removeEventListener('visibilitychange', heroVisibilityHandler)
    heroVisibilityHandler = null
  }
  heroObserver?.disconnect()
  heroObserver = null
  lazyProductObserver?.disconnect()
  lazyProductObserver = null
  lazyProductTargets.clear()
})
</script>

<style scoped>
.home-page {
  min-height: 100vh;
}
.container { max-width: 1200px; margin: 0 auto; padding: 0 24px; }

.hero {
  /* 环绕轨道参数：卡片基准尺寸 / 圆形半径 / 一圈时长 / 圆角 */
  --orbit-card-w: 140px;
  --orbit-card-h: 160px;
  --orbit-radius: 264px;
  --orbit-duration: 32s;
  --orbit-corner: 32px;
  /* 商品在卡内的占位比例（相对 160% 的图片画布）：70% 已超出卡片宽度，左右各裁掉一圈白边。
     安全上限 ≈ 44% ÷ 商品在照片中的占比；调大更饱满，但填满画面的图转到 45° 附近会被削角 */
  --orbit-photo-scale: 70%;
  /* 旋转环整体横向偏移：偏右形成「左文右环」分栏；偏移量需保证环最左侧卡片不扫到文案列 */
  --orbit-offset-x: clamp(150px, 18vw, 240px);
  position: relative;
  background: transparent;
  text-align: center;
  min-height: 720px;
  /* 首屏只展示 Hero：撑满导航下方整个视口，让商品模块从折叠线以下开始 */
  min-height: max(720px, calc(100vh - 64px));
  min-height: max(720px, calc(100dvh - 64px));
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: 40px 0;
}
.hero-inner {
  position: absolute;
  left: 0;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  z-index: 1;
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  text-align: left;
  /* 文案层横跨整个容器，透明区域需放行指针，避免挡住下方旋转卡片 */
  pointer-events: none;
}
.hero-inner .hero-cta { pointer-events: auto; }
/* 左文右环分栏：文案列收窄，不延伸到旋转环下方 */
.hero-inner .hero-subtitle,
.hero-inner .hero-desc { max-width: 460px; }
.hero-inner .hero-actions { pointer-events: auto; }

/* 眉标：标题上方的轻量引导标签 */
.hero-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 20px;
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 14px;
  color: var(--brand-primary);
  background: color-mix(in srgb, var(--brand-primary) 10%, transparent);
  border: 1px solid color-mix(in srgb, var(--brand-primary) 22%, transparent);
}
/* 描述段落：补充卖点，撑起左栏文案量 */
.hero-desc {
  margin: 0 0 28px;
  font-size: 16px;
  line-height: 1.8;
  color: var(--text-secondary);
}
/* 按钮组：主按钮独占一行 */
.hero-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 36px;
}

/* 圆形旋转环：每个槽位先旋转角度、再沿半径外推，使矩形底边始终朝向圆心。
   外层环整体旋转，框内图片反向旋转抵消环+槽位角度，保持直立；
   减少动态效果时两个动画一起停在基准状态。 */
.hero-orbit {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 整体右移：不影响环自身旋转动画（动画在 .orbit-ring 上） */
  transform: translateX(var(--orbit-offset-x, 0px));
  pointer-events: none;
}
.orbit-ring {
  position: relative;
  width: calc(var(--orbit-radius) * 2);
  height: calc(var(--orbit-radius) * 2);
  flex: none;
  animation: orbit-spin var(--orbit-duration) linear infinite;
}
.orbit-slot {
  position: absolute;
  left: 50%;
  top: 50%;
  width: var(--orbit-card-w);
  height: var(--orbit-card-h);
  margin-left: calc(var(--orbit-card-w) / -2);
  margin-top: calc(var(--orbit-card-h) / -2);
  transform: rotate(var(--slot)) translateY(calc(var(--orbit-radius) * -1));
  /* 与标题同拍：首帧即渲染骨架环，入场节奏和 .hero-title 的 0.5s 对齐 */
  animation: orbit-slot-in 0.5s var(--ease-out) backwards;
}
.orbit-card {
  position: relative;
  display: block;
  width: 100%;
  height: 100%;
  border-radius: var(--orbit-corner);
  /* 商品图统一白底：卡片做成白色瓷片，白底图与卡面无缝，商品像是浮在瓷片上。
     浅色主题下再靠 1px 内描边把瓷片从页面背景里分离出来，避免糊成一片。 */
  background: #fff;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.14), inset 0 0 0 1px rgba(15, 23, 42, 0.06);
  overflow: hidden;
  pointer-events: auto;
  text-decoration: none;
  color: inherit;
}
/* 骨架卡：商品数据/主图未就绪时先占住槽位，环与标题同时出现，不再等接口和图片。
   材质沿用全站玻璃体系；shimmer 属恒定运动，用 linear（与 .skeleton 一致）。 */
.orbit-card--skeleton,
.orbit-card.is-loading {
  background: linear-gradient(100deg, var(--glass-bg) 25%, var(--bg-hover) 50%, var(--glass-bg) 75%);
  background-size: 200% 100%;
  border: 1px solid var(--glass-border);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.10), inset 0 1px 0 var(--glass-highlight);
  animation: orbit-card-shimmer 1.6s linear infinite;
}
.orbit-card--skeleton {
  pointer-events: none;
}
@keyframes orbit-card-shimmer {
  to { background-position: -200% 0; }
}
.orbit-card:focus-visible {
  outline: 2px solid var(--brand-primary);
  outline-offset: 4px;
}
/* 图片放大到 160% 并反向旋转：卡片框转动时，照片内容保持直立且不露底。
   160% 是「矩形绕中心旋转 45° 的外接框」所需的最小覆盖尺寸，保证任何角度都铺满卡片窗口。 */
.orbit-photo {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 160%;
  height: 160%;
  margin-left: -80%;
  margin-top: -80%;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: orbit-photo-counter var(--orbit-duration) linear infinite;
}
.orbit-photo img {
  display: block;
  /* 完整展示商品：缩到卡片内切圆以内，卡片转到任何角度都裁不到商品本体；
     超出内切圆的只有白底图的空白边角，被窗口裁掉也看不出来。 */
  width: var(--orbit-photo-scale);
  height: var(--orbit-photo-scale);
  object-fit: contain;
  /* 图片解码完成后逐张淡入：未就绪时露出卡片骨架底，单张慢图不再拖住整圈 */
  opacity: 0;
  transition: opacity 0.32s var(--ease-out);
}
.orbit-card:not(.is-loading) .orbit-photo img {
  opacity: 1;
}
@keyframes orbit-spin {
  to { transform: rotate(360deg); }
}
@keyframes orbit-slot-in {
  from { opacity: 0; }
}
@keyframes orbit-photo-counter {
  from { transform: rotate(calc(var(--slot) * -1)); }
  to { transform: rotate(calc(var(--slot) * -1 - 360deg)); }
}
/* 悬停/聚焦任意卡片：环与图片的反向动画同步暂停；移出后继续 */
.hero-orbit.paused .orbit-ring,
.hero-orbit.paused .orbit-photo {
  animation-play-state: paused;
}

/* 页面切到后台 / 首屏滚出视口时暂停所有持续动画（公转、图片反向转、骨架高光、分类条滚动），
   避免不可见时仍占用 CPU/GPU；路由切到其他页面时组件会卸载，动画自然停止。 */
.hero.hero-idle .orbit-ring,
.hero.hero-idle .orbit-photo,
.hero.hero-idle .orbit-card--skeleton,
.hero.hero-idle .orbit-card.is-loading,
.hero.hero-idle .ticker-track {
  animation-play-state: paused;
}

/* 右侧竖向分类滚动条：两段相同标签循环上移，上下渐隐 */
.hero-ticker {
  position: absolute;
  right: 24px;
  top: 50%;
  /* 外框随最宽分类自适应，不再强制所有格子同宽 */
  width: max-content;
  min-width: 80px;
  max-width: calc(100vw - 48px);
  height: 224px;
  transform: translateY(-50%);
  overflow: hidden;
  z-index: 1;
  pointer-events: auto;
  -webkit-mask-image: linear-gradient(to bottom, transparent, #000 12%, #000 88%, transparent);
  mask-image: linear-gradient(to bottom, transparent, #000 12%, #000 88%, transparent);
  animation: ticker-fade-in 0.4s var(--ease-out) backwards;
}
.ticker-track {
  display: flex;
  width: max-content;
  min-width: 100%;
  flex-direction: column;
  align-items: flex-end;
  animation: ticker-scroll 26s linear infinite;
}
.ticker-group {
  display: flex;
  width: max-content;
  min-width: 100%;
  flex-direction: column;
  align-items: flex-end;
}
.ticker-tag {
  display: block;
  /* 每个分类格子按文字实际长度收缩/展开 */
  width: fit-content;
  min-width: 64px;
  max-width: 100%;
  box-sizing: border-box;
  margin-bottom: 16px;
  padding: 8px 12px;
  font-family: MiSans, "Alibaba PuHuiTi 3.0", "PingFang SC", "DengXian", "Microsoft YaHei", sans-serif;
  font-size: 14px;
  font-weight: 650;
  line-height: 1.2;
  color: var(--text-primary);
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  text-align: right;
  transition: color var(--transition-color), background var(--transition-color), border-color var(--transition-color), transform 160ms var(--ease-out);
}
@media (hover: hover) {
  .ticker-tag:hover {
    color: var(--brand-primary);
    border-color: var(--brand-primary-border);
    background: var(--bg-card);
    transform: translateX(-3px);
  }
}
/* 悬停/键盘聚焦时暂停滚动，方便点击 */
.hero-ticker:hover .ticker-track,
.hero-ticker:focus-within .ticker-track {
  animation-play-state: paused;
}
@keyframes ticker-scroll {
  to { transform: translateY(-50%); }
}
@keyframes ticker-fade-in {
  from { opacity: 0; }
}
@media (prefers-reduced-motion: reduce) {
  .ticker-track { animation: none; }
  .hero-ticker { overflow-y: auto; }
}

.hero-title {
  font-family: "Smiley Sans Oblique", MiSans, "Alibaba PuHuiTi 3.0", "PingFang SC", "DengXian", "Microsoft YaHei", sans-serif;
  font-size: clamp(40px, 4.5vw, 64px);
  font-weight: 400;
  margin: 0 0 16px;
  line-height: 1.1;
}
.hero-subtitle {
  font-size: 21px;
  color: var(--text-secondary);
  margin-bottom: 32px;
  font-weight: 400;
  min-height: 1.5em;
}
.typewriter-text { white-space: pre; }
.typewriter-cursor {
  display: inline-block; width: 2px; height: 1em;
  background: var(--brand-primary); margin-left: 2px;
  vertical-align: middle; opacity: 0;
}
.typewriter-cursor.blink {
  animation: cursor-blink 0.9s steps(2, start) infinite;
  opacity: 1;
}
@keyframes cursor-blink { to { opacity: 0; } }
.hero-cta { height: 52px; padding: 0 36px; font-size: 17px; border-radius: 8px; }

/* 首屏：眉标 → 标题 → 副标题 → 描述 → 按钮，依次轻轻浮上来（只在进页面时播一次） */
.hero-eyebrow { animation: hero-rise 0.5s var(--ease-out) backwards; }
.hero-title { animation: hero-rise 0.5s var(--ease-out) 60ms backwards; }
.hero-subtitle { animation: hero-rise 0.5s var(--ease-out) 120ms backwards; }
.hero-desc { animation: hero-rise 0.5s var(--ease-out) 180ms backwards; }
.hero-actions { animation: hero-rise 0.5s var(--ease-out) 240ms backwards; }
@keyframes hero-rise {
  from { opacity: 0; transform: translateY(12px); }
}

/* Banner */
.banner { background: var(--bg-surface); }
.banner-caption {
  position: absolute; bottom: 0; left: 0; right: 0;
  padding: 16px 24px;
  background: linear-gradient(transparent, rgba(0,0,0,0.45));
  color: #fff; font-size: 18px; font-weight: 600;
}

.main-content { padding: 48px 20px 80px; }

/* ===== 分类分区展示 ===== */
.cat-section { margin-bottom: 44px; }
.cat-header {
  display: flex; align-items: baseline; justify-content: space-between;
  margin-bottom: 16px;
}
.cat-title { font-size: 24px; font-weight: 600; color: var(--text-primary); }
.cat-side { display: flex; align-items: center; gap: 22px; }
.sub-tabs { display: flex; gap: 22px; flex-wrap: wrap; }
.sub-tab {
  font-size: 15px; color: var(--text-secondary); cursor: pointer;
  padding-bottom: 4px; border-bottom: 2px solid transparent;
  transition: color var(--transition-color), border-color var(--transition-color); user-select: none;
}
.sub-tab:hover { color: var(--brand-primary); }
.sub-tab.active { color: var(--brand-primary); border-bottom-color: var(--brand-primary); font-weight: 600; }
.view-more {
  font-size: 13px; color: var(--brand-primary); cursor: pointer;
  display: inline-flex; align-items: center; gap: 4px;
  padding: 6px 14px; border-radius: 20px;
  background: var(--brand-primary-soft);
  transition: background var(--transition-color), color var(--transition-color), box-shadow var(--transition-color);
}
.view-more:hover {
  background: var(--brand-primary);
  color: #fff;
  box-shadow: 0 2px 10px rgba(64, 158, 255, 0.35);
}
.view-more .arrow-icon {
  transition: transform var(--transition-base);
}
.view-more:hover .arrow-icon {
  transform: translateX(3px);
}

.cat-grid {
  display: grid;
  grid-template-columns: 224px repeat(4, 1fr);
  grid-auto-rows: 300px;
  gap: 14px;
}
.cat-banner {
  grid-row: 1 / 3; border-radius: 10px; overflow: hidden;
  position: relative; cursor: pointer;
  /* 磨砂玻璃：图片加载/透明区透出 webp 背景，与全站玻璃体系一致 */
  background: var(--glass-bg);
  backdrop-filter: var(--backdrop-blur);
  -webkit-backdrop-filter: var(--backdrop-blur);
}
.cat-banner img { width: 100%; height: 100%; object-fit: cover; display: block; transition: transform 0.25s var(--ease-out); }
@media (hover: hover) {
  .cat-banner:hover img { transform: scale(1.04); }
}
.cat-banner .banner-text {
  position: absolute; left: 0; right: 0; top: 0;
  padding: 22px 14px 40px; text-align: center; color: #fff;
  background: linear-gradient(rgba(0,0,0,.5), transparent);
  text-shadow: 0 1px 8px rgba(0,0,0,.45);
}
.cat-banner .banner-text h3 { font-size: 18px; font-weight: 600; margin: 0 0 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cat-banner .banner-text p { font-size: 13px; opacity: .92; margin: 0; }

.p-card {
  /* 磨砂玻璃卡（替代实底 var(--bg-card)）：背景/描边/投影/顶部高光全量玻璃化 */
  background: var(--glass-bg);
  border-radius: 10px;
  border: 1px solid var(--glass-border);
  box-shadow: var(--glass-shadow), inset 0 1px 0 var(--glass-highlight);
  backdrop-filter: var(--backdrop-blur);
  -webkit-backdrop-filter: var(--backdrop-blur);
  display: flex; flex-direction: column; align-items: center;
  padding: 22px 16px 18px; cursor: pointer;
  transition: transform var(--transition-base), box-shadow var(--transition-color); overflow: hidden;
  animation: card-rise 0.48s var(--ease-out) backwards;
}
@media (hover: hover) {
  .p-card:hover { transform: translateY(-4px); box-shadow: var(--shadow-md), inset 0 1px 0 var(--glass-highlight); }
}
.p-card img { width: 140px; height: 140px; object-fit: cover; margin-bottom: 14px; border-radius: 10px; background: #fff; }
.p-name { font-size: 14px; font-weight: 500; text-align: center; color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 100%; }
.p-desc { font-size: 12px; color: var(--text-muted); margin-top: 6px; text-align: center; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 100%; }
.p-price { margin-top: auto; padding-top: 12px; font-size: 14px; }
.p-price .cur { color: var(--brand-primary); font-weight: 600; }

.more-card {
  background: var(--glass-bg);
  border-radius: 10px;
  border: 1px solid var(--glass-border);
  box-shadow: var(--glass-shadow), inset 0 1px 0 var(--glass-highlight);
  backdrop-filter: var(--backdrop-blur);
  -webkit-backdrop-filter: var(--backdrop-blur);
  display: flex; align-items: center; justify-content: center;
  cursor: pointer; transition: transform var(--transition-base), box-shadow var(--transition-color), border-color var(--transition-color);
}
@media (hover: hover) {
  .more-card:hover { transform: translateY(-4px); box-shadow: var(--shadow-md), inset 0 1px 0 var(--glass-highlight); border-color: var(--brand-primary); }
}
.more-inner { display: flex; flex-direction: column; align-items: center; gap: 6px; }
.more-txt { font-size: 16px; font-weight: 600; color: var(--text-secondary); transition: color var(--transition-color); }
.more-sub { font-size: 12px; color: var(--text-muted); }
.more-arrow { margin-top: 2px; color: var(--brand-primary); transition: transform var(--transition-base); }
.more-card:hover .more-txt { color: var(--brand-primary); }
.more-card:hover .more-arrow { transform: translateX(3px); }

/* ===== 产品网格（热门/新品/精选） ===== */
.product-section { margin-bottom: 20px; }
.spu-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 24px;
}
.spu-card {
  /* 磨砂玻璃卡：白底 → 玻璃。商品图 cover 不透明内容自然呈现；信息区/描边透出玻璃 */
  background: var(--glass-bg);
  border-radius: 22px; overflow: hidden;
  cursor: pointer; border: 1px solid var(--glass-border);
  box-shadow: var(--glass-shadow), inset 0 1px 0 var(--glass-highlight);
  backdrop-filter: var(--backdrop-blur);
  -webkit-backdrop-filter: var(--backdrop-blur);
  transition: transform var(--transition-base), box-shadow var(--transition-base), border-color var(--transition-base);
  animation: card-rise 0.48s var(--ease-out) backwards;
}
.spu-image { aspect-ratio: 1; overflow: hidden; background: var(--bg-hover); position: relative; }
.spu-image img { width: 100%; height: 100%; object-fit: cover; transition: transform 0.25s var(--ease-out); }
@media (hover: hover) {
  .spu-card:hover { transform: var(--hover-lift); box-shadow: var(--shadow-md), inset 0 1px 0 var(--glass-highlight); border-color: var(--brand-primary-border); }
  .spu-card:hover .spu-image img { transform: scale(1.05); }
}
.fav-btn {
  position: absolute; top: 12px; right: 12px; width: 34px; height: 34px;
  border-radius: 50%; border: none;
  background: rgba(0,0,0,0.35); backdrop-filter: blur(8px);
  color: rgba(255,255,255,0.9); font-size: 18px;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  transition: background var(--transition-color), color var(--transition-color), transform var(--transition-base); line-height: 1; padding: 0;
}
@media (hover: hover) {
  .fav-btn:hover { background: rgba(0,0,0,0.55); transform: scale(1.15); }
}
.fav-btn.active { color: var(--status-danger); background: rgba(255,59,48,0.18); }
.spu-info { padding: 18px 20px 22px; }
.spu-name { font-size: 15px; color: var(--text-primary); margin: 0 0 10px; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; min-height: 45px; }
.spu-bottom { display: flex; justify-content: space-between; align-items: center; }
.spu-meta { font-size: 12px; color: var(--text-muted); margin: -2px 0 8px; }
.spu-price { color: var(--text-primary); font-size: 18px; font-weight: 700; }
.add-cart-btn { border-radius: 8px; }

/* 商品卡从下向上浮现：数据滚动加载完成后逐张入场 */
@keyframes card-rise {
  from { opacity: 0; transform: translateY(28px); }
}
.product-pending .cat-grid,
.product-pending .spu-grid { min-height: 260px; }
.cat-banner {
  animation: card-rise 0.52s var(--ease-out) backwards;
}
.more-card {
  animation: card-rise 0.48s var(--ease-out) 240ms backwards;
}
.cat-grid .p-card:nth-child(2) { animation-delay: 0ms; }
.cat-grid .p-card:nth-child(3) { animation-delay: 30ms; }
.cat-grid .p-card:nth-child(4) { animation-delay: 60ms; }
.cat-grid .p-card:nth-child(5) { animation-delay: 90ms; }
.cat-grid .p-card:nth-child(6) { animation-delay: 120ms; }
.cat-grid .p-card:nth-child(7) { animation-delay: 150ms; }
.cat-grid .p-card:nth-child(8) { animation-delay: 180ms; }
.spu-grid .spu-card:nth-child(2) { animation-delay: 30ms; }
.spu-grid .spu-card:nth-child(3) { animation-delay: 60ms; }
.spu-grid .spu-card:nth-child(4) { animation-delay: 90ms; }
.spu-grid .spu-card:nth-child(5) { animation-delay: 120ms; }
.spu-grid .spu-card:nth-child(6) { animation-delay: 150ms; }
.spu-grid .spu-card:nth-child(7) { animation-delay: 180ms; }
.spu-grid .spu-card:nth-child(8) { animation-delay: 210ms; }
/* 减少动态效果：入场本来就是极短的透明度变化，这里再去掉排队延迟 */
@media (prefers-reduced-motion: reduce) {
  .hero-title,
  .hero-subtitle,
  .hero-cta,
  .cat-banner,
  .more-card,
  .p-card,
  .spu-card { animation-delay: 0ms !important; }
}

/* ===== 领券入口 ===== */
.coupon-entry-card {
  background: linear-gradient(135deg, #fff8f0, #fff0e0);
  border-radius: var(--radius-lg); cursor: pointer;
  transition: transform var(--transition-base), box-shadow var(--transition-color); border: 1px solid #ffe0c0;
}
@media (hover: hover) {
  .coupon-entry-card:hover { transform: translateY(-2px); box-shadow: 0 8px 24px rgba(255,140,0,0.15); }
}
.coupon-entry-inner {
  display: flex; align-items: center; gap: 18px;
  padding: 28px 32px;
}
.coupon-entry-icon { font-size: 40px; }
.coupon-entry-title { font-size: 20px; font-weight: 600; color: var(--text-primary); }
.coupon-entry-desc { font-size: 14px; color: var(--text-secondary); margin-top: 4px; }
.coupon-entry-arrow { font-size: 28px; color: #e09040; margin-left: auto; }

/* ===== Load more / 筛选 ===== */
.load-more-tip { grid-column: 1 / -1; display: flex; align-items: center; justify-content: center; gap: 8px; padding: 24px 0; color: var(--text-secondary); font-size: 14px; }
.load-more-tip.no-more { color: var(--text-muted); }

/* 移动端适配 */
@media (max-width: 1000px) {
  .cat-grid { grid-template-columns: repeat(2, 1fr); grid-template-rows: none; }
  .cat-banner { grid-row: auto; min-height: 220px; }
}

/* 中窄屏：环略缩小 + 右移余量有限，进一步收紧避免卡片扫到文案列 */
@media (max-width: 1400px) and (min-width: 1161px) {
  .hero {
    --orbit-radius: 230px;
    --orbit-card-w: 124px;
    --orbit-card-h: 142px;
  }
}

/* 窄屏轨道已隐藏，文案块保持垂直居中，仅收紧留白 */
@media (max-width: 1160px) {
  .hero-orbit { display: none; }
}
@media (max-width: 860px) {
  .hero {
    min-height: max(520px, calc(100vh - 64px));
    min-height: max(520px, calc(100dvh - 64px));
    padding: var(--space-3xl) 0;
  }
  .hero-ticker { display: none; }
}

/* 手机端（≤768px）：收紧留白，商品网格降为两列 */
@media (max-width: 768px) {
  .container { padding: 0 16px; }
  .hero-inner { padding: 0 16px; }
  .main-content { padding: 26px 16px 48px; }
  .hero-eyebrow { margin-bottom: 14px; }
  .hero-subtitle { font-size: 18px; margin-bottom: 22px; }
  .hero-desc { font-size: 15px; margin-bottom: 22px; }
  .hero-cta { height: 48px; padding: 0 30px; font-size: 16px; }
  .cat-section { margin-bottom: 30px; }
  .cat-header { flex-wrap: wrap; gap: 10px; }
  .cat-title { font-size: 20px; }
  /* 两列网格下大图横幅不再跨行（内联 style 的 gridRow 用 !important 压掉） */
  .cat-banner { grid-row: auto !important; min-height: 180px; }
  .cat-grid { gap: 10px; grid-auto-rows: auto; }
  .p-card { padding: 14px 10px 12px; }
  .p-card img { width: 100%; max-width: 150px; height: auto; aspect-ratio: 1; }
  .spu-grid { gap: 12px; grid-template-columns: repeat(2, 1fr); }
  .spu-card { border-radius: 16px; }
  .spu-info { padding: 12px 12px 14px; }
  .spu-name { font-size: 14px; min-height: auto; -webkit-line-clamp: 2; }
  .spu-price { font-size: 16px; }
  .add-cart-btn { transform: scale(0.92); transform-origin: right center; }
  .coupon-entry-inner { padding: 20px; gap: 14px; }
}

/* 小屏手机（≤480px）：进一步压缩字号与卡片内边距 */
@media (max-width: 480px) {
  .hero-eyebrow { font-size: 12px; padding: 5px 12px; }
  .hero-subtitle { font-size: 16px; }
  .hero-cta { height: 46px; padding: 0 26px; font-size: 15px; }
  .sub-tabs { gap: 14px; }
  .sub-tab { font-size: 13px; }
  .p-card img { max-width: 118px; }
  .banner-caption { font-size: 15px; padding: 12px 16px; }
}
</style>
