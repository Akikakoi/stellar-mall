<template>
  <div class="inventory-page">
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">出入库日志</span>
        <div style="display: flex; gap: 8px;">
          <el-button @click="openBatchDialog">批量调整</el-button>
          <el-button type="primary" @click="openAdjustDialog">库存调整</el-button>
        </div>
      </div>

      <div class="filter-bar">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          style="width: 260px"
          @change="resetAndLoad"
        />
        <el-input
          v-model="keyword"
          placeholder="搜索商品名称"
          style="width: 220px"
          clearable
          @keyup.enter="resetAndLoad"
          @clear="resetAndLoad"
        />
        <el-select v-model="filterType" placeholder="操作类型" clearable style="width: 130px;" @change="resetAndLoad">
          <el-option label="入库" :value="1" />
          <el-option label="出库" :value="2" />
        </el-select>
        <el-button type="primary" plain @click="resetAndLoad">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column label="操作时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="商品" min-width="220">
          <template #default="{ row }">
            <div class="goods-cell">
              <span class="goods-name">{{ row.skuName || `SKU#${row.skuId}` }}</span>
              <span v-if="row.specs" class="goods-specs">{{ row.specs }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作类型" width="90">
          <template #default="{ row }">
            <el-tag :type="logTypeTag(row.type)" size="small">{{ logTypeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="80">
          <template #default="{ row }">
            <span :style="{ color: (row.quantity || 0) > 0 ? 'var(--status-success)' : 'var(--status-danger)' }">
              {{ (row.quantity || 0) > 0 ? '+' : '' }}{{ row.quantity }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="变更前 → 变更后" width="130" align="center">
          <template #default="{ row }">
            <span class="stock-transition">{{ row.stockBefore }} → {{ row.stockAfter }}</span>
          </template>
        </el-table-column>
        <el-table-column label="业务来源" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="bizTypeTag(row.businessType)" effect="plain">{{ bizTypeLabel(row.businessType, row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="相关单据" min-width="150">
          <template #default="{ row }">{{ row.businessNo || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作人" width="110">
          <template #default="{ row }">{{ operatorLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="备注" min-width="140">
          <template #default="{ row }">{{ row.remark || '—' }}</template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total"
          :page-sizes="[15, 20, 50, 100]"
          layout="total, sizes, prev, pager, next" @current-change="load" @size-change="resetAndLoad"
        />
      </div>
    </div>

    <!-- 库存调整对话框 -->
    <el-dialog v-model="stockDialogVisible" title="库存调整" width="460px">
      <el-form :model="stockForm" label-width="100px">
        <el-form-item label="选择商品">
          <el-select
            v-model="stockForm.skuId"
            filterable
            remote
            :remote-method="searchSku"
            :loading="skuSearching"
            placeholder="输入 SKU 名称搜索"
            style="width: 100%"
          >
            <el-option
              v-for="s in skuOptions"
              :key="s.id"
              :label="`${s.name}${s.specs ? ' (' + s.specs + ')' : ''} — 库存 ${s.stock}`"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="当前库存">
          <span>{{ selectedSku?.stock ?? '—' }}</span>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { adminRequest } from '@/api/request'

const loading = ref(false)
const submitting = ref(false)
const keyword = ref('')
const filterType = ref<number | ''>('')
const dateRange = ref<[string, string] | null>(null)
const list = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

// 库存调整
const stockDialogVisible = ref(false)
const skuSearching = ref(false)
const skuOptions = ref<any[]>([])
const stockForm = reactive<any>({ skuId: null, delta: 0, warnStock: 10, remark: '' })
const selectedSku = computed(() => skuOptions.value.find(s => s.id === stockForm.skuId))

// 批量调整
const batchDialogVisible = ref(false)
const batchSubmitting = ref(false)
const batchInput = ref('')

const typeLabels: Record<number, string> = { 1: '入库', 2: '出库', 3: '盘盈', 4: '盘亏', 5: '调整' }
const typeTags: Record<number, string> = { 1: 'success', 2: 'danger', 3: 'warning', 4: 'info', 5: '' }

function logTypeLabel(type: number) { return typeLabels[type] || `未知(${type})` }
function logTypeTag(type: number) { return typeTags[type] || 'info' }

/** 业务来源（businessType）展示：自动流水（订单出库/取消回滚）、手动调整等 */
const bizTypeLabels: Record<string, string> = {
  SALE_OUT: '订单出库',
  ORDER_ROLLBACK: '取消回滚',
  ADJUSTMENT: '手动调整'
}
const bizTypeTags: Record<string, string> = {
  SALE_OUT: 'danger',
  ORDER_ROLLBACK: 'success',
  ADJUSTMENT: 'warning'
}
function bizTypeLabel(businessType?: string, type?: number) {
  if (businessType && bizTypeLabels[businessType]) return bizTypeLabels[businessType]
  // 手动调整未落 businessType（旧数据）时按 type 兜底
  if (!businessType && type) return typeLabels[type] || logTypeLabel(type)
  return businessType || '—'
}
function bizTypeTag(businessType?: string) {
  return (businessType && bizTypeTags[businessType]) || 'info'
}

/** 操作人展示：管理端调整 → 员工姓名；订单自动流水 → 系统买家/系统 */
function operatorLabel(row: any) {
  if (row.operatorName) return row.operatorName
  if (!row.createUser || row.createUser === 0) return '系统'
  if (row.businessType === 'SALE_OUT' || row.businessType === 'ORDER_ROLLBACK') return `买家#${row.createUser}`
  return `#${row.createUser}`
}

function formatTime(t?: string) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 19)
}

function resetAndLoad() {
  pageNum.value = 1
  load()
}

function resetFilters() {
  keyword.value = ''
  filterType.value = ''
  dateRange.value = null
  resetAndLoad()
}

async function load() {
  loading.value = true
  try {
    const params: Record<string, any> = { page: pageNum.value, pageSize: pageSize.value }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (filterType.value !== '' && filterType.value != null) params.type = filterType.value
    if (dateRange.value?.[0]) params.begin = dateRange.value[0]
    if (dateRange.value?.[1]) params.end = dateRange.value[1]
    const res: any = await adminRequest({ url: '/admin/inventory/log', method: 'get', params })
    const d = res?.data || res || {}
    list.value = d.records || d.list || []
    total.value = d.total || 0
  } catch (e: any) { /* ignore */ } finally { loading.value = false }
}

// -------- 库存调整（弹窗内远程搜索 SKU） --------
function openAdjustDialog() {
  stockForm.skuId = null
  stockForm.delta = 0
  stockForm.warnStock = 10
  stockForm.remark = ''
  skuOptions.value = []
  stockDialogVisible.value = true
}

async function searchSku(query: string) {
  skuSearching.value = true
  try {
    const params: Record<string, any> = { page: 1, pageSize: 20 }
    if (query) params.name = query
    const res: any = await adminRequest({ url: '/admin/inventory/page', method: 'get', params })
    const d = res?.data || res || {}
    skuOptions.value = d.records || d.list || []
  } catch (e: any) { /* ignore */ } finally { skuSearching.value = false }
}

async function handleUpdateStock() {
  if (!stockForm.skuId) { ElMessage.warning('请先选择商品'); return }
  submitting.value = true
  try {
    await adminRequest({ url: '/admin/inventory/stock', method: 'put', data: { ...stockForm } })
    ElMessage.success('库存已调整')
    stockDialogVisible.value = false
    await load()
  } catch (e: any) { ElMessage.error(e?.response?.data?.msg || '操作失败') } finally { submitting.value = false }
}

// -------- 批量调整 --------
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
  } catch (e: any) { ElMessage.error(e?.response?.data?.msg || '批量调整失败') } finally { batchSubmitting.value = false }
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

.goods-cell { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.goods-name { color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.goods-specs { font-size: 12px; color: var(--text-muted); }
.stock-transition { font-variant-numeric: tabular-nums; }

:deep(.el-dialog) {
  border-radius: var(--radius-lg);
  overflow: hidden;
}
</style>
