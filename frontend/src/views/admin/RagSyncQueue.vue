<template>
  <div class="rag-sync-page">
    <div class="stat-row">
      <div class="stat-card type-pending" @click="query.status = 0; loadPage()">
        <div class="stat-num">{{ stats.pending || 0 }}</div>
        <div class="stat-label">待同步</div>
      </div>
      <div class="stat-card type-processing" @click="query.status = 1; loadPage()">
        <div class="stat-num">{{ stats.processing || 0 }}</div>
        <div class="stat-label">处理中</div>
      </div>
      <div class="stat-card type-success" @click="query.status = 2; loadPage()">
        <div class="stat-num">{{ stats.success || 0 }}</div>
        <div class="stat-label">成功</div>
      </div>
      <div class="stat-card type-failed" @click="query.status = 3; loadPage()">
        <div class="stat-num">{{ stats.failed || 0 }}</div>
        <div class="stat-label">失败</div>
      </div>
      <div class="stat-card type-total" @click="query.status = null; loadPage()">
        <div class="stat-num">{{ total || 0 }}</div>
        <div class="stat-label">总数</div>
      </div>
    </div>

    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">RAG 同步队列 (Outbox)</span>
        <div class="actions">
          <el-button type="primary" @click="processAll" :loading="processingAll">
            <el-icon style="margin-right: 4px;"><RefreshRight /></el-icon>
            一键处理全部
          </el-button>
          <el-button @click="refreshAll">
            <el-icon style="margin-right: 4px;"><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </div>

      <div class="filter-bar">
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px;" @change="loadPage">
          <el-option label="待同步" :value="0" />
          <el-option label="处理中" :value="1" />
          <el-option label="成功" :value="2" />
          <el-option label="失败" :value="3" />
        </el-select>
        <el-select v-model="query.eventType" placeholder="事件类型" clearable style="width: 180px;" @change="loadPage">
          <el-option label="SPU 保存" value="SAVE" />
          <el-option label="SPU 上架" value="ONSHELF" />
          <el-option label="SPU 下架" value="OFFSHELF" />
          <el-option label="SPU 删除" value="DELETE" />
        </el-select>
        <el-input v-model="query.bizId" placeholder="业务ID (SPU ID)" style="width: 180px;" clearable @keyup.enter="loadPage" />
        <el-button type="primary" @click="loadPage">查询</el-button>
      </div>

      <el-table :data="records" v-loading="loading" stripe style="width: 100%;">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="eventType" label="事件类型" width="150">
          <template #default="{ row }">
            <el-tag :type="eventTag(row.eventType)">{{ row.eventType || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="bizId" label="业务ID" width="100" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="retryCount" label="重试" width="70" />
        <el-table-column label="错误信息" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.lastError || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column prop="processedAt" label="处理时间" width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" v-if="row.status === 0 || row.status === 3" @click="retryOne(row)">
              重试
            </el-button>
            <el-button link size="small" @click="viewPayload(row)">查看数据</el-button>
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

    <el-dialog v-model="payloadVisible" title="Payload 数据" width="720px">
      <pre class="payload-pre">{{ payloadJson }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { listRagSyncAll, listRagSyncPending, retryRagSyncOne, processAllRagSync, getRagSyncStats } from '@/api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, RefreshRight } from '@element-plus/icons-vue'

const loading = ref(false)
const processingAll = ref(false)
const records = ref([])
const total = ref(0)
const stats = reactive({ pending: 0, processing: 0, success: 0, failed: 0 })

const query = reactive({ page: 1, pageSize: 20, status: null, eventType: '', bizId: '' })

const payloadVisible = ref(false)
const payloadJson = ref('')

async function refreshAll() {
  await loadStats()
  loadPage()
}

const STATUS_MAP = {
  0: ['待同步', 'warning'],
  1: ['处理中', 'primary'],
  2: ['成功', 'success'],
  3: ['失败', 'danger']
}
function statusText(s) { return (STATUS_MAP[s] && STATUS_MAP[s][0]) || '未知' }
function statusTag(s) { return (STATUS_MAP[s] && STATUS_MAP[s][1]) || 'info' }

function eventTag(ev) {
  if (!ev) return 'info'
  if (ev === 'SAVE' || ev === 'UPDATE') return 'success'
  if (ev === 'ONSHELF') return 'primary'
  if (ev === 'OFFSHELF') return 'warning'
  if (ev === 'DELETE') return 'danger'
  return 'info'
}

async function loadStats() {
  try {
    const res = await getRagSyncStats()
    const d = res || {}
    stats.pending = d.pending ?? d.pendingCount ?? 0
    stats.processing = d.processing ?? d.processingCount ?? 0
    stats.success = d.success ?? d.successCount ?? 0
    stats.failed = d.failed ?? d.failedCount ?? 0
  } catch (e) {}
}

async function loadPage() {
  loading.value = true
  try {
    const res = await listRagSyncAll({ ...query })
    const d = res || {}
    if (Array.isArray(d.records)) {
      records.value = d.records
    } else if (Array.isArray(d.list)) {
      records.value = d.list
    } else if (Array.isArray(res)) {
      records.value = res
    } else {
      records.value = []
    }
    total.value = (typeof d.total === 'number') ? d.total : records.value.length
  } finally { loading.value = false }
}

async function retryOne(row) {
  try {
    await retryRagSyncOne(row.id)
    ElMessage.success('已加入重试队列')
    refreshAll()
  } catch (e) {}
}

async function processAll() {
  try {
    await ElMessageBox.confirm('确定立即处理所有待同步/失败的记录？', '提示', { type: 'warning' })
    processingAll.value = true
    const res = await processAllRagSync()
    const d = res || {}
    ElMessage.success(`处理完成：成功 ${d.successCount ?? d.success ?? 0}，失败 ${d.failedCount ?? d.failed ?? 0}`)
    refreshAll()
  } catch (e) {
  } finally { processingAll.value = false }
}

function viewPayload(row) {
  try {
    if (typeof row.payload === 'string') {
      payloadJson.value = JSON.stringify(JSON.parse(row.payload), null, 2)
    } else if (row.payload) {
      payloadJson.value = JSON.stringify(row.payload, null, 2)
    } else {
      payloadJson.value = '(空)'
    }
  } catch (e) {
    payloadJson.value = String(row.payload || '')
  }
  payloadVisible.value = true
}

onMounted(async () => {
  await loadStats()
  loadPage()
})
</script>

<style scoped>
.rag-sync-page { display: flex; flex-direction: column; gap: 16px; }
.stat-row { display: grid; grid-template-columns: repeat(5, 1fr); gap: 14px; }
.stat-card {
  padding: 20px;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  transition: transform var(--transition-base), box-shadow var(--transition-base);
  box-shadow: var(--shadow-sm);
  border-left: 4px solid transparent;
}
.stat-card:hover { transform: var(--hover-lift); box-shadow: var(--shadow-md); }
.type-pending { border-left-color: var(--status-warning); }
.type-pending .stat-num { color: var(--status-warning); }
.type-processing { border-left-color: var(--brand-primary); }
.type-processing .stat-num { color: var(--brand-primary); }
.type-success { border-left-color: var(--status-success); }
.type-success .stat-num { color: var(--status-success); }
.type-failed { border-left-color: var(--status-danger); }
.type-failed .stat-num { color: var(--status-danger); }
.type-total { border-left-color: var(--brand-primary); }
.type-total .stat-num { color: var(--brand-primary); }
.stat-num { font-size: 28px; font-weight: 700; margin-bottom: 6px; }
.stat-label { color: var(--text-muted); font-size: 14px; }

.panel {
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-lg);
  padding: 20px 24px;
  box-shadow: var(--shadow-sm);
}
.panel-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.panel-title { font-size: 18px; font-weight: 600; color: var(--text-primary); }
.actions { display: flex; gap: 10px; }
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

.payload-pre {
  max-height: 500px;
  overflow: auto;
  background: var(--bg-base);
  padding: 16px;
  border-radius: var(--radius-md);
  font-size: 13px;
  font-family: 'Courier New', monospace;
  color: var(--text-primary);
  white-space: pre-wrap;
  word-break: break-all;
}

:deep(.el-dialog) {
  border-radius: var(--radius-lg);
  overflow: hidden;
}
</style>
