<template>
  <div class="banner-mgmt">
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">轮播图管理</span>
        <el-button type="primary" @click="openAdd">新增轮播图</el-button>
      </div>
      <div class="filter-bar">
        <el-input v-model="keyword" placeholder="搜索标题" style="width: 240px" clearable @keyup.enter="load" />
        <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 120px;" @change="load">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
      </div>

      <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column label="预览" width="180">
        <template #default="{ row }">
          <el-image :src="row.imageUrl" fit="cover" style="width: 140px; height: 60px; border-radius: var(--radius-sm);" />
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="160" />
      <el-table-column prop="linkUrl" label="跳转链接" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.linkUrl || '-' }}</template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="openEdit(row)">编辑</el-button>
          <el-button :type="row.status === 1 ? 'warning' : 'success'" link size="small" @click="handleToggleStatus(row)">
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
          <el-button type="danger" link size="small" @click="handleDelete(row.id)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑轮播图' : '新增轮播图'" width="520px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="如：618年中大促" />
        </el-form-item>
        <el-form-item label="图片" prop="imageUrl">
          <el-input v-model="form.imageUrl" placeholder="输入图片URL" />
          <el-image v-if="form.imageUrl" :src="form.imageUrl" fit="cover" style="width: 100%; height: 140px; margin-top: 8px; border-radius: var(--radius-md);" />
        </el-form-item>
        <el-form-item label="跳转链接">
          <el-input v-model="form.linkUrl" placeholder="点击跳转的链接，留空则不跳转" />
        </el-form-item>
        <el-form-item label="排序值" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :max="999" />
          <span style="margin-left: 8px; color: var(--text-muted);">越大越靠前</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminRequest } from '@/api/request'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(null)
const keyword = ref('')
const filterStatus = ref(null)
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const formRef = ref(null)

const form = reactive({
  title: '', imageUrl: '', linkUrl: '', sort: 0, status: 1
})

const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  imageUrl: [{ required: true, message: '请输入图片URL', trigger: 'blur' }]
}

function resetForm() {
  form.title = ''; form.imageUrl = ''; form.linkUrl = ''; form.sort = 0; form.status = 1
  editId.value = null; isEdit.value = false
}

async function load() {
  loading.value = true
  try {
    const params = { page: pageNum.value, pageSize: pageSize.value }
    if (keyword.value) params.title = keyword.value
    if (filterStatus.value !== null && filterStatus.value !== undefined) params.status = filterStatus.value
    const res = await adminRequest({ url: '/admin/banner/page', method: 'get', params })
    const d = res?.data || res || {}
    list.value = d.records || d.list || []
    total.value = d.total || 0
  } catch (e) {} finally { loading.value = false }
}

function openAdd() { resetForm(); dialogVisible.value = true }
function openEdit(row) {
  isEdit.value = true; editId.value = row.id
  form.title = row.title; form.imageUrl = row.imageUrl; form.linkUrl = row.linkUrl || ''
  form.sort = row.sort ?? 0; form.status = row.status
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const data = { ...form }
    if (isEdit.value) {
      data.id = editId.value
      await adminRequest({ url: '/admin/banner', method: 'put', data })
      ElMessage.success('已更新')
    } else {
      await adminRequest({ url: '/admin/banner', method: 'post', data })
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (e) { ElMessage.error('操作失败') } finally { submitting.value = false }
}

async function handleDelete(id) {
  try {
    await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' })
    await adminRequest({ url: `/admin/banner/${id}`, method: 'delete' })
    ElMessage.success('已删除')
    await load()
  } catch (e) {}
}

async function handleToggleStatus(row) {
  const nextStatus = row.status === 1 ? 0 : 1
  const actionText = nextStatus === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确定${actionText}该轮播图？`, '提示', { type: 'warning' })
    await adminRequest({ url: '/admin/banner', method: 'put', data: { id: row.id, status: nextStatus } })
    ElMessage.success(`已${actionText}`)
    await load()
  } catch (e) {}
}

onMounted(load)
</script>

<style scoped>
.banner-mgmt { display: flex; flex-direction: column; gap: 16px; }
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

:deep(.el-dialog) {
  border-radius: var(--radius-lg);
  overflow: hidden;
}
</style>