<template>
  <div class="recycle-bin">
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">商品回收站</span>
        <el-button type="danger" @click="handleClearAll" :disabled="total === 0">清空回收站</el-button>
      </div>
      <div class="filter-bar">
        <el-input v-model="keyword" placeholder="搜索商品名称" style="width: 280px" clearable @keyup.enter="load" />
      </div>

      <el-table :data="list" v-loading="loading" stripe @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" />
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="商品名称" min-width="180" />
      <el-table-column prop="categoryName" label="分类" width="120" />
      <el-table-column label="价格" width="100">
        <template #default="{ row }">¥{{ Number(row.minPrice || 0).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="deleteTime" label="删除时间" width="170">
        <template #default="{ row }">{{ row.deleteTime || row.updateTime }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handleRestore(row.id)">恢复</el-button>
          <el-button type="danger" link size="small" @click="handleForceDelete(row.id)">彻底删除</el-button>
        </template>
      </el-table-column>
    </el-table>

      <div class="batch-bar" v-if="selectedIds.length > 0">
        <span>已选 {{ selectedIds.length }} 项</span>
        <el-button type="primary" @click="handleBatchRestore">批量恢复</el-button>
        <el-button type="danger" @click="handleBatchDelete">批量彻底删除</el-button>
      </div>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total"
          layout="total, prev, pager, next" @current-change="load" @size-change="load"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminRequest } from '@/api/request'

const loading = ref(false)
const keyword = ref('')
const list = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const selectedIds = ref<any[]>([])

function handleSelectionChange(rows: any) {
  selectedIds.value = rows.map((r: any) => r.id)
}

async function load() {
  loading.value = true
  try {
    const params: Record<string, any> = { page: pageNum.value, pageSize: pageSize.value }
    if (keyword.value) params.name = keyword.value
    const res: any = await adminRequest({ url: '/admin/recycle/page', method: 'get', params })
    const d: any = res?.data || res || {}
    list.value = d.records || d.list || []
    total.value = d.total || 0
  } catch (e: any) {} finally { loading.value = false }
}

async function handleRestore(id: any) {
  try {
    await adminRequest({ url: `/admin/recycle/${id}/restore`, method: 'post' })
    ElMessage.success('已恢复')
    await load()
  } catch (e: any) { ElMessage.error('恢复失败') }
}

async function handleForceDelete(id: any) {
  try {
    await ElMessageBox.confirm('彻底删除后不可恢复，确定？', '警告', { type: 'warning', confirmButtonText: '确定删除' })
    await adminRequest({ url: `/admin/recycle/${id}`, method: 'delete' })
    ElMessage.success('已彻底删除')
    await load()
  } catch (e: any) {}
}

async function handleBatchRestore() {
  try {
    await adminRequest({ url: '/admin/recycle/batch-restore', method: 'post', data: { ids: selectedIds.value } })
    ElMessage.success('批量恢复成功')
    selectedIds.value = []
    await load()
  } catch (e: any) { ElMessage.error('操作失败') }
}

async function handleBatchDelete() {
  try {
    await ElMessageBox.confirm(`确定彻底删除所选 ${selectedIds.value.length} 个商品？`, '警告', { type: 'warning', confirmButtonText: '确定删除' })
    await adminRequest({ url: '/admin/recycle/batch-delete', method: 'post', data: { ids: selectedIds.value } })
    ElMessage.success('批量删除成功')
    selectedIds.value = []
    await load()
  } catch (e: any) {}
}

async function handleClearAll() {
  try {
    await ElMessageBox.confirm('确定清空回收站？所有商品将不可恢复！', '严重警告', { type: 'error', confirmButtonText: '确定清空' })
    await adminRequest({ url: '/admin/recycle/clear', method: 'delete' })
    ElMessage.success('回收站已清空')
    await load()
  } catch (e: any) {}
}

onMounted(load)
</script>

<style scoped>
.recycle-bin { display: flex; flex-direction: column; gap: 16px; }
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
.batch-bar { margin-top: 12px; padding: 10px 16px; background: var(--brand-primary-soft); border-radius: var(--radius-sm); display: flex; align-items: center; gap: 12px; }
</style>