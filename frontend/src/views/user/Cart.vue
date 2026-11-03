<template>
  <div class="cart-page">

    <main class="container main-content" v-loading="loading">
      <div v-if="!loading && cartStore.items.length === 0" class="empty">
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

          <div v-for="item in cartStore.items" :key="item.id || item.skuId" class="row item" :class="{ 'row-unavailable': item.unavailable }">
            <div class="col col-check">
              <el-checkbox :model-value="!!item.checked" :disabled="!!item.unavailable" @change="cartStore.toggleChecked(item.id || item.skuId)" />
            </div>
            <div class="col col-product">
              <div class="product-card" @click="goDetail(item.spuId || item.skuId)">
                <img :src="item.image || item.pic || __PH" class="thumb" v-placeholder />
                <div class="product-info">
                  <div class="name">
                    {{ item.ghost ? '商品已失效' : item.name }}
                    <el-tag v-if="item.ghost" type="info" size="small" class="status-tag">已删除</el-tag>
                    <el-tag v-else-if="item.unavailable" type="danger" size="small" class="status-tag">已停售</el-tag>
                  </div>
                  <div class="sku-specs" v-if="item.skuName || item.skuSpecs">
                    {{ item.skuName || (item.skuSpecs || '').replace(/;/g, ' / ') }}
                  </div>
                  <div class="stock-warn" v-if="!item.unavailable && item.stock != null && Number(item.stock) < Number(item.quantity)">
                    库存不足，仅剩 {{ item.stock }} 件
                  </div>
                  <div class="service-tags" v-if="(item.services || []).length">
                    <span class="service-tag" v-for="svc in (item.services as any[])" :key="svc.id || svc.title">
                      {{ svc.title }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
            <div class="col col-price">¥{{ Number(item.price || 0).toFixed(2) }}</div>
            <div class="col col-qty">
              <el-input-number
                :model-value="item.quantity"
                :min="1"
                :max="itemMaxQty(item)"
                :disabled="!!item.unavailable"
                size="small"
                @update:model-value="(v: any) => cartStore.updateQty(item.id || item.skuId, v)"
              />
            </div>
            <div class="col col-subtotal amount">
              ¥{{ itemSubtotal(item).toFixed(2) }}
              <div v-if="itemServiceFee(item) > 0" class="service-row">
                + 服务费 ¥{{ (itemServiceFee(item) * Number(item.quantity || 0)).toFixed(2) }}
              </div>
            </div>
            <div class="col col-op">
              <el-button type="danger" link @click="handleRemove(item)">删除</el-button>
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

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useCartStore } from '@/stores/cart'

const __PH = window.__PH

const router = useRouter()
const cartStore = useCartStore()
const loading = ref(false)

/**
 * 单件商品的数量上限：取每人限购与实时库存中的较小值，均未设置则不设上限
 */
function itemMaxQty(item: any) {
  const limit = Number(item?.perUserLimit) || 0
  const stock = Number(item?.stock)
  const caps: number[] = []
  if (limit > 0) caps.push(limit)
  if (!Number.isNaN(stock) && stock > 0) caps.push(stock)
  if (caps.length === 0) return undefined
  return Math.min(...caps)
}

/**
 * 单个购物车项的保障服务费（单价口径，未乘数量）
 */
function itemServiceFee(item: any) {
  return Number(item?.serviceFee || 0)
}

/**
 * 单个购物车项小计 = 商品单价 × 数量 + 服务费 × 数量（与结算页口径一致）
 */
function itemSubtotal(item: any) {
  const qty = Number(item?.quantity || 0)
  return Number(item?.price || 0) * qty + itemServiceFee(item) * qty
}

/**
 * 删除购物车商品前弹出确认框
 */
async function handleRemove(item: any) {
  try {
    await ElMessageBox.confirm(`确定从购物车中删除「${item.name}」？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await cartStore.remove(item.id || item.skuId)
    ElMessage.success('已删除')
  } catch {
    ElMessage.error('删除失败')
  }
}

/**
 * 在新标签页打开商品详情页
 * @param {number|string} id - 商品 SPU ID
 */
function goDetail(id: any) {
  if (id) {
    const route = router.resolve(`/spu/${id}`)
    window.open(route.href, '_blank')
  }
}

/**
 * 跳转到订单提交页面进行结算
 */
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
.empty {
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-lg);
  padding: 80px 0;
  box-shadow: var(--glass-shadow), inset 0 1px 0 var(--glass-highlight);
  backdrop-filter: var(--backdrop-blur);
  -webkit-backdrop-filter: var(--backdrop-blur);
  overflow: hidden;
}

.cart-table {
  background: var(--glass-bg);
  backdrop-filter: var(--backdrop-blur);
  -webkit-backdrop-filter: var(--backdrop-blur);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: var(--glass-shadow), inset 0 1px 0 var(--glass-highlight);
}
.row {
  display: grid;
  grid-template-columns: 60px 1fr 120px 160px 140px 80px;
  align-items: center;
  padding: 18px 20px;
  border-bottom: 1px solid var(--glass-border);
}
.row.header {
  background: var(--glass-hover);
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 600;
}
.row.item:hover { background: var(--glass-hover); }
.row.item.row-unavailable { opacity: 0.55; }
.row.item.row-unavailable:hover { background: transparent; }
.row.item.row-unavailable .thumb { filter: grayscale(1); }
.status-tag { margin-left: 6px; }
.stock-warn { color: var(--el-color-danger, #f56c6c); font-size: 12px; line-height: 1.4; }
.row:last-child { border-bottom: none; }
.col-check { text-align: center; }
.product-card { display: flex; align-items: center; gap: 14px; cursor: pointer; }
.thumb { width: 80px; height: 80px; border-radius: var(--radius-sm); object-fit: cover; flex-shrink: 0; }
.product-info { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.name { color: var(--text-primary); font-size: 14px; line-height: 1.5; }
.sku-specs { color: var(--text-secondary); opacity: 0.75; font-size: 12px; line-height: 1.4; }
.service-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.service-tag {
  color: var(--brand-primary);
  font-size: 12px;
  line-height: 1;
  padding: 4px 8px;
  border: 1px solid var(--brand-primary);
  border-radius: 4px;
  background: color-mix(in srgb, var(--brand-primary) 8%, transparent);
  white-space: nowrap;
}
.service-row { color: var(--brand-primary); font-size: 12px; margin-top: 4px; }
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

/* ===== 移动端：六列表格重排为两行卡片布局 ===== */
@media (max-width: 768px) {
  .cart-page { padding-bottom: 170px; }
  .container { padding: 0 12px; }
  .main-content { padding: 16px 12px 24px; }
  /* 表头在移动端无意义，隐藏；行改为 「勾选+商品 / 数量+小计」 两行 */
  .row.header { display: none; }
  .row {
    grid-template-columns: 36px 1fr auto;
    grid-template-areas:
      'check product op'
      'check qty     subtotal';
    gap: 8px 10px;
    padding: 14px 12px;
  }
  .col-check { grid-area: check; }
  .col-product { grid-area: product; }
  .col-qty { grid-area: qty; justify-content: flex-start; }
  .col-subtotal { grid-area: subtotal; justify-self: end; }
  .col-op { grid-area: op; align-self: start; }
  /* 单价与小计重复，移动端隐藏单价列 */
  .col-price { display: none; }
  .thumb { width: 64px; height: 64px; }
  /* 结算底栏抬高，避开底部快捷条 */
  body.has-mobile-dock .footer-bar { bottom: 58px; }
}

@media (max-width: 480px) {
  .footer-bar { height: 64px; }
  .footer-bar .left, .footer-bar .right { padding: 0 12px; }
  .footer-bar .right { gap: 10px; }
  .count-text { display: none; }
  .total-text { font-size: 13px; }
  .total-text .amount { font-size: 20px; }
  .btn-checkout { min-width: 104px; padding: 0 16px; }
}
</style>
