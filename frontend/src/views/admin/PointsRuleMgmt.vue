<template>
  <div class="points-rule-mgmt">
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">积分规则管理</span>
      </div>
      <el-table :data="rules" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="规则类型" width="100">
          <template #default="{ row }">
            <el-tag :type="ruleTypeTag(row.ruleType)" size="small">{{ ruleTypeText(row.ruleType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ruleName" label="名称" min-width="120" />
        <el-table-column prop="earnPoints" label="积分" width="80" />
        <el-table-column label="条件" width="140">
          <template #default="{ row }">
            <span v-if="row.conditionValue">每{{ row.conditionValue }}元</span>
            <span v-else>--</span>
          </template>
        </el-table-column>
        <el-table-column label="每日上限" width="90">
          <template #default="{ row }">{{ row.maxPerDay || '不限' }}</template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除该规则？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button type="danger" link size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑规则' : '新增规则'" width="520px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules_validate" label-width="100px">
        <el-form-item label="规则类型" prop="ruleType">
          <el-select v-model="form.ruleType" placeholder="选择规则类型" :disabled="isEdit">
            <el-option label="下单赚积分" value="ORDER" />
            <el-option label="每日签到" value="CHECKIN" />
            <el-option label="评价赚积分" value="REVIEW" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="form.ruleName" placeholder="如：下单赚积分" />
        </el-form-item>
        <el-form-item label="获得积分" prop="earnPoints">
          <el-input-number v-model="form.earnPoints" :min="1" :max="9999" />
          <span class="form-hint">分</span>
        </el-form-item>
        <el-form-item label="条件值" prop="conditionValue">
          <el-input-number v-model="form.conditionValue" :min="0" :precision="2" />
          <span class="form-hint">元（仅下单类型需要，如每消费1元得积分）</span>
        </el-form-item>
        <el-form-item label="每日上限">
          <el-input-number v-model="form.maxPerDay" :min="1" :max="999" />
          <span class="form-hint">次/天（空则不限制）</span>
        </el-form-item>
        <el-form-item label="每单上限">
          <el-input-number v-model="form.maxPerOrder" :min="1" :max="9999" />
          <span class="form-hint">分/单（空则不限制）</span>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="规则说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 积分调整弹窗 -->
    <div class="panel" style="margin-top: 16px;">
      <div class="panel-head">
        <span class="panel-title">积分调整</span>
      </div>
      <el-form :model="adjustForm" label-width="80px" inline>
        <el-form-item label="用户ID">
          <el-input v-model="adjustForm.userId" placeholder="输入用户ID" style="width: 160px" />
        </el-form-item>
        <el-form-item label="积分数">
          <el-input-number v-model="adjustForm.points" :min="-9999" :max="99999" style="width: 160px" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="adjustForm.description" placeholder="调整原因" style="width: 200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="adjusting" @click="doAdjust">确认调整</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listPointsRules, savePointsRule, deletePointsRule, adjustPoints } from '@/api/admin'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const rules = ref<any[]>([])

const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const formRef = ref<any>(null)

const defaultForm = () => ({
  id: null, ruleType: 'CHECKIN', ruleName: '', earnPoints: 5,
  conditionValue: null, maxPerDay: null, maxPerOrder: null,
  status: 1, description: ''
})
const form = ref(defaultForm())

const rules_validate: Record<string, any> = {
  ruleType: [{ required: true, message: '请选择规则类型', trigger: 'change' }],
  ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  earnPoints: [{ required: true, message: '请输入积分数', trigger: 'blur' }],
}

const adjustForm = ref<any>({ userId: '', points: 0, description: '' })
const adjusting = ref(false)

function ruleTypeTag(type: any) {
  const map: Record<string, any> = { ORDER: 'primary', CHECKIN: 'success', REVIEW: 'warning' }
  return map[type] || 'info'
}
function ruleTypeText(type: any) {
  const map: Record<string, any> = { ORDER: '下单', CHECKIN: '签到', REVIEW: '评价' }
  return map[type] || type
}

async function load() {
  loading.value = true
  try {
    const res = await listPointsRules()
    rules.value = res || []
  } catch (e: any) {
    ElMessage.error('加载规则失败')
  } finally { loading.value = false }
}

function openEdit(row: any) {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

function resetForm() {
  form.value = defaultForm()
  isEdit.value = false
}

async function doSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await savePointsRule(form.value)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '保存失败')
  } finally { saving.value = false }
}

async function handleDelete(id: any) {
  try {
    await deletePointsRule(id)
    ElMessage.success('删除成功')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '删除失败')
  }
}

async function doAdjust() {
  if (!adjustForm.value.userId) {
    ElMessage.warning('请输入用户ID')
    return
  }
  if (adjustForm.value.points === 0) {
    ElMessage.warning('积分数不能为0')
    return
  }
  adjusting.value = true
  try {
    await adjustPoints({
      userId: Number(adjustForm.value.userId),
      points: adjustForm.value.points,
      description: adjustForm.value.description || null
    })
    ElMessage.success('调整成功')
    adjustForm.value = { userId: '', points: 0, description: '' }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '调整失败')
  } finally { adjusting.value = false }
}

onMounted(() => load())
</script>

<style scoped>
.points-rule-mgmt { padding: 0; }
.panel {
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-lg);
  padding: 20px 24px;
  box-shadow: var(--shadow-sm);
}
.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--border-subtle);
}
.panel-title { font-size: 16px; font-weight: 600; }
.form-hint { margin-left: 8px; font-size: 12px; color: var(--text-muted); }
</style>
