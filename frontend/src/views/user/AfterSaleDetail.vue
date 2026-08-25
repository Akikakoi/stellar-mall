<template>
  <div class="aftersale-detail-page">
    <main class="container main-content" v-loading="loading">
      <!-- 状态卡 -->
      <div class="status-card">
        <div class="status-left">
          <div class="status-title">
            <el-tag :type="statusTag(afterSale?.status)" size="large">{{ afterSale?.statusText || '加载中' }}</el-tag>
          </div>
          <div class="status-sub">{{ statusHint }}</div>
        </div>
        <div class="status-actions">
          <el-button v-if="afterSale?.status === AFTER_SALE_STATUS.APPLIED || afterSale?.status === AFTER_SALE_STATUS.AUDITING" 
            plain type="danger" @click="onCancel">取消申请</el-button>
          <el-button v-if="afterSale?.status === AFTER_SALE_STATUS.RETURNING" 
            type="primary" @click="openReturnDialog">填写退货物流</el-button>
        </div>
      </div>

      <!-- 售后信息 -->
      <div class="panel">
        <div class="panel-head"><span class="panel-title">售后信息</span></div>
        <el-descriptions :column="2" border size="default">
          <el-descriptions-item label="售后单号">AS{{ afterSale?.id || '-' }}</el-descriptions-item>
          <el-descriptions-item label="售后类型">
            <el-tag size="small">{{ afterSale?.typeText || '-' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="申请原因">{{ afterSale?.reason || '-' }}</el-descriptions-item>
          <el-descriptions-item label="退款金额">
            <span class="price-red">¥{{ Number(afterSale?.amount || 0).toFixed(2) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="申请时间">{{ afterSale?.createTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="详细描述">{{ afterSale?.detail || '（无）' }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 商品信息 -->
      <div class="panel">
        <div class="panel-head"><span class="panel-title">售后商品</span></div>
        <div class="item-row">
          <img :src="afterSale?.spuImage || __PH" class="thumb" onerror="this.src=window.__PH;this.onerror=null" />
          <div class="item-info">
            <div class="item-name">{{ afterSale?.spuName || '商品' }}</div>
            <div class="item-spec">{{ afterSale?.skuSpecs || '默认规格' }}</div>
            <div class="item-qty">x{{ afterSale?.qty || 1 }}</div>
          </div>
        </div>
      </div>

      <!-- 订单信息 -->
      <div class="panel">
        <div class="panel-head"><span class="panel-title">关联订单</span></div>
        <el-descriptions :column="1" border size="default">
          <el-descriptions-item label="订单编号">
            <span class="mono">{{ afterSale?.orderNo || '-' }}</span>
            <el-button link type="primary" @click="goOrder" style="margin-left: 8px;">查看订单</el-button>
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 审核信息 -->
      <div class="panel" v-if="afterSale?.status !== AFTER_SALE_STATUS.APPLIED && afterSale?.status !== AFTER_SALE_STATUS.CANCELLED">
        <div class="panel-head"><span class="panel-title">处理信息</span></div>
        <el-descriptions :column="1" border size="default">
          <el-descriptions-item v-if="afterSale?.auditRemark" label="审核备注">{{ afterSale.auditRemark }}</el-descriptions-item>
          <el-descriptions-item v-if="afterSale?.auditTime" label="审核时间">{{ afterSale.auditTime }}</el-descriptions-item>
          <el-descriptions-item v-if="afterSale?.returnTracking" label="退货物流">{{ afterSale.returnTracking }}</el-descriptions-item>
          <el-descriptions-item v-if="afterSale?.refundTime" label="退款完成时间">{{ afterSale.refundTime }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 物流轨迹占位 -->
      <div class="panel" v-if="afterSale?.returnTracking">
        <div class="panel-head"><span class="panel-title">退货物流</span></div>
        <el-timeline>
          <el-timeline-item timestamp="已提交退货运单" placement="top">
            <p>快递单号：{{ afterSale.returnTracking }}</p>
          </el-timeline-item>
        </el-timeline>
      </div>
    </main>

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

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAfterSale, cancelAfterSale, submitReturnTracking } from '@/api/mall'
import { AFTER_SALE_STATUS } from '@/constants/order'
import { ElMessage, ElMessageBox } from 'element-plus'

const __PH = window.__PH
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const afterSale = ref<any>(null)

// 退货物流
const returnVisible = ref(false)
const returnTracking = ref('')
const returnSubmitting = ref(false)

const STATUS_TAG_MAP: Record<string, any> = {
  [AFTER_SALE_STATUS.APPLIED]: 'warning',
  [AFTER_SALE_STATUS.AUDITING]: 'warning',
  [AFTER_SALE_STATUS.RETURNING]: 'primary',
  [AFTER_SALE_STATUS.REFUNDING]: 'danger',
  [AFTER_SALE_STATUS.COMPLETED]: 'success',
  [AFTER_SALE_STATUS.REJECTED]: 'info',
  [AFTER_SALE_STATUS.CANCELLED]: 'info'
}
function statusTag(s: any) { return STATUS_TAG_MAP[s] || 'info' }

/**
 * 根据售后状态返回对应的提示文案
 */
const statusHint = computed(() => {
  const s = afterSale.value?.status
  switch (s) {
    case AFTER_SALE_STATUS.APPLIED: return '您的售后申请已提交，等待商家审核'
    case AFTER_SALE_STATUS.AUDITING: return '商家正在审核您的售后申请'
    case AFTER_SALE_STATUS.RETURNING: return '商家已同意退货，请尽快寄回商品并填写物流单号'
    case AFTER_SALE_STATUS.REFUNDING: return '退款正在处理中，请耐心等待'
    case AFTER_SALE_STATUS.COMPLETED: return '售后已完成，退款已退回'
    case AFTER_SALE_STATUS.REJECTED: return '商家拒绝了您的售后申请'
    case AFTER_SALE_STATUS.CANCELLED: return '售后申请已取消'
    default: return ''
  }
})

/**
 * 根据路由参数加载售后详情数据
 */
async function load() {
  const id = route.params.id
  if (!id) return
  loading.value = true
  try {
    const data = await getAfterSale(Number(id))
    afterSale.value = data || null
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '加载失败')
  } finally {
    loading.value = false
  }
}

/**
 * 取消当前售后申请，弹出确认框后调用取消接口并刷新数据
 */
async function onCancel() {
  try {
    await ElMessageBox.confirm('确定取消该售后申请吗？', '提示', { type: 'warning' })
    await cancelAfterSale(afterSale.value.id)
    ElMessage.success('已取消')
    load()
  } catch (e: any) { /* user cancelled */ }
}

/**
 * 打开退货物流填写弹窗，重置输入状态
 */
function openReturnDialog() {
  returnTracking.value = ''
  returnVisible.value = true
}

/**
 * 提交退货物流单号，校验后调用接口并刷新详情
 */
async function submitReturn() {
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
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '提交失败')
  } finally {
    returnSubmitting.value = false
  }
}

/**
 * 跳转到关联的订单详情页
 */
function goOrder() {
  if (afterSale.value?.orderId) {
    router.push(`/order/${afterSale.value.orderId}`)
  }
}

onMounted(load)
</script>

<style scoped>
.aftersale-detail-page { min-height: 100vh; padding-bottom: 60px; }
.container { max-width: 900px; margin: 0 auto; padding: 0 20px; }
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

.panel {
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-lg);
  padding: 20px 24px;
  margin-bottom: 16px;
  box-shadow: var(--shadow-sm);
}
.panel-head {
  padding-bottom: 12px; margin-bottom: 12px;
  border-bottom: 1px solid var(--border-subtle);
}
.panel-title { font-size: 16px; font-weight: 600; color: var(--text-primary); }

.item-row {
  display: flex; align-items: center; padding: 8px 0;
}
.thumb { width: 64px; height: 64px; border-radius: var(--radius-sm); object-fit: cover; margin-right: 14px; }
.item-name { color: var(--text-primary); font-size: 14px; font-weight: 500; margin-bottom: 4px; }
.item-spec { color: var(--text-muted); font-size: 12px; }
.item-qty { color: var(--text-muted); font-size: 13px; }

.price-red { color: #E33; font-weight: 600; }
.mono { font-family: 'Courier New', monospace; }

:deep(.el-descriptions__label) { width: 120px; color: var(--text-muted); }
</style>
