<template>
  <div class="order-list-page">

    <main class="container main-content" v-loading="loading">
      <el-tabs v-model="activeTab" class="order-tabs" @tab-change="loadOrders">
        <el-tab-pane label="全部" name="" />
        <el-tab-pane label="待付款" name="1" />
        <el-tab-pane label="待发货" name="2" />
        <el-tab-pane label="待收货" name="3" />
        <el-tab-pane label="已完成" name="5" />
        <el-tab-pane label="已退款" name="7" />
      </el-tabs>

      <div v-if="orders.length === 0 && !loading" class="empty">
        <el-empty description="暂无订单" />
      </div>

      <div v-for="order in orders" :key="order.id" class="order-card">
        <div class="order-head">
          <div class="info">
            <span class="label">订单号：</span><span class="order-no">{{ order.orderNo || order.id }}</span>
            <span class="label" style="margin-left: 24px;">下单时间：</span>
            <span>{{ order.createTime || order.orderTime || '-' }}</span>
          </div>
          <el-tag :type="statusTag(order.statusCode)" class="status-tag">{{ statusText(order.statusCode) }}</el-tag>
          <span v-if="order.statusCode === ORDER_STATUS.PENDING" class="order-countdown" :class="{ 'cd-expired': isCountdownExpired(order.id) }">
            <el-icon :size="13"><Timer /></el-icon>
            <span>{{ countdownText(order.id) }}</span>
          </span>
        </div>

        <div class="order-body" v-if="order.items && order.items.length">
          <div v-for="it in order.items" :key="it.id || it.skuId" class="item-row">
            <img :src="it.image || it.pic || __PH" class="thumb" onerror="this.src=window.__PH;this.onerror=null" />
            <div class="item-info">
              <div class="item-name">{{ it.spuName || it.name || it.skuName }}</div>
              <div class="item-spec">{{ it.skuSpecs || it.specs || '' }}</div>
            </div>
            <div class="item-price">¥{{ Number(it.price || 0).toFixed(2) }}</div>
            <div class="item-qty">x{{ it.qty || it.quantity || it.number || 1 }}</div>
          </div>
        </div>

        <div class="order-foot">
          <div class="total">
            共 <b>{{ order.totalQuantity || (order.items && order.items.length) || 0 }}</b> 件商品，
            实付：<span class="amount">¥{{ Number(order.payAmount || order.amount || order.totalAmount || 0).toFixed(2) }}</span>
          </div>
          <div class="actions">
            <template v-if="order.statusCode === ORDER_STATUS.PENDING">
              <el-button size="small" plain @click="cancel(order)">取消订单</el-button>
              <el-button size="small" type="primary" @click="pay(order)">去支付</el-button>
            </template>
            <template v-else-if="order.statusCode === ORDER_STATUS.SHIPPED">
              <el-button size="small" type="primary" @click="confirm(order)">确认收货</el-button>
            </template>
            <template v-if="order.statusCode === ORDER_STATUS.REVIEWABLE || order.statusCode === ORDER_STATUS.COMPLETED">
              <el-button size="small" type="primary" @click="goReview(order)">去评价</el-button>
            </template>
            <template v-if="showAfterSale(order.statusCode)">
              <el-button size="small" type="warning" @click="goAfterSale(order)">申请售后</el-button>
            </template>
            <template v-if="afterSaleMap[order.id]?.status === AFTER_SALE_STATUS.RETURNING">
              <el-button size="small" type="primary" @click="openReturn(order)">填写物流</el-button>
            </template>
            <template v-if="order.statusCode === ORDER_STATUS.CANCELLED || order.statusCode === ORDER_STATUS.COMPLETED || order.statusCode === ORDER_STATUS.REFUNDED">
              <el-button size="small" link @click="handleDelete(order)">删除</el-button>
            </template>
            <el-button size="small" link @click="viewDetail(order)">查看详情</el-button>
          </div>
        </div>
      </div>
    </main>

    <!-- 支付方式选择对话框 -->
    <el-dialog v-model="payDialogVisible" title="选择支付方式" width="420px" :close-on-click-modal="false">
      <div class="pay-dialog-body">
        <p class="pay-dialog-tip">订单 {{ pendingOrder?.orderNo || pendingOrder?.id }}，应付 ¥{{ Number(pendingOrder?.payAmount || pendingOrder?.amount || 0).toFixed(2) }}</p>
        <el-radio-group v-model="selectedPayMethod" class="pay-method-group">
          <el-radio value="1" class="pay-method-radio">微信支付</el-radio>
          <el-radio value="2" class="pay-method-radio">支付宝</el-radio>
          <el-radio value="4" class="pay-method-radio">钱包支付</el-radio>
        </el-radio-group>
      </div>
      <template #footer>
        <el-button @click="payDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="paying" @click="doPay">确认支付</el-button>
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
        <el-button type="primary" :loading="returnSubmitting" @click="submitReturn">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { listOrder, cancelOrder, payOrder, confirmOrder, deleteUserOrder, listAfterSales, submitReturnTracking } from '@/api/mall'
import { ORDER_STATUS, AFTER_SALE_STATUS } from '@/constants/order'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Timer } from '@element-plus/icons-vue'

const __PH = window.__PH

const router = useRouter()
const loading = ref(false)
const activeTab = ref('')
const orders = ref([])

// ---- 订单倒计时（15 分钟自动过期） ----
const ORDER_EXPIRE_MINUTES = 15
const countdowns = reactive({})  // orderId → { remaining, text, expired }
let countdownTimer = null

function initCountdowns(list) {
  list.forEach(order => {
    if (order.statusCode === ORDER_STATUS.PENDING && order.createTime) {
      const deadline = new Date(order.createTime).getTime() + ORDER_EXPIRE_MINUTES * 60 * 1000
      countdowns[order.id] = { deadline, text: '', expired: false }
    }
  })
}

function updateCountdowns() {
  let changed = false
  const ids = Object.keys(countdowns)
  for (const id of ids) {
    const cd = countdowns[id]
    if (!cd || cd.deadline == null) continue
    const diff = Math.max(0, cd.deadline - Date.now())
    const prevText = cd.text
    cd.expired = diff <= 0
    if (cd.expired) {
      cd.text = '已超时'
    } else {
      const m = Math.floor(diff / 60000)
      const s = Math.floor((diff % 60000) / 1000)
      cd.text = `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')} 后自动取消`
    }
    if (prevText !== cd.text) changed = true
  }
  // 清理已不在列表中的倒计时
  const activeIds = new Set(orders.value.map(o => o.id))
  for (const id of ids) {
    if (!activeIds.has(Number(id))) delete countdowns[id]
  }
  if (changed) {
    // 触发响应式更新
  }
}

function startCountdown() {
  stopCountdown()
  countdownTimer = setInterval(updateCountdowns, 1000)
}

function stopCountdown() {
  if (countdownTimer) { clearInterval(countdownTimer); countdownTimer = null }
}

function countdownText(orderId) {
  return countdowns[orderId]?.text || ''
}

function isCountdownExpired(orderId) {
  return !!countdowns[orderId]?.expired
}

// 售后物流
const afterSaleMap = ref({})  // orderId → AfterSaleVO
const returnVisible = ref(false)
const returnOrderId = ref(null)
const returnTracking = ref('')
const returnSubmitting = ref(false)

const STATUS_MAP = {
  [ORDER_STATUS.CANCELLED]: ['已取消', 'info'],
  [ORDER_STATUS.PENDING]: ['待付款', 'warning'],
  [ORDER_STATUS.PAID]: ['待发货', 'primary'],
  [ORDER_STATUS.SHIPPED]: ['待收货', 'primary'],
  [ORDER_STATUS.REVIEWABLE]: ['待评价', 'success'],
  [ORDER_STATUS.COMPLETED]: ['已完成', 'success'],
  [ORDER_STATUS.REFUNDING]: ['退款中', 'warning'],
  [ORDER_STATUS.REFUNDED]: ['已退款', 'danger']
}

function statusText(s) { return (STATUS_MAP[s] && STATUS_MAP[s][0]) || '未知' }
function statusTag(s) { return (STATUS_MAP[s] && STATUS_MAP[s][1]) || 'info' }

async function loadOrders() {
  loading.value = true
  try {
    const res = await listOrder({ status: activeTab.value || undefined, page: 1, pageSize: 50 })
    const d = res || {}
    orders.value = d.records || d.list || Array.isArray(res) ? res : []
    initCountdowns(orders.value)
    startCountdown()
  } finally {
    loading.value = false
  }
  // 异步加载售后单（不阻塞订单展示）
  loadAfterSales()
}

async function loadAfterSales() {
  try {
    const asRes = await listAfterSales({ page: 1, pageSize: 200 })
    const asList = (asRes?.records || asRes?.list || [])
    const map = {}
    asList.forEach(a => { if (a.orderId) map[a.orderId] = a })
    afterSaleMap.value = map
  } catch (e) { /* 售后加载失败不影响订单展示 */ }
}

async function cancel(order) {
  try {
    await ElMessageBox.confirm('确定要取消该订单吗？', '提示', { type: 'warning' })
    await cancelOrder(order.id, '用户取消')
    ElMessage.success('已取消')
    loadOrders()
  } catch (e) {}
}

const paying = ref(false)
const payDialogVisible = ref(false)
const pendingOrder = ref(null)
const selectedPayMethod = ref('1')

async function pay(order) {
  pendingOrder.value = order
  selectedPayMethod.value = '1'
  payDialogVisible.value = true
}

async function doPay() {
  if (!pendingOrder.value) return
  paying.value = true
  try {
    await payOrder(pendingOrder.value.id, parseInt(selectedPayMethod.value))
    ElMessage.success('支付成功')
    payDialogVisible.value = false
    loadOrders()
  } catch (e) {
    if (e?.message) {
      ElMessage.error(e?.response?.data?.msg || e?.message || '支付失败')
    }
  } finally {
    paying.value = false
  }
}

async function confirm(order) {
  try {
    await ElMessageBox.confirm('确认已收到商品？', '提示', { type: 'warning' })
    await confirmOrder(order.id)
    ElMessage.success('已确认收货')
    loadOrders()
  } catch (e) {}
}

async function handleDelete(order) {
  try {
    await ElMessageBox.confirm(
      `确定永久删除订单「${order.orderNo || order.id}」？此操作不可撤销。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
    )
    await deleteUserOrder(order.id)
    ElMessage.success('已删除')
    loadOrders()
  } catch (e) {
    if (e !== 'cancel' && e?.message) {
      ElMessage.error(e?.response?.data?.msg || e?.message || '删除失败')
    }
  }
}

function viewDetail(order) {
  router.push(`/order/${order.id}`)
}

function goReview(order) {
  router.push({ path: `/order/${order.id}`, query: { review: '1' } })
}

function goAfterSale(order) {
  router.push(`/aftersale/apply?orderId=${order.id}`)
}

function showAfterSale(code) {
  return code === ORDER_STATUS.PAID || code === ORDER_STATUS.SHIPPED || code === ORDER_STATUS.COMPLETED
}

function openReturn(order) {
  returnOrderId.value = order.id
  returnTracking.value = ''
  returnVisible.value = true
}

async function submitReturn() {
  if (!returnTracking.value.trim()) {
    ElMessage.warning('请输入快递单号')
    return
  }
  returnSubmitting.value = true
  try {
    const as = afterSaleMap.value[returnOrderId.value]
    await submitReturnTracking({ id: as.id, returnTracking: returnTracking.value.trim() })
    ElMessage.success('退货物流已提交')
    returnVisible.value = false
    loadOrders()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '提交失败')
  } finally {
    returnSubmitting.value = false
  }
}

onMounted(loadOrders)
onUnmounted(stopCountdown)
</script>

<style scoped>
.order-list-page { min-height: 100vh; }
.container { max-width: 1200px; margin: 0 auto; padding: 0 20px; }

.main-content { padding: 24px 20px 60px; }
.order-tabs {
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-lg);
  padding: 0 20px;
  margin-bottom: 20px;
}
.empty { background: var(--bg-card); border-radius: var(--radius-lg); padding: 80px 0; border: 1px solid var(--border-base); }

.order-card {
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-lg);
  margin-bottom: 16px;
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}
.order-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: var(--bg-hover);
  border-bottom: 1px solid var(--border-subtle);
}
.order-head .info { color: var(--text-secondary); font-size: 14px; }
.order-head .label { color: var(--text-muted); }
.order-no { font-family: 'Courier New', monospace; }
.status-tag { font-size: 13px; }

/* 订单倒计时 */
.order-countdown {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--brand-warning, #e6a23c);
  font-weight: 500;
  margin-left: 12px;

  &.cd-expired {
    color: var(--status-danger, #f56c6c);
  }
}

.order-body { padding: 12px 24px; }
.item-row {
  display: grid;
  grid-template-columns: 80px 1fr 120px 80px;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px dashed var(--border-subtle);
}
.item-row:last-child { border-bottom: none; }
.thumb { width: 64px; height: 64px; border-radius: var(--radius-sm); object-fit: cover; }
.item-name { color: var(--text-primary); font-size: 14px; margin-bottom: 4px; }
.item-spec { color: var(--text-muted); font-size: 12px; }
.item-price { color: var(--text-primary); text-align: right; }
.item-qty { color: var(--text-muted); text-align: right; }

.order-foot {
  padding: 16px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px solid var(--border-subtle);
}
.order-foot .total { color: var(--text-secondary); }
.order-foot .amount { color: var(--text-primary); font-size: 20px; font-weight: 700; margin-left: 4px; }
.actions { display: flex; gap: 10px; }

/* 支付方式选择 */
.pay-dialog-body { text-align: center; padding: 8px 0 16px; }
.pay-dialog-tip { color: var(--text-secondary); font-size: 15px; margin-bottom: 20px; }
.pay-method-group { display: flex; flex-direction: column; gap: 14px; align-items: flex-start; }
.pay-method-radio { padding: 12px 16px; border: 1px solid var(--border-base); border-radius: var(--radius-md); width: 100%; }
.pay-method-radio:hover { border-color: var(--brand-primary); }
</style>
