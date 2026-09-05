<template>
  <div class="inventory-page">
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">库存管理</span>
        <div style="display: flex; gap: 8px;">
          <el-button @click="openBatchDialog">批量调整</el-button>
          <el-button type="primary" @click="exportCSV">导出CSV</el-button>
        </div>
      </div>

      <div class="filter-bar">
        <el-input v-model="keyword" placeholder="搜索商品名称" style="width: 300px" clearable @keyup.enter="load" />
        <el-select v-model="filterLowStock" placeholder="库存状态" clearable style="width: 150px;" @change="load">
          <el-option label="低库存预警" value="1" />
          <el-option label="全部" value="" />
        </el-select>
      </div>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="name" label="SKU名称" min-width="180" />
        <el-table-column prop="specs" label="规格" width="120" />
        <el-table-column label="价格" width="100">
          <template #default="{ row }">¥{{ Number(row.price || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="库存" width="120">
          <template #default="{ row }">
            <span :class="{ 'low-stock': row.stock <= (row.warnStock || 10) }">
              {{ row.stock }}
            </span>
            <el-tag v-if="row.stock <= (row.warnStock || 10)" type="danger" size="small" style="margin-left: 6px">低库存</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="warnStock" label="预警值" width="80" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '在售' : '停售' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openEditStock(row)">调整库存</el-button>
            <el-button type="primary" link size="small" @click="openStockLog(row)">流水</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total"
          layout="total, prev, pager, next" @current-change="load" @size-change="load"
        />
      </div>
    </div>

    <!-- 调整库存对话框 -->
    <el-dialog v-model="stockDialogVisible" title="调整库存" width="420px">
      <el-form :model="stockForm" label-width="100px">
        <el-form-item label="当前库存">
          <span>{{ stockForm.currentStock }}</span>
        </el-form-item>
        <el-form-item label="调整数量">
          <el-input-number v-model="stockForm.delta" :min="-9999" :max="9999" />
          <span style="margin-left: 8px; color: var(--text-muted);">正数入库，负数出库</span>
        </el-form-item>
        <el-form-item label="预警库存">
          <el-input-number v-model="stockForm.warnStock" :min="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="stockForm.remark" placeholder="调整原因（选填）" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stockDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleUpdateStock">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量调整对话框 -->
    <el-dialog v-model="batchDialogVisible" title="批量调整库存" width="500px">
      <p style="margin-bottom: 12px; color: var(--text-muted);">每行一个 SKU，格式：SKU_ID, 调整数量, 备注</p>
      <el-input
        v-model="batchInput"
        type="textarea"
        :rows="8"
        placeholder="例如：
1, 10, 采购入库
2, -5, 报废出库
3, 20, 盘盈入库"
      />
      <template #footer>
        <el-button @click="batchDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchSubmitting" @click="handleBatchUpdate">执行批量调整</el-button>
      </template>
    </el-dialog>

    <!-- 库存流水对话框 -->
    <el-dialog v-model="logDialogVisible" title="库存变动流水" width="800px">
      <template #header>
        <span>库存变动流水<template v-if="logSkuName"> — {{ logSkuName }}</template></span>
      </template>
      <el-table :data="logList" v-loading="logLoading" stripe size="small">
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ row.createTime }}</template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="logTypeTag(row.type)" size="small">{{ logTypeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="变动数量" width="100">
          <template #default="{ row }">
            <span :style="{ color: (row.quantity || 0) > 0 ? 'var(--status-success)' : 'var(--status-danger)' }">
              {{ (row.quantity || 0) > 0 ? '+' : '' }}{{ row.quantity }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="变动前" width="80" prop="stockBefore" />
        <el-table-column label="变动后" width="80" prop="stockAfter" />
        <el-table-column label="备注" min-width="160" prop="remark" />
        <el-table-column label="操作人" width="100" prop="createUser" />
      </el-table>
      <div class="pagination-wrap" style="margin-top: 16px;">
        <el-pagination
          v-model:current-page="logPageNum" v-model:page-size="logPageSize" :total="logTotal"
          layout="total, prev, pager, next" @current-change="loadStockLog" @size-change="loadStockLog"
          small
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { adminRequest } from '@/api/request'

const loading = ref(false)
const submitting = ref(false)
const keyword = ref('')
const filterLowStock = ref('')
const list = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const stockDialogVisible = ref(false)
const stockForm = reactive<any>({ skuId: null, currentStock: 0, delta: 0, warnStock: 10, remark: '' })

// 批量调整
const batchDialogVisible = ref(false)
const batchSubmitting = ref(false)
const batchInput = ref('')

// 库存流水
const logDialogVisible = ref(false)
const logLoading = ref(false)
const logSkuName = ref('')
const logSkuId = ref<number | null>(null)
const logList = ref<any[]>([])
const logTotal = ref(0)
const logPageNum = ref(1)
const logPageSize = ref(15)

const typeLabels: Record<number, string> = { 1: '入库', 2: '出库', 3: '盘盈', 4: '盘亏', 5: '调整' }
const typeTags: Record<number, string> = { 1: 'success', 2: 'danger', 3: 'warning', 4: 'info', 5: '' }

function logTypeLabel(type: number) { return typeLabels[type] || `未知(${type})` }
function logTypeTag(type: number) { return typeTags[type] || 'info' }

async function load() {
  loading.value = true
  try {
    const params: Record<string, any> = { page: pageNum.value, pageSize: pageSize.value }
    if (keyword.value) params.name = keyword.value
    if (filterLowStock.value === '1') params.lowStock = 1
    const res: any = await adminRequest({ url: '/admin/inventory/page', method: 'get', params })
    const d = res?.data || res || {}
    list.value = d.records || d.list || []
    total.value = d.total || 0
  } catch (e: any) { /* ignore */ } finally { loading.value = false }
}

function openEditStock(row: any) {
  stockForm.skuId = row.id
  stockForm.currentStock = row.stock || 0
  stockForm.delta = 0
  stockForm.warnStock = row.warnStock || 10
  stockForm.remark = ''
  stockDialogVisible.value = true
}

async function handleUpdateStock() {
  submitting.value = true
  try {
    await adminRequest({ url: '/admin/inventory/stock', method: 'put', data: { ...stockForm } })
    ElMessage.success('库存已更新')
    stockDialogVisible.value = false
    await load()
  } catch (e: any) { ElMessage.error('操作失败') } finally { submitting.value = false }
}

function openBatchDialog() {
  batchInput.value = ''
  batchDialogVisible.value = true
}

async function handleBatchUpdate() {
  const lines = batchInput.value.trim().split('\n').filter(l => l.trim())
  if (!lines.length) { ElMessage.warning('请输入要调整的 SKU 数据'); return }
  const items: any[] = []
  for (const line of lines) {
    const parts = line.split(',').map(s => s.trim())
    if (parts.length < 2) { ElMessage.warning(`格式错误: ${line}`); return }
    const skuId = Number(parts[0])
    const delta = Number(parts[1])
    if (!Number.isInteger(skuId) || !Number.isInteger(delta)) { ElMessage.warning(`数字格式错误: ${line}`); return }
    items.push({ skuId, delta, remark: parts[2] || '批量调整' })
  }
  batchSubmitting.value = true
  try {
    await adminRequest({ url: '/admin/inventory/batch-stock', method: 'post', data: items })
    ElMessage.success(`批量调整完成，共 ${items.length} 项`)
    batchDialogVisible.value = false
    await load()
  } catch (e: any) { ElMessage.error('批量调整失败') } finally { batchSubmitting.value = false }
}

function openStockLog(row: any) {
  logSkuId.value = row.id
  logSkuName.value = row.name
  logPageNum.value = 1
  loadStockLog()
  logDialogVisible.value = true
}

async function loadStockLog() {
  logLoading.value = true
  try {
    const params: Record<string, any> = { skuId: logSkuId.value, page: logPageNum.value, pageSize: logPageSize.value }
    const res: any = await adminRequest({ url: '/admin/inventory/log', method: 'get', params })
    const d = res?.data || res || {}
    logList.value = d.records || d.list || []
    logTotal.value = d.total || 0
  } catch (e: any) { /* ignore */ } finally { logLoading.value = false }
}

function exportCSV() {
  let csv = 'SKU ID,SPU ID,名称,规格,价格,库存,预警值,状态\n'
  list.value.forEach((r: any) => {
    csv += `${r.id},${r.spuId},"${r.name || ''}","${r.specs || ''}",${r.price},${r.stock},${r.warnStock},${r.status === 1 ? '在售' : '停售'}\n`
  })
  const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = '库存报表.csv'; a.click()
  URL.revokeObjectURL(url)
}

onMounted(load)
</script>

<style scoped>
.inventory-page { display: flex; flex-direction: column; gap: 16px; }
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
.low-stock { color: var(--status-danger); font-weight: 600; }

:deep(.el-dialog) {
  border-radius: var(--radius-lg);
  overflow: hidden;
}
</style>