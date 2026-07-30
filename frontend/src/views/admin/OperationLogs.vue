<template>
  <div class="panel">
      <div class="panel-head">
        <span class="panel-title">操作日志</span>
      </div>
      <div class="filter-bar">
        <el-select v-model="action" placeholder="按操作类型筛选" clearable style="width:260px" @change="reload">
          <el-option v-for="a in actions" :key="a" :label="a" :value="a" />
        </el-select>
        <el-button :icon="Refresh" @click="reload">刷新</el-button>
      </div>
      <el-table :data="items" v-loading="loading" size="default" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="created_at" label="时间" width="180">
          <template #default="{row}">{{ fmt(row.created_at) }}</template>
        </el-table-column>
        <el-table-column prop="username" label="操作用户" width="130">
          <template #default="{row}">
            <el-tag v-if="row.username" size="small">{{ row.username }}</el-tag>
            <span v-else style="color:var(--text-muted)">未登录/系统</span>
          </template>
        </el-table-column>
        <el-table-column prop="action" label="操作类型" width="140">
          <template #default="{row}">
            <el-tag :type="actionColor(row.action)" size="small" effect="dark">{{ row.action }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="resource" label="资源对象" show-overflow-tooltip />
        <el-table-column prop="detail" label="详情" min-width="260" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" width="140" />
        <el-table-column label="结果" width="90" align="center">
          <template #default="{row}">
            <el-tag size="small" :type="row.status==='fail'?'danger':'success'">
              {{ row.status==='fail' ? '失败' : '成功' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          layout="total, sizes, prev, pager, next, jumper"
          :total="total" v-model:current-page="page" v-model:page-size="pageSize"
          :page-sizes="[20, 50, 100]"
          @current-change="reload"
          @size-change="reload"
        />
      </div>
    </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { apiAdminLogs } from '@/api/rag'

const actions = ['login','register','change_password','kb_upload','kb_delete','chat']
const page = ref(1); const pageSize = ref(20); const total = ref(0); const loading = ref(false)
const items = ref([]); const action = ref('')

onMounted(reload)

async function reload() {
  loading.value = true
  try {
    const r = await apiAdminLogs({ page: page.value, page_size: pageSize.value, action: action.value })
    items.value = r.data.items; total.value = r.data.total
  } finally { loading.value = false }
}
function fmt(v) { return v ? new Date(v).toLocaleString('zh-CN', {hour12:false}) : '' }
function actionColor(a) {
  return ({
    login:'success', register:'primary', change_password:'warning',
    kb_upload:'success', kb_delete:'danger', chat:'info',
  })[a] || ''
}
</script>

<style scoped>
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
</style>
