<template>
  <div class="aftersale-apply-page">
    <main class="container main-content" v-loading="loading">
      <!-- 订单信息 -->
      <div class="panel">
        <div class="panel-head"><span class="panel-title">选择售后商品</span></div>
        <div class="section-desc">请选择需要售后的订单商品（仅已支付/已发货/已完成的订单可申请）</div>
        
        <el-select v-model="selectedOrderId" placeholder="请先选择订单" class="order-select" @change="onOrderChange" filterable>
          <el-option v-for="o in availableOrders" :key="o.id" :label="`${o.orderNo} (¥${Number(o.payAmount || 0).toFixed(2)})`" :value="o.id" />
        </el-select>

        <div v-if="selectedOrder && orderItems.length" class="items-section">
          <div 
            v-for="it in orderItems" :key="it.skuId" 
            class="item-row"
            :class="{ selected: selectedItem?.skuId === it.skuId }"
            @click="selectedItem = it"
          >
            <el-radio v-model="selectedSkuId" :value="it.skuId" class="item-radio" />
            <img :src="it.pic || it.image || __PH" class="thumb" onerror="this.src=window.__PH;this.onerror=null" />
            <div class="item-info">
              <div class="item-name">{{ it.spuName }}</div>
              <div class="item-spec">{{ it.skuSpecs || '默认规格' }}</div>
            </div>
            <div class="item-price">¥{{ Number(it.price || 0).toFixed(2) }}</div>
            <div class="item-qty">x{{ it.qty || 1 }}</div>
          </div>
        </div>

        <el-empty v-if="selectedOrder && !orderItems.length" description="该订单无商品可申请售后" />
      </div>

      <!-- 售后信息 -->
      <div class="panel" v-if="selectedItem">
        <div class="panel-head"><span class="panel-title">售后信息</span></div>

        <el-form :model="form" label-width="100px" class="form">
          <el-form-item label="售后类型" required>
            <el-radio-group v-model="form.type">
              <el-radio :value="AFTER_SALE_TYPE.REFUND_ONLY">仅退款</el-radio>
              <el-radio :value="AFTER_SALE_TYPE.RETURN_REFUND">退货退款</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="退款金额" required>
            <span class="refund-amount">¥{{ refundAmount.toFixed(2) }}</span>
            <span class="hint">
              （商品 ¥{{ Number(selectedItem.price || 0).toFixed(2) }} x {{ selectedItem.qty || 1 }}
              <template v-if="hasCouponDiscount">，已按优惠券比例折算</template>）
            </span>
          </el-form-item>

          <el-form-item label="申请原因" required>
            <el-select v-model="form.reason" placeholder="请选择原因" class="reason-select">
              <el-option label="商品质量问题" value="商品质量问题" />
              <el-option label="商品与描述不符" value="商品与描述不符" />
              <el-option label="发错货/漏发" value="发错货/漏发" />
              <el-option label="不想要了" value="不想要了" />
              <el-option label="其他原因" value="其他原因" />
            </el-select>
          </el-form-item>

          <el-form-item label="详细描述">
            <el-input v-model="form.detail" type="textarea" :rows="3" placeholder="请描述问题详情（选填）" maxlength="500" show-word-limit />
          </el-form-item>
        </el-form>

        <div class="submit-row">
          <el-button type="primary" @click="onSubmit" :loading="submitting">提交申请</el-button>
          <el-button @click="onCancel">取消</el-button>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listOrder, getOrder, submitAfterSale } from '@/api/mall'
import { ElMessage } from 'element-plus'
import { ORDER_STATUS, AFTER_SALE_TYPE } from '@/constants/order'

const __PH = window.__PH

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const submitting = ref(false)
const availableOrders = ref<any[]>([])
const selectedOrderId = ref<any>(null)
const selectedOrder = ref<any>(null)
const orderItems = ref<any[]>([])
const selectedSkuId = ref<any>(null)
const selectedItem = ref<any>(null)

const form = ref<any>({
  type: AFTER_SALE_TYPE.REFUND_ONLY,
  reason: '',
  detail: ''
})

/**
 * 计算退款金额，若订单有优惠券折扣则按比例折算
 */
const refundAmount = computed(() => {
  if (!selectedItem.value) return 0
  const itemAmount = Number(selectedItem.value.price || 0) * (selectedItem.value.qty || 1)
  const total = Number(selectedOrder.value?.totalAmount || itemAmount)
  const paid = Number(selectedOrder.value?.payAmount || itemAmount)
  if (total > 0 && paid < total) {
    return Math.round(itemAmount * paid / total * 100) / 100
  }
  return itemAmount
})

/**
 * 判断当前订单是否使用了优惠券折扣（实付 < 商品合计）
 */
const hasCouponDiscount = computed(() => {
  const total = Number(selectedOrder.value?.totalAmount || 0)
  const paid = Number(selectedOrder.value?.payAmount || 0)
  return total > 0 && paid < total
})

watch(selectedSkuId, (val: any) => {
  selectedItem.value = orderItems.value.find((it: any) => it.skuId === val) || null
})

/**
 * 加载用户订单列表，筛选出可申请售后的订单（已支付/已发货/已完成/可评价）
 */
async function loadAvailableOrders() {
  loading.value = true
  try {
    const res: any = await listOrder({ page: 1, pageSize: 100 }) // 兼容 records/list/裸数组多种返回结构
    const all = res?.records || res?.list || (Array.isArray(res) ? res : [])
    // 筛选可售后订单：PAID(2)/SHIPPED(3)/COMPLETED(5)
    availableOrders.value = all.filter((o: any) => {
      const s = o.statusCode
      return s === ORDER_STATUS.PAID || s === ORDER_STATUS.SHIPPED 
        || s === ORDER_STATUS.COMPLETED || s === ORDER_STATUS.REVIEWABLE
    })
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '加载订单失败')
  } finally {
    loading.value = false
  }
}

/**
 * 切换选中订单，加载订单详情和商品列表，并重置表单
 * @param {number} orderId - 订单ID
 */
async function onOrderChange(orderId: any) {
  if (!orderId) {
    selectedOrder.value = null
    orderItems.value = []
    selectedSkuId.value = null
    return
  }
  loading.value = true
  try {
    const data = await getOrder(orderId)
    selectedOrder.value = data || {}
    orderItems.value = data?.items || []
    selectedSkuId.value = null
    selectedItem.value = null
    form.value = {
      type: AFTER_SALE_TYPE.REFUND_ONLY,
      reason: '',
      detail: ''
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '加载订单详情失败')
  } finally {
    loading.value = false
  }
}

/**
 * 提交售后申请，校验表单后调用接口，成功后跳转到售后列表页
 */
async function onSubmit() {
  if (!selectedItem.value) { ElMessage.warning('请选择售后商品'); return }
  if (!form.value.reason) { ElMessage.warning('请选择申请原因'); return }

  submitting.value = true
  try {
    await submitAfterSale({
      orderId: selectedOrder.value.id,
      skuId: selectedItem.value.skuId,
      type: form.value.type,
      reason: form.value.reason,
      detail: form.value.detail,
      images: null
    })
    ElMessage.success('售后申请已提交，请等待商家审核')
    router.push('/aftersale/list')
  } catch (e: any) {
    const msg = e?.response?.data?.msg || e?.message || '提交失败'
    ElMessage.error(msg)
  } finally {
    submitting.value = false
  }
}

/**
 * 取消操作，返回上一页
 */
function onCancel() {
  router.back()
}

onMounted(() => {
  loadAvailableOrders()
  const orderId = route.query.orderId
  if (orderId) {
    selectedOrderId.value = Number(orderId)
    onOrderChange(Number(orderId))
  }
})
</script>

<style scoped>
.aftersale-apply-page { min-height: 100vh; padding-bottom: 60px; }
.container { max-width: 900px; margin: 0 auto; padding: 0 20px; }
.main-content { padding: 32px 20px 60px; }

.panel {
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-lg);
  padding: 20px 24px;
  margin-bottom: 16px;
  box-shadow: var(--shadow-sm);
}
.panel-head {
  padding-bottom: 12px; margin-bottom: 16px;
  border-bottom: 1px solid var(--border-subtle);
}
.panel-title { font-size: 16px; font-weight: 600; color: var(--text-primary); }
.section-desc { color: var(--text-muted); font-size: 13px; margin-bottom: 16px; }
.order-select { width: 100%; max-width: 500px; }

.items-section { margin-top: 16px; }
.item-row {
  display: flex; align-items: center; padding: 12px;
  border: 1px solid var(--border-subtle); border-radius: var(--radius-md);
  margin-bottom: 10px; cursor: pointer; transition: all .2s;
}
.item-row:hover { background: var(--bg-hover); }
.item-row.selected { border-color: var(--brand-primary); background: var(--bg-hover); }
.item-radio { margin-right: 12px; }
.thumb { width: 64px; height: 64px; border-radius: var(--radius-sm); object-fit: cover; margin-right: 12px; }
.item-info { flex: 1; }
.item-name { color: var(--text-primary); font-size: 14px; font-weight: 500; margin-bottom: 4px; }
.item-spec { color: var(--text-muted); font-size: 12px; }
.item-price { width: 100px; text-align: right; color: var(--text-primary); }
.item-qty { width: 60px; text-align: right; color: var(--text-muted); }

.form { margin-top: 8px; }
.amount-input { width: 180px; }
.refund-amount { font-size: 18px; font-weight: 700; color: var(--brand-danger, #e53935); }
.reason-select { width: 260px; }
.hint { color: var(--text-muted); font-size: 12px; margin-left: 10px; }
.submit-row { display: flex; gap: 12px; margin-top: 20px; }
</style>
