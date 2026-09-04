<template>
  <div class="home-page">
    <!-- Hero 区域 -->
    <section class="hero">
      <div class="container hero-inner">
        <h1 class="hero-title">星耀商城</h1>
        <p class="hero-subtitle">
          <span class="typewriter-text">{{ subtitleText }}</span>
          <span class="typewriter-cursor" :class="{ blink: showCursor }"></span>
        </p>
        <el-button type="primary" size="large" class="hero-cta" @click="scrollToContent">立即选购</el-button>
      </div>
    </section>

    <!-- 动态模块区域 -->
    <div ref="contentRef">
      <template v-if="!filterMode">
        <template v-for="mod in modules" :key="mod.id">
          <!-- 轮播图 -->
          <div v-if="mod.type === 'BANNER' && banners.length > 0" class="banner-wrap">
            <el-carousel :interval="4000" height="320px" indicator-position="outside">
              <el-carousel-item v-for="b in banners" :key="b.id">
                <a :href="b.linkUrl || 'javascript:;'" :target="b.linkUrl ? '_blank' : '_self'">
                  <img :src="b.imageUrl" :alt="b.title" loading="lazy" style="width:100%;height:100%;object-fit:cover;" />
                  <div class="banner-caption">{{ b.title }}</div>
                </a>
              </el-carousel-item>
            </el-carousel>
          </div>

          <!-- 分类展示 -->
          <template v-else-if="mod.type === 'CATEGORY_SHOWCASE'">
            <section v-if="mod._products.length > 0 || mod._loading" class="container main-content cat-section">
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
                  <img :src="mod._products[0].mainImage || __PH" :alt="mod._products[0].name" loading="lazy" onerror="this.src=window.__PH;this.onerror=null" />
                  <div class="banner-text">
                    <h3>{{ mod._products[0].name }}</h3>
                    <p>¥{{ Number(mod._products[0].minPrice || 0).toFixed(2) }} 起</p>
                  </div>
                </div>
                <div v-for="p in mod._products.slice(1, 8)" :key="p.id" class="p-card" @click="goDetail(p.id)">
                  <img :src="p.mainImage || __PH" :alt="p.name" loading="lazy" onerror="this.src=window.__PH;this.onerror=null" />
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

          <!-- 热门推荐 / 新品上市 / 精选商品 -->
          <template v-else-if="mod.type === 'HOT_PRODUCTS' || mod.type === 'NEW_PRODUCTS' || mod.type === 'PRODUCT_GRID'">
            <section v-if="mod._products.length > 0 || mod._loading" class="container main-content product-section">
              <div class="cat-header">
                <div class="cat-title">{{ mod.title }}</div>
                <a class="view-more" @click="openShopSearch()">查看更多 <svg class="arrow-icon" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/></svg></a>
              </div>
              <div class="spu-grid" v-loading="mod._loading">
                <div v-for="spu in mod._products" :key="spu.id" class="spu-card" @click="goDetail(spu.id)">
                  <div class="spu-image">
                    <img :src="spu.mainImage || __PH" :alt="spu.name" loading="lazy" onerror="this.src=window.__PH;this.onerror=null" />
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
            <section v-for="sec in sections" :key="sec.id" class="cat-section" v-show="sec.loading || sec.products.length > 0">
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
                  <img :src="sec.products[0].mainImage || __PH" :alt="sec.products[0].name" loading="lazy" onerror="this.src=window.__PH;this.onerror=null" />
                  <div class="banner-text"><h3>{{ sec.products[0].name }}</h3><p>¥{{ Number(sec.products[0].minPrice || 0).toFixed(2) }} 起</p></div>
                </div>
                <div v-for="p in sec.products.slice(1, 8)" :key="p.id" class="p-card" @click="goDetail(p.id)">
                  <img :src="p.mainImage || __PH" :alt="p.name" loading="lazy" onerror="this.src=window.__PH;this.onerror=null" />
                  <div class="p-name">{{ p.name }}</div><div class="p-desc">已售 {{ p.saleCount || 0 }} · 评论 {{ p.commentCount || 0 }}</div>
                  <div class="p-price"><span class="cur">¥{{ Number(p.minPrice || 0).toFixed(2) }}</span></div>
                </div>
                <div class="more-card" @click="viewMore(sec)" v-if="sec.total > 8">
                  <div class="more-inner"><div class="more-txt">浏览更多</div><div class="more-sub">{{ activeSubName(sec) }}</div><svg class="more-arrow" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><polyline points="9 18 15 12 9 6"></polyline></svg></div>
                </div>
              </div>
            </section>
            <el-empty v-if="!sectionsLoading && sections.every((s: any) => s.products.length === 0)" class="glass-empty" description="暂无商品" />
          </main>
        </template>
      </template>

      <!-- 筛选模式：平铺网格 -->
      <div v-if="filterMode" class="spu-grid container main-content" v-loading="loading" v-infinite-scroll="loadMore" :infinite-scroll-disabled="!hasMore || loading || loadingMore" :infinite-scroll-distance="120" :infinite-scroll-immediate="false">
        <div v-for="spu in spus" :key="spu.id" class="spu-card" @click="goDetail(spu.id)">
          <div class="spu-image">
            <img :src="spu.mainImage || __PH" :alt="spu.name" loading="lazy" onerror="this.src=window.__PH;this.onerror=null" />
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

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'
import { listSpu, listCategory, addFavorite, removeFavorite, batchCheckFavorites, getSpu, addCart } from '@/api/mall'
import { userRequest } from '@/api/request'
import { track } from '@/utils/tracker'
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
const fullSubtitle = '精选好物，品质生活'
let typewriterTimer: any = null

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
 * 并发加载所有模块的商品数据，并批量检查收藏状态
 */
async function loadAllModuleProducts() {
  await Promise.all(modules.value.map((m: any) => loadModuleProducts(m)))
  // 批量检查收藏
  const allIds = modules.value.flatMap((m: any) => m._products.map((p: any) => p.id))
  if (allIds.length > 0 && userStore.token) {
    try {
      const res = await batchCheckFavorites(allIds)
      if (Array.isArray(res)) res.forEach((id: any) => favSet.add(id))
    } catch (e: any) {}
  }
  // 埋点：首页曝光（去重后上报，extra 带 spuIds 供后续商品热度分析）
  const uniqueIds = Array.from(new Set(allIds))
  if (uniqueIds.length) {
    track('view_item_list', { scene: 'home', extra: { spuIds: uniqueIds.slice(0, 50) } })
  }
}

// ========== 回退：分类分区 ==========
const sections = ref<any[]>([])
const sectionsLoading = ref(false)

/**
 * 根据分类树构建回退分区列表，每个分类对应一个展示区域
 * @param {Array} tree - 分类树
 */
function buildSections(tree: any) {
  sections.value = (tree || []).map((c: any) => ({
    id: c.id, name: c.name,
    activeSubId: null, products: [], total: 0, loading: false
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
  } catch (e: any) { sec.products = [] } finally { sec.loading = false }
}

/**
 * 并发加载所有分类分区的商品数据
 */
async function loadAllSections() {
  sectionsLoading.value = true
  try { await Promise.all(sections.value.map((s: any) => loadSectionProducts(s))) } finally { sectionsLoading.value = false }
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
onMounted(async () => {
  startTypewriter()
  await loadBanners()
  await loadCategory()
  await loadModules()

  if (modules.value.length > 0) {
    await loadAllModuleProducts()
  } else {
    await loadAllSections()
  }

  if (userStore.token && !userStore.nickname) {
    try { await userStore.fetchProfile() } catch (e: any) {}
  }
  try { await cartStore.load() } catch (e: any) {}
})

onUnmounted(() => {
  clearInterval(typewriterTimer)
})
</script>

<style scoped>
.home-page {
  min-height: 100vh;
}
.container { max-width: 1200px; margin: 0 auto; padding: 0 24px; }

.hero {
  background: transparent;
  text-align: center;
  padding: var(--space-3xl) 20px;
}
.hero-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 16px;
  height: 204px;
}
.hero-title { margin: 0 0 18px; line-height: 1.2; }
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

/* Banner */
.banner, .banner-wrap { background: var(--bg-surface); }
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
  transition: color .2s, border-color .2s; user-select: none;
}
.sub-tab:hover { color: var(--brand-primary); }
.sub-tab.active { color: var(--brand-primary); border-bottom-color: var(--brand-primary); font-weight: 600; }
.view-more {
  font-size: 13px; color: var(--brand-primary); cursor: pointer;
  display: inline-flex; align-items: center; gap: 4px;
  padding: 6px 14px; border-radius: 20px;
  background: var(--brand-primary-soft);
  transition: background .25s, color .25s, box-shadow .25s;
}
.view-more:hover {
  background: var(--brand-primary);
  color: #fff;
  box-shadow: 0 2px 10px rgba(64, 158, 255, 0.35);
}
.view-more .arrow-icon {
  transition: transform .25s;
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
.cat-banner img { width: 100%; height: 100%; object-fit: cover; display: block; transition: transform .4s; }
.cat-banner:hover img { transform: scale(1.04); }
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
  transition: transform .25s, box-shadow .25s; overflow: hidden;
}
.p-card:hover { transform: translateY(-4px); box-shadow: var(--shadow-md), inset 0 1px 0 var(--glass-highlight); }
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
  cursor: pointer; transition: all .25s;
}
.more-card:hover { transform: translateY(-4px); box-shadow: var(--shadow-md), inset 0 1px 0 var(--glass-highlight); border-color: var(--brand-primary); }
.more-inner { display: flex; flex-direction: column; align-items: center; gap: 6px; }
.more-txt { font-size: 16px; font-weight: 600; color: var(--text-secondary); transition: color .2s; }
.more-sub { font-size: 12px; color: var(--text-muted); }
.more-arrow { margin-top: 2px; color: var(--brand-primary); transition: transform .2s; }
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
}
.spu-card:hover { transform: var(--hover-lift); box-shadow: var(--shadow-md), inset 0 1px 0 var(--glass-highlight); border-color: var(--brand-primary-border); }
.spu-image { aspect-ratio: 1; overflow: hidden; background: var(--bg-hover); position: relative; }
.spu-image img { width: 100%; height: 100%; object-fit: cover; transition: transform 0.4s ease; }
.spu-card:hover .spu-image img { transform: scale(1.05); }
.fav-btn {
  position: absolute; top: 12px; right: 12px; width: 34px; height: 34px;
  border-radius: 50%; border: none;
  background: rgba(0,0,0,0.35); backdrop-filter: blur(8px);
  color: rgba(255,255,255,0.9); font-size: 18px;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  transition: all 0.2s; line-height: 1; padding: 0;
}
.fav-btn:hover { background: rgba(0,0,0,0.55); transform: scale(1.15); }
.fav-btn.active { color: var(--status-danger); background: rgba(255,59,48,0.18); }
.spu-info { padding: 18px 20px 22px; }
.spu-name { font-size: 15px; color: var(--text-primary); margin: 0 0 10px; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; min-height: 45px; }
.spu-bottom { display: flex; justify-content: space-between; align-items: center; }
.spu-meta { font-size: 12px; color: var(--text-muted); margin: -2px 0 8px; }
.spu-price { color: var(--text-primary); font-size: 18px; font-weight: 700; }
.add-cart-btn { border-radius: 8px; }

/* ===== 领券入口 ===== */
.coupon-entry-card {
  background: linear-gradient(135deg, #fff8f0, #fff0e0);
  border-radius: var(--radius-lg); cursor: pointer;
  transition: all .25s; border: 1px solid #ffe0c0;
}
.coupon-entry-card:hover { transform: translateY(-2px); box-shadow: 0 8px 24px rgba(255,140,0,0.15); }
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
</style>
