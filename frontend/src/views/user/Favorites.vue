<template>
  <div class="favorites-page">

    <main class="container main-content">
      <div class="page-header">
        <h2 class="page-title">我的收藏</h2>
        <div class="layout-controls">
          <span class="control-label">每行显示</span>
          <el-radio-group v-model="colsPerRow" size="small" @change="saveLayout">
            <el-radio-button :value="2">2</el-radio-button>
            <el-radio-button :value="3">3</el-radio-button>
            <el-radio-button :value="4">4</el-radio-button>
            <el-radio-button :value="5">5</el-radio-button>
          </el-radio-group>
        </div>
      </div>

      <div class="fav-grid" :style="{ gridTemplateColumns: `repeat(${colsPerRow}, 1fr)` }" v-loading="loading">
        <div v-for="item in favorites" :key="item.id" class="fav-card">
          <div class="fav-image" @click="goDetail(item.spuId)">
            <img :src="item.spuImage || __PH" :alt="item.spuName" onerror="this.src=window.__PH;this.onerror=null" />
          </div>
          <div class="fav-info">
            <h3 class="fav-name" @click="goDetail(item.spuId)">{{ item.spuName }}</h3>
            <div class="fav-price">¥{{ Number(item.minPrice || 0).toFixed(2) }}</div>
            <div class="fav-actions">
              <el-button size="small" type="primary" @click="goDetail(item.spuId)">查看详情</el-button>
              <el-button size="small" plain @click="handleRemove(item)">取消收藏</el-button>
            </div>
          </div>
        </div>
        <el-empty v-if="!loading && favorites.length === 0" description="暂无收藏商品">
          <el-button type="primary" @click="router.push('/')">去逛逛</el-button>
        </el-empty>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'
import { listFavorites, removeFavorite } from '@/api/mall'
import { ElMessage, ElMessageBox } from 'element-plus'

const __PH = window.__PH

const router = useRouter()
const userStore = useUserStore()
const cartStore = useCartStore()

const loading = ref(false)
const favorites = ref([])

const LAYOUT_KEY = 'stellar_fav_cols'
const colsPerRow = ref(Number(localStorage.getItem(LAYOUT_KEY)) || 4)

function saveLayout() {
  localStorage.setItem(LAYOUT_KEY, colsPerRow.value)
}

async function loadFavorites() {
  loading.value = true
  try {
    const res = await listFavorites()
    favorites.value = Array.isArray(res) ? res : []
  } catch (e) {
    favorites.value = []
  } finally {
    loading.value = false
  }
}

function goDetail(spuId) {
  const route = router.resolve(`/spu/${spuId}`)
  window.open(route.href, '_blank')
}

async function handleRemove(item) {
  try {
    await ElMessageBox.confirm(`确定取消收藏「${item.spuName}」？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await removeFavorite(item.spuId)
    ElMessage.success('已取消收藏')
    favorites.value = favorites.value.filter(f => f.id !== item.id)
  } catch (e) {
    ElMessage.error('取消收藏失败')
  }
}

onMounted(async () => {
  if (userStore.token && !userStore.nickname) {
    try { await userStore.fetchProfile() } catch (e) {}
  }
  try { await cartStore.load() } catch (e) {}
  await loadFavorites()
})
</script>

<style scoped>
.favorites-page { min-height: 100vh; }
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
.layout-controls {
  display: flex;
  align-items: center;
  gap: 10px;
}
.control-label { font-size: 13px; color: var(--text-secondary); }

.fav-grid {
  display: grid;
  gap: 18px;
}
.fav-card {
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: transform var(--transition-base), box-shadow var(--transition-base);
}
.fav-card:hover { transform: var(--hover-lift); box-shadow: var(--shadow-md); }
.fav-image {
  aspect-ratio: 1;
  overflow: hidden;
  background: var(--bg-hover);
  cursor: pointer;
}
.fav-image img { width: 100%; height: 100%; object-fit: cover; transition: transform 0.4s ease; }
.fav-card:hover .fav-image img { transform: scale(1.05); }
.fav-info { padding: 14px 16px 18px; }
.fav-name {
  font-size: 14px;
  color: var(--text-primary);
  margin: 0 0 8px;
  line-height: 1.4;
  cursor: pointer;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 40px;
}
.fav-name:hover { color: var(--brand-primary); }
.fav-price { color: var(--text-primary); font-size: 18px; font-weight: 700; margin-bottom: 14px; }
.fav-actions { display: flex; gap: 10px; }
</style>
