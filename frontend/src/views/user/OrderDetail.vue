<template>
  <div class="order-detail-page">

    <main class="container main-content" v-loading="loading">
      <!-- 顶部状态卡 -->
      <div class="status-card">
        <div class="status-left">
          <div class="status-title">
            <el-tag :type="statusTag(order?.statusCode)" size="large">{{ statusText(order?.statusCode) }}</el-tag>
          </div>
          <div class="status-sub">
            <template v-if="order?.statusCode === ORDER_STATUS.PENDING">
              <span>请尽快完成支付，剩余 </span>
              <b class="cd-time">{{ countdownText }}</b>
              <span> 订单将自动取消</span>
            </template>
            <template v-else-if="order?.statusCode === ORDER_STATUS.SHIPPED">商品已发货，请注意查收</template>
            <template v-else-if="order?.statusCode === ORDER_STATUS.COMPLETED">感谢您的购买，祝您生活愉快</template>
            <template v-else-if="order?.statusCode === ORDER_STATUS.CANCELLED">订单已取消</template>
            <template v-else-if="order?.statusCode === ORDER_STATUS.REFUNDING">商家正在处理退款，请耐心等待</template>
            <template v-else-if="order?.statusCode === ORDER_STATUS.REFUNDED">订单已退款</template>
            <template v-else>—</template>
          </div>
        </div>
        <div class="status-actions" v-if="showActions">
          <el-button v-if="order?.statusCode === ORDER_STATUS.PENDING" plain @click="onCancel">取消订单</el-button>
          <el-button v-if="order?.statusCode === ORDER_STATUS.PENDING" type="primary" @click="onPay">立即支付</el-button>
          <el-button v-if="order?.statusCode === ORDER_STATUS.SHIPPED" type="primary" @click="onConfirm">确认收货</el-button>
          <el-button v-if="showAfterSaleBtn" type="warning" @click="onApplyAfterSale">申请售后</el-button>
          <el-button v-if="afterSale?.status === AFTER_SALE_STATUS.RETURNING" type="primary" @click="onOpenReturn">填写退货物流</el-button>
        </div>
      </div>

      <!-- 收货地址 -->
      <div class="panel">
        <div class="panel-head"><span class="panel-title">收货信息</span></div>
        <div class="addr-row">
          <el-icon class="addr-icon"><Location /></el-icon>
          <div class="addr-text">{{ order?.address || '（未填写）' }}</div>
        </div>
      </div>

      <!-- 商品明细 -->
      <div class="panel">
        <div class="panel-head">
          <span class="panel-title">商品清单</span>
          <span class="item-count">共 {{ totalQty }} 件</span>
        </div>
        <div class="items-list">
          <div class="item-row" v-for="it in order?.items || []" :key="it.id || it.skuId">
            <div class="item-main" @click="goSpu(it.spuId)">
              <img :src="it.pic || it.image || __PH" class="thumb"
                   onerror="this.src=window.__PH;this.onerror=null" />
              <div class="item-detail">
                <div class="item-info">
                  <div class="item-name">{{ it.spuName }}</div>
                  <div class="item-spec">{{ it.skuSpecs || '默认规格' }}</div>
                </div>
                <div class="item-meta">
                  <span class="item-price">¥{{ Number(it.price || 0).toFixed(2) }}</span>
                  <span class="item-qty">x{{ it.qty || 1 }}</span>
                  <span class="item-subtotal">¥{{ Number(it.subtotal || 0).toFixed(2) }}</span>
                </div>
              </div>
            </div>
            <div v-if="isReviewable(order?.statusCode) && !reviewedSkuIds.has(it.skuId)" class="item-actions">
              <el-button size="small" type="primary" @click.stop="openReview(it)">评价</el-button>
            </div>
            <div v-else-if="isReviewable(order?.statusCode)" class="item-actions">
              <el-tag size="small" type="success">已评价</el-tag>
            </div>
          </div>
          <el-empty v-if="!order?.items || order.items.length === 0" description="暂无商品" />
        </div>
      </div>

      <!-- 订单信息 -->
      <div class="panel">
        <div class="panel-head"><span class="panel-title">订单信息</span></div>
        <el-descriptions :column="1" border size="default">
          <el-descriptions-item label="订单编号">
            <span class="mono">{{ order?.orderNo || '-' }}</span>
            <el-button link type="primary" @click="copyOrderNo">复制</el-button>
          </el-descriptions-item>
          <el-descriptions-item label="下单时间">{{ order?.createTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="支付方式">{{ payMethodText(order?.payMethod) }}</el-descriptions-item>
          <el-descriptions-item label="买家备注">{{ order?.remark || '（无）' }}</el-descriptions-item>
          <el-descriptions-item label="商品合计">
            <span class="price-muted">¥{{ Number(order?.totalAmount || 0).toFixed(2) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="实付金额">
            <span class="price-pay">¥{{ Number(order?.payAmount || 0).toFixed(2) }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="order?.pointsDeducted > 0" label="积分抵扣">
            <span>{{ order.pointsDeducted }}积分 = ¥{{ Number(order.pointsAmount || 0).toFixed(2) }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </main>

    <!-- 底部操作栏（仅待付款 / 待收货时出现） -->
    <div v-if="showActions" class="bottom-bar">
      <div class="container bottom-inner">
        <div class="amount">
          实付：<span class="amount-num">¥{{ Number(order?.payAmount || 0).toFixed(2) }}</span>
        </div>
        <div class="actions">
          <el-button v-if="order?.statusCode === ORDER_STATUS.PENDING" plain @click="onCancel">取消订单</el-button>
          <el-button v-if="order?.statusCode === ORDER_STATUS.PENDING" type="primary" @click="onPay">立即支付</el-button>
          <el-button v-if="order?.statusCode === ORDER_STATUS.SHIPPED" type="primary" @click="onConfirm">确认收货</el-button>
        </div>
      </div>
    </div>

    <!-- 评价弹窗 -->
    <el-dialog v-model="reviewVisible" title="发表评价" width="520px" destroy-on-close>
      <div class="review-target">
        <img :src="reviewItem?.pic || reviewItem?.image || __PH" class="review-thumb" />
        <div>
          <div class="review-name">{{ reviewItem?.spuName }}</div>
          <div class="review-spec">{{ reviewItem?.skuSpecs || '默认规格' }}</div>
        </div>
      </div>
      <el-form label-width="80px" class="review-form">
        <el-form-item label="评分">
          <el-rate v-model="reviewForm.rating" :max="5" show-score />
        </el-form-item>
        <el-form-item label="评价内容">
          <el-input
            v-model="reviewForm.content"
            type="textarea"
            :rows="4"
            placeholder="分享您的使用体验（至少 2 个字）"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="reviewSubmitting" @click="submitReview">提交评价</el-button>
      </template>
    </el-dialog>

    <!-- 退货物流弹窗 -->
    <el-dialog v-model="returnVisible" title="填写退货物流" width="420px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="快递单号" required>
          <el-input v-model="returnTracking" placeholder="请输入退货快递单号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="returnVisible = false">取消</el-button>
        <el-button type="primary" :loading="returnSubmitting" @click="onSubmitReturn">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getOrder as apiGetOrder,
  payOrder as apiPayOrder,
  cancelOrder as apiCancelOrder,
  confirmOrder as apiConfirmOrder,
  submitReview as apiSubmitReview,
  getAfterSaleByOrder,
  submitReturnTracking,
} from '@/api/mall'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Location } from '@element-plus/icons-vue'
import { ORDER_STATUS, AFTER_SALE_STATUS } from '@/constants/order'

const __PH = window.__PH

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const order = ref(null)
const afterSale = ref(null)  // 关联的售后单

// ---- 订单倒计时 ----
const ORDER_EXPIRE_MINUTES = 15
const countdownText = ref('')
let countdownTimer = null

function updateCountdown() {
  if (!order.value || order.value.statusCode !== ORDER_STATUS.PENDING || !order.value.createTime) {
    countdownText.value = ''
    stopCountdown()
    return
  }
  const deadline = new Date(order.value.createTime).getTime() + ORDER_EXPIRE_MINUTES * 60 * 1000
  const diff = Math.max(0, deadline - Date.now())
  if (diff <= 0) {
    countdownText.value = '0:00'
    stopCountdown()
    return
  }
  const m = Math.floor(diff / 60000)
  const s = Math.floor((diff % 60000) / 1000)
  countdownText.value = `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

function startCountdown() {
  stopCountdown()
  updateCountdown()
  countdownTimer = setInterval(updateCountdown, 1000)
}

function stopCountdown() {
  if (countdownTimer) { clearInterval(countdownTimer); countdownTimer = null }
}

// 退货物流弹窗
const returnVisible = ref(false)
const returnTracking = ref('')
const returnSubmitting = ref(false)

// 评价弹窗
const reviewVisible = ref(false)
const reviewItem = ref(null)
const reviewForm = ref({ rating: 5, content: '' })
const reviewSubmitting = ref(false)
const reviewedSkuIds = ref(new Set())

function openReview(item) {
  reviewItem.value = item
  reviewForm.value = { rating: 5, content: '' }
  reviewVisible.value = true
}

async function submitReview() {
  const item = reviewItem.value
  if (!item) return
  if (!reviewForm.value.content || reviewForm.value.content.trim().length < 2) {
    ElMessage.warning('请至少输入 2 个字的评价内容')
    return
  }
  reviewSubmitting.value = true
  try {
    await apiSubmitReview({
      spuId: item.spuId,
      skuId: item.skuId,
      orderId: order.value?.id,
      orderNo: order.value?.orderNo,
      rating: reviewForm.value.rating,
      content: reviewForm.value.content.trim()
    })
    ElMessage.success('评价提交成功')
    reviewedSkuIds.value.add(item.skuId)
    reviewVisible.value = false
  } catch (e) {
    const msg = e?.response?.data?.msg || e?.message || '提交失败'
    ElMessage.error(msg)
  } finally {
    reviewSubmitting.value = false
  }
}

function load() {
  const id = route.params.id
  if (!id) {
    ElMessage.warning('订单ID缺失')
    return
  }
  loading.value = true
  apiGetOrder(id).then(res => {
    order.value = res || {}
    // 加载关联售后单
    getAfterSaleByOrder(id).then(as => { afterSale.value = as || null }).catch(() => {})
    // 如果从"去评价"进入，自动打开第一个未评价商品的评价弹窗
    if (route.query.review === '1' && isReviewable(order.value?.statusCode)) {
      nextTick(() => {
        const firstItem = (order.value?.items || []).find(it => !reviewedSkuIds.value.has(it.skuId))
        if (firstItem) openReview(firstItem)
      })
    }
  }).catch(e => {
    const msg = e?.response?.data?.msg || e?.message || '加载失败'
    ElMessage.error(msg)
  }).finally(() => {
    loading.value = false
    startCountdown()
  })
}

function isReviewable(statusCode) {
  return statusCode === ORDER_STATUS.REVIEWABLE || statusCode === ORDER_STATUS.COMPLETED
}

const STATUS_MAP = {
  [ORDER_STATUS.CANCELLED]: ['已取消', 'info'],
  [ORDER_STATUS.PENDING]: ['待付款', 'warning'],
  [ORDER_STATUS.PAID]: ['待发货', 'warning'],
  [ORDER_STATUS.SHIPPED]: ['待收货', 'primary'],
  [ORDER_STATUS.REVIEWABLE]: ['待评价', 'success'],
  [ORDER_STATUS.COMPLETED]: ['已完成', 'success'],
  [ORDER_STATUS.REFUNDING]: ['退款中', 'warning'],
  [ORDER_STATUS.REFUNDED]: ['已退款', 'danger']
}
function statusText(s) { return (STATUS_MAP[s] && STATUS_MAP[s][0]) || '未知' }
function statusTag(s)  { return (STATUS_MAP[s] && STATUS_MAP[s][1]) || 'info' }

function payMethodText(m) {
  if (m == null) return '—'
  const map = { 1: '微信支付', 2: '支付宝', 4: '银行卡' }
  return map[m] || `方式${m}`
}

const showActions = computed(() =>
  order.value && (order.value.statusCode === ORDER_STATUS.PENDING || order.value.statusCode === ORDER_STATUS.SHIPPED)
)

const showAfterSaleBtn = computed(() => {
  const s = order.value?.statusCode
  return s === ORDER_STATUS.PAID || s === ORDER_STATUS.SHIPPED || s === ORDER_STATUS.COMPLETED
})
const totalQty = computed(() =>
  (order.value?.items || []).reduce((s, i) => s + (Number(i.qty) || 0), 0)
)

async function onPay() {
  try {
    await ElMessageBox.confirm('确认支付该订单？', '支付确认', { type: 'warning' })
    await apiPayOrder(order.value.id)
    ElMessage.success('支付成功')
    load()
  } catch (e) { /* 用户取消不提示 */ }
}

async function onCancel() {
  try {
    await ElMessageBox.confirm('确定要取消该订单吗？', '提示', { type: 'warning' })
    await apiCancelOrder(order.value.id, '用户取消')
    ElMessage.success('已取消')
    load()
  } catch (e) {}
}

async function onConfirm() {
  try {
    await ElMessageBox.confirm('确认已收到商品？', '提示', { type: 'warning' })
    await apiConfirmOrder(order.value.id)
    ElMessage.success('已确认收货')
    load()
  } catch (e) {}
}

function copyOrderNo() {
  const t = order.value?.orderNo
  if (!t) return
  try {
    navigator.clipboard && navigator.clipboard.writeText(t)
    ElMessage.success('订单号已复制')
  } catch (e) {
    ElMessage.info(t)
  }
}

function goSpu(spuId) {
  if (spuId) {
    const route = router.resolve(`/spu/${spuId}`)
    window.open(route.href, '_blank')
  }
}
function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/order/list')
}

function onApplyAfterSale() {
  router.push(`/aftersale/apply?orderId=${order.value.id}`)
}

function onOpenReturn() {
  returnTracking.value = ''
  returnVisible.value = true
}

async function onSubmitReturn() {
  if (!returnTracking.value.trim()) {
    ElMessage.warning('请输入快递单号')
    return
  }
  returnSubmitting.value = true
  try {
    await submitReturnTracking({ id: afterSale.value.id, returnTracking: returnTracking.value.trim() })
    ElMessage.success('退货物流已提交')
    returnVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '提交失败')
  } finally {
    returnSubmitting.value = false
  }
}

onMounted(load)
onUnmounted(stopCountdown)
watch(() => route.params.id, load)
</script>

<style scoped>
.order-detail-page { min-height: 100vh; padding-bottom: 100px; }
.container { max-width: 1100px; margin: 0 auto; padding: 0 20px; }
.main-content { padding: 32px 20px 60px; }

.status-card {
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-lg);
  padding: 24px 28px;
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 18px;
  box-shadow: var(--shadow-sm);
}
.status-sub { margin-top: 10px; font-size: 14px; color: var(--text-secondary); }
.cd-time { color: var(--brand-warning, #e6a23c); font-family: 'Courier New', monospace; }
.status-actions { display: flex; gap: 12px; }

.panel {
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-lg);
  padding: 20px 24px;
  margin-bottom: 16px;
  box-shadow: var(--shadow-sm);
}
.panel-head {
  display: flex; justify-content: space-between; align-items: center;
  padding-bottom: 12px; margin-bottom: 12px;
  border-bottom: 1px solid var(--border-subtle);
}
.panel-title {
  font-size: 16px; font-weight: 600; color: var(--text-primary);
}
.item-count { color: var(--text-muted); font-size: 13px; }

.addr-row { display: flex; align-items: flex-start; gap: 12px; padding: 6px 0; }
.addr-icon { color: var(--brand-primary); margin-top: 2px; font-size: 18px; }
.addr-text { color: var(--text-primary); line-height: 1.7; }

.item-row {
  padding: 14px 0;
  border-bottom: 1px dashed var(--border-subtle);
  transition: background .2s;
}
.item-row:last-child { border-bottom: none; }
.item-row:hover { background: var(--bg-hover); }
.item-main {
  display: flex;
  align-items: center;
  gap: 16px;
  cursor: pointer;
}
.item-detail {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.item-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
  padding-right: 12px;
}
.thumb { width: 72px; height: 72px; border-radius: var(--radius-sm); object-fit: cover; }
.item-info { flex: 1; min-width: 0; }
.item-name { color: var(--text-primary); font-size: 14px; font-weight: 500; margin-bottom: 6px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-spec { color: var(--text-muted); font-size: 12px; }
.item-meta { display: flex; align-items: center; gap: 20px; flex-shrink: 0; }
.item-price { color: var(--text-primary); text-align: right; width: 90px; flex-shrink: 0; }
.item-qty   { color: var(--text-muted); text-align: right; width: 60px; flex-shrink: 0; }
.item-subtotal { color: var(--text-primary); text-align: right; font-weight: 600; width: 100px; flex-shrink: 0; }

/* 评价弹窗 */
.review-target {
  display: flex; align-items: center; gap: 12px;
  background: var(--bg-hover); padding: 12px; border-radius: var(--radius-md);
  margin-bottom: 20px;
}
.review-thumb { width: 56px; height: 56px; border-radius: var(--radius-sm); object-fit: cover; }
.review-name { color: var(--text-primary); font-size: 14px; font-weight: 500; }
.review-spec { color: var(--text-muted); font-size: 12px; margin-top: 4px; }
.review-form :deep(.el-form-item__label) { color: var(--text-secondary); }

:deep(.el-descriptions__label) { width: 120px; color: var(--text-muted); }
.price-muted { color: var(--text-secondary); }
.price-pay   { color: var(--text-primary); font-size: 18px; font-weight: 700; }
.mono { font-family: 'Courier New', monospace; }

.bottom-bar {
  position: fixed; left: 0; right: 0; bottom: 0;
  background: var(--glass-bg);
  backdrop-filter: var(--backdrop-blur);
  -webkit-backdrop-filter: var(--backdrop-blur);
  border-top: 1px solid var(--glass-border);
  box-shadow: var(--glass-shadow);
  z-index: 90;
}
.bottom-inner { display: flex; justify-content: space-between; align-items: center; height: 64px; }
.amount { color: var(--text-secondary); }
.amount-num { color: var(--text-primary); font-size: 22px; font-weight: 700; margin-left: 4px; }
.actions { display: flex; gap: 10px; }
</style>
