<template>
  <div class="review-mgmt">
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">评价管理</span>
      </div>
      <div class="filter-bar">
        <el-input v-model="spuNameFilter" placeholder="商品名称" style="width: 200px" clearable @keyup.enter="load" />
        <el-select v-model="statusFilter" placeholder="状态" clearable style="width: 120px;" @change="load">
          <el-option label="显示" :value="1" />
          <el-option label="隐藏" :value="0" />
        </el-select>
      </div>

      <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column label="商品" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.spuName || '商品 #' + row.spuId }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="username" label="用户" width="100" />
      <el-table-column label="评分" width="180">
        <template #default="{ row }"><el-rate :model-value="row.rating" disabled size="small" /></template>
      </el-table-column>
      <el-table-column prop="content" label="内容" min-width="220" show-overflow-tooltip />
      <el-table-column prop="reply" label="回复" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.reply || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '显示' : '隐藏' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="openReply(row)">回复</el-button>
          <el-button v-if="row.status === 1" type="warning" link size="small" @click="toggleStatus(row.id, 0)">隐藏</el-button>
          <el-button v-else type="success" link size="small" @click="toggleStatus(row.id, 1)">显示</el-button>
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

    <el-dialog v-model="replyDialogVisible" title="回复评价" width="500px">
      <div class="review-preview">
        <div class="user-info">{{ replyTarget.username }} - {{ replyTarget.createTime?.substring(0, 10) }}</div>
        <div class="content">{{ replyTarget.content }}</div>
      </div>
      <el-input v-model="replyText" type="textarea" :rows="3" placeholder="输入回复内容..." style="margin-top: 12px" />
      <template #footer>
        <el-button @click="replyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleReply">回复</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { adminRequest } from '@/api/request'

const loading = ref(false)
const submitting = ref(false)
const spuIdFilter = ref<any>(null)
const spuNameFilter = ref('')
const statusFilter = ref<any>(null)
const list = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const replyDialogVisible = ref(false)
const replyTarget = reactive<any>({})
const replyText = ref('')

async function load() {
  loading.value = true
  try {
    const params: Record<string, any> = { page: pageNum.value, pageSize: pageSize.value }
    if (spuIdFilter.value) params.spuId = spuIdFilter.value
    if (spuNameFilter.value) params.spuName = spuNameFilter.value
    if (statusFilter.value !== null && statusFilter.value !== undefined) params.status = statusFilter.value
    const res: any = await adminRequest({ url: '/admin/review/page', method: 'get', params })
    const d = res?.data || res || {}
    list.value = d.records || d.list || []
    total.value = d.total || 0
  } catch (e: any) {} finally { loading.value = false }
}

function openReply(row: any) {
  Object.assign(replyTarget, row)
  replyText.value = row.reply || ''
  replyDialogVisible.value = true
}

async function handleReply() {
  submitting.value = true
  try {
    await adminRequest({ url: `/admin/review/${replyTarget.id}/reply`, method: 'post', data: { reply: replyText.value } })
    ElMessage.success('已回复')
    replyDialogVisible.value = false
    await load()
  } catch (e: any) { ElMessage.error('回复失败') } finally { submitting.value = false }
}

async function toggleStatus(id: any, status: any) {
  try {
    const url = status === 0 ? `/admin/review/${id}/hide` : `/admin/review/${id}/show`
    await adminRequest({ url, method: 'post' })
    ElMessage.success('状态已更新')
    await load()
  } catch (e: any) { ElMessage.error('操作失败') }
}

onMounted(load)
</script>

<style scoped>
.review-mgmt { display: flex; flex-direction: column; gap: 16px; }
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
.review-preview { background: var(--bg-hover); padding: 12px; border-radius: var(--radius-md); margin-bottom: 4px; }
.review-preview .user-info { color: var(--text-muted); font-size: 13px; margin-bottom: 6px; }
.review-preview .content { color: var(--text-primary); font-size: 14px; }

:deep(.el-dialog) {
  border-radius: var(--radius-lg);
  overflow: hidden;
}
</style>