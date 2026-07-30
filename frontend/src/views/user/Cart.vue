<template>
  <div class="cart-page">

    <main class="container main-content" v-loading="loading">
      <div v-if="cartStore.items.length === 0" class="empty">
        <el-empty description="购物车是空的，快去选购吧">
          <el-button type="primary" @click="router.push('/')">去逛逛</el-button>
        </el-empty>
      </div>

      <template v-else>
        <div class="cart-table">
          <div class="row header">
            <div class="col col-check">
              <el-checkbox :model-value="cartStore.allChecked" @change="cartStore.toggleAllChecked" />
            </div>
            <div class="col col-product">商品</div>
            <div class="col col-price">单价</div>
            <div class="col col-qty">数量</div>
            <div class="col col-subtotal">小计</div>
            <div class="col col-op">操作</div>
          </div>

          <div v-for="item in cartStore.items" :key="item.id || item.skuId" class="row item">
            <div class="col col-check">
              <el-checkbox :model-value="!!item.checked" @change="cartStore.toggleChecked(item.id || item.skuId)" />
            </div>
            <div class="col col-product">
              <div class="product-card" @click="goDetail(item.spuId || item.skuId)">
                <img :src="item.image || item.pic || __PH" class="thumb" onerror="this.src=window.__PH;this.onerror=null" />
                <div class="name">{{ item.name }}</div>
              </div>
            </div>
            <div class="col col-price">¥{{ Number(item.price || 0).toFixed(2) }}</div>
            <div class="col col-qty">
              <el-input-number
                :model-value="item.quantity"
                :min="1"
                size="small"
                @update:model-value="(v) => cartStore.updateQty(item.id || item.skuId, v)"
              />
            </div>
            <div class="col col-subtotal amount">
              ¥{{ (Number(item.price || 0) * Number(item.quantity || 0)).toFixed(2) }}
            </div>
            <div class="col col-op">
              <el-button type="danger" link @click="cartStore.remove(item.id || item.skuId)">删除</el-button>
            </div>
          </div>
        </div>

        <div class="footer-bar">
          <div class="left">
            <el-checkbox :model-value="cartStore.allChecked" @change="cartStore.toggleAllChecked">全选</el-checkbox>
            <el-button link style="margin-left: 16px" @click="cartStore.clear()">清空购物车</el-button>
          </div>
          <div class="right">
            <span class="count-text">已选 <b>{{ cartStore.checkedCount }}</b> 件</span>
            <span class="total-text">合计：<span class="amount">¥{{ cartStore.checkedAmount.toFixed(2) }}</span></span>
            <el-button type="primary" size="large" class="btn-checkout" :disabled="cartStore.checkedCount === 0" @click="checkout">
              去结算
            </el-button>
          </div>
        </div>
      </template>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useCartStore } from '@/stores/cart'

const __PH = window.__PH

const router = useRouter()
const cartStore = useCartStore()
const loading = ref(false)

function goDetail(id) {
  if (id) {
    const route = router.resolve(`/spu/${id}`)
    window.open(route.href, '_blank')
  }
}

function checkout() {
  router.push('/order/submit')
}

onMounted(async () => {
  loading.value = true
  try {
    await cartStore.load()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.cart-page { min-height: 100vh; padding-bottom: 100px; }
.container { max-width: 1200px; margin: 0 auto; padding: 0 20px; }

.main-content { padding: 24px 20px 40px; }
.empty { background: var(--bg-card); border-radius: var(--radius-lg); padding: 80px 0; border: 1px solid var(--border-base); }

.cart-table { background: var(--bg-card); border-radius: var(--radius-lg); border: 1px solid var(--border-base); overflow: hidden; }
.row {
  display: grid;
  grid-template-columns: 60px 1fr 120px 160px 140px 80px;
  align-items: center;
  padding: 18px 20px;
  border-bottom: 1px solid var(--border-subtle);
}
.row.header {
  background: var(--bg-hover);
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 600;
}
.row.item:hover { background: var(--bg-hover); }
.col-check { text-align: center; }
.product-card { display: flex; align-items: center; gap: 14px; cursor: pointer; }
.thumb { width: 80px; height: 80px; border-radius: var(--radius-sm); object-fit: cover; }
.name { color: var(--text-primary); font-size: 14px; line-height: 1.5; }
.col-price, .col-subtotal { color: var(--text-primary); }
.amount { color: var(--text-primary); font-weight: 600; }
.col-qty { display: flex; justify-content: center; }

.footer-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 72px;
  background: var(--glass-bg);
  backdrop-filter: var(--backdrop-blur);
  -webkit-backdrop-filter: var(--backdrop-blur);
  border-top: 1px solid var(--glass-border);
  display: flex;
  align-items: center;
  box-shadow: var(--glass-shadow);
  z-index: 200;
}
.footer-bar .left, .footer-bar .right {
  max-width: 1200px;
  padding: 0 20px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  flex: 1;
}
.footer-bar .right { justify-content: flex-end; gap: 24px; }
.count-text { color: var(--text-secondary); }
.count-text b { color: var(--text-primary); font-size: 16px; }
.total-text { color: var(--text-secondary); font-size: 15px; }
.total-text .amount { font-size: 24px; margin-left: 6px; }
.btn-checkout { min-width: 140px; }
</style>
