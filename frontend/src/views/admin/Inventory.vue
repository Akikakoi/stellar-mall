<template>
  <div class="inventory-page">
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">库存管理</span>
        <el-button type="primary" @click="exportCSV">导出CSV</el-button>
      </div>

      <div class="filter-bar">
        <el-input v-model="keyword" placeholder="搜索商品或SKU" style="width: 300px" clearable @keyup.enter="load" />
        <el-select v-model="filterLowStock" placeholder="库存状态" clearable style="width: 150px;" @change="load">
          <el-option label="低库存预警" value="1" />
          <el-option label="全部" value="" />
        </el-select>
      </div>

      <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="id" label="SKU ID" width="70" />
      <el-table-column prop="spuId" label="SPU ID" width="70" />
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
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="openEditStock(row)">调整库存</el-button>
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

    <el-dialog v-model="stockDialogVisible" title="调整库存" width="400px">
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
      </el-form>
      <template #footer>
        <el-button @click="stockDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleUpdateStock">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { adminRequest } from '@/api/request'

const loading = ref(false)
const submitting = ref(false)
const keyword = ref('')
const filterLowStock = ref('')
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const stockDialogVisible = ref(false)
const stockForm = reactive({ skuId: null, currentStock: 0, delta: 0, warnStock: 10 })

async function load() {
  loading.value = true
  try {
    const params = { page: pageNum.value, pageSize: pageSize.value }
    if (keyword.value) params.name = keyword.value
    if (filterLowStock.value === '1') params.lowStock = 1
    const res = await adminRequest({ url: '/admin/inventory/page', method: 'get', params })
    const d = res?.data || res || {}
    list.value = d.records || d.list || []
    total.value = d.total || 0
  } catch (e) {} finally { loading.value = false }
}

function openEditStock(row) {
  stockForm.skuId = row.id
  stockForm.currentStock = row.stock || 0
  stockForm.delta = 0
  stockForm.warnStock = row.warnStock || 10
  stockDialogVisible.value = true
}

async function handleUpdateStock() {
  submitting.value = true
  try {
    await adminRequest({ url: '/admin/inventory/stock', method: 'put', data: { ...stockForm } })
    ElMessage.success('库存已更新')
    stockDialogVisible.value = false
    await load()
  } catch (e) { ElMessage.error('操作失败') } finally { submitting.value = false }
}

function exportCSV() {
  let csv = 'SKU ID,SPU ID,名称,规格,价格,库存,预警值,状态\n'
  list.value.forEach(r => {
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