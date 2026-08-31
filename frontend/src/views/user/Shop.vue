<template>
  <div class="shop-page">
    <!-- 顶部大搜索框（分类页隐藏，带搜索历史） -->
    <div class="shop-search-bar" v-if="!isCategoryPage">
      <div class="shop-search-wrap">
        <div class="shop-search-pill">
          <el-icon :size="18" class="search-prefix-icon"><Search /></el-icon>
          <input
            v-model="searchKeyword"
            type="text"
            class="search-core-input"
            placeholder="搜索商品..."
            @keyup.enter="doShopSearch"
            @focus="historyOpen = true"
            @blur="closeHistory"
          />
          <button
            v-if="searchKeyword"
            class="search-clear-btn"
            @click="searchKeyword = ''"
            title="清除"
          >
            <el-icon :size="14"><Close /></el-icon>
          </button>
          <button
            class="search-submit-btn"
            :disabled="!searchKeyword.trim()"
            @click="doShopSearch"
          >
            <el-icon :size="16"><Search /></el-icon>
            <span>搜索</span>
          </button>
        </div>
        <!-- 搜索历史下拉（与主页搜索框共享同一份历史数据） -->
        <div class="history-panel" v-if="historyOpen && searchHistory.length > 0">
          <div class="history-panel-head">搜索历史</div>
          <ul class="history-list">
            <li
              v-for="h in searchHistory"
              :key="h"
              class="history-item"
              @mousedown.prevent="pickHistory(h)"
            >
              <el-icon :size="14" class="history-clock"><Clock /></el-icon>
              <span class="history-text">{{ h }}</span>
              <span
                class="history-del"
                title="删除"
                @mousedown.stop.prevent="removeHistoryItem(h)"
              >
                <el-icon :size="12"><Close /></el-icon>
              </span>
            </li>
          </ul>
        </div>
      </div>
    </div>

    <main class="container">
      <div class="shop-layout">
        <!-- 侧栏：聚合筛选 -->
        <aside class="filter-sidebar">
          <h3 class="sidebar-title">筛选</h3>
          <!-- 排序 -->
          <div class="filter-section">
            <h3 class="filter-title">排序</h3>
            <ul class="filter-list">
              <li class="filter-item"
                :class="{ active: activeSort === 'default' }"
                @click="setSort('default')">
                <span class="filter-label">综合</span>
              </li>
              <li class="filter-item"
                :class="{ active: activeSort === 'price_asc' }"
                @click="setSort('price_asc')">
                <span class="filter-label">价格从低到高</span>
              </li>
              <li class="filter-item"
                :class="{ active: activeSort === 'price_desc' }"
                @click="setSort('price_desc')">
                <span class="filter-label">价格从高到低</span>
              </li>
              <li class="filter-item"
                :class="{ active: activeSort === 'sales_desc' }"
                @click="setSort('sales_desc')">
                <span class="filter-label">销量优先</span>
              </li>
              <li class="filter-item"
                :class="{ active: activeSort === 'comments_desc' }"
                @click="setSort('comments_desc')">
                <span class="filter-label">评论优先</span>
              </li>
            </ul>
          </div>

          <!-- 分类 -->
          <div class="filter-section" v-if="allCategories.length > 0">
            <h3 class="filter-title">商品分类</h3>
            <ul class="filter-list">
              <li v-for="c in displayCategories" :key="c.key"
                class="filter-item"
                :class="{ active: activeCategoryId === c.key }"
                @click="toggleCategoryFilter(c.key)">
                <span class="filter-label">{{ c.name }}</span>
              </li>
            </ul>
          </div>

          <div class="filter-empty" v-if="!hasActiveFilters && allCategories.length === 0">
            <span class="empty-text">暂无筛选条件</span>
          </div>
        </aside>

        <!-- 主内容：结果列表 -->
        <div class="main-content">
          <!-- 结果统计 + 列数切换 -->
          <div class="result-bar">
            <span class="result-count" v-if="!loading">
              共 <strong>{{ total }}</strong> 件商品
              <span v-if="keyword"> &mdash; <em class='hl'>{{ keyword }}</em></span>
              <span v-else-if="categoryName"> &mdash; {{ categoryName }}</span>
            </span>
            <span v-if="hasActiveFilters" class="active-filters-hint">
              (筛选已生效，{{ total }} 件)
            </span>
            <div class="col-toggle">
              <span class="control-label">每行显示</span>
              <el-radio-group v-model="columns" size="small" @change="setColumns">
                <el-radio-button :value="2">2</el-radio-button>
                <el-radio-button :value="3">3</el-radio-button>
                <el-radio-button :value="4">4</el-radio-button>
                <el-radio-button :value="5">5</el-radio-button>
              </el-radio-group>
            </div>
          </div>

          <!-- 商品网格 -->
          <div class="spu-grid"
            :style="{ gridTemplateColumns: `repeat(${columns}, 1fr)` }"
            v-infinite-scroll="loadMore"
            :infinite-scroll-disabled="!hasMore || loading || loadingMore"
            :infinite-scroll-distance="120"
            :infinite-scroll-immediate="false">
            <!-- 骨架屏：首次加载时显示 -->
            <div v-if="loading && spus.length === 0" v-for="i in columns * 2" :key="'skeleton-'+i" class="spu-card spu-card--skeleton">
              <div class="spu-image skeleton-box"></div>
              <div class="spu-info">
                <div class="skeleton-line skeleton-line--long"></div>
                <div class="skeleton-line skeleton-line--short"></div>
                <div class="spu-bottom">
                  <div class="skeleton-line skeleton-line--price"></div>
                  <div class="skeleton-line skeleton-line--btn"></div>
                </div>
              </div>
            </div>
            <div v-for="spu in spus" :key="spu.id" class="spu-card" @click="goDetail(spu.id)">
              <div class="spu-image">
                <img :src="spu.mainImage || __PH" :alt="spu.name" loading="lazy"
                  onerror="this.src=window.__PH;this.onerror=null" />
                <button class="fav-btn" :class="{ active: favSet.has(spu.id) }" @click.stop="toggleFav(spu)"
                  :title="favSet.has(spu.id) ? '取消收藏' : '添加收藏'">
                  {{ favSet.has(spu.id) ? '♥' : '♡' }}
                </button>
              </div>
              <div class="spu-info">
                <h3 class="spu-name" v-html="highlightedName(spu.id, spu.name)"></h3>
                <div class="spu-meta">已售 {{ spu.saleCount || 0 }} · 评论 {{ spu.commentCount || 0 }}</div>
                <div class="spu-bottom">
                  <div class="spu-price">¥{{ Number(spu.minPrice || 0).toFixed(2) }}</div>
                  <el-button size="small" type="primary" class="add-cart-btn" @click.stop="handleAddToCart(spu)">
                    加入购物车
                  </el-button>
                </div>
              </div>
            </div>
            <el-empty v-if="!loading && spus.length === 0" description="暂无商品，试试其他关键词吧" />
            <div v-if="loadingMore" class="load-more-tip">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>加载更多商品中...</span>
            </div>
            <div v-else-if="!hasMore && spus.length > 0" class="load-more-tip no-more">已经到底啦</div>
          </div>
        </div>
      </div>
    </main>

    <!-- 规格选择弹窗（append-to-body：遮罩脱离 .shop-page 的 transform 层，保持视口定位）。
         选型号界面统一复用详情页的 SkuSpecSelector：规格分组按钮 + 数量控件。 -->
    <el-dialog v-model="skuDialogVisible" :title="`选择规格 - ${currentSpu?.name || ''}`" width="520px" destroy-on-close append-to-body>
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
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Loading, Search, Close, Clock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'
import { listSpu, listCategory, addFavorite, removeFavorite, batchCheckFavorites, getSpu, addCart } from '@/api/mall'
import { ElMessage } from 'element-plus'
import { useSearchHistory } from '@/composables/useSearchHistory'
import SkuSpecSelector from '@/components/SkuSpecSelector.vue'

const __PH = window.__PH
const route = useRoute()
const router = useRouter()

// 是否分类页（URL 带 categoryId 时隐藏顶部搜索框）
const isCategoryPage = computed(() => !!route.query.categoryId)
const userStore = useUserStore()
const cartStore = useCartStore()

const columns = ref(Number(localStorage.getItem('stellar_shop_cols')) || 4)
function setColumns(n: any) {
  localStorage.setItem('stellar_shop_cols', String(n))
}

const loading = ref(false)
const loadingMore = ref(false)
const keyword = ref('')
const searchKeyword = ref('')  // 顶部大搜索框的输入绑定
const historyOpen = ref(false) // 搜索历史下拉是否展开
// 搜索历史（与导航栏/主页搜索框共享同一份数据，按用户隔离）
const { searchHistory, addToHistory, removeHistory } = useSearchHistory()
const categoryId = ref<any>(null)
const categoryName = ref('')
const spus = ref<any[]>([])
const favSet = reactive(new Set())
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const hasMore = computed(() => spus.value.length < total.value)

// 高亮映射
const highlights = ref<any>({})

// 聚合数据
const aggCategories = ref<any[]>([])
const aggPriceRanges = ref<any[]>([])
const initialAggCategories = ref<any[]>([]) // 保存初始（无筛选）聚合，用于始终显示全部分类数量
const activeCategoryId = ref<any>(null)
// 自定义价格筛选
const customPriceFrom = ref('')
const customPriceTo = ref('')

// 排序
const activeSort = ref('default') // 'default' | 'price_asc' | 'price_desc' | 'sales_desc' | 'comments_desc'

// 所有分类（用于名称映射）
const allCategories = ref<any[]>([])
const categoryMap = reactive<any>({})

const hasActiveFilters = computed(() => !!activeCategoryId.value || customPriceFrom.value !== '' || customPriceTo.value !== '' || activeSort.value !== 'default')

// 展示所有分类，合并聚合中的商品数量
// 使用 initialAggCategories（初始无筛选聚合）确保数量始终显示全量，不受后续筛选影响
const displayCategories = computed(() => {
  const aggMap: Record<string, any> = {}
  const source = initialAggCategories.value.length > 0 ? initialAggCategories.value : aggCategories.value
  source.forEach((a: any) => { aggMap[Number(a.key)] = a.docCount })
  return allCategories.value.map((c: any) => ({
    key: c.id,
    name: c.name,
    docCount: aggMap[c.id] || 0
  }))
})

// --- 规格弹窗（选型号统一复用详情页 SkuSpecSelector：仅规格 + 数量，不含保障服务） ---
const skuDialogVisible = ref(false)
const currentSpu = ref<any>(null)
const currentSkus = ref<any[]>([])
const cartQty = ref(1)          // 数量，由 SkuSpecSelector 通过 v-model 同步
const addingCart = ref(false)
const selectedSku = ref<any>(null)   // 由 SkuSpecSelector 的 sku-change 事件填充
function onSkuChange(sku: any) { selectedSku.value = sku }

// --- 高亮渲染 ---
/**
 * 渲染商品名称，优先使用搜索高亮片段，否则返回 HTML 转义后的原始名称
 * @param {number|string} spuId - 商品 SPU ID
 * @param {string} fallback - 无高亮时的回退文本
 * @returns {string} 高亮 HTML 或转义后的名称
 */
function highlightedName(spuId: any, fallback: any) {
  const hl = highlights.value[String(spuId)]
  if (hl && hl.length > 0) return hl[0]
  // 用内置 HTML 转义后的 fallback
  return escapeHtml(String(fallback || ''))
}
/**
 * HTML 特殊字符转义，防止 XSS
 * @param {string} str - 原始字符串
 * @returns {string} 转义后的字符串
 */
function escapeHtml(str: any) {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;')
}

// --- 搜索历史交互 ---
/**
 * 关闭搜索历史面板（延迟执行，避免点击面板项时先触发 blur 导致无法选中）
 */
function closeHistory() {
  setTimeout(() => { historyOpen.value = false }, 120)
}

/**
 * 选中一条搜索历史：填入输入框并立即搜索
 * @param {string} kw - 历史关键词
 */
function pickHistory(kw: any) {
  searchKeyword.value = kw
  historyOpen.value = false
  doShopSearch()
}

/**
 * 删除一条搜索历史（确认弹窗在 composable 内），删除后为空则收起面板
 * @param {string} kw - 要删除的关键词
 */
async function removeHistoryItem(kw: any) {
  const ok = await removeHistory(kw)
  if (ok && searchHistory.value.length === 0) historyOpen.value = false
}

// --- 聚合交互 ---
/**
 * 执行商城搜索，更新 URL 并重置筛选条件后重新加载商品列表
 */
function doShopSearch() {
  const kw = searchKeyword.value.trim()
  if (!kw) return
  addToHistory(kw)
  historyOpen.value = false
  keyword.value = kw
  // 更新 URL 但不跳转新页面
  router.replace({ query: { ...route.query, keyword: kw } })
  // 重置并重新加载
  activeCategoryId.value = null
  customPriceFrom.value = ''
  customPriceTo.value = ''
  activeSort.value = 'default'
  initialAggCategories.value = []
  aggCategories.value = []
  page.value = 1; spus.value = []
  loadSpu()
}

/**
 * 应用自定义价格区间筛选，校验最低价不能高于最高价
 */
function applyPriceFilter() {
  // 校验：最低价不能大于最高价
  const from = Number(customPriceFrom.value)
  const to = Number(customPriceTo.value)
  if (customPriceFrom.value !== '' && customPriceTo.value !== '' && from > to) {
    ElMessage.warning('最低价不能高于最高价')
    return
  }
  page.value = 1; spus.value = []
  loadSpu()
}

/**
 * 切换排序方式，再次点击同一排序则恢复默认排序
 * @param {string} sortKey - 排序键值
 */
function setSort(sortKey: any) {
  if (activeSort.value === sortKey) {
    activeSort.value = 'default'
  } else {
    activeSort.value = sortKey
  }
  page.value = 1; spus.value = []
  loadSpu()
}

/**
 * 切换分类筛选，再次点击同一分类则取消筛选
 * @param {number|string} catId - 分类 ID
 */
function toggleCategoryFilter(catId: any) {
  if (activeCategoryId.value === catId) {
    activeCategoryId.value = null
    router.replace({ query: { ...route.query, categoryId: undefined } })
  } else {
    activeCategoryId.value = catId
    router.replace({ query: { ...route.query, categoryId: catId } })
  }
  page.value = 1; spus.value = []
  loadSpu()
}

/**
 * 清除所有筛选条件（分类、价格、排序）并重新加载
 */
function clearAllFilters() {
  activeCategoryId.value = null
  customPriceFrom.value = ''
  customPriceTo.value = ''
  activeSort.value = 'default'
  initialAggCategories.value = []
  aggCategories.value = []
  page.value = 1; spus.value = []
  router.replace({ query: { ...route.query, categoryId: undefined } })
  loadSpu()
}

function categoryNameById(id: any) {
  return categoryMap[id] || null
}

/**
 * 根据 URL 中的 categoryId 刷新分类上下文：分类名 + 浏览器标签标题
 * 分类页标签显示分类名，避免与搜索页的“商品搜索”重复
 */
function refreshCategoryContext() {
  const cat = allCategories.value.find((c: any) => c.id == route.query.categoryId)
  categoryName.value = cat ? cat.name : ''
  document.title = cat
    ? `${cat.name} - 星耀商城`
    : '商品搜索 - 星耀商城'
}

// URL 中 categoryId 变化时（进入分类页 / 侧栏切换分类 / 清除分类）更新分类上下文
watch(
  () => route.query.categoryId,
  () => { refreshCategoryContext() }
)

// --- 价格区间 → 请求参数 ---
/**
 * 将自定义价格区间转换为请求参数
 * @returns {Object} 包含 priceFrom / priceTo 的参数对象
 */
function priceFilterParams() {
  const params: Record<string, any> = {}
  if (customPriceFrom.value !== '') {
    const v = Number(customPriceFrom.value)
    if (!Number.isNaN(v) && v >= 0) params.priceFrom = v
  }
  if (customPriceTo.value !== '') {
    const v = Number(customPriceTo.value)
    if (!Number.isNaN(v) && v >= 0) params.priceTo = v
  }
  return params
}

// --- 排序 → 请求参数 ---
/**
 * 将当前排序方式转换为请求参数
 * @returns {Object} 包含 sortBy / sortOrder 的参数对象
 */
function sortParams() {
  switch (activeSort.value) {
    case 'price_asc':  return { sortBy: 'minPrice', sortOrder: 'asc' }
    case 'price_desc': return { sortBy: 'minPrice', sortOrder: 'desc' }
    case 'sales_desc': return { sortBy: 'saleCount', sortOrder: 'desc' }
    case 'comments_desc': return { sortBy: 'commentCount', sortOrder: 'desc' }
    default:           return {}
  }
}

// --- 数据加载 ---
/**
 * 加载商品列表，支持追加模式（滚动加载更多）和首次加载模式
 * @param {boolean} [append=false] - 是否为追加模式
 */
async function loadSpu(append = false) {
  if (append) {
    loadingMore.value = true
  } else {
    loading.value = true
    page.value = 1
    spus.value = []
    total.value = 0
    favSet.clear()
    highlights.value = {}
    // 聚合数据不重置，保持初始全量数据
    aggPriceRanges.value = []
  }
  try {
    const params: Record<string, any> = { page: page.value, pageSize: pageSize.value }
    if (keyword.value) params.name = keyword.value
    if (categoryId.value) params.categoryId = categoryId.value

    // 聚合筛选参数
    if (activeCategoryId.value) params.categoryId = Number(activeCategoryId.value)
    const pf = priceFilterParams()
    if (pf.priceFrom !== undefined) params.priceFrom = pf.priceFrom
    if (pf.priceTo !== undefined) params.priceTo = pf.priceTo
    // 排序参数
    const sp = sortParams()
    if (sp.sortBy) params.sortBy = sp.sortBy
    if (sp.sortOrder) params.sortOrder = sp.sortOrder

    const res: any = await listSpu(params)
    if (res && typeof res === 'object') {
      total.value = res.total || 0
      const records = Array.isArray(res.records) ? res.records
        : Array.isArray(res.list) ? res.list
        : Array.isArray(res) ? res : []
      if (append) {
        spus.value.push(...records)
        if (records.length) await batchCheckFavs(records)
      } else {
        spus.value = [...records]
        // 高亮
        if (res.highlights) {
          highlights.value = res.highlights
        }
        // 聚合：首次加载时保存全量聚合数据，后续不再更新
        const isFirstLoad = initialAggCategories.value.length === 0 && aggCategories.value.length === 0
        console.log('[Shop] aggregations:', JSON.stringify(res.aggregations), 'isFirstLoad:', isFirstLoad)
        if (res.aggregations && !append && isFirstLoad) {
          if (res.aggregations.categories && res.aggregations.categories.length > 0) {
            aggCategories.value = res.aggregations.categories
            initialAggCategories.value = res.aggregations.categories
            console.log('[Shop] categories saved:', res.aggregations.categories.length, 'buckets')
          } else {
            console.warn('[Shop] categories is null/empty in response')
          }
          if (res.aggregations.priceRanges && res.aggregations.priceRanges.length > 0) {
            aggPriceRanges.value = res.aggregations.priceRanges
          }
        }
        await batchCheckFavs()
      }
    }
  } catch (e: any) {
    console.error('loadSpu error:', e)
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

/**
 * 滚动加载更多商品，页码递增后调用 loadSpu 追加模式
 */
async function loadMore() {
  if (loading.value || loadingMore.value || !hasMore.value) return
  page.value += 1
  await loadSpu(true)
}

/**
 * 批量检查商品收藏状态，仅登录用户生效
 * @param {Array} [items] - 商品列表，默认使用当前 spus
 */
async function batchCheckFavs(items?: any[]) {
  const target = items || spus.value
  if (!userStore.token || target.length === 0) return
  try {
    const ids = target.map((s: any) => s.id)
    const res = await batchCheckFavorites(ids)
    if (Array.isArray(res)) res.forEach((id: any) => favSet.add(id))
  } catch (e: any) { }
}

/**
 * 切换商品收藏状态，未登录时跳转登录页
 * @param {Object} spu - 商品对象
 */
async function toggleFav(spu: any) {
  if (!userStore.token) { ElMessage.warning('请先登录'); router.push('/login'); return }
  try {
    if (favSet.has(spu.id)) {
      await removeFavorite(spu.id)
      favSet.delete(spu.id)
      ElMessage.success('已取消收藏')
    } else {
      await addFavorite(spu.id)
      favSet.add(spu.id)
      ElMessage.success('已添加收藏')
    }
  } catch (e: any) { ElMessage.error('操作失败') }
}

function goDetail(id: any) {
  const r = router.resolve(`/spu/${id}`)
  window.open(r.href, '_blank')
}

/**
 * 处理加入购物车：获取商品 SKU 列表，单个 SKU 直接加入，多个 SKU 弹出规格选择弹窗
 * @param {Object} spu - 商品对象
 */
async function handleAddToCart(spu: any) {
  if (!userStore.token) { ElMessage.warning('请先登录'); router.push('/login'); return }
  try {
    const res = await getSpu(spu.id)
    const detail: any = res || {}
    const skuList = detail.skuList || detail.skuListVo || []
    if (!skuList.length) { ElMessage.warning('该商品暂无规格可选'); return }
    if (skuList.length === 1) { await doAddCart(skuList[0].id); return }
    currentSpu.value = detail
    currentSkus.value = skuList.filter((s: any) => s.status !== 0)
    cartQty.value = 1
    selectedSku.value = null
    skuDialogVisible.value = true
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '加载商品规格失败')
  }
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
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '加入购物车失败')
  } finally { addingCart.value = false }
}

function confirmAddToCart() { doAddCart(selectedSku.value?.id, cartQty.value) }

// --- 初始化 ---
onMounted(async () => {
  const q = route.query.keyword
  if (q) {
    keyword.value = q as string
    searchKeyword.value = q as string      // 顶部搜索框预填
  }
  categoryId.value = route.query.categoryId || null

  // 加载分类列表（用于聚合面板名称映射）
  try {
    const cats = await listCategory()
    const flat: any[] = []
    function walk(list: any) {
      if (!list) return
      for (const c of list) {
        flat.push(c)
        walk(c.children)
      }
    }
    walk(cats || [])
    allCategories.value = flat
    flat.forEach((c: any) => { categoryMap[c.id] = c.name })
    refreshCategoryContext()
  } catch (e: any) { }

  if (userStore.token && !userStore.nickname) { try { await userStore.fetchProfile() } catch (e: any) { } }
  try { await cartStore.load() } catch (e: any) { }
  await loadSpu()
})

</script>

<style scoped>
.shop-page { min-height: 100vh; padding-top: 48px; }
/* 强制搜索页内容常驻 GPU 合成层：弹窗遮罩移除时内容层不会短暂丢失（约 4 帧闪烁） */
.shop-page {
  transform: translateZ(0);
}

/* ===== 顶部大搜索框 ===== */
.shop-search-bar {
  display: flex;
  justify-content: center;
  padding: 0 24px 32px;
}
.shop-search-wrap {
  position: relative;
  width: 100%;
  max-width: 560px;
}
/* 搜索历史下拉 */
.history-panel {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  background: var(--bg-card, #fff);
  border: 1px solid var(--border-base, #e4e7ed);
  border-radius: 12px;
  box-shadow: 0 6px 24px rgba(0,0,0,0.10);
  padding: 6px 0 8px;
  z-index: 20;
}
.history-panel-head {
  padding: 6px 16px 4px;
  font-size: 12px;
  color: var(--text-muted);
}
.history-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 280px;
  overflow-y: auto;
}
.history-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-primary);
  transition: background 0.15s;
}
.history-item:hover {
  background: var(--bg-hover);
}
.history-clock {
  flex-shrink: 0;
  color: var(--text-muted);
}
.history-text {
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
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.15s;
  opacity: 0;
}
.history-item:hover .history-del {
  opacity: 1;
}
.history-del:hover {
  background: var(--bg-hover);
  color: var(--status-danger, #F56C6C);
}
.shop-search-pill {
  display: flex;
  align-items: center;
  width: 100%;
  max-width: 560px;
  height: 48px;
  background: var(--bg-card, #fff);
  border: 1px solid var(--border-base, #e4e7ed);
  border-radius: 24px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.06);
  transition: border-color 0.2s, box-shadow 0.2s;
  overflow: hidden;
}
.shop-search-pill:focus-within {
  border-color: var(--brand-primary, #409EFF);
  box-shadow: 0 2px 16px rgba(64,158,255,0.15);
}
.search-prefix-icon {
  flex-shrink: 0;
  margin-left: 16px;
  color: var(--text-muted, #999);
}
.search-core-input {
  flex: 1;
  min-width: 0;
  height: 100%;
  border: none;
  outline: none;
  background: transparent;
  font-size: 15px;
  color: var(--text-primary, #333);
  padding: 0 12px;
}
.search-core-input:focus {
  outline: none;
  box-shadow: none;
}
.search-core-input::placeholder {
  color: var(--text-muted, #bbb);
}
.search-clear-btn {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  margin-right: 4px;
  border: none;
  border-radius: 50%;
  background: var(--bg-hover, #e8e8e8);
  color: var(--text-secondary, #888);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
  padding: 0;
}
.search-clear-btn:hover {
  background: var(--text-muted, #bbb);
  color: #fff;
}
.search-submit-btn {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 4px;
  height: 36px;
  margin-right: 6px;
  padding: 0 16px;
  border: none;
  border-radius: 20px;
  background: var(--brand-primary, #409EFF);
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s, opacity 0.2s;
}
.search-submit-btn:hover:not(:disabled) {
  background: var(--brand-primary-hover, #337ecc);
}
.search-submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.container { max-width: 1200px; margin: 0 auto; padding: 0 24px 80px; }

/* ===== 两栏布局 ===== */
.shop-layout {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

/* ===== 侧栏 ===== */
.filter-sidebar {
  width: 170px;
  flex-shrink: 0;
  position: sticky;
  top: 20px;
}
.sidebar-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 18px;
}
.filter-section {
  margin-bottom: 24px;
}
.filter-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 10px;
}
.filter-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.filter-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 8px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-secondary);
  transition: all 0.15s;
  margin-bottom: 2px;
}
.filter-item:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}
.filter-item.active {
  background: var(--brand-primary-soft, #e6f0ff);
  color: var(--brand-primary, #409EFF);
  font-weight: 500;
}
.filter-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.filter-count {
  font-size: 11px;
  color: var(--text-muted);
  margin-left: 6px;
}
.filter-empty {
  margin-top: 8px;
}
.empty-text {
  font-size: 12px;
  color: var(--text-muted);
}
/* 自定义价格输入 */
.price-input-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.price-input {
  flex: 1;
  min-width: 0;
}
.price-separator {
  font-size: 13px;
  color: var(--text-muted);
  flex-shrink: 0;
}

/* ===== 主内容区 ===== */
.main-content {
  flex: 1;
  min-width: 0;
}

.result-bar {
  margin-bottom: 24px;
  padding: 0 4px;
  font-size: 15px;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.result-count { flex: 1; }
.result-bar strong { color: var(--text-primary); font-size: 18px; }
.active-filters-hint {
  color: var(--brand-primary, #409EFF);
  font-size: 12px;
  margin-left: 8px;
}

.col-toggle {
  display: flex;
  align-items: center;
  gap: 10px;
}
.control-label { font-size: 13px; color: var(--text-secondary); }

.spu-grid {
  display: grid;
  gap: 18px;
}
.spu-card {
  background: var(--bg-card); border-radius: var(--radius-lg); overflow: hidden;
  cursor: pointer; border: 1px solid var(--border-base);
  transition: transform var(--transition-base), box-shadow var(--transition-base), border-color var(--transition-base);
}
.spu-card:hover { transform: var(--hover-lift); box-shadow: var(--shadow-md); border-color: var(--brand-primary-border); }
.spu-image { aspect-ratio: 1; overflow: hidden; background: var(--bg-hover); position: relative; }
.spu-image img { width: 100%; height: 100%; object-fit: cover; transition: transform 0.4s ease; }
.spu-card:hover .spu-image img { transform: scale(1.05); }
.fav-btn {
  position: absolute; top: 12px; right: 12px; width: 34px; height: 34px; border-radius: 50%;
  border: none; background: rgba(0,0,0,0.35); backdrop-filter: blur(8px); color: rgba(255,255,255,0.9);
  font-size: 18px; cursor: pointer; display: flex; align-items: center; justify-content: center;
  transition: all 0.2s; line-height: 1; padding: 0;
}
.fav-btn:hover { background: rgba(0,0,0,0.55); transform: scale(1.15); }
.fav-btn.active { color: var(--status-danger); background: rgba(255,59,48,0.18); }
.spu-info { padding: 18px 20px 22px; }
.spu-name {
  font-size: 15px; color: var(--text-primary); margin: 0 0 10px; line-height: 1.5;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; min-height: 45px;
}
.spu-bottom { display: flex; justify-content: space-between; align-items: center; }
.spu-meta { font-size: 12px; color: var(--text-muted); margin: -2px 0 8px; }
.spu-price { color: var(--text-primary); font-size: 18px; font-weight: 700; }
.add-cart-btn { border-radius: 8px; }
.load-more-tip { grid-column: 1 / -1; display: flex; align-items: center; justify-content: center; gap: 8px;
  padding: 24px 0; color: var(--text-secondary); font-size: 14px; }
.load-more-tip.no-more { color: var(--text-muted); }

/* ===== 高亮样式 ===== */
:deep(.hl) {
  color: var(--brand-primary, #409EFF);
  font-weight: 600;
  font-style: normal;
  background: transparent;
}

/* ===== 移动端适配 ===== */
@media (max-width: 768px) {
  .shop-layout {
    flex-direction: column;
  }
  .filter-sidebar {
    width: 100%;
    position: static;
    display: flex;
    gap: 16px;
    overflow-x: auto;
    padding-bottom: 12px;
    margin-bottom: 16px;
    border-bottom: 1px solid var(--border-base);
  }
  .filter-section {
    flex-shrink: 0;
    margin-bottom: 0;
    min-width: 120px;
  }
  .filter-title { font-size: 12px; }
  .filter-item { font-size: 12px; padding: 5px 6px; }
  .price-input-row { flex-wrap: nowrap; }
  .price-input { min-width: 50px; }
  .spu-grid { grid-template-columns: repeat(2, 1fr); gap: 12px; }
}

/* ===== 骨架屏 ===== */
.spu-card--skeleton { cursor: default; pointer-events: none; }
.spu-card--skeleton:hover { transform: none; box-shadow: none; border-color: var(--border-base); }
.skeleton-box {
  background: linear-gradient(90deg, var(--bg-hover, #f0f0f0) 25%, var(--bg-card, #e8e8e8) 50%, var(--bg-hover, #f0f0f0) 75%);
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.5s infinite;
}
.skeleton-line {
  height: 14px;
  border-radius: 4px;
  background: linear-gradient(90deg, var(--bg-hover, #f0f0f0) 25%, var(--bg-card, #e8e8e8) 50%, var(--bg-hover, #f0f0f0) 75%);
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.5s infinite;
  margin-bottom: 10px;
}
.skeleton-line--long { width: 100%; height: 16px; }
.skeleton-line--short { width: 60%; }
.skeleton-line--price { width: 80px; height: 20px; margin-bottom: 0; }
.skeleton-line--btn { width: 70px; height: 28px; border-radius: 8px; margin-bottom: 0; }
@keyframes skeleton-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>
