<template>
  <div class="coupon-mgmt">
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">优惠券管理</span>
        <el-button type="primary" @click="openAdd">新增优惠券</el-button>
      </div>
      <div class="filter-bar">
        <el-input v-model="keyword" placeholder="搜索优惠券名称" style="width: 240px" clearable @keyup.enter="load" />
        <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 120px;" @change="load">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
      </div>

      <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ row.type === 1 ? '满减券' : '折扣券' }}</template>
      </el-table-column>
      <el-table-column label="优惠内容" width="160">
        <template #default="{ row }">
          <template v-if="row.type === 1">满{{ row.conditionAmount }}减{{ row.discountAmount }}</template>
          <template v-else>满{{ row.conditionAmount }}打{{ (row.discountAmount * 10).toFixed(1) }}折</template>
        </template>
      </el-table-column>
      <el-table-column label="发放/已领/已用" width="150">
        <template #default="{ row }">{{ row.totalCount }} / {{ row.receivedCount }} / {{ row.usedCount }}</template>
      </el-table-column>
      <el-table-column label="有效期" width="200">
        <template #default="{ row }">{{ row.startTime?.substring(0, 10) }} ~ {{ row.endTime?.substring(0, 10) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="openEdit(row)">编辑</el-button>
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑优惠券' : '新增优惠券'" width="500px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="优惠券名称" prop="name">
          <el-input v-model="form.name" placeholder="如：新用户满减券" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio :value="1">满减券</el-radio>
            <el-radio :value="2">折扣券</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="使用门槛" prop="conditionAmount">
          <el-input-number v-model="form.conditionAmount" :min="0" :precision="2" style="width: 200px" />
          <span style="margin-left: 8px; color: var(--text-muted);">元</span>
        </el-form-item>
        <el-form-item :label="form.type === 1 ? '减免金额' : '折扣比例'" prop="discountAmount">
          <el-input-number v-model="form.discountAmount" :min="0.01" :max="form.type === 1 ? 9999 : 0.99" :precision="2" :step="form.type === 1 ? 1 : 0.05" style="width: 200px" />
          <span style="margin-left: 8px; color: var(--text-muted);">{{ form.type === 1 ? '元' : '（0.85即85折）' }}</span>
        </el-form-item>
        <el-form-item label="发放总量" prop="totalCount">
          <el-input-number v-model="form.totalCount" :min="1" style="width: 200px" />
        </el-form-item>
        <el-form-item label="每人限领" prop="perUserLimit">
          <el-input-number v-model="form.perUserLimit" :min="1" :max="99" style="width: 200px" />
        </el-form-item>
        <el-form-item label="有效期" required>
          <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD HH:mm:ss" />
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

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminRequest } from '@/api/request'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<any>(null)
const keyword = ref('')
const filterStatus = ref<any>(null)
const list = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const formRef = ref<any>(null)
const dateRange = ref<any[]>([])

const form = reactive<any>({
  name: '', type: 1, conditionAmount: 0, discountAmount: 10,
  totalCount: 100, perUserLimit: 1, status: 1
})

const rules: Record<string, any> = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  type: [{ required: true }],
  conditionAmount: [{ required: true }],
  discountAmount: [{ required: true }],
  totalCount: [{ required: true }],
  perUserLimit: [{ required: true }]
}

/** 重置表单为默认初始值 */
function resetForm() {
  form.name = ''; form.type = 1; form.conditionAmount = 0; form.discountAmount = 10
  form.totalCount = 100; form.perUserLimit = 1; form.status = 1
  editId.value = null; isEdit.value = false; dateRange.value = []
}

/** 加载优惠券分页列表，根据当前搜索条件筛选 */
async function load() {
  loading.value = true
  try {
    const params: Record<string, any> = { page: pageNum.value, pageSize: pageSize.value }
    if (keyword.value) params.name = keyword.value
    if (filterStatus.value !== null && filterStatus.value !== undefined) params.status = filterStatus.value
    const res: any = await adminRequest({ url: '/admin/coupon/page', method: 'get', params })
    const d: any = res?.data || res || {}
    list.value = d.records || d.list || []
    total.value = d.total || 0
  } catch (e: any) {} finally { loading.value = false }
}

/** 打开新增优惠券对话框，重置表单 */
function openAdd() { resetForm(); dialogVisible.value = true }
/** 打开编辑优惠券对话框，回填已有数据 */
function openEdit(row: any) {
  isEdit.value = true; editId.value = row.id
  form.name = row.name; form.type = row.type; form.conditionAmount = row.conditionAmount
  form.discountAmount = row.discountAmount; form.totalCount = row.totalCount
  form.perUserLimit = row.perUserLimit; form.status = row.status
  dateRange.value = row.startTime && row.endTime ? [row.startTime, row.endTime] : []
  dialogVisible.value = true
}

/** 保存优惠券：校验后创建或更新 */
async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const data: Record<string, any> = { ...form }
    if (dateRange.value && dateRange.value.length === 2) {
      data.startTime = dateRange.value[0]
      data.endTime = dateRange.value[1]
    }
    if (isEdit.value) {
      data.id = editId.value
      await adminRequest({ url: '/admin/coupon', method: 'put', data })
      ElMessage.success('已更新')
    } else {
      await adminRequest({ url: '/admin/coupon', method: 'post', data })
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (e: any) { ElMessage.error('操作失败') } finally { submitting.value = false }
}

/** 删除优惠券，二次确认后执行 */
async function handleDelete(id: any) {
  try {
    await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' })
    await adminRequest({ url: `/admin/coupon/${id}`, method: 'delete' })
    ElMessage.success('已删除')
    await load()
  } catch (e: any) {}
}

onMounted(load)
</script>

<style scoped>
.coupon-mgmt { display: flex; flex-direction: column; gap: 16px; }
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