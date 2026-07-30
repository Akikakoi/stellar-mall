<template>
  <div class="points-product-mgmt">
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">积分商城管理</span>
        <el-button type="primary" @click="openAdd">新增商品</el-button>
      </div>
      <div class="filter-bar">
        <el-input v-model="keyword" placeholder="搜索商品名称" style="width: 240px" clearable @keyup.enter="load" />
        <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 120px;" @change="load">
          <el-option label="上架" :value="1" />
          <el-option label="下架" :value="0" />
        </el-select>
      </div>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">{{ row.productTypeText }}</template>
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
            <el-button type="primary" link size="small" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button type="danger" link size="small">删除</el-button>
              </template>
            </el-popconfirm>
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑商品' : '新增商品'" width="520px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="商品名称" prop="name">
          <el-input v-model="form.name" placeholder="如：5元满减券" />
        </el-form-item>
        <el-form-item label="商品类型" prop="productType">
          <el-radio-group v-model="form.productType">
            <el-radio value="COUPON">优惠券</el-radio>
            <el-radio value="PHYSICAL">实物</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="所需积分" prop="pointsPrice">
          <el-input-number v-model="form.pointsPrice" :min="1" :max="99999" style="width: 200px" />
        </el-form-item>
        <el-form-item label="库存" prop="stock">
          <el-input-number v-model="form.stock" :min="0" :max="99999" style="width: 200px" />
        </el-form-item>
        <el-form-item label="关联优惠券" v-if="form.productType === 'COUPON'">
          <el-input-number v-model="form.couponId" :min="1" placeholder="优惠券ID" style="width: 200px" />
          <span class="form-hint">优惠券模板ID</span>
        </el-form-item>
        <el-form-item label="商品图片">
          <el-input v-model="form.imageUrl" placeholder="图片URL" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999" style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">上架</el-radio>
            <el-radio :value="0">下架</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="商品描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { pagePointsProducts, savePointsProduct, deletePointsProduct } from '@/api/admin'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const list = ref([])
const keyword = ref('')
const filterStatus = ref(null)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const formRef = ref(null)

const defaultForm = () => ({
  id: null, name: '', productType: 'COUPON', pointsPrice: 100,
  stock: 0, imageUrl: '', description: '', couponId: null,
  status: 1, sortOrder: 0
})
const form = ref(defaultForm())

const rules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  productType: [{ required: true, message: '请选择商品类型', trigger: 'change' }],
  pointsPrice: [{ required: true, message: '请输入所需积分', trigger: 'blur' }],
}

async function load() {
  loading.value = true
  try {
    const res = await pagePointsProducts({
      name: keyword.value || undefined,
      status: filterStatus.value,
      page: pageNum.value,
      pageSize: pageSize.value
    })
    const d = res || {}
    list.value = d.records || d.list || []
    total.value = d.total || 0
  } catch (e) {
    ElMessage.error('加载失败')
  } finally { loading.value = false }
}

function openAdd() {
  isEdit.value = false
  form.value = defaultForm()
  dialogVisible.value = true
}

function openEdit(row) {
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
    await savePointsProduct(form.value)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '保存失败')
  } finally { saving.value = false }
}

async function handleDelete(id) {
  try {
    await deletePointsProduct(id)
    ElMessage.success('删除成功')
    await load()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '删除失败')
  }
}

onMounted(() => load())
</script>

<style scoped>
.points-product-mgmt { padding: 0; }
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
