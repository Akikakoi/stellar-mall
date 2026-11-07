<template>
  <div class="user-mgmt">
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">用户管理</span>
      </div>
      <div class="filter-bar">
        <el-input v-model="keyword" placeholder="搜索邮箱 / 昵称" clearable style="width: 260px" @keyup.enter="load" />
        <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 120px" @change="load">
          <el-option label="正常" :value="1" />
          <el-option label="已锁定" :value="0" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
      </div>

      <el-table :data="list" v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="email" label="邮箱" min-width="220" />
        <el-table-column prop="nickname" label="昵称" min-width="140" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '正常' : '已锁定' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="注册时间" width="200" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button :type="row.status === 1 ? 'danger' : 'primary'" link size="small" @click="toggle(row)">
              {{ row.status === 1 ? '锁定' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="load"
          @size-change="load"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageUsers, setUserStatus, type AdminUser } from '@/api/admin'

const list = ref<AdminUser[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const filterStatus = ref<number | undefined>(undefined)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await pageUsers({
      page: pageNum.value,
      pageSize: pageSize.value,
      keyword: keyword.value || undefined,
      status: filterStatus.value ?? undefined
    })
    list.value = res.records || []
    total.value = Number(res.total || 0)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '加载用户失败')
  } finally {
    loading.value = false
  }
}

async function toggle(row: AdminUser) {
  const next = row.status === 1 ? 0 : 1
  const action = next === 0 ? '锁定' : '启用'
  try {
    await ElMessageBox.confirm(`确定${action}用户 ${row.email} 吗？`, '提示', { type: 'warning' })
    await setUserStatus(row.id, next)
    ElMessage.success(`已${action}`)
    load()
  } catch (e: any) {
    if (e !== 'cancel' && e?.message) {
      ElMessage.error(e?.response?.data?.msg || e.message || `${action}失败`)
    }
  }
}

onMounted(load)
</script>

<style scoped>
.user-mgmt {
  padding: 0;
}
</style>