<template>
  <div class="points-mgmt">
    <el-tabs v-model="activeTab" type="border-card" @tab-change="onTabChange">
      <!-- ==================== 积分规则 ==================== -->
      <el-tab-pane label="积分规则" name="rules">
        <div class="panel">
          <div class="panel-head">
            <span class="panel-title">积分规则</span>
          </div>
          <el-table :data="rules" v-loading="rulesLoading" stripe>
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
                <el-button type="primary" link size="small" @click="openRuleEdit(row)">编辑</el-button>
                <el-popconfirm title="确定删除该规则？" @confirm="handleRuleDelete(row.id)">
                  <template #reference>
                    <el-button type="danger" link size="small">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 积分调整 -->
        <div class="panel" style="margin-top: 16px;">
          <div class="panel-head">
            <span class="panel-title">手动积分调整</span>
          </div>
          <el-form :model="adjustForm" label-width="80px" inline>
            <el-form-item label="用户ID">
              <el-input v-model="adjustForm.userId" placeholder="输入用户ID" style="width: 160px" />
            </el-form-item>
            <el-form-item label="目标积分">
              <el-input-number v-model="adjustForm.points" :min="0" :max="99999" style="width: 160px" />
            </el-form-item>
            <el-form-item label="说明">
              <el-input v-model="adjustForm.description" placeholder="调整原因" style="width: 200px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="adjusting" @click="doAdjust">确认调整</el-button>
            </el-form-item>
          </el-form>
        </div>

        <!-- 规则编辑弹窗 -->
        <el-dialog v-model="ruleDialogVisible" :title="isRuleEdit ? '编辑规则' : '新增规则'" width="520px" @closed="resetRuleForm">
          <el-form ref="ruleFormRef" :model="ruleForm" :rules="ruleValidate" label-width="100px">
            <el-form-item label="规则类型" prop="ruleType">
              <el-select v-model="ruleForm.ruleType" placeholder="选择规则类型" :disabled="isRuleEdit">
                <el-option label="下单赚积分" value="ORDER" />
                <el-option label="每日签到" value="CHECKIN" />
                <el-option label="评价赚积分" value="REVIEW" />
              </el-select>
            </el-form-item>
            <el-form-item label="规则名称" prop="ruleName">
              <el-input v-model="ruleForm.ruleName" placeholder="如：下单赚积分" />
            </el-form-item>
            <el-form-item label="获得积分" prop="earnPoints">
              <el-input-number v-model="ruleForm.earnPoints" :min="1" :max="9999" />
              <span class="form-hint">分</span>
            </el-form-item>
            <el-form-item label="条件值" prop="conditionValue">
              <el-input-number v-model="ruleForm.conditionValue" :min="0" :precision="2" />
              <span class="form-hint">元（仅下单类型需要）</span>
            </el-form-item>
            <el-form-item label="每日上限">
              <el-input-number v-model="ruleForm.maxPerDay" :min="1" :max="999" />
              <span class="form-hint">次/天</span>
            </el-form-item>
            <el-form-item label="每单上限">
              <el-input-number v-model="ruleForm.maxPerOrder" :min="1" :max="9999" />
              <span class="form-hint">分/单</span>
            </el-form-item>
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="ruleForm.status">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">禁用</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="描述">
              <el-input v-model="ruleForm.description" type="textarea" :rows="2" placeholder="规则说明" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="ruleDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="ruleSaving" @click="doRuleSave">保存</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>

      <!-- ==================== 积分商城 ==================== -->
      <el-tab-pane label="积分商城" name="products">
        <div class="panel">
          <div class="panel-head">
            <span class="panel-title">积分商品</span>
            <el-button type="primary" @click="openProductAdd">+ 新增商品</el-button>
          </div>
          <div class="filter-bar">
            <el-input v-model="prodKeyword" placeholder="搜索商品名称" style="width: 240px" clearable @keyup.enter="loadProducts" />
            <el-select v-model="prodFilterStatus" placeholder="状态" clearable style="width: 120px;" @change="loadProducts">
              <el-option label="上架" :value="1" />
              <el-option label="下架" :value="0" />
            </el-select>
          </div>
          <el-table :data="prodList" v-loading="prodLoading" stripe>
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
            <el-table-column label="类型" width="80">
              <template #default="{ row }">{{ row.productType === 'COUPON' ? '优惠券' : '实物' }}</template>
            </el-table-column>
            <el-table-column label="所需积分" width="90">
              <template #default="{ row }">{{ row.pointsPrice }}</template>
            </el-table-column>
            <el-table-column prop="stock" label="库存" width="70" />
            <el-table-column prop="sortOrder" label="排序" width="70" />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                  {{ row.status === 1 ? '上架' : '下架' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="160" />
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="openProductEdit(row)">编辑</el-button>
                <el-popconfirm title="确定删除？" @confirm="handleProductDelete(row.id)">
                  <template #reference>
                    <el-button type="danger" link size="small">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="prodPageNum" v-model:page-size="prodPageSize" :total="prodTotal"
              layout="total, prev, pager, next" @current-change="loadProducts" @size-change="loadProducts"
            />
          </div>
        </div>

        <!-- 商品编辑弹窗 -->
        <el-dialog v-model="prodDialogVisible" :title="isProdEdit ? '编辑商品' : '新增商品'" width="520px" @closed="resetProductForm">
          <el-form ref="prodFormRef" :model="prodForm" :rules="prodValidate" label-width="100px">
            <el-form-item label="商品名称" prop="name">
              <el-input v-model="prodForm.name" placeholder="如：5元满减券" />
            </el-form-item>
            <el-form-item label="商品类型" prop="productType">
              <el-radio-group v-model="prodForm.productType">
                <el-radio value="COUPON">优惠券</el-radio>
                <el-radio value="PHYSICAL">实物</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="所需积分" prop="pointsPrice">
              <el-input-number v-model="prodForm.pointsPrice" :min="1" :max="99999" style="width: 200px" />
            </el-form-item>
            <el-form-item label="库存" prop="stock">
              <el-input-number v-model="prodForm.stock" :min="0" :max="99999" style="width: 200px" />
            </el-form-item>
            <el-form-item label="关联优惠券" v-if="prodForm.productType === 'COUPON'">
              <el-input-number v-model="prodForm.couponId" :min="1" placeholder="优惠券ID" style="width: 200px" />
              <span class="form-hint">优惠券模板ID</span>
            </el-form-item>
            <el-form-item label="商品图片">
              <el-input v-model="prodForm.imageUrl" placeholder="图片URL" />
            </el-form-item>
            <el-form-item label="排序">
              <el-input-number v-model="prodForm.sortOrder" :min="0" :max="999" style="width: 200px" />
            </el-form-item>
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="prodForm.status">
                <el-radio :value="1">上架</el-radio>
                <el-radio :value="0">下架</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="描述">
              <el-input v-model="prodForm.description" type="textarea" :rows="2" placeholder="商品描述" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="prodDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="prodSaving" @click="doProductSave">保存</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listPointsRules, savePointsRule, deletePointsRule, adjustPoints, pagePointsProducts, savePointsProduct, deletePointsProduct } from '@/api/admin'
import { ElMessage } from 'element-plus'

const activeTab = ref('rules')

// ==================== 积分规则 ====================
const rulesLoading = ref(false)
const rules = ref<any[]>([])
const ruleDialogVisible = ref(false)
const isRuleEdit = ref(false)
const ruleSaving = ref(false)
const ruleFormRef = ref<any>(null)

const defaultRuleForm = () => ({
  id: null, ruleType: 'CHECKIN', ruleName: '', earnPoints: 5,
  conditionValue: null, maxPerDay: null, maxPerOrder: null,
  status: 1, description: ''
})
const ruleForm = ref<any>(defaultRuleForm())

const ruleValidate: Record<string, any> = {
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

async function loadRules() {
  rulesLoading.value = true
  try {
    const res = await listPointsRules()
    rules.value = res || []
  } catch (e: any) { ElMessage.error('加载规则失败') } finally { rulesLoading.value = false }
}

function openRuleEdit(row: any) {
  isRuleEdit.value = true
  ruleForm.value = { ...row }
  ruleDialogVisible.value = true
}

function resetRuleForm() {
  ruleForm.value = defaultRuleForm()
  isRuleEdit.value = false
}

async function doRuleSave() {
  const valid = await ruleFormRef.value?.validate().catch(() => false)
  if (!valid) return
  ruleSaving.value = true
  try {
    await savePointsRule(ruleForm.value)
    ElMessage.success('保存成功')
    ruleDialogVisible.value = false
    await loadRules()
  } catch (e: any) { ElMessage.error(e?.response?.data?.msg || '保存失败') } finally { ruleSaving.value = false }
}

async function handleRuleDelete(id: any) {
  try {
    await deletePointsRule(id)
    ElMessage.success('删除成功')
    await loadRules()
  } catch (e: any) { ElMessage.error(e?.response?.data?.msg || '删除失败') }
}

async function doAdjust() {
  if (!adjustForm.value.userId) { ElMessage.warning('请输入用户ID'); return }
  adjusting.value = true
  try {
    await adjustPoints({ userId: Number(adjustForm.value.userId), points: adjustForm.value.points, description: adjustForm.value.description || null })
    ElMessage.success('调整成功')
    adjustForm.value = { userId: '', points: 0, description: '' }
  } catch (e: any) { ElMessage.error(e?.response?.data?.msg || '调整失败') } finally { adjusting.value = false }
}

// ==================== 积分商城 ====================
const prodLoading = ref(false)
const prodList = ref<any[]>([])
const prodKeyword = ref('')
const prodFilterStatus = ref<any>(null)
const prodPageNum = ref(1)
const prodPageSize = ref(10)
const prodTotal = ref(0)
const prodDialogVisible = ref(false)
const isProdEdit = ref(false)
const prodSaving = ref(false)
const prodFormRef = ref<any>(null)

const defaultProdForm = () => ({
  id: null, name: '', productType: 'COUPON', pointsPrice: 100,
  stock: 0, imageUrl: '', description: '', couponId: null,
  status: 1, sortOrder: 0
})
const prodForm = ref<any>(defaultProdForm())

const prodValidate: Record<string, any> = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  productType: [{ required: true, message: '请选择商品类型', trigger: 'change' }],
  pointsPrice: [{ required: true, message: '请输入所需积分', trigger: 'blur' }],
}

async function loadProducts() {
  prodLoading.value = true
  try {
    const res = await pagePointsProducts({ name: prodKeyword.value || undefined, status: prodFilterStatus.value, page: prodPageNum.value, pageSize: prodPageSize.value })
    const d: any = res || {}
    prodList.value = d.records || d.list || []
    prodTotal.value = d.total || 0
  } catch (e: any) { ElMessage.error('加载失败') } finally { prodLoading.value = false }
}

function openProductAdd() {
  isProdEdit.value = false
  prodForm.value = defaultProdForm()
  prodDialogVisible.value = true
}

function openProductEdit(row: any) {
  isProdEdit.value = true
  prodForm.value = { ...row }
  prodDialogVisible.value = true
}

function resetProductForm() {
  prodForm.value = defaultProdForm()
  isProdEdit.value = false
}

async function doProductSave() {
  const valid = await prodFormRef.value?.validate().catch(() => false)
  if (!valid) return
  prodSaving.value = true
  try {
    await savePointsProduct(prodForm.value)
    ElMessage.success('保存成功')
    prodDialogVisible.value = false
    await loadProducts()
  } catch (e: any) { ElMessage.error(e?.response?.data?.msg || '保存失败') } finally { prodSaving.value = false }
}

async function handleProductDelete(id: any) {
  try {
    await deletePointsProduct(id)
    ElMessage.success('删除成功')
    await loadProducts()
  } catch (e: any) { ElMessage.error(e?.response?.data?.msg || '删除失败') }
}

function onTabChange() {
  if (activeTab.value === 'products' && !prodList.value.length) {
    loadProducts()
  }
}

onMounted(() => { loadRules(); loadProducts() })
</script>

<style scoped>
.points-mgmt { }
.points-mgmt :deep(.el-tabs.el-tabs--border-card) {
  border-radius: var(--radius-lg);
  overflow: hidden;
}
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
.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
.form-hint { margin-left: 8px; font-size: 12px; color: var(--text-muted); }
</style>