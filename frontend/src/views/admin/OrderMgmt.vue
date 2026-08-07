<template>
  <div class="mgmt-page">
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">订单管理</span>
        <el-button @click="handleExportOrders" :loading="exporting">导出订单</el-button>
      </div>

      <div class="filter-bar">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 210px;"
          :disabled="loading"
        />
        <el-input v-model="query.orderNo" placeholder="订单号搜索" style="width: 180px" clearable :disabled="loading" @keyup.enter="onQueryClick" />
        <el-select v-model="query.status" placeholder="订单状态" clearable style="width: 120px;" :disabled="loading">
          <el-option label="待付款" value="PENDING" />
          <el-option label="待发货" value="PAID" />
          <el-option label="待收货" value="SHIPPED" />
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="已取消" value="CANCELLED" />
          <el-option label="已退款" value="REFUNDED" />
        </el-select>
        <el-button type="primary" :disabled="loading" @click="onQueryClick">查询</el-button>
      </div>

      <el-table :data="records" v-loading="loading" stripe empty-text="暂无数据" style="width: 100%;">
        <el-table-column prop="orderNo" label="订单号" width="200" show-overflow-tooltip />
        <el-table-column label="订单金额" width="120">
          <template #default="{ row }">¥{{ Number(row.payAmount ?? 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="商品明细" min-width="250">
          <template #default="{ row }">
            <div class="order-items">
              <template v-if="row.items && row.items.length">
                <div v-for="it in row.items" :key="it.id" class="order-item-line">
                  <el-image v-if="it.pic" :src="it.pic" style="width: 32px; height: 32px; border-radius: var(--radius-sm);" fit="cover" class="item-thumb">
                    <template #error><div class="img-placeholder-xs">N/A</div></template>
                  </el-image>
                  <span class="item-name">{{ it.spuName }}</span>
                  <span class="item-specs" v-if="it.skuSpecs">{{ it.skuSpecs }}</span>
                  <span class="item-qty">x{{ it.qty }}</span>
                  <span class="item-price">¥{{ Number(it.price ?? 0).toFixed(2) }}</span>
                </div>
              </template>
              <span v-else class="text-muted">-</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="address" label="收货地址" min-width="160" show-overflow-tooltip />
        <el-table-column label="支付方式" width="90">
          <template #default="{ row }">{{ row.payMethod === 1 ? '微信' : row.payMethod === 2 ? '支付宝' : '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="下单时间" width="170" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PAID'" type="primary" link size="small" @click="handleShip(row)">发货</el-button>
            <template v-if="row.status === 'COMPLETED' || row.status === 'CANCELLED' || row.status === 'REFUNDED'">
              <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
            </template>
            <el-tooltip v-else-if="row.status === 'SHIPPED'" content="已发货，等待用户收货" placement="top">
              <span class="text-muted" style="font-size: 12px;">已发货</span>
            </el-tooltip>
            <el-tooltip v-else-if="row.status === 'PENDING'" content="待用户付款" placement="top">
              <span class="text-muted" style="font-size: 12px;">-</span>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.pageSize"
          :total="total"
          layout="total, prev, pager, next, sizes"
          :page-sizes="[10, 20, 50, 100]"
          background
          @current-change="loadPage"
          @size-change="loadPage"
        />
      </div>
    </div>

    <!-- 发货对话框 -->
    <el-dialog v-model="shipDialogVisible" title="发货" width="420px">
      <el-form label-width="80px">
        <el-form-item label="订单号">
          <span class="form-text">{{ shipTarget.orderNo }}</span>
        </el-form-item>
        <el-form-item label="快递公司">
          <el-input v-model="shipForm.deliveryCompany" placeholder="如：顺丰、中通（可选）" />
        </el-form-item>
        <el-form-item label="快递单号">
          <el-input v-model="shipForm.trackingNo" placeholder="输入快递单号（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shipDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="shipSubmitting" @click="doShip">确认发货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { pageOrder, shipOrder, deleteOrder } from '@/api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()

const loading = ref(false)
const records = ref([])
const total = ref(0)
const shipDialogVisible = ref(false)
const shipSubmitting = ref(false)
const shipTarget = reactive({})
const shipForm = reactive({ trackingNo: '', deliveryCompany: '' })

const query = reactive({ page: 1, pageSize: 10, orderNo: '', status: '' })
const dateRange = ref(null)

const STATUS_MAP = {
  PENDING: '待付款',
  PAID: '待发货',
  SHIPPED: '待收货',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  REFUNDING: '退款中',
  REFUNDED: '已退款'
}

/** 获取订单状态的中文标签 */
function statusLabel(s) {
  return STATUS_MAP[s] || s || '-'
}

/** 获取订单状态对应的 Element Plus Tag 类型 */
function statusTagType(s) {
  switch (s) {
    case 'PENDING': return 'warning'
    case 'PAID': return 'primary'
    case 'SHIPPED': return 'success'
    case 'COMPLETED': return 'info'
    case 'CANCELLED': return 'danger'
    case 'REFUNDING': return 'warning'
    case 'REFUNDED': return 'danger'
    default: return 'info'
  }
}

/** 点击查询按钮，重置到第1页并加载数据 */
function onQueryClick() {
  query.page = 1
  loadPage()
}

/** 将日期对象或字符串转为 YYYY-MM-DD 格式 */
function fmtDate(d) {
  if (!d) return ''
  const dt = d instanceof Date ? d : new Date(d)
  const y = dt.getFullYear()
  const m = String(dt.getMonth() + 1).padStart(2, '0')
  const day = String(dt.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/** 加载订单分页列表，根据当前查询条件筛选 */
async function loadPage() {
  loading.value = true
  try {
    const params = {
      page: query.page,
      pageSize: query.pageSize
    }
    if (query.orderNo) params.orderNo = query.orderNo
    if (query.status) params.status = query.status
    if (dateRange.value && dateRange.value.length === 2 && dateRange.value[0] && dateRange.value[1]) {
      params.startTime = fmtDate(dateRange.value[0])
      params.endTime = fmtDate(dateRange.value[1])
    }
    const res = await pageOrder(params)
    const d = res || {}
    records.value = d.records || d.list || []
    total.value = d.total || 0
  } finally {
    loading.value = false
  }
}

/** 打开发货对话框，回填当前订单信息 */
function handleShip(row) {
  Object.assign(shipTarget, row)
  shipForm.trackingNo = ''
  shipForm.deliveryCompany = ''
  shipDialogVisible.value = true
}

/** 执行发货操作，提交快递公司和快递单号 */
async function doShip() {
  shipSubmitting.value = true
  try {
    await shipOrder(shipTarget.id, {
      trackingNo: shipForm.trackingNo || null,
      deliveryCompany: shipForm.deliveryCompany || null
    })
    ElMessage.success('发货成功，已通知用户')
    shipDialogVisible.value = false
    loadPage()
  } catch (e) {
    if (e !== 'cancel') {
      const msg = e?.response?.data?.msg || e?.message || '发货失败'
      ElMessage.error(msg)
    }
  } finally { shipSubmitting.value = false }
}

/** 删除订单，二次确认后执行永久删除 */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定永久删除订单「${row.orderNo}」？此操作不可撤销。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
    )
    await deleteOrder(row.id)
    ElMessage.success('已删除')
    loadPage()
  } catch (e) {
    if (e !== 'cancel' && e?.message) {
      const msg = e?.response?.data?.msg || e?.message || '删除失败'
      ElMessage.error(msg)
    }
  }
}

/** 导出订单数据为 Excel 文件 */
async function handleExportOrders() {
  exporting.value = true
  try {
    const params = new URLSearchParams()
    if (query.status) params.append('status', query.status)
    if (dateRange.value?.[0]) params.append('startTime', toDateStr(dateRange.value[0]))
    if (dateRange.value?.[1]) params.append('endTime', toDateStr(dateRange.value[1]) + ' 23:59:59')
    const token = localStorage.getItem('stellar_admin_token') || ''
    const resp = await fetch(`/admin/export/orders?${params}`, { headers: { token } })
    if (!resp.ok) throw new Error()
    downloadBlob(await resp.blob(), '订单数据导出.xlsx')
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}

const exporting = ref(false)
/** 将日期对象转为 YYYY-MM-DD 字符串 */
function toDateStr(d) {
  if (!d) return ''
  const dt = new Date(d)
  return dt.getFullYear() + '-' + String(dt.getMonth() + 1).padStart(2, '0') + '-' + String(dt.getDate()).padStart(2, '0')
}
/** 下载 Blob 数据为文件 */
function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = filename; a.click()
  URL.revokeObjectURL(url)
}

onMounted(() => {
  // 从 URL 参数读取预设筛选条件（由控制台等页面跳转带入）
  if (route.query.status) {
    query.status = route.query.status
  }
  if (route.query.startTime && route.query.endTime) {
    dateRange.value = [new Date(route.query.startTime), new Date(route.query.endTime)]
  }
  loadPage()
})
</script>

<style scoped>
.mgmt-page { display: flex; flex-direction: column; gap: 16px; }
.panel {
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-lg);
  padding: 20px 24px;
  box-shadow: var(--shadow-sm);
}
.panel-head {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;
}
.panel-title {
  font-size: 18px; font-weight: 600; color: var(--text-primary);
}
.filter-bar {
  margin-bottom: 16px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-md);
  padding: 12px 16px;
}
.pagination-wrap { margin-top: 20px; display: flex; justify-content: flex-end; }

.order-items { display: flex; flex-direction: column; gap: 6px; }
.order-item-line { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.item-thumb { flex-shrink: 0; }
.item-name { color: var(--text-primary); font-weight: 500; max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-specs { color: var(--text-muted); font-size: 12px; max-width: 100px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-qty { color: var(--text-muted); font-size: 12px; }
.item-price { color: var(--status-danger); font-weight: 500; margin-left: auto; }

.img-placeholder-xs {
  width: 32px; height: 32px; background: var(--bg-base); color: var(--text-muted);
  display: flex; align-items: center; justify-content: center; border-radius: var(--radius-sm); font-size: 10px;
}
.text-muted { color: var(--text-muted); }
</style>