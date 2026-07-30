<template>
  <div class="mgmt-page">
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">员工管理</span>
        <div class="panel-actions">
          <el-button @click="handleExportUsers" :loading="exporting">导出用户</el-button>
          <el-button type="primary" @click="openDialog('create')">+ 新增员工</el-button>
        </div>
      </div>

      <div class="filter-bar">
        <el-input v-model="query.keyword" placeholder="姓名/用户名搜索" style="width: 240px" clearable @keyup.enter="loadPage" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px;">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button type="primary" @click="loadPage">查询</el-button>
      </div>

      <el-table :data="records" v-loading="loading" stripe style="width: 100%;">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="name" label="姓名" width="140" />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column prop="idNumber" label="身份证号" width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 1" @change="(v) => toggleStatus(row, v)" />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openDialog('edit', row)">编辑</el-button>
            <el-button type="danger" link size="small" v-if="row.id !== 1" @click="handleDelete(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新增员工' : '编辑员工'" width="520px" destroy-on-close>
      <el-form :model="form" :rules="formRules" ref="formRef" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="dialogMode === 'edit'" />
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="dialogMode === 'create'">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="身份证号" prop="idNumber">
          <el-input v-model="form.idNumber" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.sex">
            <el-radio :value="1">男</el-radio>
            <el-radio :value="2">女</el-radio>
            <el-radio :value="0">保密</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { pageEmployee, saveEmployee, updateEmployee, setEmployeeStatus } from '@/api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const submitting = ref(false)
const records = ref([])
const total = ref(0)

const query = reactive({ page: 1, pageSize: 10, keyword: '', status: null })

const dialogVisible = ref(false)
const dialogMode = ref('create')
const formRef = ref(null)
const form = reactive({
  id: null, username: '', password: '', name: '', phone: '', idNumber: '', sex: 0, status: 1
})
const formRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 6, message: '密码至少6位', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }]
}

async function loadPage() {
  loading.value = true
  try {
    const res = await pageEmployee({ ...query })
    const d = res || {}
    records.value = d.records || d.list || []
    total.value = d.total || 0
  } finally { loading.value = false }
}

function openDialog(mode, row) {
  dialogMode.value = mode
  if (mode === 'create') {
    Object.assign(form, { id: null, username: '', password: '', name: '', phone: '', idNumber: '', sex: 0, status: 1 })
  } else {
    Object.assign(form, {
      id: row.id,
      username: row.username,
      password: '',
      name: row.name,
      phone: row.phone || '',
      idNumber: row.idNumber || '',
      sex: row.sex ?? 0,
      status: row.status ?? 1
    })
  }
  dialogVisible.value = true
}

async function submitForm() {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch (e) { return }
  submitting.value = true
  try {
    if (dialogMode.value === 'create') await saveEmployee({ ...form })
    else await updateEmployee({ ...form })
    ElMessage.success(dialogMode.value === 'create' ? '新增成功' : '更新成功')
    dialogVisible.value = false
    loadPage()
  } catch (e) {
    console.error('submitForm failed:', e)
  } finally { submitting.value = false }
}

async function toggleStatus(row, val) {
  try {
    await setEmployeeStatus(row.id, val ? 1 : 0)
    ElMessage.success(val ? '已启用' : '已禁用')
  } catch (e) { loadPage() }
}

async function handleDelete(row) {
  ElMessage.info('演示环境：删除接口暂未开放（员工数据建议禁用而非删除）')
}

// 用户导出
const exporting = ref(false)
async function handleExportUsers() {
  exporting.value = true
  try {
    const token = localStorage.getItem('stellar_admin_token') || ''
    const resp = await fetch('/admin/export/users', { headers: { token } })
    if (!resp.ok) throw new Error()
    const blob = await resp.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = '用户数据导出.xlsx'; a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}

onMounted(loadPage)
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
.panel-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.panel-actions { display: flex; gap: 8px; align-items: center; }
.panel-title { font-size: 18px; font-weight: 600; color: var(--text-primary); }
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

:deep(.el-dialog) {
  border-radius: var(--radius-lg);
  overflow: hidden;
}
</style>
