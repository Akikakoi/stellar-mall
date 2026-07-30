<template>
  <div class="spu-detail-page">

    <main class="container main-content" v-loading="loading">
      <!-- 商品已下架提示 -->
      <div v-if="offShelf" class="off-shelf-notice">
        <div class="off-shelf-icon">📦</div>
        <h2>该商品已下架</h2>
        <p>抱歉，您查看的商品已被商家下架，暂时无法购买。</p>
        <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
      </div>
      <div v-else-if="spu" class="detail-wrap">
        <!-- 商品图片轮播 -->
        <div class="gallery">
          <div v-if="galleryImages.length === 1" class="single-image" @click="openZoom(0)">
            <img :src="galleryImages[0]" :alt="spu.name" onerror="this.src=window.__PH;this.onerror=null" />
            <div class="zoom-trigger">
              <el-icon><ZoomIn /></el-icon>
            </div>
          </div>
          <div v-else class="carousel-wrap">
            <el-carousel
              ref="carouselRef"
              :interval="5000"
              height="420px"
              arrow="hover"
              indicator-position="outside"
              trigger="click"
              :autoplay="galleryImages.length > 1"
            >
              <el-carousel-item v-for="(img, idx) in galleryImages" :key="idx">
                <img
                  :src="img"
                  :alt="`${spu.name} - 图片 ${idx + 1}`"
                  class="carousel-img"
                  @click="openZoom(idx)"
                  onerror="this.src=window.__PH;this.onerror=null"
                />
                <div class="zoom-trigger" @click.stop="openZoom(idx)">
                  <el-icon><ZoomIn /></el-icon>
                </div>
              </el-carousel-item>
            </el-carousel>
          </div>
        </div>

        <!-- 图片放大预览 -->
        <Teleport to="body">
          <div v-if="zoomVisible" class="zoom-overlay" @click="closeZoom" @keydown="handleZoomKeydown" tabindex="0" ref="zoomOverlayRef">
            <div class="zoom-close" @click="closeZoom">
              <el-icon :size="28"><Close /></el-icon>
            </div>
            <div class="zoom-prev" v-if="galleryImages.length > 1" @click.stop="prevImage">
              <el-icon :size="36"><ArrowLeft /></el-icon>
            </div>
            <div class="zoom-body" @click.stop>
              <img :src="galleryImages[zoomIndex]" :alt="`${spu.name} - 大图`" onerror="this.src=window.__PH;this.onerror=null" />
            </div>
            <div class="zoom-next" v-if="galleryImages.length > 1" @click.stop="nextImage">
              <el-icon :size="36"><ArrowRight /></el-icon>
            </div>
            <div class="zoom-counter" v-if="galleryImages.length > 1">{{ zoomIndex + 1 }} / {{ galleryImages.length }}</div>
            <div class="zoom-thumbnails" v-if="galleryImages.length > 1" @click.stop>
              <div
                v-for="(img, idx) in galleryImages"
                :key="'thumb-' + idx"
                class="zoom-thumb"
                :class="{ active: idx === zoomIndex }"
                @click="zoomIndex = idx"
              >
                <img :src="img" :alt="`缩略图 ${idx + 1}`" onerror="this.src=window.__PH;this.onerror=null" />
              </div>
            </div>
          </div>
        </Teleport>
        <div class="info">
          <h1 class="name">{{ spu.name }}</h1>
          <div class="subtitle">{{ spu.description || spu.subTitle || '' }}</div>
          <div class="price-block">
            <span class="label">售价</span>
            <span class="price">¥{{ displayPrice.toFixed(2) }}</span>
            <span v-if="serviceFee > 0" class="service-tip">（含保障服务 ¥{{ serviceFee }}）</span>
          </div>
          <div class="meta-row sale-row">
            <span class="meta-label">销量：</span>
            <span class="sale-count">已售 {{ spu.saleCount || 0 }}</span>
          </div>
          <div class="meta-row">
            <span class="meta-label">分类：</span>
            <span>{{ spu.categoryName || '-' }}</span>
          </div>

          <SkuSpecSelector
            v-if="skus && skus.length"
            :spu="spu"
            :skus="skus"
            v-model="qty"
            v-model:selectedServices="selectedServices"
            :services="skuServices"
            @sku-change="onSkuChange"
            class="sku-selector"
          />
          <div v-else class="sku-section">
            <div class="meta-label" style="color: var(--status-danger);">该商品暂无可购买规格</div>
          </div>

          <div class="action-row">
            <el-button type="primary" size="large" class="btn-cart" :disabled="!selectedSku" @click="handleAddCart">加入购物车</el-button>
            <el-button type="primary" size="large" class="btn-buy" :disabled="!selectedSku" @click="handleBuyNow">立即购买</el-button>
            <el-button size="large" class="btn-fav" :class="{ active: isFav }" @click="toggleFav">
              {{ isFav ? '♥ 已收藏' : '♡ 收藏' }}
            </el-button>
          </div>
        </div>
      </div>
    </main>

    <!-- 评价区域 -->
    <div class="review-section container">
      <div class="review-header">
        <h3>商品评价 ({{ reviewTotal }})</h3>
        <div class="review-avg" v-if="reviewTotal > 0">
          <span class="avg-score">{{ avgRating }}</span>
          <span class="avg-text">分</span>
        </div>
      </div>
      <div class="review-list" v-if="reviews.length > 0">
        <div v-for="r in reviews" :key="r.id" class="review-item">
          <div class="review-user">
            <span class="user-name">{{ r.username || '匿名用户' }}</span>
            <el-rate :model-value="r.rating" disabled size="small" />
          </div>
          <div class="review-content">{{ r.content }}</div>
          <div v-if="r.reply" class="review-reply">
            <span class="reply-label">商家回复：</span>{{ r.reply }}
          </div>
          <div class="review-time">{{ r.createTime?.substring(0, 10) }}</div>

          <!-- 评论区域 -->
          <div class="comment-section">
            <el-button link size="small" @click="toggleComments(r)" class="comment-toggle">
              💬 评论 {{ commentCounts[r.id] || '' }}
            </el-button>
            <div v-if="expandedReviews[r.id]" class="comment-body">
              <div v-if="comments[r.id]?.length" class="comment-list">
                <div v-for="c in comments[r.id]" :key="c.id" class="comment-item">
                  <span class="comment-user">{{ c.username || '匿名用户' }}</span>
                  <span class="comment-text">{{ c.content }}</span>
                  <span class="comment-time">{{ c.createTime?.substring(0, 10) }}</span>
                </div>
              </div>
              <div v-else class="comment-empty">暂无评论</div>
              <div class="comment-input-row">
                <el-input :model-value="commentInputs[r.id] || ''" @update:model-value="val => commentInputs[r.id] = val" placeholder="写下你的评论..." size="small" maxlength="500"
                  @keyup.enter="submitComment(r)" />
                <el-button size="small" type="primary" @click="submitComment(r)">发表</el-button>
              </div>
            </div>
          </div>
        </div>
      </div>
      <el-empty v-else-if="reviewTotal === 0" description="暂无评价" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getSpu, addFavorite, removeFavorite, isFavorited, getReviewComments, submitReviewComment } from '@/api/mall'
import { userRequest } from '@/api/request'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import SkuSpecSelector from '@/components/SkuSpecSelector.vue'

const __PH = window.__PH

const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()
const userStore = useUserStore()

const loading = ref(false)
const offShelf = ref(false)
const spu = ref(null)
const skus = ref([])
const selectedSku = ref(null)
const qty = ref(1)
const isFav = ref(false)
const selectedServices = ref([])

// 演示：保障服务数据，后续可替换为后端接口
const skuServices = ref([
  { id: 'screen_insurance', title: '碎屏险 · 1年', price: 99 },
  { id: 'extend_warranty', title: '延长保修 · 2年', price: 159 },
  { id: 'battery_insurance', title: '电池换新险', price: 49 }
])

const serviceFee = computed(() => {
  return selectedServices.value.reduce((sum, id) => {
    const svc = skuServices.value.find(s => s.id === id)
    return sum + (svc ? svc.price : 0)
  }, 0)
})

const selectedServicesDetail = computed(() => {
  return selectedServices.value.map(id => {
    const svc = skuServices.value.find(s => s.id === id)
    return svc ? { ...svc } : null
  }).filter(Boolean)
})

const displayPrice = computed(() => {
  const base = Number(selectedSku.value?.price || spu.value?.minPrice || 0)
  return base + serviceFee.value
})

// --- 轮播图 ---
const carouselRef = ref(null)
const galleryImages = computed(() => {
  if (!spu.value) return [__PH]
  const images = []
  const main = spu.value.mainImage
  if (main) images.push(main)
  const subs = spu.value.subImages
  if (subs && typeof subs === 'string') {
    subs.split(';').forEach(s => {
      const u = s.trim()
      if (u) images.push(u)
    })
  }
  if (images.length === 0) images.push(__PH)
  return images
})

// 放大预览
const zoomVisible = ref(false)
const zoomIndex = ref(0)
const zoomOverlayRef = ref(null)

function openZoom(index) {
  zoomIndex.value = index
  zoomVisible.value = true
  nextTick(() => { zoomOverlayRef.value?.focus() })
}

function closeZoom() {
  zoomVisible.value = false
}

function prevImage() {
  const len = galleryImages.value.length
  zoomIndex.value = (zoomIndex.value - 1 + len) % len
}

function nextImage() {
  const len = galleryImages.value.length
  zoomIndex.value = (zoomIndex.value + 1) % len
}

function handleZoomKeydown(e) {
  if (e.key === 'Escape') closeZoom()
  if (e.key === 'ArrowLeft') prevImage()
  if (e.key === 'ArrowRight') nextImage()
}

// 阻止背景滚动
watch(zoomVisible, (v) => {
  document.body.style.overflow = v ? 'hidden' : ''
})

onBeforeUnmount(() => {
  document.body.style.overflow = ''
})

function onSkuChange(sku) {
  selectedSku.value = sku
}

// Reviews
const reviews = ref([])
const reviewTotal = ref(0)
const avgRating = ref(0)

// Review comments
const expandedReviews = ref({})
const comments = ref({})
const commentCounts = ref({})
const commentInputs = ref({})

async function toggleComments(review) {
  const rid = review.id
  if (expandedReviews.value[rid]) {
    expandedReviews.value[rid] = false
    return
  }
  expandedReviews.value[rid] = true
  if (!comments.value[rid]) {
    try {
      const res = await getReviewComments(rid)
      const list = Array.isArray(res) ? res : (res?.data || res?.records || [])
      comments.value[rid] = list
      commentCounts.value[rid] = list.length
    } catch (e) {
      comments.value[rid] = []
    }
  }
}

async function submitComment(review) {
  const rid = review.id
  const content = commentInputs.value[rid]?.trim()
  if (!content) return
  try {
    await submitReviewComment(rid, content)
    commentInputs.value[rid] = ''
    const res = await getReviewComments(rid)
    const list = Array.isArray(res) ? res : (res?.data || res?.records || [])
    comments.value[rid] = list
    commentCounts.value[rid] = list.length
    ElMessage.success('评论成功')
  } catch (e) {
    ElMessage.error('评论失败，请先登录')
  }
}

async function loadReviews() {
  try {
    const res = await userRequest({ url: `/user/review/spu/${route.params.id}`, method: 'get', params: { page: 1, pageSize: 20 }, __silent: true })
    const d = res?.data || res || {}
    reviews.value = d.records || d.list || []
    reviewTotal.value = d.total || 0
  } catch (e) {}
  try {
    const res = await userRequest({ url: `/user/review/spu/${route.params.id}/rating`, method: 'get', __silent: true })
    avgRating.value = Number(res || 0).toFixed(1)
  } catch (e) {}
}

async function loadDetail() {
  loading.value = true
  offShelf.value = false
  try {
    const res = await getSpu(route.params.id)
    spu.value = res || null
    skus.value = res?.skus || res?.skuList || []
    selectedServices.value = []
    await checkFav()
  } catch (e) {
    const msg = (e?.response?.data?.msg) || e?.message || ''
    if (msg.includes('已下架')) {
      offShelf.value = true
    }
    spu.value = null
    skus.value = []
  } finally {
    loading.value = false
  }
}

async function checkFav() {
  if (!userStore.token) return
  try {
    const res = await isFavorited(route.params.id)
    isFav.value = res === true
  } catch (e) {
    // ignore
  }
}

async function toggleFav() {
  if (!userStore.token) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  try {
    const spuId = Number(route.params.id)
    if (isFav.value) {
      await removeFavorite(spuId)
      isFav.value = false
      ElMessage.success('已取消收藏')
    } else {
      await addFavorite(spuId)
      isFav.value = true
      ElMessage.success('已添加收藏')
    }
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

async function handleAddCart() {
  const sku = selectedSku.value
  if (!sku || !sku.id) {
    ElMessage.warning('请先选择商品规格')
    return
  }
  try {
    await cartStore.add({
      skuId: Number(sku.id),
      spuId: Number(spu.value?.id),
      skuName: sku.name || spu.value?.name,
      skuPrice: sku.price || spu.value?.minPrice,
      pic: sku.image || spu.value?.mainImage,
      qty: Number(qty.value) || 1,
      quantity: Number(qty.value) || 1,
      services: selectedServicesDetail.value,
      serviceFee: serviceFee.value
    })
    ElMessage.success('已加入购物车')
  } catch (e) {
    const msg = e?.message || e?.msg || ''
    if (!msg.includes('SKU不存在') && !msg.includes('停售')) {
      ElMessage.success('已加入购物车 (本地)')
    }
  }
}

function handleBuyNow() {
  const sku = selectedSku.value
  if (!sku || !sku.id) {
    ElMessage.warning('请先选择商品规格')
    return
  }
  const item = {
    skuId: Number(sku.id),
    spuId: Number(spu.value?.id),
    name: sku.name || spu.value?.name || '',
    price: Number(sku.price || spu.value?.minPrice || 0),
    image: sku.image || spu.value?.mainImage || '',
    quantity: Number(qty.value) || 1,
    services: selectedServicesDetail.value,
    serviceFee: serviceFee.value
  }
  router.push({ path: '/order/submit', query: { direct: JSON.stringify([item]) } })
}

onMounted(() => { loadDetail(); loadReviews() })
</script>

<style scoped>
.spu-detail-page { min-height: 100vh; }
.container { max-width: 1200px; margin: 0 auto; padding: 0 20px; }

.off-shelf-notice {
  text-align: center;
  padding: 80px 40px;
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-xl);
}
.off-shelf-icon { font-size: 64px; margin-bottom: 20px; }
.off-shelf-notice h2 { font-size: 24px; color: var(--text-primary); margin: 0 0 12px; }
.off-shelf-notice p { font-size: 15px; color: var(--text-muted); margin: 0 0 28px; }

.main-content { padding: 28px 20px 60px; }
.detail-wrap {
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-xl);
  padding: 32px;
  display: grid;
  grid-template-columns: 500px 1fr;
  gap: 48px;
  box-shadow: var(--shadow-sm);
}

/* ===== 图片轮播 ===== */
.gallery {
  position: relative;
  background: #ffffff;
  border-radius: var(--radius-xl);
  overflow: hidden;
}

/* 单图 */
.single-image {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
  cursor: zoom-in;
  background: #ffffff;
  border-radius: var(--radius-xl);
  overflow: hidden;
}
.single-image img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}

/* 轮播容器 */
.carousel-wrap {
  width: 100%;
  position: relative;
  background: #ffffff;
}
.carousel-wrap :deep(.el-carousel),
.carousel-wrap :deep(.el-carousel__container) {
  border-radius: var(--radius-xl);
  overflow: hidden;
}
.carousel-wrap :deep(.el-carousel) {
  background: transparent;
}
/* 箭头：透明背景，仅灰色 chevron */
.carousel-wrap :deep(.el-carousel__arrow) {
  background: transparent;
  color: rgba(0, 0, 0, 0.45);
  width: 44px;
  height: 44px;
  border-radius: 0;
  box-shadow: none;
  transition: color 0.2s;
}
.carousel-wrap :deep(.el-carousel__arrow:hover) {
  background: transparent;
  color: rgba(0, 0, 0, 0.8);
}
.carousel-wrap :deep(.el-carousel__arrow i) {
  font-size: 22px;
  font-weight: 600;
}
/* 指示器：短横线 */
.carousel-wrap :deep(.el-carousel__indicators) {
  margin-top: 14px;
}
.carousel-wrap :deep(.el-carousel__indicator) {
  padding: 6px 3px;
}
.carousel-wrap :deep(.el-carousel__indicator .el-carousel__button) {
  width: 20px;
  height: 2px;
  border-radius: 0;
  background: rgba(0, 0, 0, 0.15);
  opacity: 1;
  transition: all 0.25s ease;
}
.carousel-wrap :deep(.el-carousel__indicator.is-active .el-carousel__button) {
  width: 36px;
  height: 2px;
  background: rgba(0, 0, 0, 0.7);
}
/* 暗色主题适配 */
:global(html.theme-dark) .carousel-wrap,
:global(html.theme-dark) .gallery,
:global(html.theme-dark) .single-image {
  background: #1d1d1f;
}
:global(html.theme-dark) .carousel-wrap :deep(.el-carousel__arrow) {
  color: rgba(255, 255, 255, 0.5);
}
:global(html.theme-dark) .carousel-wrap :deep(.el-carousel__arrow:hover) {
  color: rgba(255, 255, 255, 0.9);
}
:global(html.theme-dark) .carousel-wrap :deep(.el-carousel__indicator .el-carousel__button) {
  background: rgba(255, 255, 255, 0.2);
}
:global(html.theme-dark) .carousel-wrap :deep(.el-carousel__indicator.is-active .el-carousel__button) {
  background: rgba(255, 255, 255, 0.75);
}

.carousel-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  cursor: zoom-in;
  display: block;
  background: #ffffff;
}

/* 放大按钮 */
.zoom-trigger {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.25s, background 0.25s;
  z-index: 5;
}
.gallery:hover .zoom-trigger,
.single-image:hover .zoom-trigger,
.carousel-wrap:hover .zoom-trigger {
  opacity: 1;
}
.zoom-trigger:hover {
  background: rgba(0, 0, 0, 0.7);
}

/* ===== 放大预览遮罩 ===== */
.zoom-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: rgba(0, 0, 0, 0.92);
  display: flex;
  align-items: center;
  justify-content: center;
  outline: none;
  animation: zoomFadeIn 0.2s ease;
}
@keyframes zoomFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.zoom-close {
  position: absolute;
  top: 20px;
  right: 24px;
  color: rgba(255, 255, 255, 0.8);
  cursor: pointer;
  z-index: 10;
  transition: color 0.2s;
  padding: 8px;
}
.zoom-close:hover { color: #fff; }

.zoom-prev,
.zoom-next {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  color: rgba(255, 255, 255, 0.7);
  cursor: pointer;
  z-index: 10;
  padding: 12px;
  transition: color 0.2s;
}
.zoom-prev:hover,
.zoom-next:hover { color: #fff; }
.zoom-prev { left: 16px; }
.zoom-next { right: 16px; }

.zoom-body {
  max-width: 80vw;
  max-height: 72vh;
  display: flex;
  align-items: center;
  justify-content: center;
}
.zoom-body img {
  max-width: 100%;
  max-height: 72vh;
  object-fit: contain;
  border-radius: 4px;
  user-select: none;
  -webkit-user-drag: none;
}

.zoom-counter {
  position: absolute;
  bottom: 100px;
  left: 50%;
  transform: translateX(-50%);
  color: rgba(255, 255, 255, 0.6);
  font-size: 14px;
  font-variant-numeric: tabular-nums;
}

.zoom-thumbnails {
  position: absolute;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 8px;
  padding: 6px 12px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: var(--radius-sm);
}
.zoom-thumb {
  width: 56px;
  height: 56px;
  border-radius: 6px;
  overflow: hidden;
  border: 2px solid transparent;
  cursor: pointer;
  opacity: 0.5;
  transition: all 0.2s;
  flex-shrink: 0;
}
.zoom-thumb:hover { opacity: 0.8; }
.zoom-thumb.active {
  opacity: 1;
  border-color: #fff;
}
.zoom-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.info { display: flex; flex-direction: column; }
.name { font-size: 24px; color: var(--text-primary); margin: 0 0 8px; }
.subtitle { color: var(--text-muted); margin-bottom: 20px; min-height: 22px; }
.price-block {
  padding: 0 0 20px;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--border-subtle);
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.price-block .label { color: var(--text-muted); font-size: 14px; }
.price-block .price { color: var(--text-primary); font-size: 32px; font-weight: 700; }
.price-block .service-tip { color: var(--brand-primary); font-size: 13px; margin-left: 8px; }
.meta-row { margin-bottom: 12px; color: var(--text-secondary); }
.sale-row .sale-count { color: var(--brand-primary); font-weight: 600; }
.meta-label { color: var(--text-muted); display: inline-block; min-width: 70px; }
.sku-section { margin: 16px 0 20px; }
.sku-capsules { margin-top: 10px; display: flex; flex-wrap: wrap; gap: 10px; }
.sku-capsule {
  padding: 8px 18px;
  border-radius: var(--radius-xl);
  border: 1px solid var(--border-base);
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}
.sku-capsule:hover { border-color: var(--brand-primary); color: var(--text-primary); }
.sku-capsule.active { border-color: var(--brand-primary); background: var(--brand-primary-soft); color: var(--brand-primary); }
.sku-info { margin-top: 12px; color: var(--text-secondary); font-size: 14px; }
.qty-row { margin: 16px 0 24px; display: flex; align-items: center; gap: 14px; }
.sku-selector { margin: 20px 0 24px; }

.action-row { display: flex; gap: 14px; margin-top: auto; }
.btn-cart, .btn-buy { min-width: 160px; }
.btn-fav {
  min-width: 120px;
  border: 1px solid var(--border-base);
  color: var(--text-secondary);
  background: var(--bg-card);
  transition: all 0.2s;
}
.btn-fav.active {
  border-color: var(--status-danger);
  color: var(--status-danger);
  background: rgba(255, 59, 48, 0.08);
}
.btn-fav:hover { border-color: var(--status-danger); color: var(--status-danger); }

/* Reviews */
.review-section { max-width: 1200px; margin: 0 auto; padding: 0 20px 40px; }
.review-header { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; }
.review-header h3 { margin: 0; color: var(--text-primary); font-size: 18px; }
.review-avg { display: flex; align-items: baseline; }
.avg-score { font-size: 28px; font-weight: 700; color: var(--text-primary); }
.avg-text { font-size: 14px; color: var(--text-muted); margin-left: 2px; }
.review-list { display: flex; flex-direction: column; gap: 12px; }
.review-item {
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-md);
  padding: 16px 20px;
}
.review-user { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; }
.user-name { color: var(--text-primary); font-weight: 500; }
.review-content { color: var(--text-secondary); font-size: 14px; line-height: 1.6; margin-bottom: 6px; }
.review-reply {
  background: var(--bg-hover);
  border-left: 3px solid var(--brand-primary);
  padding: 8px 12px;
  margin: 8px 0;
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
  font-size: 13px;
  color: var(--text-secondary);
}
.reply-label { color: var(--brand-primary); font-weight: 600; }
.review-time { color: var(--text-muted); font-size: 12px; }

.comment-section { margin-top: 10px; }
.comment-toggle { padding: 2px 0; color: var(--text-muted); font-size: 13px; }
.comment-body { margin-top: 8px; }
.comment-list { display: flex; flex-direction: column; gap: 6px; margin-bottom: 10px; }
.comment-item {
  background: var(--bg-hover);
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 13px;
}
.comment-user { color: var(--brand-primary); font-weight: 600; white-space: nowrap; }
.comment-text { color: var(--text-secondary); flex: 1; }
.comment-time { color: var(--text-muted); font-size: 11px; white-space: nowrap; }
.comment-empty { color: var(--text-muted); font-size: 13px; margin-bottom: 8px; }
.comment-input-row { display: flex; gap: 8px; align-items: center; }
.comment-input-row .el-input { flex: 1; }

/* ===== 响应式 ===== */
@media (max-width: 1024px) {
  .detail-wrap {
    grid-template-columns: 400px 1fr;
    gap: 32px;
    padding: 24px;
  }
  .carousel-wrap :deep(.el-carousel__container) {
    height: 380px !important;
  }
}

@media (max-width: 768px) {
  .detail-wrap {
    grid-template-columns: 1fr;
    gap: 24px;
    padding: 16px;
  }
  .gallery {
    max-width: 100%;
  }
  .carousel-wrap :deep(.el-carousel__container) {
    height: 320px !important;
  }
  .carousel-wrap :deep(.el-carousel__arrow) {
    width: 36px;
    height: 36px;
  }
  .carousel-wrap :deep(.el-carousel__arrow i) {
    font-size: 18px;
  }
  .carousel-wrap :deep(.el-carousel__indicator .el-carousel__button) {
    width: 16px;
  }
  .carousel-wrap :deep(.el-carousel__indicator.is-active .el-carousel__button) {
    width: 28px;
  }
  .name { font-size: 20px; }
  .price-block .price { font-size: 26px; }
  .action-row { flex-wrap: wrap; }
  .btn-cart, .btn-buy { min-width: 0; flex: 1; }

  .zoom-body {
    max-width: 94vw;
    max-height: 60vh;
  }
  .zoom-body img { max-height: 60vh; }
  .zoom-prev { left: 4px; padding: 8px; }
  .zoom-next { right: 4px; padding: 8px; }
  .zoom-thumbnails {
    gap: 4px;
    padding: 4px 8px;
  }
  .zoom-thumb {
    width: 40px;
    height: 40px;
  }
  .zoom-counter { bottom: 90px; }
}
</style>
