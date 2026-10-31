<template>
  <div class="service-mgmt">
    <div class="toolbar">
      <el-button type="primary" @click="openDialog()">新增服务</el-button>
      <span class="tip">保障服务按「单个商品」配置组合：在商品编辑中选择该商品可选购的服务；服务价格以此处为准，结算时后端按服务表权威重算。</span>
    </div>

    <el-table :data="services" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="code" label="标识（code）" min-width="150" show-overflow-tooltip />
      <el-table-column prop="title" label="名称" min-width="160" />
      <el-table-column label="单价" width="110">
        <template #default="{ row }">¥{{ Number(row.price || 0).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.description || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="openDialog(row)">编辑</el-button>
          <el-button
            size="small" link
            :type="row.status === 1 ? 'warning' : 'success'"
            @click="toggleStatus(row)"
          >{{ row.status === 1 ? '停用' : '启用' }}</el-button>
          <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑服务' : '新增服务'" width="520px" destroy-on-close>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="110px">
        <el-form-item label="标识（code）" prop="code">
          <el-input v-model="form.code" :disabled="!!form.id" placeholder="如：screen_insurance" />
          <div class="form-hint">创建后不可修改；历史订单快照靠它解析。建议小写下划线命名。</div>
        </el-form-item>
        <el-form-item label="名称" prop="title">
          <el-input v-model="form.title" placeholder="如：碎屏险 · 1年" maxlength="64" />
        </el-form-item>
        <el-form-item label="单价（元）" prop="price">
          <el-input-number v-model="form.price" :min="0" :precision="2" :step="1" />
          <span class="form-hint" style="margin-left: 8px;">按商品件数倍乘：数量 2 时收 2 份</span>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="200" placeholder="选填" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
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
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listServices, saveService, deleteService } from '@/api/admin'

const loading = ref(false)
const submitting = ref(false)
const services = ref<any[]>([])
const dialogVisible = ref(false)
const formRef = ref<any>(null)

const emptyForm = () => ({ id: null, code: '', title: '', price: 0, description: '', status: 1 })
const form = ref<any>(emptyForm())

const rules: Record<string, any> = {
  code: [{ required: true, message: '请输入服务标识', trigger: 'blur' },
         { pattern: /^[a-z][a-z0-9_]*$/, message: '小写字母开头，仅小写字母/数字/下划线', trigger: 'blur' }],
  title: [{ required: true, message: '请输入服务名称', trigger: 'blur' }],
  price: [{ required: true, message: '请输入单价', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    services.value = (await listServices()) || []
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '加载失败')
  } finally {
    loading.value = false
  }
}

function openDialog(row?: any) {
  form.value = row
    ? { id: row.id, code: row.code, title: row.title, price: Number(row.price ?? 0), description: row.description || '', status: row.status ?? 1 }
    : emptyForm()
  dialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch { return }
  submitting.value = true
  try {
    await saveService({ ...form.value, price: Number(form.value.price ?? 0) })
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '保存失败')
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row: any) {
  const target = row.status === 1 ? 0 : 1
  try {
    await saveService({ ...row, status: target })
    ElMessage.success(target === 1 ? '已启用' : '已停用')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '操作失败')
  }
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除服务「${row.title}」？`, '提示', {
      confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
    })
  } catch { return }
  try {
    await deleteService(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '删除失败')
  }
}

onMounted(load)
</script>

<style scoped>
.service-mgmt { padding: 0; }
.toolbar { margin-bottom: 14px; display: flex; align-items: center; gap: 12px; }
.tip { font-size: 12px; color: var(--text-muted, #999); }
.form-hint { font-size: 12px; color: var(--text-muted, #999); }
</style>
