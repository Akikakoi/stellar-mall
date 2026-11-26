<template>
  <section v-if="visible" ref="railRef" class="recommend-rail">
    <div class="rail-head">
      <div class="rail-heading">
        <h2 class="rail-title">{{ title }}</h2>
        <p v-if="displayNote && !hideNote" class="rail-note">{{ displayNote }}</p>
      </div>
      <span class="rail-badge" :class="{ personalized }">{{ personalized ? '为你推荐' : '热门推荐' }}</span>
    </div>

    <div v-if="loading" class="rec-grid">
      <div v-for="i in limit" :key="`sk-${i}`" class="rec-card skeleton">
        <div class="rec-img-wrap sk-img"></div>
        <div class="rec-body">
          <div class="sk-line"></div>
          <div class="sk-line short"></div>
        </div>
      </div>
    </div>

    <div v-else class="rec-grid">
      <article
        v-for="(item, index) in items"
        :key="item.id"
        class="rec-card"
        @click="goDetail(item, index)"
      >
        <div class="rec-img-wrap">
          <img :src="ossThumb(item.mainImage, 480) || __PH" :alt="item.name" loading="lazy" v-placeholder />
        </div>
        <div class="rec-body">
          <h3 class="rec-name" :title="item.name">{{ item.name }}</h3>
          <p class="rec-reason" :title="item.reason">{{ item.reason }}</p>
          <div class="rec-bottom">
            <span class="rec-price">¥{{ Number(item.minPrice || 0).toFixed(2) }}</span>
            <span class="rec-cat">{{ item.categoryName }}</span>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getRecommendations } from '@/api/mall'
import type { RecommendItem } from '@/types/models'
import { ossThumb } from '@/utils/imgThumb'
import { getDeviceId, track } from '@/utils/tracker'

const props = withDefaults(defineProps<{
  title?: string
  limit?: number
  excludeIds?: number[]
  seedSpuId?: number
  /** 只展示不高于该价格的商品（购物车凑单用） */
  maxPrice?: number
  note?: string
  /** 隐藏标题下方的说明文字 */
  hideNote?: boolean
  /** 埋点场景：recommend_home / recommend_detail / recommend_cart */
  scene?: string
  /** 点击商品是否新开标签页（推荐位统一默认新开） */
  openInNewTab?: boolean
  /** 是否允许推荐已交互商品（购物车凑单用） */
  includeInteracted?: boolean
}>(), {
  title: '猜你喜欢',
  limit: 8,
  excludeIds: () => [],
  seedSpuId: undefined,
  maxPrice: undefined,
  note: '',
  hideNote: false,
  scene: 'recommend_home',
  openInNewTab: true,
  includeInteracted: false
})

const router = useRouter()
const __PH = (window as any).__PH || ''
const items = ref<RecommendItem[]>([])
const loading = ref(true)
const personalized = ref(false)
const apiNote = ref('')
/** 后端 A/B 分桶（A/B），随曝光与点击埋点一起上报，供按桶计算 CTR */
const abBucket = ref<string | null>(null)
const railRef = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null
let exposureTracked = false

const visible = computed(() => loading.value || items.value.length > 0)
const displayNote = computed(() => {
  if (props.note) return props.note
  if (apiNote.value) return apiNote.value
  return personalized.value ? '根据你的浏览与购买偏好推荐' : '按近期热度为你推荐'
})

function trackExposure() {
  if (exposureTracked || items.value.length === 0) return
  exposureTracked = true
  track('view_item_list', {
    scene: props.scene,
    extra: {
      module: props.title,
      itemIds: items.value.map((item) => item.id),
      sources: items.value.map((item) => item.source),
      personalized: personalized.value,
      abBucket: abBucket.value,
      seedSpuId: props.seedSpuId ?? null,
      maxPrice: props.maxPrice ?? null
    }
  })
}

/** 只有推荐位真正进入视口才记曝光，避免首页底部推荐位“未看先算”。 */
function maybeTrackExposure() {
  if (exposureTracked || items.value.length === 0) return
  const el = railRef.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  if (rect.top < window.innerHeight && rect.bottom > 0) trackExposure()
}

function setupObserver() {
  if (typeof IntersectionObserver === 'undefined' || !railRef.value) {
    maybeTrackExposure()
    return
  }
  observer = new IntersectionObserver((entries) => {
    if (entries.some((entry) => entry.isIntersecting)) maybeTrackExposure()
  }, { threshold: 0.35 })
  observer.observe(railRef.value)
}

async function load() {
  loading.value = true
  try {
    const excluded = new Set(props.excludeIds || [])
    const limit = Math.max(1, Math.min(props.limit, 30))
    // 有价格上限时多取一些候选，过滤后仍尽量填满展示位
    const requestLimit = props.maxPrice != null ? 30 : Math.min(30, limit + excluded.size)
    const res = await getRecommendations(requestLimit, props.seedSpuId, props.maxPrice, getDeviceId(), props.includeInteracted)
    items.value = (res?.items || [])
      .filter((item) => !excluded.has(item.id))
      .filter((item) => props.maxPrice == null || Number(item.minPrice || 0) <= props.maxPrice)
      .slice(0, limit)
    personalized.value = !!res?.personalized
    abBucket.value = res?.abBucket ?? null
    apiNote.value = res?.note || ''
  } catch {
    items.value = []
  } finally {
    loading.value = false
    nextTick(maybeTrackExposure)
  }
}

function goDetail(item: RecommendItem, index: number) {
  track('view_item', {
    spuId: item.id,
    categoryId: item.categoryId,
    scene: props.scene,
    position: index + 1,
    amount: Number(item.minPrice || 0),
    extra: {
      module: props.title,
      source: item.source,
      reason: item.reason,
      personalized: personalized.value,
      abBucket: abBucket.value
    }
  })
  const href = router.resolve(`/spu/${item.id}`).href
  if (props.openInNewTab) {
    window.open(href, '_blank', 'noopener,noreferrer')
  } else {
    router.push(href)
  }
}

onMounted(() => {
  void load()
  nextTick(setupObserver)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
})
</script>

<style scoped>
.recommend-rail {
  max-width: 1200px;
  margin: 12px auto 56px;
  padding: 0 20px;
}

.rail-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.rail-heading { min-width: 0; }

.rail-title {
  margin: 0;
  font-size: 22px;
  line-height: 1.2;
  font-weight: 700;
  color: var(--text-primary);
}

.rail-note {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--text-muted);
}

.rail-badge {
  flex: none;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  color: var(--text-muted);
  background: var(--bg-hover);
  border: 1px solid var(--border-base);
}

.rail-badge.personalized {
  color: var(--brand-primary);
  border-color: color-mix(in srgb, var(--brand-primary) 35%, transparent);
  background: color-mix(in srgb, var(--brand-primary) 10%, transparent);
}

.rec-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
}

.rec-card {
  min-width: 0;
  overflow: hidden;
  cursor: pointer;
  border-radius: 14px;
  border: 1px solid var(--border-base);
  background: var(--bg-card);
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
}

.rec-card:hover {
  transform: translateY(-4px);
  border-color: color-mix(in srgb, var(--brand-primary) 28%, var(--border-base));
  box-shadow: 0 14px 32px rgba(0, 0, 0, 0.08);
}

.rec-img-wrap {
  position: relative;
  aspect-ratio: 1 / 1;
  overflow: hidden;
  background: var(--bg-hover);
}

.rec-img-wrap img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.45s ease;
}

.rec-card:hover .rec-img-wrap img { transform: scale(1.04); }

.rec-body { padding: 12px 14px 14px; }

.rec-name {
  margin: 0;
  font-size: 14px;
  line-height: 1.4;
  font-weight: 600;
  color: var(--text-primary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 39px;
}

.rec-reason {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.4;
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rec-bottom {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-top: 10px;
}

.rec-price {
  font-size: 17px;
  font-weight: 700;
  color: var(--status-danger);
}

.rec-cat {
  flex: none;
  font-size: 11px;
  color: var(--text-muted);
}

/* 骨架 */
.rec-card.skeleton { cursor: default; }
.rec-card.skeleton:hover { transform: none; box-shadow: none; }
.sk-img {
  background: linear-gradient(90deg, var(--bg-hover) 25%, var(--bg-card) 50%, var(--bg-hover) 75%);
  background-size: 200% 100%;
  animation: rec-shimmer 1.4s linear infinite;
}
.sk-line {
  height: 12px;
  margin-top: 10px;
  border-radius: 6px;
  background: linear-gradient(90deg, var(--bg-hover) 25%, var(--bg-card) 50%, var(--bg-hover) 75%);
  background-size: 200% 100%;
  animation: rec-shimmer 1.4s linear infinite;
}
.sk-line.short { width: 62%; }

@keyframes rec-shimmer {
  from { background-position: 200% 0; }
  to { background-position: -200% 0; }
}

@media (max-width: 1100px) {
  .rec-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}

@media (max-width: 760px) {
  .recommend-rail { padding: 0 14px; margin-bottom: 40px; }
  .rail-title { font-size: 19px; }
  .rec-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
  .rec-name { font-size: 13px; min-height: 36px; }
  .rec-price { font-size: 15px; }
}

@media (prefers-reduced-motion: reduce) {
  .rec-card, .rec-img-wrap img { transition: none; }
  .sk-img, .sk-line { animation: none; }
}
</style>
