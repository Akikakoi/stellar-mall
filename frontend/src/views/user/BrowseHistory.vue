<template>
  <div class="browse-page">

    <main class="container main-content">
      <div class="page-header">
        <h2 class="page-title">浏览历史</h2>
        <div class="header-actions">
          <el-button size="small" plain :disabled="!records.length" @click="handleClear">
            清空全部
          </el-button>
        </div>
      </div>

      <div class="browse-grid" v-loading="loading">
        <div v-for="item in records" :key="item.id" class="browse-card"
             :class="{ 'is-delisted': isDelisted(item) }" @click="goDetail(item.spuId)">
          <div class="browse-image">
            <img :src="item.spuImage || __PH" :alt="item.spuName"
                 v-placeholder />
            <div v-if="isDelisted(item)" class="delisted-mask">
              <span class="delisted-tag">已下架</span>
            </div>
          </div>
          <div class="browse-info">
            <h3 class="browse-name" :title="item.spuName">{{ item.spuName || '该商品已下架' }}</h3>
            <div class="browse-meta">
              <div class="browse-time">浏览于 {{ formatTime(item.browseTime) }}</div>
              <div class="browse-price">¥{{ Number(item.minPrice || 0).toFixed(2) }}</div>
            </div>
            <div class="browse-actions">
              <el-button size="small" type="primary" plain @click.stop="goDetail(item.spuId)">
                查看详情
              </el-button>
              <el-button size="small" plain @click.stop="handleRemove(item)">
                删除
              </el-button>
            </div>
          </div>
        </div>

        <el-empty v-if="!loading && records.length === 0" class="glass-empty" description="暂无浏览记录">
          <el-button type="primary" @click="router.push('/')">去逛逛</el-button>
        </el-empty>
      </div>

      <div v-if="total > pageSize" class="pager-wrapper">
        <el-pagination background layout="prev, pager, next" :total="total" :page-size="pageSize"
                       v-model:current-page="page" @current-change="loadRecords" />
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'
import { listBrowseHistory, deleteBrowseHistory, clearBrowseHistory } from '@/api/mall'
import { ElMessage, ElMessageBox } from 'element-plus'

const __PH = window.__PH

const router = useRouter()
const userStore = useUserStore()
const cartStore = useCartStore()

const loading = ref(false)
const records = ref<any[]>([])
const page = ref(1)
const pageSize = ref(12)
const total = ref(0)

/** 已下架/已删除判断：status 为 null 或 0 视为下架/失效 */
function isDelisted(item: any) {
  return item.spuStatus == null || Number(item.spuStatus) === 0
}

/** 时间显示：YYYY-MM-DD HH:mm */
function formatTime(t?: string) {
  if (!t) return '-'
  const d = new Date(t)
  if (isNaN(d.getTime())) return t
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/** 分页加载浏览历史 */
async function loadRecords() {
  loading.value = true
  try {
    const res = await listBrowseHistory({ pageNum: page.value, pageSize: pageSize.value })
    records.value = res?.records || []
    total.value = res?.total || 0
  } catch (e: any) {
    records.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 打开商品详情页（新标签页） */
function goDetail(spuId: any) {
  const route = router.resolve(`/spu/${spuId}`)
  window.open(route.href, '_blank')
}

/** 删除单条浏览记录 */
async function handleRemove(item: any) {
  try {
    await ElMessageBox.confirm(`确定删除「${item.spuName || '该商品'}」的浏览记录？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteBrowseHistory(item.id)
    ElMessage.success('已删除')
    records.value = records.value.filter((r: any) => r.id !== item.id)
    total.value = Math.max(total.value - 1, 0)
    if (records.value.length === 0 && page.value > 1) {
      page.value -= 1
      await loadRecords()
    }
  } catch (e: any) {
    ElMessage.error('删除失败')
  }
}

/** 清空全部浏览记录 */
async function handleClear() {
  try {
    await ElMessageBox.confirm('确定清空全部浏览记录吗？此操作不可恢复。', '清空浏览历史', {
      confirmButtonText: '清空',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await clearBrowseHistory()
    ElMessage.success('已清空')
    records.value = []
    total.value = 0
    page.value = 1
  } catch (e: any) {
    ElMessage.error('清空失败')
  }
}

onMounted(async () => {
  if (userStore.token && !userStore.nickname) {
    try { await userStore.fetchProfile() } catch (e: any) {}
  }
  try { await cartStore.load() } catch (e: any) {}
  await loadRecords()
})
</script>

<style scoped>
.browse-page { min-height: 100vh; }
.container { max-width: 1200px; margin: 0 auto; padding: 0 20px; }

.main-content { padding: 28px 20px 60px; }
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  flex-wrap: wrap;
  gap: 12px;
}
.page-title { font-size: 24px; color: var(--text-primary); margin: 0; }

.browse-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 18px;
}
@media (max-width: 1200px) { .browse-grid { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 800px) { .browse-grid { grid-template-columns: repeat(2, 1fr); } }

.browse-card {
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-lg);
  overflow: hidden;
  cursor: pointer;
  transition: transform var(--transition-base), box-shadow var(--transition-base);
}
.browse-card:hover { transform: var(--hover-lift); box-shadow: var(--shadow-md); }
.browse-card.is-delisted { opacity: 0.75; }

.browse-image {
  position: relative;
  aspect-ratio: 1;
  overflow: hidden;
  background: var(--bg-hover);
}
.browse-image img { width: 100%; height: 100%; object-fit: cover; transition: transform 0.4s ease; }
.browse-card:hover .browse-image img { transform: scale(1.05); }
.delisted-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: blur(2px);
}
.delisted-tag {
  padding: 6px 16px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 14px;
  letter-spacing: 2px;
}
.browse-info { padding: 12px 14px 16px; }
.browse-name {
  font-size: 14px;
  color: var(--text-primary);
  margin: 0 0 8px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 20px;
}
.is-delisted .browse-name { color: var(--text-muted); text-decoration: line-through; }
.browse-meta {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 12px;
}
.browse-time { font-size: 12px; color: var(--text-muted); }
.browse-price { color: var(--text-primary); font-size: 16px; font-weight: 700; }
.browse-actions { display: flex; gap: 10px; }

.pager-wrapper { margin-top: 28px; display: flex; justify-content: center; }
</style>