<template>
  <div class="storefront-demo" data-testid="storefront-demo">
    <div class="demo-announcement">
      <span><i /> STELLAR SELECTS · 2026</span>
      <span>新品上架 / 全场顺丰包邮 / 30 天无忧退换</span>
    </div>

    <header class="demo-nav">
      <RouterLink to="/demo/storefront" class="demo-brand" aria-label="星耀商城重设计 Demo">
        <span class="brand-mark" aria-hidden="true">
          <svg viewBox="0 0 32 32"><path d="M16 2l3.4 10.6L30 16l-10.6 3.4L16 30l-3.4-10.6L2 16l10.6-3.4z" /></svg>
        </span>
        <span><b>STELLAR</b><small>星耀商城</small></span>
      </RouterLink>

      <nav class="nav-links" aria-label="演示导航">
        <a href="#catalog">本月精选</a>
        <a href="#philosophy">品牌提案</a>
        <a href="#api-contract">接口契约</a>
      </nav>

      <div class="nav-actions">
        <button class="icon-button search-jump" type="button" aria-label="聚焦商品搜索" @click="focusSearch">
          <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="6.5" /><path d="M16 16l4 4" /></svg>
        </button>
        <button
          class="source-badge"
          :class="`is-${source}`"
          type="button"
          :disabled="source === 'loading'"
          title="点击重新连接后端"
          @click="retryConnection"
        >
          <i />
          {{ sourceLabel }}
          <svg v-if="source === 'demo'" viewBox="0 0 24 24" aria-hidden="true"><path d="M20 11a8 8 0 10-2.3 6M20 5v6h-6" /></svg>
        </button>
      </div>
    </header>

    <main>
      <section class="demo-hero" aria-labelledby="hero-title">
        <div class="hero-copy">
          <div class="hero-eyebrow"><span>01</span> THE EVERYDAY EDIT</div>
          <h1 id="hero-title">把日常，升级成<br><em>喜欢的样子。</em></h1>
          <p class="hero-script">Curated objects for brighter days.</p>
          <p class="hero-intro">不堆砌参数，只挑选真正改善体验的设计。<br>从一件好物开始，让科技自然地融入生活。</p>
          <div class="hero-actions">
            <button class="demo-primary" type="button" @click="scrollToCatalog">
              开始选购
              <span aria-hidden="true">↘</span>
            </button>
            <button class="text-link" type="button" @click="openProduct(heroProduct)">
              查看本期主角 <span aria-hidden="true">→</span>
            </button>
          </div>
          <div class="hero-footnote">
            <span>EDITOR'S CHOICE</span>
            <span>Vol. 11 — Nov. 2026</span>
          </div>
        </div>

        <div class="hero-stage">
          <div class="stage-heading"><span>THE NEW CLASSIC</span><span>01 / 06</span></div>
          <div class="stage-sticker">NEW<br>DROP</div>
          <div class="hero-visual">
            <DemoProductVisual :product="heroProduct" />
          </div>
          <div class="stage-side">STELLAR ORIGINAL</div>
          <button class="stage-product" type="button" @click="openProduct(heroProduct)">
            <span><small>本期主角</small><b>{{ heroProduct.name }}</b></span>
            <span class="stage-price">¥{{ formatPrice(heroProduct.minPrice) }} <i>↗</i></span>
          </button>
        </div>
      </section>

      <section class="service-strip" aria-label="商城服务承诺">
        <div><span>01</span><p><b>正品保障</b><small>品牌直供，每一单都安心</small></p></div>
        <div><span>02</span><p><b>极速送达</b><small>顺丰配送，进度实时可查</small></p></div>
        <div><span>03</span><p><b>无忧售后</b><small>30 天退换，服务始终在线</small></p></div>
      </section>

      <section id="catalog" class="catalog-section" aria-labelledby="catalog-title">
        <div class="section-heading">
          <div>
            <p class="section-kicker">CURATED FOR YOU / 本月精选</p>
            <h2 id="catalog-title">好物，不必千篇一律。</h2>
          </div>
          <p class="section-note">从真实商品数据出发，<br>重新组织每一次发现与选择。</p>
        </div>

        <div class="catalog-toolbar">
          <aside class="category-panel" aria-label="商品分类">
            <p>SHOP BY CATEGORY</p>
            <div class="category-list">
              <button type="button" :class="{ active: activeCategory === null }" @click="selectCategory(null)">
                <span>全部好物</span><small>{{ source === 'demo' ? demoProducts.length : total }}</small>
              </button>
              <button
                v-for="category in categories"
                :key="category.id"
                type="button"
                :class="{ active: activeCategory === category.id }"
                @click="selectCategory(category.id)"
              >
                <span>{{ category.name }}</span><small>↗</small>
              </button>
            </div>
            <div class="category-aside-note"><span>*</span> 每一件，都值得日常使用。</div>
          </aside>

          <div class="products-panel">
            <div class="products-tools">
              <form class="product-search" role="search" @submit.prevent="submitSearch">
                <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="6.5" /><path d="M16 16l4 4" /></svg>
                <input
                  ref="searchInputRef"
                  v-model="searchInput"
                  type="search"
                  placeholder="搜索商品，例如「耳机」"
                  aria-label="搜索商品"
                >
                <button v-if="searchInput" class="clear-search" type="button" aria-label="清空搜索" @click="clearSearch">×</button>
                <button class="search-submit" type="submit">搜索</button>
              </form>
              <div class="result-meta" aria-live="polite">
                <span>{{ resultDescription }}</span>
                <b>{{ String(total).padStart(2, '0') }} ITEMS</b>
              </div>
            </div>

            <div v-if="loading" class="product-grid" aria-label="正在加载商品">
              <div v-for="n in 6" :key="n" class="product-skeleton"><i /><span /><span /></div>
            </div>

            <div v-else-if="products.length" class="product-grid">
              <article
                v-for="(product, index) in products"
                :key="product.id"
                class="product-card"
                data-testid="product-card"
                :style="{ '--card-index': index }"
              >
                <button class="product-image-button" type="button" :aria-label="`查看 ${product.name}`" @click="openProduct(product)">
                  <div class="product-visual">
                    <DemoProductVisual :product="product" />
                  </div>
                  <span class="product-index">{{ String(index + 1).padStart(2, '0') }}</span>
                  <span v-if="product.isNew === 1" class="product-flag is-new">NEW</span>
                  <span v-else-if="product.isHot === 1" class="product-flag">HOT</span>
                  <span class="quick-view">快速查看 ↗</span>
                </button>
                <div class="product-info">
                  <p class="product-category">{{ product.categoryName || categoryName(product.categoryId) || 'STELLAR SELECTS' }}</p>
                  <button class="product-name" type="button" @click="openProduct(product)">{{ product.name }}</button>
                  <p class="product-subtitle">{{ product.subTitle || '为日常精心挑选' }}</p>
                  <div class="product-bottom">
                    <div><b>¥{{ displayPrice(product) }}</b><small v-if="product.saleCount">已售 {{ formatSales(product.saleCount) }}</small></div>
                    <button type="button" aria-label="查看商品详情" @click="openProduct(product)">↗</button>
                  </div>
                </div>
              </article>
            </div>

            <div v-else class="empty-state">
              <span>0 RESULTS</span>
              <h3>这一页，暂时留白。</h3>
              <p>换个关键词或分类，再发现一件心仪好物。</p>
              <button class="demo-primary" type="button" @click="resetFilters">查看全部商品 <span>↗</span></button>
            </div>
          </div>
        </div>
      </section>

      <section id="philosophy" class="philosophy-banner">
        <div class="philosophy-symbol" aria-hidden="true">✳</div>
        <div><p>OUR PHILOSOPHY</p><h2>Less noise.<br><em>More delight.</em></h2></div>
        <p class="philosophy-copy">少一些选择负担，多一些确定喜欢。<br>我们相信，好的商城设计应该让商品自己说话。</p>
        <button type="button" @click="scrollToCatalog">继续探索 <span>↗</span></button>
      </section>

      <section id="api-contract" class="contract-section">
        <details>
          <summary><span>FOR DEVELOPERS</span><b>查看本页前后端接口契约</b><i>＋</i></summary>
          <div class="contract-content">
            <div><small>01 / 商品列表</small><code>GET /user/spu/page</code><p>参数使用 <code>page / pageSize / name / categoryId</code>，读取 <code>records / total</code>。</p></div>
            <div><small>02 / 分类树</small><code>GET /user/category/list</code><p>直接消费现有 <code>Category[]</code>，保留后端分类 ID，不另建前端分类映射。</p></div>
            <div><small>03 / 商品详情</small><code>GET /user/spu/{id}</code><p>复用 <code>Spu</code> 与嵌套 <code>skuList</code>，价格、库存和规格均由接口驱动。</p></div>
          </div>
          <p class="contract-note">统一响应由 request.ts 解包 { code, msg, data }。当前 Demo 只读；后端不可用时使用同构本地数据，并显示 DEMO 标记。</p>
        </details>
      </section>
    </main>

    <footer class="demo-footer">
      <div class="footer-brand"><b>STELLAR</b><span>让每一次选择，都更接近喜欢。</span></div>
      <div><span>READ-ONLY CONCEPT DEMO</span><RouterLink to="/">返回原商城 ↗</RouterLink></div>
    </footer>

    <Teleport to="body">
      <div v-if="drawerOpen" class="demo-drawer-overlay" @mousedown.self="closeDrawer">
          <aside class="demo-drawer" role="dialog" aria-modal="true" aria-labelledby="drawer-title" data-testid="product-drawer">
            <button ref="drawerCloseRef" class="drawer-close" type="button" aria-label="关闭商品详情" @click="closeDrawer">×</button>
            <template v-if="selectedProduct">
              <div class="drawer-visual"><DemoProductVisual :product="selectedProduct" /></div>
              <div class="drawer-content">
                <p class="drawer-kicker">STELLAR OBJECT / {{ String(selectedProduct.id).padStart(2, '0') }}</p>
                <h2 id="drawer-title">{{ selectedProduct.name }}</h2>
                <p class="drawer-subtitle">{{ selectedProduct.subTitle }}</p>
                <div class="drawer-price"><small>RMB</small><b>¥{{ formatPrice(activeSku?.price ?? selectedProduct.minPrice) }}</b></div>
                <p class="drawer-description">{{ selectedProduct.description || '这件商品暂时没有补充介绍，但价格与规格信息仍来自同一商品接口。' }}</p>

                <div v-if="detailLoading" class="detail-loading"><i /> 正在读取完整规格…</div>
                <p v-if="detailError" class="detail-error">{{ detailError }}，当前展示列表数据。</p>

                <div v-if="productSkus(selectedProduct).length" class="sku-section">
                  <div class="sku-heading"><b>选择规格</b><small>{{ productSkus(selectedProduct).length }} OPTIONS</small></div>
                  <div class="sku-options">
                    <button
                      v-for="item in productSkus(selectedProduct)"
                      :key="item.id"
                      type="button"
                      :class="{ active: selectedSkuId === item.id }"
                      @click="selectSku(item)"
                    >
                      <span>{{ shortSkuName(item) }}</span>
                      <small>¥{{ formatPrice(item.price) }} · 库存 {{ item.stock }}</small>
                    </button>
                  </div>
                  <div v-if="activeSku" class="spec-list">
                    <span v-for="spec in skuSpecs(activeSku)" :key="`${spec.key}-${spec.value}`"><small>{{ spec.key }}</small>{{ spec.value }}</span>
                  </div>
                </div>

                <div class="purchase-row">
                  <div class="quantity-control" aria-label="选择数量">
                    <button type="button" aria-label="减少数量" :disabled="quantity <= 1" @click="quantity--">−</button>
                    <span>{{ quantity }}</span>
                    <button type="button" aria-label="增加数量" :disabled="quantity >= maxQuantity" @click="quantity++">＋</button>
                  </div>
                  <span class="stock-note">{{ activeSku ? `现货 ${activeSku.stock} 件` : '规格以完整详情页为准' }}</span>
                </div>

                <button class="drawer-primary" type="button" @click="goToFullDetail">去完整商品页购买 <span>↗</span></button>
                <p class="read-only-note">本 Demo 为只读体验，不会写入购物车或创建订单。</p>
              </div>
            </template>
          </aside>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import DemoProductVisual from '@/components/demo/DemoProductVisual.vue'
import {
  fetchStorefrontCategories,
  fetchStorefrontProduct,
  fetchStorefrontProducts,
} from '@/api/storefrontDemo'
import { demoCategories, demoProducts } from '@/data/storefrontDemo'
import type { Category, Sku, Spu } from '@/types/models'

type DataSource = 'loading' | 'live' | 'demo'

const router = useRouter()
const products = ref<Spu[]>([])
const categories = ref<Category[]>([])
const heroProduct = ref<Spu>(demoProducts[0]!)
const selectedProduct = ref<Spu | null>(null)
const source = ref<DataSource>('loading')
const loading = ref(true)
const detailLoading = ref(false)
const detailError = ref('')
const total = ref(0)
const searchInput = ref('')
const activeKeyword = ref('')
const activeCategory = ref<number | null>(null)
const searchInputRef = ref<HTMLInputElement | null>(null)
const drawerOpen = ref(false)
const drawerCloseRef = ref<HTMLButtonElement | null>(null)
const selectedSkuId = ref<number | null>(null)
const quantity = ref(1)
let requestSequence = 0
let detailSequence = 0

const sourceLabel = computed(() => ({
  loading: 'CONNECTING',
  live: 'LIVE · 后端已连接',
  demo: 'DEMO · 本地数据',
}[source.value]))

const activeSku = computed(() => {
  const list = selectedProduct.value ? productSkus(selectedProduct.value) : []
  return list.find(item => item.id === selectedSkuId.value) || list[0]
})
const maxQuantity = computed(() => Math.max(1, Math.min(activeSku.value?.stock || 99, 99)))
const resultDescription = computed(() => {
  const parts: string[] = []
  if (activeCategory.value !== null) parts.push(categoryName(activeCategory.value))
  if (activeKeyword.value) parts.push(`“${activeKeyword.value}”`)
  return parts.length ? parts.join(' / ') : '全部精选商品'
})

function productSkus(product: Spu): Sku[] {
  return product.skuList || product.skus || []
}

function formatPrice(value?: number) {
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(Number(value || 0))
}

function displayPrice(product: Spu) {
  const min = formatPrice(product.minPrice)
  if (product.maxPrice && Number(product.maxPrice) !== Number(product.minPrice)) {
    return `${min} 起`
  }
  return min
}

function formatSales(value: number) {
  return value >= 10000 ? `${(value / 10000).toFixed(1)}w+` : `${value}+`
}

function categoryName(id?: number) {
  if (!id) return ''
  for (const category of categories.value) {
    if (category.id === id) return category.name
    const child = category.children?.find(item => item.id === id)
    if (child) return child.name
  }
  return demoCategories.find(item => item.id === id)?.name || ''
}

function applyDemoFilter() {
  const keyword = activeKeyword.value.toLowerCase()
  products.value = demoProducts.filter((product) => {
    const matchesCategory = activeCategory.value === null || product.categoryId === activeCategory.value
    const haystack = `${product.name} ${product.subTitle || ''}`.toLowerCase()
    return matchesCategory && (!keyword || haystack.includes(keyword))
  })
  total.value = products.value.length
  source.value = 'demo'
}

async function loadProducts(forceLive = false) {
  const sequence = ++requestSequence
  if (source.value === 'demo' && !forceLive) {
    applyDemoFilter()
    loading.value = false
    return
  }

  loading.value = true
  try {
    const response = await fetchStorefrontProducts({
      page: 1,
      pageSize: 9,
      status: 1,
      ...(activeKeyword.value ? { name: activeKeyword.value } : {}),
      ...(activeCategory.value !== null ? { categoryId: activeCategory.value } : {}),
    })
    if (sequence !== requestSequence) return
    products.value = Array.isArray(response?.records) ? response.records : []
    total.value = Number(response?.total || products.value.length)
    source.value = 'live'
    if (!activeKeyword.value && activeCategory.value === null && products.value.length) {
      heroProduct.value = products.value[0]!
    }
  } catch {
    if (sequence !== requestSequence) return
    applyDemoFilter()
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

async function loadCategories() {
  try {
    const response = await fetchStorefrontCategories()
    categories.value = Array.isArray(response) && response.length ? response : demoCategories
  } catch {
    categories.value = demoCategories
  }
}

async function retryConnection() {
  if (source.value === 'loading') return
  source.value = 'loading'
  await Promise.all([loadCategories(), loadProducts(true)])
}

function selectCategory(id: number | null) {
  if (activeCategory.value === id) return
  activeCategory.value = id
  loadProducts()
}

function submitSearch() {
  activeKeyword.value = searchInput.value.trim()
  loadProducts()
}

function clearSearch() {
  searchInput.value = ''
  activeKeyword.value = ''
  loadProducts()
  searchInputRef.value?.focus()
}

function resetFilters() {
  searchInput.value = ''
  activeKeyword.value = ''
  activeCategory.value = null
  loadProducts()
}

function scrollToCatalog() {
  document.getElementById('catalog')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function focusSearch() {
  scrollToCatalog()
  nextTick(() => setTimeout(() => searchInputRef.value?.focus(), 350))
}

async function openProduct(product: Spu) {
  const sequence = ++detailSequence
  drawerOpen.value = true
  selectedProduct.value = product
  detailError.value = ''
  quantity.value = 1
  selectedSkuId.value = productSkus(product)[0]?.id || null
  nextTick(() => drawerCloseRef.value?.focus())

  if (source.value === 'demo') {
    selectedProduct.value = demoProducts.find(item => item.id === product.id) || product
    selectedSkuId.value = productSkus(selectedProduct.value)[0]?.id || null
    return
  }

  detailLoading.value = true
  try {
    const detail = await fetchStorefrontProduct(product.id)
    if (sequence !== detailSequence) return
    selectedProduct.value = { ...product, ...detail }
    selectedSkuId.value = productSkus(selectedProduct.value)[0]?.id || null
  } catch {
    if (sequence === detailSequence) detailError.value = '完整规格暂时读取失败'
  } finally {
    if (sequence === detailSequence) detailLoading.value = false
  }
}

function closeDrawer() {
  drawerOpen.value = false
  detailSequence++
}

function selectSku(item: Sku) {
  selectedSkuId.value = item.id
  quantity.value = 1
}

function shortSkuName(item: Sku) {
  const specs = skuSpecs(item).map(spec => spec.value).join(' · ')
  return specs || item.name
}

function skuSpecs(item: Sku) {
  if (!item.specs) return []
  return item.specs.split(';').filter(Boolean).map((part) => {
    const [key, ...rest] = part.split(':')
    return { key, value: rest.join(':') || key }
  })
}

function goToFullDetail() {
  if (!selectedProduct.value) return
  const id = selectedProduct.value.id
  closeDrawer()
  router.push(`/spu/${id}`)
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && drawerOpen.value) closeDrawer()
}

watch(drawerOpen, (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
})

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  Promise.all([loadCategories(), loadProducts(true)])
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
  requestSequence++
  detailSequence++
})
</script>

<style scoped>
.storefront-demo,
.demo-drawer {
  --demo-bg: #f2f0e9;
  --demo-paper: #faf8f2;
  --demo-ink: #171918;
  --demo-muted: #6d706b;
  --demo-line: #c9c8c0;
  --demo-blue: #3155ef;
  --demo-lime: #d7ff4f;
  color: var(--demo-ink);
  font-family: 'MiSans', 'Alibaba PuHuiTi 3.0', sans-serif;
  color-scheme: light;
}
.storefront-demo {
  min-height: 100vh;
  overflow: hidden;
  background-color: var(--demo-bg);
  background-image: radial-gradient(rgba(23, 25, 24, .09) .7px, transparent .7px);
  background-size: 8px 8px;
}
button, input { font: inherit; }
button { color: inherit; }
button:focus-visible, a:focus-visible, input:focus-visible, summary:focus-visible {
  outline: 3px solid var(--demo-blue);
  outline-offset: 4px;
}
button { cursor: pointer; }
button:disabled { cursor: not-allowed; }
.demo-announcement {
  min-height: 34px;
  padding: 8px 4vw;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  background: var(--demo-ink);
  color: #fff;
  font-family: 'Courier New', monospace;
  font-size: 10px;
  letter-spacing: 1.3px;
}
.demo-announcement span:first-child { display: flex; align-items: center; gap: 8px; }
.demo-announcement i { width: 7px; height: 7px; border-radius: 50%; background: var(--demo-lime); }
.demo-nav {
  max-width: 1500px;
  margin: 0 auto;
  padding: 24px 4vw;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 32px;
  border-bottom: 1px solid rgba(23, 25, 24, .2);
}
.demo-brand { display: inline-flex; align-items: center; gap: 12px; width: max-content; }
.brand-mark {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  background: var(--demo-blue);
  border: 1px solid var(--demo-ink);
  box-shadow: 3px 3px 0 var(--demo-ink);
}
.brand-mark svg { width: 25px; fill: var(--demo-lime); }
.demo-brand > span:last-child { display: flex; flex-direction: column; }
.demo-brand b { font-size: 20px; line-height: 1; letter-spacing: 2.5px; }
.demo-brand small { margin-top: 5px; color: var(--demo-muted); font-size: 10px; letter-spacing: 4px; }
.nav-links { display: flex; align-items: center; gap: 34px; }
.nav-links a { position: relative; font-size: 13px; font-weight: 650; }
.nav-links a::after { content: ''; position: absolute; left: 0; right: 100%; bottom: -7px; height: 2px; background: var(--demo-blue); transition: right .2s; }
.nav-links a:hover::after { right: 0; }
.nav-actions { display: flex; justify-content: flex-end; align-items: center; gap: 12px; }
.icon-button { width: 40px; height: 40px; display: grid; place-items: center; border: 0; background: transparent; }
.icon-button svg, .product-search svg, .source-badge svg { width: 20px; fill: none; stroke: currentColor; stroke-width: 1.7; stroke-linecap: round; }
.source-badge {
  min-height: 34px;
  padding: 8px 12px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  border: 1px solid var(--demo-line);
  border-radius: 30px;
  background: rgba(250, 248, 242, .7);
  font-family: 'Courier New', monospace;
  font-size: 10px;
  letter-spacing: .4px;
}
.source-badge i { width: 7px; height: 7px; border-radius: 50%; background: #d49b23; }
.source-badge.is-live i { background: #20a66a; box-shadow: 0 0 0 3px rgba(32, 166, 106, .13); }
.source-badge.is-demo i { background: #d49b23; }
.source-badge svg { width: 13px; }
.source-badge.is-demo:hover { border-color: var(--demo-ink); }
.demo-hero {
  max-width: 1500px;
  min-height: 660px;
  margin: 0 auto;
  padding: 56px 4vw 86px;
  display: grid;
  grid-template-columns: minmax(0, 1.03fr) minmax(440px, .97fr);
  align-items: center;
  gap: clamp(42px, 6vw, 96px);
}
.hero-copy { position: relative; z-index: 1; animation: demo-reveal .8s both; }
.hero-eyebrow { display: flex; align-items: center; gap: 14px; margin-bottom: 30px; font-family: 'Courier New', monospace; font-size: 11px; letter-spacing: 2px; }
.hero-eyebrow span { display: grid; place-items: center; width: 29px; height: 29px; background: var(--demo-ink); color: white; font-size: 10px; }
.hero-copy h1 { margin: 0; font-size: clamp(48px, 5.4vw, 82px); font-weight: 650; line-height: 1.08; letter-spacing: -.055em; }
.hero-copy h1 em { position: relative; color: var(--demo-blue); font-style: normal; white-space: nowrap; }
.hero-copy h1 em::after { content: ''; position: absolute; left: 2%; right: 0; bottom: -8px; height: 8px; background: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 400 10'%3E%3Cpath d='M2 7C100 0 290 1 398 5' fill='none' stroke='%233155ef' stroke-width='3'/%3E%3C/svg%3E") center/100% 100% no-repeat; }
.hero-script { margin: 28px 0 18px; color: var(--demo-muted); font-family: Georgia, serif; font-size: 20px; font-style: italic; }
.hero-intro { margin: 0; color: var(--demo-muted); font-size: 14px; line-height: 1.9; }
.hero-actions { margin-top: 34px; display: flex; align-items: center; gap: 28px; }
.demo-primary {
  min-height: 52px;
  padding: 0 10px 0 23px;
  display: inline-flex;
  align-items: center;
  gap: 24px;
  border: 1px solid var(--demo-ink);
  background: var(--demo-ink);
  color: white;
  font-size: 14px;
  font-weight: 650;
  box-shadow: 4px 4px 0 var(--demo-blue);
  transition: transform .2s, box-shadow .2s;
}
.demo-primary span { width: 34px; height: 34px; display: grid; place-items: center; background: var(--demo-lime); color: var(--demo-ink); font-size: 20px; }
.demo-primary:hover { transform: translate(2px, 2px); box-shadow: 2px 2px 0 var(--demo-blue); }
.text-link { padding: 10px 0; border: 0; border-bottom: 1px solid var(--demo-ink); background: transparent; font-size: 13px; }
.text-link span { margin-left: 10px; color: var(--demo-blue); }
.hero-footnote { margin-top: 52px; display: flex; gap: 24px; color: var(--demo-muted); font-family: 'Courier New', monospace; font-size: 9px; letter-spacing: 1px; }
.hero-stage {
  position: relative;
  min-width: 0;
  height: 530px;
  border: 1.5px solid var(--demo-ink);
  border-radius: 2px 62px 2px 2px;
  background: var(--demo-blue);
  box-shadow: 13px 13px 0 var(--demo-ink);
  animation: demo-stage-in 1s .1s both;
}
.stage-heading { position: absolute; z-index: 2; top: 25px; left: 27px; right: 27px; display: flex; justify-content: space-between; color: white; font-family: 'Courier New', monospace; font-size: 10px; letter-spacing: 1.5px; }
.stage-sticker { position: absolute; z-index: 3; top: 65px; right: 22px; width: 67px; height: 67px; display: grid; place-items: center; border: 1px solid var(--demo-ink); border-radius: 50%; background: var(--demo-lime); text-align: center; font-family: 'Courier New', monospace; font-size: 11px; font-weight: 700; line-height: 1.2; transform: rotate(11deg); }
.hero-visual { position: absolute; inset: 60px 25px 93px 25px; }
.hero-visual :deep(.demo-product-visual) { background: transparent; }
.hero-visual :deep(.visual-grid), .hero-visual :deep(.visual-caption) { color: white; opacity: .35; }
.hero-visual :deep(.visual-orbit) { stroke: var(--demo-lime); }
.stage-side { position: absolute; right: 11px; bottom: 118px; color: rgba(255, 255, 255, .55); font-family: 'Courier New', monospace; font-size: 8px; letter-spacing: 3px; writing-mode: vertical-rl; }
.stage-product { position: absolute; left: 0; right: 0; bottom: 0; min-height: 90px; padding: 17px 25px; display: flex; align-items: center; justify-content: space-between; gap: 20px; border: 0; border-top: 1px solid var(--demo-ink); background: var(--demo-paper); text-align: left; }
.stage-product > span:first-child { min-width: 0; display: flex; flex-direction: column; gap: 6px; }
.stage-product small { color: var(--demo-muted); font-size: 9px; letter-spacing: 2px; }
.stage-product b { overflow: hidden; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.stage-price { flex: none; font-family: 'Courier New', monospace; font-size: 19px; font-weight: 700; }
.stage-price i { margin-left: 8px; color: var(--demo-blue); font-style: normal; }
.service-strip { max-width: 1500px; margin: 0 auto; padding: 0 4vw 72px; display: grid; grid-template-columns: repeat(3, 1fr); gap: 0; }
.service-strip > div { padding: 23px 28px; display: flex; align-items: center; gap: 20px; border: 1px solid var(--demo-line); border-right: 0; background: rgba(250, 248, 242, .65); }
.service-strip > div:last-child { border-right: 1px solid var(--demo-line); }
.service-strip > div > span { color: var(--demo-blue); font-family: Georgia, serif; font-size: 29px; font-style: italic; }
.service-strip p { margin: 0; display: flex; flex-direction: column; gap: 5px; }
.service-strip b { font-size: 13px; }
.service-strip small { color: var(--demo-muted); font-size: 11px; }
.catalog-section { padding: 76px 4vw 96px; border-top: 1.5px solid var(--demo-ink); background: var(--demo-paper); scroll-margin-top: 20px; }
.section-heading { max-width: 1400px; margin: 0 auto 52px; display: flex; align-items: flex-end; justify-content: space-between; gap: 40px; }
.section-kicker { margin: 0 0 15px; color: var(--demo-blue); font-family: 'Courier New', monospace; font-size: 10px; letter-spacing: 1.6px; }
.section-heading h2 { margin: 0; font-size: clamp(34px, 4vw, 57px); line-height: 1.15; letter-spacing: -.04em; }
.section-note { margin: 0 0 6px; color: var(--demo-muted); font-size: 12px; line-height: 1.8; }
.catalog-toolbar { max-width: 1400px; margin: 0 auto; display: grid; grid-template-columns: 190px minmax(0, 1fr); gap: 36px; }
.category-panel > p { margin: 0 0 17px; font-family: 'Courier New', monospace; font-size: 9px; letter-spacing: 1px; }
.category-list { border-top: 1px solid var(--demo-ink); }
.category-list button { width: 100%; padding: 16px 8px; display: flex; align-items: center; justify-content: space-between; border: 0; border-bottom: 1px solid var(--demo-line); background: transparent; font-size: 13px; text-align: left; transition: padding .2s, background .2s, color .2s; }
.category-list button small { color: var(--demo-muted); font-family: 'Courier New', monospace; font-size: 10px; }
.category-list button:hover { padding-left: 13px; color: var(--demo-blue); }
.category-list button.active { padding-left: 13px; background: var(--demo-blue); color: white; }
.category-list button.active small { color: white; }
.category-aside-note { margin-top: 45px; color: var(--demo-muted); font-family: Georgia, serif; font-size: 12px; font-style: italic; line-height: 1.7; }
.category-aside-note span { display: block; color: var(--demo-blue); font-size: 36px; }
.products-tools { margin-bottom: 24px; display: grid; grid-template-columns: minmax(240px, 1fr) auto; align-items: end; gap: 20px; }
.product-search { height: 48px; padding-left: 15px; display: flex; align-items: center; border: 1px solid var(--demo-ink); background: white; }
.product-search > svg { flex: none; width: 18px; color: var(--demo-muted); }
.product-search input { min-width: 0; flex: 1; height: 100%; padding: 0 12px; border: 0; outline: 0; background: transparent; color: var(--demo-ink); font-size: 12px; }
.product-search input::-webkit-search-cancel-button { display: none; }
.clear-search { width: 30px; height: 30px; border: 0; background: transparent; color: var(--demo-muted); font-size: 22px; }
.search-submit { align-self: stretch; padding: 0 20px; border: 0; border-left: 1px solid var(--demo-ink); background: var(--demo-ink); color: white; font-size: 12px; }
.search-submit:hover { background: var(--demo-blue); }
.result-meta { padding-bottom: 4px; display: flex; flex-direction: column; align-items: flex-end; gap: 5px; }
.result-meta span { max-width: 200px; overflow: hidden; color: var(--demo-muted); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.result-meta b { font-family: 'Courier New', monospace; font-size: 10px; letter-spacing: 1px; }
.product-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 20px; }
.product-card { min-width: 0; overflow: hidden; border: 1px solid var(--demo-line); background: #fff; animation: demo-card-in .55s calc(var(--card-index) * 60ms) both; transition: transform .22s, box-shadow .22s, border-color .22s; }
.product-card:hover { border-color: var(--demo-ink); box-shadow: 6px 6px 0 var(--demo-ink); transform: translate(-3px, -3px); }
.product-image-button { position: relative; width: 100%; padding: 0; display: block; border: 0; border-bottom: 1px solid var(--demo-line); background: transparent; text-align: left; }
.product-visual { aspect-ratio: 1.12; overflow: hidden; }
.product-visual :deep(.demo-product-visual) { transition: transform .45s cubic-bezier(.2, .8, .2, 1); }
.product-card:hover .product-visual :deep(.demo-product-visual) { transform: scale(1.035); }
.product-index { position: absolute; top: 13px; left: 13px; padding: 5px 7px; background: rgba(250, 248, 242, .85); font-family: 'Courier New', monospace; font-size: 9px; }
.product-flag { position: absolute; top: 13px; right: 13px; padding: 6px 8px; background: var(--demo-ink); color: white; font-family: 'Courier New', monospace; font-size: 9px; letter-spacing: 1px; }
.product-flag.is-new { background: var(--demo-lime); color: var(--demo-ink); }
.quick-view { position: absolute; right: 12px; bottom: 12px; padding: 8px 11px; background: var(--demo-blue); color: white; font-size: 10px; opacity: 0; transform: translateY(6px); transition: opacity .2s, transform .2s; }
.product-card:hover .quick-view, .product-image-button:focus-visible .quick-view { opacity: 1; transform: translateY(0); }
.product-info { padding: 18px; }
.product-category { margin: 0 0 10px; color: var(--demo-blue); font-family: 'Courier New', monospace; font-size: 8px; letter-spacing: 1.3px; text-transform: uppercase; }
.product-name { width: 100%; min-height: 45px; padding: 0; display: block; border: 0; background: transparent; font-size: 16px; font-weight: 650; line-height: 1.45; text-align: left; }
.product-name:hover { color: var(--demo-blue); }
.product-subtitle { min-height: 34px; margin: 8px 0 17px; display: -webkit-box; overflow: hidden; color: var(--demo-muted); font-size: 10px; line-height: 1.65; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.product-bottom { padding-top: 14px; display: flex; align-items: flex-end; justify-content: space-between; border-top: 1px solid #ecebe6; }
.product-bottom > div { display: flex; flex-direction: column; gap: 4px; }
.product-bottom b { font-family: 'Courier New', monospace; font-size: 18px; }
.product-bottom small { color: var(--demo-muted); font-size: 9px; }
.product-bottom > button { width: 31px; height: 31px; border: 1px solid var(--demo-ink); border-radius: 50%; background: transparent; font-size: 17px; transition: background .2s, color .2s; }
.product-bottom > button:hover { background: var(--demo-blue); color: white; }
.product-skeleton { height: 390px; padding: 18px; border: 1px solid var(--demo-line); background: white; }
.product-skeleton i { display: block; height: 245px; margin: -18px -18px 22px; background: #e8e7e2; animation: demo-pulse 1.2s infinite alternate; }
.product-skeleton span { display: block; width: 80%; height: 14px; margin: 13px 0; background: #e8e7e2; animation: demo-pulse 1.2s infinite alternate; }
.product-skeleton span:last-child { width: 45%; }
.empty-state { min-height: 450px; padding: 60px 25px; display: flex; flex-direction: column; align-items: center; justify-content: center; border: 1px dashed var(--demo-line); text-align: center; }
.empty-state > span { color: var(--demo-blue); font-family: 'Courier New', monospace; font-size: 10px; letter-spacing: 2px; }
.empty-state h3 { margin: 18px 0 10px; font-size: 30px; }
.empty-state p { margin: 0 0 28px; color: var(--demo-muted); font-size: 13px; }
.philosophy-banner { margin: 80px 4vw; padding: 48px; display: grid; grid-template-columns: auto 1.1fr 1fr auto; align-items: center; gap: 42px; background: var(--demo-ink); color: white; scroll-margin-top: 20px; }
.philosophy-symbol { color: var(--demo-lime); font-size: 75px; line-height: 1; }
.philosophy-banner > div:nth-child(2) p { margin: 0 0 14px; color: var(--demo-lime); font-family: 'Courier New', monospace; font-size: 9px; letter-spacing: 2px; }
.philosophy-banner h2 { margin: 0; font-family: Georgia, serif; font-size: clamp(38px, 4vw, 60px); font-weight: 400; line-height: .95; }
.philosophy-banner h2 em { color: var(--demo-lime); font-weight: 400; }
.philosophy-copy { margin: 0; color: #b9bdb6; font-size: 12px; line-height: 1.9; }
.philosophy-banner > button { padding: 13px 0; border: 0; border-bottom: 1px solid #777; background: transparent; color: white; font-size: 12px; white-space: nowrap; }
.philosophy-banner > button span { margin-left: 15px; color: var(--demo-lime); }
.contract-section { margin: 0 4vw 80px; border-top: 1px solid var(--demo-ink); border-bottom: 1px solid var(--demo-ink); scroll-margin-top: 20px; }
.contract-section summary { padding: 22px 0; display: grid; grid-template-columns: 180px 1fr auto; align-items: center; cursor: pointer; list-style: none; }
.contract-section summary::-webkit-details-marker { display: none; }
.contract-section summary span { color: var(--demo-blue); font-family: 'Courier New', monospace; font-size: 9px; letter-spacing: 1.5px; }
.contract-section summary b { font-size: 13px; }
.contract-section summary i { font-size: 22px; font-style: normal; transition: transform .2s; }
.contract-section details[open] summary i { transform: rotate(45deg); }
.contract-content { padding: 5px 0 25px; display: grid; grid-template-columns: repeat(3, 1fr); gap: 20px; }
.contract-content > div { padding: 22px; border: 1px solid var(--demo-line); background: rgba(250, 248, 242, .7); }
.contract-content small { display: block; margin-bottom: 14px; color: var(--demo-muted); font-size: 10px; }
.contract-content code { color: var(--demo-blue); font-family: 'Courier New', monospace; font-size: 12px; }
.contract-content p { margin: 13px 0 0; color: var(--demo-muted); font-size: 11px; line-height: 1.8; }
.contract-content p code { padding: 1px 3px; background: #e5e4de; color: var(--demo-ink); font-size: 10px; }
.contract-note { margin: 0; padding: 0 0 22px; color: var(--demo-muted); font-size: 10px; line-height: 1.7; }
.demo-footer { padding: 35px 4vw; display: flex; align-items: center; justify-content: space-between; gap: 30px; background: var(--demo-ink); color: white; }
.footer-brand { display: flex; align-items: center; gap: 24px; }
.footer-brand b { font-size: 18px; letter-spacing: 3px; }
.footer-brand span, .demo-footer > div:last-child > span { color: #8e948c; font-size: 10px; }
.demo-footer > div:last-child { display: flex; align-items: center; gap: 24px; }
.demo-footer > div:last-child > span { font-family: 'Courier New', monospace; letter-spacing: 1px; }
.demo-footer a { color: var(--demo-lime); font-size: 11px; }
.demo-drawer-overlay { position: fixed; z-index: 5000; inset: 0; background: rgba(15, 17, 16, .58); backdrop-filter: blur(6px); animation: demo-overlay-in .2s both; }
.demo-drawer { position: absolute; top: 0; right: 0; width: min(570px, 100%); height: 100dvh; overflow-y: auto; background: var(--demo-bg); box-shadow: -20px 0 60px rgba(0, 0, 0, .22); animation: demo-drawer-in .3s cubic-bezier(.23, 1, .32, 1) both; }
.drawer-close { position: absolute; z-index: 3; top: 18px; right: 18px; width: 39px; height: 39px; border: 1px solid var(--demo-ink); border-radius: 50%; background: var(--demo-paper); font-size: 25px; line-height: 1; }
.drawer-close:hover { background: var(--demo-lime); }
.drawer-visual { height: 390px; border-bottom: 1px solid var(--demo-ink); }
.drawer-content { padding: 32px 36px 40px; }
.drawer-kicker { margin: 0 0 13px; color: var(--demo-blue); font-family: 'Courier New', monospace; font-size: 9px; letter-spacing: 1.5px; }
.drawer-content h2 { margin: 0; padding-right: 20px; font-size: 28px; line-height: 1.35; letter-spacing: -.03em; }
.drawer-subtitle { margin: 12px 0 0; color: var(--demo-muted); font-size: 12px; line-height: 1.7; }
.drawer-price { margin: 25px 0; display: flex; align-items: baseline; gap: 10px; }
.drawer-price small { color: var(--demo-muted); font-family: 'Courier New', monospace; font-size: 9px; }
.drawer-price b { color: var(--demo-blue); font-family: 'Courier New', monospace; font-size: 31px; }
.drawer-description { margin: 0; padding: 20px 0; border-top: 1px solid var(--demo-line); color: var(--demo-muted); font-size: 12px; line-height: 1.85; }
.detail-loading { padding: 13px; display: flex; align-items: center; gap: 10px; background: #e8e7e0; color: var(--demo-muted); font-size: 11px; }
.detail-loading i { width: 12px; height: 12px; border: 2px solid var(--demo-blue); border-top-color: transparent; border-radius: 50%; animation: demo-spin .8s linear infinite; }
.detail-error { padding: 10px 12px; background: #fff1cf; color: #86600b; font-size: 11px; }
.sku-section { margin-top: 25px; }
.sku-heading { margin-bottom: 12px; display: flex; align-items: center; justify-content: space-between; }
.sku-heading b { font-size: 13px; }
.sku-heading small { color: var(--demo-muted); font-family: 'Courier New', monospace; font-size: 9px; }
.sku-options { display: grid; gap: 9px; }
.sku-options button { padding: 14px 15px; display: flex; align-items: center; justify-content: space-between; gap: 15px; border: 1px solid var(--demo-line); background: var(--demo-paper); text-align: left; }
.sku-options button span { font-size: 12px; }
.sku-options button small { flex: none; color: var(--demo-muted); font-size: 9px; }
.sku-options button.active { border: 1.5px solid var(--demo-blue); background: rgba(49, 85, 239, .06); box-shadow: 3px 3px 0 rgba(49, 85, 239, .18); }
.sku-options button.active span { color: var(--demo-blue); font-weight: 650; }
.spec-list { margin-top: 13px; display: flex; flex-wrap: wrap; gap: 8px; }
.spec-list > span { padding: 7px 9px; background: #e7e6df; font-size: 10px; }
.spec-list small { margin-right: 7px; color: var(--demo-muted); }
.purchase-row { margin: 27px 0 18px; display: flex; align-items: center; justify-content: space-between; }
.quantity-control { height: 39px; display: flex; align-items: center; border: 1px solid var(--demo-ink); background: var(--demo-paper); }
.quantity-control button { width: 38px; height: 100%; border: 0; background: transparent; font-size: 17px; }
.quantity-control button:disabled { color: #b8b8b2; }
.quantity-control span { width: 40px; text-align: center; font-family: 'Courier New', monospace; font-size: 13px; }
.stock-note { color: var(--demo-muted); font-size: 10px; }
.drawer-primary { width: 100%; min-height: 54px; padding: 0 19px; display: flex; align-items: center; justify-content: space-between; border: 1px solid var(--demo-ink); background: var(--demo-blue); color: white; font-size: 13px; font-weight: 650; box-shadow: 4px 4px 0 var(--demo-ink); }
.drawer-primary span { color: var(--demo-lime); font-size: 20px; }
.drawer-primary:hover { background: #2446d8; }
.read-only-note { margin: 16px 0 0; color: var(--demo-muted); font-size: 9px; text-align: center; }
@keyframes demo-overlay-in { from { opacity: 0; } to { opacity: 1; } }
@keyframes demo-drawer-in { from { transform: translateX(100%); } to { transform: translateX(0); } }
@keyframes demo-reveal { from { opacity: 0; transform: translateY(22px); } to { opacity: 1; transform: translateY(0); } }
@keyframes demo-stage-in { from { opacity: 0; transform: translate(20px, 15px) rotate(2deg); } to { opacity: 1; transform: translate(0) rotate(0); } }
@keyframes demo-card-in { from { opacity: 0; transform: translateY(14px); } to { opacity: 1; transform: translateY(0); } }
@keyframes demo-pulse { from { opacity: .45; } to { opacity: 1; } }
@keyframes demo-spin { to { transform: rotate(360deg); } }
@media (min-width: 1600px) {
  .philosophy-banner, .contract-section { max-width: 1400px; margin-left: auto; margin-right: auto; }
}
@media (max-width: 1180px) {
  .demo-nav { grid-template-columns: 1fr auto; }
  .nav-links { display: none; }
  .demo-hero { grid-template-columns: minmax(0, 1fr) minmax(390px, .85fr); gap: 45px; }
  .hero-copy h1 { font-size: clamp(46px, 5.6vw, 67px); }
  .product-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .philosophy-banner { grid-template-columns: auto 1fr 1fr; }
  .philosophy-banner > button { display: none; }
}
@media (max-width: 860px) {
  .demo-hero { padding-top: 42px; grid-template-columns: 1fr; }
  .hero-copy { max-width: 650px; }
  .hero-copy h1 { font-size: clamp(50px, 9vw, 72px); }
  .hero-stage { width: calc(100% - 12px); max-width: 630px; margin: 0 auto; }
  .service-strip { grid-template-columns: 1fr; }
  .service-strip > div { border-right: 1px solid var(--demo-line); border-bottom: 0; }
  .service-strip > div:last-child { border-bottom: 1px solid var(--demo-line); }
  .section-heading { align-items: flex-start; flex-direction: column; }
  .section-note br { display: none; }
  .catalog-toolbar { grid-template-columns: 1fr; }
  .category-panel > p, .category-aside-note { display: none; }
  .category-list { display: flex; overflow-x: auto; border-top: 0; gap: 8px; padding-bottom: 5px; }
  .category-list button { width: auto; flex: none; padding: 10px 14px; border: 1px solid var(--demo-line); }
  .category-list button:hover, .category-list button.active { padding-left: 14px; }
  .category-list button small { margin-left: 12px; }
  .philosophy-banner { grid-template-columns: auto 1fr; gap: 30px; }
  .philosophy-copy { grid-column: 2; }
  .contract-content { grid-template-columns: 1fr; }
  .contract-section summary { grid-template-columns: 130px 1fr auto; }
  .demo-footer, .footer-brand { align-items: flex-start; flex-direction: column; }
}
@media (max-width: 580px) {
  .demo-announcement { justify-content: center; font-size: 8px; }
  .demo-announcement span:last-child { display: none; }
  .demo-nav { padding: 18px 20px; gap: 15px; }
  .brand-mark { width: 36px; height: 36px; }
  .demo-brand b { font-size: 16px; }
  .demo-brand small { font-size: 8px; letter-spacing: 3px; }
  .search-jump { display: none; }
  .source-badge { max-width: 140px; padding: 7px 9px; font-size: 8px; }
  .demo-hero { min-height: auto; padding: 38px 20px 60px; gap: 48px; }
  .hero-copy h1 { font-size: clamp(43px, 12.6vw, 62px); }
  .hero-eyebrow { margin-bottom: 23px; }
  .hero-script { font-size: 17px; }
  .hero-intro br { display: none; }
  .hero-actions { align-items: flex-start; flex-direction: column; gap: 17px; }
  .hero-footnote { margin-top: 36px; flex-direction: column; gap: 6px; }
  .hero-stage { height: 440px; border-radius: 2px 45px 2px 2px; box-shadow: 8px 8px 0 var(--demo-ink); }
  .hero-visual { inset: 58px 10px 90px; }
  .stage-sticker { width: 56px; height: 56px; font-size: 9px; }
  .stage-product { padding: 15px; }
  .stage-product b { max-width: 210px; font-size: 12px; }
  .stage-price { font-size: 15px; }
  .stage-price i { display: none; }
  .service-strip { padding: 0 20px 55px; }
  .catalog-section { padding: 55px 20px 65px; }
  .section-heading { margin-bottom: 35px; gap: 20px; }
  .section-heading h2 { font-size: 37px; }
  .products-tools { grid-template-columns: 1fr; }
  .result-meta { align-items: flex-start; flex-direction: row; justify-content: space-between; }
  .product-grid { grid-template-columns: 1fr; }
  .product-name { min-height: auto; font-size: 18px; }
  .product-subtitle { min-height: auto; }
  .product-visual { aspect-ratio: 1.25; }
  .quick-view { opacity: 1; transform: none; }
  .philosophy-banner { margin: 50px 20px; padding: 32px 25px; grid-template-columns: 1fr; }
  .philosophy-symbol { font-size: 52px; }
  .philosophy-copy { grid-column: 1; }
  .contract-section { margin: 0 20px 55px; }
  .contract-section summary { grid-template-columns: 1fr auto; gap: 8px; }
  .contract-section summary span { grid-column: 1 / -1; }
  .demo-footer { padding: 30px 20px; }
  .demo-footer > div:last-child { align-items: flex-start; flex-direction: column; gap: 12px; }
  .drawer-visual { height: 310px; }
  .drawer-content { padding: 26px 22px 34px; }
  .drawer-content h2 { font-size: 24px; }
  .sku-options button { align-items: flex-start; flex-direction: column; gap: 6px; }
}
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { scroll-behavior: auto !important; animation-duration: .01ms !important; animation-iteration-count: 1 !important; transition-duration: .01ms !important; }
}
</style>
