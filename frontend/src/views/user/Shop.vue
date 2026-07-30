<template>
  <div class="shop-page">
    <!-- 顶部大搜索框 -->
    <div class="shop-search-bar">
      <div class="shop-search-pill">
        <el-icon :size="18" class="search-prefix-icon"><Search /></el-icon>
        <input
          v-model="searchKeyword"
          type="text"
          class="search-core-input"
          placeholder="搜索商品..."
          @keyup.enter="doShopSearch"
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
    </div>

    <main class="container">
      <div class="shop-layout">
        <!-- 侧栏：聚合筛选 -->
        <aside class="filter-sidebar">
          <h3 class="sidebar-title">筛选</h3>
          <!-- 价格区间 -->
          <div class="filter-section">
            <h3 class="filter-title">价格区间</h3>
            <div class="price-input-row">
              <el-input
                v-model="customPriceFrom"
                placeholder="最低价"
                size="small"
                class="price-input"
                @keyup.enter="applyPriceFilter"
                @blur="applyPriceFilter"
              />
              <span class="price-separator">—</span>
              <el-input
                v-model="customPriceTo"
                placeholder="最高价"
                size="small"
                class="price-input"
                @keyup.enter="applyPriceFilter"
                @blur="applyPriceFilter"
              />
            </div>
          </div>

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
            </ul>
          </div>

          <!-- 分类 -->
          <div class="filter-section" v-if="aggCategories.length > 0">
            <h3 class="filter-title">商品分类</h3>
            <ul class="filter-list">
              <li v-for="b in aggCategories" :key="b.key"
                class="filter-item"
                :class="{ active: activeCategoryId === b.key }"
                @click="toggleCategoryFilter(b.key)">
                <span class="filter-label">{{ categoryNameById(b.key) || '分类 ' + b.key }}</span>
                <span class="filter-count">{{ b.docCount }}</span>
              </li>
            </ul>
          </div>

          <!-- 清除全部 -->
          <div class="filter-section" v-if="hasActiveFilters">
            <el-button size="small" text type="danger" @click="clearAllFilters">清除全部筛选</el-button>
          </div>
          <div class="filter-empty" v-if="!hasActiveFilters && aggCategories.length === 0">
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
          <div class="spu-grid" v-loading="loading"
            :style="{ gridTemplateColumns: `repeat(${columns}, 1fr)` }"
            v-infinite-scroll="loadMore"
            :infinite-scroll-disabled="!hasMore || loading || loadingMore"
            :infinite-scroll-distance="120"
            :infinite-scroll-immediate="false">
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

    <!-- 规格选择弹窗 -->
    <el-dialog v-model="skuDialogVisible" :title="`选择规格 - ${currentSpu?.name || ''}`" width="520px" destroy-on-close>
      <div class="sku-selected-preview" v-if="selectedSku">
        <img :src="selectedSku.image || currentSpu?.mainImage || __PH" class="sku-preview-img" />
        <div class="sku-preview-info">
          <div class="sku-preview-price">¥{{ Number(selectedSku.price || 0).toFixed(2) }}</div>
          <div class="sku-preview-spec">{{ selectedSku.specs || '默认规格' }}</div>
          <div class="sku-preview-stock">库存：{{ selectedSku.stock || 0 }}</div>
        </div>
      </div>
      <div class="sku-options">
        <div v-for="sku in currentSkus" :key="sku.id" class="sku-option"
          :class="{ active: selectedSkuId === sku.id, disabled: (sku.stock || 0) <= 0 }"
          @click="(sku.stock || 0) > 0 ? selectedSkuId = sku.id : null">
          <div class="sku-option-name">{{ sku.name || sku.specs || '默认规格' }}</div>
          <div class="sku-option-price">¥{{ Number(sku.price || 0).toFixed(2) }}</div>
          <div v-if="(sku.stock || 0) <= 0" class="sku-option-soldout">缺货</div>
        </div>
      </div>
      <template #footer>
        <el-button @click="skuDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="addingCart" :disabled="!selectedSkuId" @click="confirmAddToCart">加入购物车</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Loading, Search, Close } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'
import { listSpu, listCategory, addFavorite, removeFavorite, batchCheckFavorites, getSpu, addCart } from '@/api/mall'
import { ElMessage } from 'element-plus'

const __PH = window.__PH
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const cartStore = useCartStore()

const columns = ref(Number(localStorage.getItem('stellar_shop_cols')) || 4)
function setColumns(n) {
  localStorage.setItem('stellar_shop_cols', String(n))
}

const loading = ref(false)
const loadingMore = ref(false)
const keyword = ref('')
const searchKeyword = ref('')  // 顶部大搜索框的输入绑定
const categoryId = ref(null)
const category2Id = ref(null)
const categoryName = ref('')
const spus = ref([])
const favSet = reactive(new Set())
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const hasMore = computed(() => spus.value.length < total.value)

// 高亮映射
const highlights = ref({})

// 聚合数据
const aggCategories = ref([])
const aggPriceRanges = ref([])
const activeCategoryId = ref(null)
// 自定义价格筛选
const customPriceFrom = ref('')
const customPriceTo = ref('')

// 排序
const activeSort = ref('default') // 'default' | 'price_asc' | 'price_desc' | 'sales_desc'

// 所有分类（用于名称映射）
const allCategories = ref([])
const categoryMap = reactive({})

const hasActiveFilters = computed(() => !!activeCategoryId.value || customPriceFrom.value !== '' || customPriceTo.value !== '' || activeSort.value !== 'default')

// --- 规格弹窗 ---
const skuDialogVisible = ref(false)
const currentSpu = ref(null)
const currentSkus = ref([])
const selectedSkuId = ref(null)
const addingCart = ref(false)
const selectedSku = computed(() => currentSkus.value.find(s => s.id === selectedSkuId.value))

// --- 高亮渲染 ---
function highlightedName(spuId, fallback) {
  const hl = highlights.value[String(spuId)]
  if (hl && hl.length > 0) return hl[0]
  // 用内置 HTML 转义后的 fallback
  return escapeHtml(String(fallback || ''))
}
function escapeHtml(str) {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;')
}

// --- 聚合交互 ---
function doShopSearch() {
  const kw = searchKeyword.value.trim()
  if (!kw) return
  keyword.value = kw
  // 更新 URL 但不跳转新页面
  router.replace({ query: { ...route.query, keyword: kw } })
  // 重置并重新加载
  activeCategoryId.value = null
  customPriceFrom.value = ''
  customPriceTo.value = ''
  activeSort.value = 'default'
  page.value = 1; spus.value = []
  loadSpu()
}

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

function setSort(sortKey) {
  if (activeSort.value === sortKey) {
    activeSort.value = 'default'
  } else {
    activeSort.value = sortKey
  }
  page.value = 1; spus.value = []
  loadSpu()
}

function toggleCategoryFilter(catId) {
  if (activeCategoryId.value === catId) {
    activeCategoryId.value = null
  } else {
    activeCategoryId.value = catId
  }
  page.value = 1; spus.value = []
  loadSpu()
}

function clearAllFilters() {
  activeCategoryId.value = null
  customPriceFrom.value = ''
  customPriceTo.value = ''
  activeSort.value = 'default'
  page.value = 1; spus.value = []
  loadSpu()
}

function categoryNameById(id) {
  return categoryMap[id] || null
}

// --- 价格区间 → 请求参数 ---
function priceFilterParams() {
  const params = {}
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
function sortParams() {
  switch (activeSort.value) {
    case 'price_asc':  return { sortBy: 'minPrice', sortOrder: 'asc' }
    case 'price_desc': return { sortBy: 'minPrice', sortOrder: 'desc' }
    case 'sales_desc': return { sortBy: 'saleCount', sortOrder: 'desc' }
    default:           return {}
  }
}

// --- 数据加载 ---
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
    aggCategories.value = []
    aggPriceRanges.value = []
  }
  try {
    const params = { page: page.value, pageSize: pageSize.value }
    if (keyword.value) params.name = keyword.value
    if (categoryId.value) params.categoryId = categoryId.value
    if (category2Id.value) params.category2Id = category2Id.value

    // 聚合筛选参数
    if (activeCategoryId.value) params.categoryId = Number(activeCategoryId.value)
    const pf = priceFilterParams()
    if (pf.priceFrom !== undefined) params.priceFrom = pf.priceFrom
    if (pf.priceTo !== undefined) params.priceTo = pf.priceTo
    // 排序参数
    const sp = sortParams()
    if (sp.sortBy) params.sortBy = sp.sortBy
    if (sp.sortOrder) params.sortOrder = sp.sortOrder

    const res = await listSpu(params)
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
        // 聚合（只在首页加载时更新）
        if (res.aggregations) {
          if (res.aggregations.categories) aggCategories.value = res.aggregations.categories
          if (res.aggregations.priceRanges) aggPriceRanges.value = res.aggregations.priceRanges
        }
        await batchCheckFavs()
      }
    }
  } catch (e) {
    console.error('loadSpu error:', e)
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

async function loadMore() {
  if (loading.value || loadingMore.value || !hasMore.value) return
  page.value += 1
  await loadSpu(true)
}

async function batchCheckFavs(items) {
  const target = items || spus.value
  if (!userStore.token || target.length === 0) return
  try {
    const ids = target.map(s => s.id)
    const res = await batchCheckFavorites(ids)
    if (Array.isArray(res)) res.forEach(id => favSet.add(id))
  } catch (e) { }
}

async function toggleFav(spu) {
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
  } catch (e) { ElMessage.error('操作失败') }
}

function goDetail(id) {
  const r = router.resolve(`/spu/${id}`)
  window.open(r.href, '_blank')
}

async function handleAddToCart(spu) {
  if (!userStore.token) { ElMessage.warning('请先登录'); router.push('/login'); return }
  try {
    const res = await getSpu(spu.id)
    const detail = res || {}
    const skuList = detail.skuList || detail.skuListVo || []
    if (!skuList.length) { ElMessage.warning('该商品暂无规格可选'); return }
    if (skuList.length === 1) { await doAddCart(skuList[0].id); return }
    currentSpu.value = detail
    currentSkus.value = skuList.filter(s => s.status !== 0)
    selectedSkuId.value = currentSkus.value[0]?.id || null
    skuDialogVisible.value = true
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '加载商品规格失败')
  }
}

async function doAddCart(skuId) {
  if (!skuId) { ElMessage.warning('请选择商品规格'); return }
  addingCart.value = true
  try {
    await addCart({ skuId, qty: 1 })
    ElMessage.success('已加入购物车')
    skuDialogVisible.value = false
    await cartStore.load()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '加入购物车失败')
  } finally { addingCart.value = false }
}

function confirmAddToCart() { doAddCart(selectedSkuId.value) }

// --- 初始化 ---
onMounted(async () => {
  const q = route.query.keyword
  if (q) {
    keyword.value = q
    searchKeyword.value = q      // 顶部搜索框预填
  }
  categoryId.value = route.query.categoryId || null
  category2Id.value = route.query.category2Id || null

  // 加载分类列表（用于聚合面板名称映射）
  try {
    const cats = await listCategory()
    const flat = []
    function walk(list) {
      if (!list) return
      for (const c of list) {
        flat.push(c)
        walk(c.children)
      }
    }
    walk(cats || [])
    allCategories.value = flat
    flat.forEach(c => { categoryMap[c.id] = c.name })
    const cat = flat.find(c => c.id == category2Id.value || c.id == categoryId.value)
    if (cat) categoryName.value = cat.name
  } catch (e) { }

  if (userStore.token && !userStore.nickname) { try { await userStore.fetchProfile() } catch (e) { } }
  try { await cartStore.load() } catch (e) { }
  await loadSpu()
})

</script>

<style scoped>
.shop-page { min-height: 100vh; padding-top: 48px; }

/* ===== 顶部大搜索框 ===== */
.shop-search-bar {
  display: flex;
  justify-content: center;
  padding: 0 24px 32px;
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

/* ===== 规格弹窗 ===== */
.sku-selected-preview { display: flex; align-items: center; gap: 14px;
  background: var(--bg-hover); padding: 14px; border-radius: var(--radius-md); margin-bottom: 18px; }
.sku-preview-img { width: 72px; height: 72px; object-fit: cover; border-radius: var(--radius-sm); }
.sku-preview-price { color: var(--text-primary); font-size: 18px; font-weight: 700; }
.sku-preview-spec { color: var(--text-secondary); font-size: 13px; margin-top: 4px; }
.sku-preview-stock { color: var(--text-muted); font-size: 12px; margin-top: 2px; }
.sku-options { display: flex; flex-wrap: wrap; gap: 10px; }
.sku-option { min-width: 100px; padding: 10px 14px; border: 1px solid var(--border-base);
  border-radius: 8px; background: var(--bg-card); cursor: pointer; transition: all 0.2s; position: relative; }
.sku-option:hover:not(.disabled) { border-color: var(--brand-primary); }
.sku-option.active { border-color: var(--brand-primary); background: var(--brand-primary-soft); }
.sku-option.disabled { opacity: 0.5; cursor: not-allowed; }
.sku-option-name { font-size: 13px; color: var(--text-primary); }
.sku-option-price { font-size: 13px; color: var(--brand-primary); font-weight: 600; margin-top: 4px; }
.sku-option-soldout { position: absolute; top: 2px; right: 4px; font-size: 10px; color: var(--status-danger); }

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
</style>
