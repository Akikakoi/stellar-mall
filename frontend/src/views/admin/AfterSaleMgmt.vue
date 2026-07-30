<template>
  <div class="admin-aftersale-page">
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="售后状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable class="sel">
            <el-option v-for="(v, k) in AFTER_SALE_STATUS_TEXT" :key="k" :label="v" :value="Number(k)" />
          </el-select>
        </el-form-item>
        <el-form-item label="售后类型">
          <el-select v-model="searchForm.type" placeholder="全部" clearable class="sel">
            <el-option v-for="(v, k) in AFTER_SALE_TYPE_TEXT" :key="k" :label="v" :value="Number(k)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="id" label="售后单号" width="120">
          <template #default="{ row }">AS{{ row.id }}</template>
        </el-table-column>
        <el-table-column prop="orderNo" label="订单编号" width="180" />
        <el-table-column prop="spuName" label="商品" min-width="180" show-overflow-tooltip />
        <el-table-column label="售后类型" width="120">
          <template #default="{ row }">{{ row.typeText }}</template>
        </el-table-column>
        <el-table-column label="退款金额" width="120">
          <template #default="{ row }">¥{{ Number(row.amount || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="售后状态" width="130">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ row.statusText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="申请时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="viewDetail(row)">详情</el-button>
            <el-button v-if="row.status === AFTER_SALE_STATUS.APPLIED" size="small" link type="success" @click="approve(row)">通过</el-button>
            <el-button v-if="row.status === AFTER_SALE_STATUS.APPLIED" size="small" link type="danger" @click="reject(row)">拒绝</el-button>
            <el-button v-if="row.status === AFTER_SALE_STATUS.REFUNDING" size="small" link type="primary" @click="confirmRefundBtn(row)">确认退款</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 审核弹窗 -->
    <el-dialog v-model="auditVisible" title="审核售后" width="500px" destroy-on-close>
      <el-descriptions v-if="auditRow" :column="1" border size="default">
        <el-descriptions-item label="售后单号">AS{{ auditRow.id }}</el-descriptions-item>
        <el-descriptions-item label="商品">{{ auditRow.spuName }}</el-descriptions-item>
        <el-descriptions-item label="售后类型">{{ auditRow.typeText }}</el-descriptions-item>
        <el-descriptions-item label="退款金额">¥{{ Number(auditRow.amount || 0).toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="申请原因">{{ auditRow.reason }}</el-descriptions-item>
        <el-descriptions-item label="详细描述">{{ auditRow.detail || '（无）' }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="100px" style="margin-top: 16px;">
        <el-form-item label="审核备注">
          <el-input v-model="auditRemark" type="textarea" :rows="3" :placeholder="auditAction === 'approve' ? '通过备注（选填）' : '请填写拒绝原因'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button :type="auditAction === 'approve' ? 'success' : 'danger'" :loading="auditSubmitting" @click="doAudit">
          {{ auditAction === 'approve' ? '确认通过' : '确认拒绝' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="售后详情" width="550px" destroy-on-close>
      <el-descriptions v-if="detailRow" :column="1" border size="default">
        <el-descriptions-item label="售后单号">AS{{ detailRow.id }}</el-descriptions-item>
        <el-descriptions-item label="关联订单">{{ detailRow.orderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="商品">{{ detailRow.spuName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="规格">{{ detailRow.skuSpecs || '默认规格' }}</el-descriptions-item>
        <el-descriptions-item label="售后类型">
          <el-tag size="small">{{ detailRow.typeText }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="售后状态">
          <el-tag :type="statusTag(detailRow.status)" size="small">{{ detailRow.statusText }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="退款金额">
          <span style="color: #E33; font-weight: 600;">¥{{ Number(detailRow.amount || 0).toFixed(2) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="申请原因">{{ detailRow.reason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="详细描述">{{ detailRow.detail || '（无）' }}</el-descriptions-item>
        <el-descriptions-item v-if="detailRow.auditRemark" label="审核备注">{{ detailRow.auditRemark }}</el-descriptions-item>
        <el-descriptions-item v-if="detailRow.auditTime" label="审核时间">{{ detailRow.auditTime }}</el-descriptions-item>
        <el-descriptions-item v-if="detailRow.returnTracking" label="退货运单">{{ detailRow.returnTracking }}</el-descriptions-item>
        <el-descriptions-item v-if="detailRow.refundTime" label="退款时间">{{ detailRow.refundTime }}</el-descriptions-item>
        <el-descriptions-item label="申请时间">{{ detailRow.createTime || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 退款确认弹窗 -->
    <el-dialog v-model="confirmRefundVisible" title="确认退款" width="420px" destroy-on-close>
      <p v-if="confirmRefundRow">
        确认退款完成？售后单号 <b>AS{{ confirmRefundRow.id }}</b>，
        金额 <b style="color: #E33;">¥{{ Number(confirmRefundRow.amount || 0).toFixed(2) }}</b>
      </p>
      <p style="color: var(--text-muted); font-size: 13px;">确认后订单将标记为"已完成"，库存将回滚。</p>
      <template #footer>
        <el-button @click="confirmRefundVisible = false">取消</el-button>
        <el-button type="primary" :loading="confirmRefundSubmitting" @click="doConfirmRefund">确认退款</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { pageAfterSale, auditAfterSale, confirmRefund } from '@/api/admin'
import { AFTER_SALE_STATUS, AFTER_SALE_STATUS_TEXT, AFTER_SALE_TYPE_TEXT } from '@/constants/order'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const searchForm = ref({ status: null, type: null })

// 审核
const auditVisible = ref(false)
const auditRow = ref(null)
const auditAction = ref('approve')
const auditRemark = ref('')
const auditSubmitting = ref(false)

// 详情
const detailVisible = ref(false)
const detailRow = ref(null)

// 退款确认
const confirmRefundVisible = ref(false)
const confirmRefundRow = ref(null)
const confirmRefundSubmitting = ref(false)

const STATUS_TAG_MAP = {
  [AFTER_SALE_STATUS.APPLIED]: 'warning',
  [AFTER_SALE_STATUS.AUDITING]: '',
  [AFTER_SALE_STATUS.RETURNING]: 'primary',
  [AFTER_SALE_STATUS.REFUNDING]: 'danger',
  [AFTER_SALE_STATUS.COMPLETED]: 'success',
  [AFTER_SALE_STATUS.REJECTED]: 'info',
  [AFTER_SALE_STATUS.CANCELLED]: 'info'
}
function statusTag(s) { return STATUS_TAG_MAP[s] || 'info' }

async function loadData() {
  loading.value = true
  try {
    const res = await pageAfterSale({
      page: page.value,
      pageSize: pageSize.value,
      status: searchForm.value.status || undefined,
      type: searchForm.value.type || undefined
    })
    const d = res || {}
    tableData.value = d.records || d.list || []
    total.value = d.total || 0
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '加载失败')
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  loadData()
}

function reset() {
  searchForm.value = { status: null, type: null }
  page.value = 1
  loadData()
}

function viewDetail(row) {
  detailRow.value = row
  detailVisible.value = true
}

function approve(row) {
  auditRow.value = row
  auditAction.value = 'approve'
  auditRemark.value = ''
  auditVisible.value = true
}

function reject(row) {
  auditRow.value = row
  auditAction.value = 'reject'
  auditRemark.value = ''
  auditVisible.value = true
}

async function doAudit() {
  if (auditAction.value === 'reject' && !auditRemark.value.trim()) {
    ElMessage.warning('拒绝时必须填写拒绝原因')
    return
  }
  auditSubmitting.value = true
  try {
    await auditAfterSale({
      id: auditRow.value.id,
      approved: auditAction.value === 'approve',
      remark: auditRemark.value.trim() || null
    })
    ElMessage.success(auditAction.value === 'approve' ? '审核通过' : '已拒绝')
    auditVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '操作失败')
  } finally {
    auditSubmitting.value = false
  }
}

function confirmRefundBtn(row) {
  confirmRefundRow.value = row
  confirmRefundVisible.value = true
}

async function doConfirmRefund() {
  confirmRefundSubmitting.value = true
  try {
    await confirmRefund(confirmRefundRow.value.id)
    ElMessage.success('退款已完成，库存已回滚')
    confirmRefundVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '退款失败')
  } finally {
    confirmRefundSubmitting.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.admin-aftersale-page { padding: 20px; }
.search-card { margin-bottom: 16px; }
.sel { width: 160px; }
.table-card { min-height: 400px; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
