<template>
  <div class="mgmt-page">
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">分类管理</span>
        <el-button type="primary" @click="openDialog('create')">+ 新增分类</el-button>
      </div>

      <div class="filter-bar">
        <el-select v-model="query.type" placeholder="类型" clearable style="width: 160px;" :disabled="loading">
          <el-option label="商品分类" :value="1" />
          <el-option label="套餐分类" :value="2" />
        </el-select>

        <!-- ===== 排序控件（字段 + 方向），加载中禁用 ===== -->
        <span style="color: var(--text-secondary); font-size:13px; white-space:nowrap;">排序：</span>
        <el-select v-model="query.sortBy" placeholder="排序字段" clearable style="width:130px;" :disabled="loading" @change="onSortByChange">
          <el-option label="创建时间" value="createTime" />
          <el-option label="分类名称" value="name" />
        </el-select>
        <el-select v-model="query.sortOrder" placeholder="排序方向" clearable style="width:110px;" :disabled="loading || !query.sortBy" @change="onSortChange">
          <el-option label="升序  ↑" value="asc" />
          <el-option label="降序  ↓" value="desc" />
        </el-select>

        <el-button type="primary" :disabled="loading" @click="onQueryClick">查询</el-button>
      </div>

      <el-table :data="records" v-loading="loading" stripe empty-text="暂无数据" style="width: 100%;">
        
        <el-table-column min-width="200">
          <template #header>
            <span class="col-header-sort">
              分类名称
              <span v-if="query.sortBy === 'name'" class="sort-arrow" :class="query.sortOrder">
                {{ query.sortOrder === 'asc' ? '▲' : '▼' }}
              </span>
            </span>
          </template>
          <template #default="{ row }">
            <div style="display:flex; flex-direction:column; line-height:1.4;">
            <span style="font-weight:600; color: var(--text-primary);">{{ row.name }}</span>
            <span style="font-size:12px; color: var(--text-muted); margin-top:2px;">
              {{ row.type == 1 ? '商品分类' : (row.type == 2 ? '套餐分类' : '—') }}
            </span>
          </div>
          </template>
        </el-table-column>
        <!-- ========== 旧：类型（商品/套餐）；新：具体商品数量（口径 = 该分类作用域下关联的 SPU 数，L1 = 自身直接挂 + L2 子分类全部） ========== -->
        <el-table-column label="商品数量" width="130" align="center">
          <template #default="{ row }">
            <!-- 统一按数量着色：空值/N/A 灰 → 0 灰 inf → 少(1-9)蓝 → 中(10-49)绿 → 多(>=50)橙，方便运营一眼识别冷门/热门分类 -->
            <el-tag v-if="row.spuCount == null || row.spuCount === ''" type="info" effect="plain">—</el-tag>
            <el-tag v-else-if="Number(row.spuCount) <= 0" type="info">0 件</el-tag>
            <el-tag v-else-if="Number(row.spuCount) < 10" type="primary">{{ row.spuCount }} 件</el-tag>
            <el-tag v-else-if="Number(row.spuCount) < 50" type="success" effect="dark">{{ row.spuCount }} 件</el-tag>
            <el-tag v-else type="warning" effect="dark">{{ row.spuCount }} 件</el-tag>
          </template>
        </el-table-column>
        <!-- 作为轻量保留：把原来的「类型」信息放到分类名称下面做二级展示，让运营仍能区分商品/套餐，同时主要列位让给数量 -->
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 1" @change="(v) => toggleStatus(row, v)" />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openDialog('edit', row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新增分类' : '编辑分类'" width="480px" destroy-on-close>
      <el-form :model="form" :rules="formRules" ref="formRef" label-width="90px">
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio :value="1">商品分类</el-radio>
            <el-radio :value="2">套餐分类</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="9999" />
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
import { pageCategory, saveCategory, updateCategory, deleteCategory, checkCategoryDeletable, setCategoryStatus } from '@/api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'
import { storage } from '@/utils/storage'

// ======== 排序记忆：storage key + 排序字段/方向白名单 ========
const SORT_STORAGE_KEY = 'stellar:admin:category-mgmt:sort:v1'
const SORT_BY_ALLOWED = new Set(['createTime', 'name'])
const SORT_ORDER_ALLOWED = new Set(['asc', 'desc'])

const loading = ref(false)
const submitting = ref(false)
const records = ref([])
const total = ref(0)

// 新增 sortBy / sortOrder，与后端 CategoryPageQueryDTO 字段对齐（null 走默认排序 sort DESC, create_time DESC）
const query = reactive({ page: 1, pageSize: 10, type: null, sortBy: null, sortOrder: null })

/** 从 storage 还原上次选择的排序维度 + 方向，未知值回默认，保证刷新后保持。 */
function restoreSort() {
  const obj = storage.local.getObject(SORT_STORAGE_KEY)
  if (obj && SORT_BY_ALLOWED.has(obj.sortBy)) {
    query.sortBy = obj.sortBy
    if (SORT_ORDER_ALLOWED.has(obj.sortOrder)) query.sortOrder = obj.sortOrder
    else query.sortOrder = null
  }
}

/** 把当前排序维度 + 方向写入 storage；任一缺失则清掉 key。 */
function persistSort() {
  if (query.sortBy && SORT_BY_ALLOWED.has(query.sortBy) && query.sortOrder && SORT_ORDER_ALLOWED.has(query.sortOrder)) {
    storage.local.setObject(SORT_STORAGE_KEY, { sortBy: query.sortBy, sortOrder: query.sortOrder })
  } else {
    storage.local.remove(SORT_STORAGE_KEY)
  }
}

/** 切换排序字段：若已有方向则立刻重新加载；若没选方向，给个默认方向 asc。 */
function onSortByChange() {
  if (query.sortBy && !query.sortOrder) query.sortOrder = 'asc'
  query.page = 1
  persistSort()
  loadPage()
}

/** 切换排序方向：立刻重新加载。 */
function onSortChange() {
  query.page = 1
  persistSort()
  loadPage()
}

/** 「查询」按钮：重置到第 1 页，保留排序。 */
function onQueryClick() {
  query.page = 1
  loadPage()
}

const dialogVisible = ref(false)
const dialogMode = ref('create')
const formRef = ref(null)
const form = reactive({ id: null, name: '', type: 1, sort: 0, status: 1 })
const formRules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

/** 加载分类分页列表，根据当前查询条件筛选 */
async function loadPage() {
  loading.value = true
  try {
    const res = await pageCategory({ ...query })
    const d = res || {}
    records.value = d.records || d.list || []
    total.value = d.total || 0
  } finally {
    loading.value = false
  }
}

/** 打开新增/编辑分类对话框，create 模式初始化空表单，edit 模式回填数据 */
function openDialog(mode, row) {
  dialogMode.value = mode
  if (mode === 'create') {
    Object.assign(form, { id: null, name: '', type: 1, sort: 0, status: 1 })
  } else {
    Object.assign(form, {
      id: row.id,
      name: row.name,
      type: row.type,
      sort: row.sort ?? 0,
      status: row.status ?? 1
    })
  }
  dialogVisible.value = true
}

/** 提交分类表单：校验后保存或更新分类 */
async function submitForm() {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch (e) { return }
  submitting.value = true
  try {
    if (dialogMode.value === 'create') await saveCategory({ ...form })
    else await updateCategory({ ...form })
    ElMessage.success(dialogMode.value === 'create' ? '新增成功' : '更新成功')
    dialogVisible.value = false
    loadPage()
  } finally { submitting.value = false }
}

/** 切换分类启用/禁用状态 */
async function toggleStatus(row, val) {
  try {
    await setCategoryStatus(row.id, val ? 1 : 0)
    ElMessage.success(val ? '已启用' : '已禁用')
  } catch (e) { loadPage() }
}

/** 删除分类：先预校验是否可删，通过后二次确认再执行删除 */
async function handleDelete(row) {
  // 阶段 1：预校验（后端 checkDeletable 会查「作用域 = 分类 + 子分类」下的商品数量与子分类数量）
  let deletable = true
  let reason = ''
  try {
    const res = await checkCategoryDeletable(row.id)
    const d = res || {}
    deletable = d.deletable !== false
    if (!deletable) {
      const parts = []
      if (typeof d.linkedSpuCount === 'number') parts.push(`关联商品 ${d.linkedSpuCount} 个`)
      if (typeof d.childCount === 'number') parts.push(`子分类 ${d.childCount} 个`)
      const detail = parts.length ? `（${parts.join('，')}）` : ''
      reason = (d.reason || '当前分类禁止删除') + detail
    }
  } catch (e) {
    // 预校验接口异常：保守起见仍允许走确认 → 最终后端 Service 仍会做最终防线拦截
  }

  // 阶段 2：如果预校验不可删 → 直接弹禁止原因（含商品数量），不再进入确认框
  if (!deletable) {
    try {
      await ElMessageBox.alert(reason, '禁止删除分类', { type: 'error', confirmButtonText: '我知道了' })
    } catch (_) {}
    return
  }

  // 阶段 3：预校验通过 → 进入传统二次确认
  try {
    await ElMessageBox.confirm(`确定删除分类「${row.name}」？删除后无法恢复。`, '提示', { type: 'warning' })
  } catch (e) {
    // 用户点取消
    return
  }
  try {
    await deleteCategory(row.id)
    ElMessage.success('已删除')
    loadPage()
  } catch (e) {
    // 后端 Service 最终防线的拒绝消息（以防绕过预校验直接调 API 的场景）
    const msg = (e && e.response && e.response.data && e.response.data.msg) || e?.message || '删除失败'
    ElMessage.error(msg)
  }
}

onMounted(() => {
  restoreSort()    // 先还原上次排序（还原到 query），再拉数据，保证刷新后保持
  loadPage()
})
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
/* ======== 排序：列头 & 箭头样式 ======== */
.col-header-sort { display: inline-flex; align-items: center; gap: 4px; font-weight: 600; }
.sort-arrow {
  display: inline-block;
  color: var(--brand-primary);
  font-size: 10px;
  line-height: 1;
  padding: 2px 4px;
  border-radius: var(--radius-sm);
  background: var(--brand-primary-soft);
  transform: translateY(-1px);
}
.sort-arrow.asc  { color: var(--status-success); }
.sort-arrow.desc { color: var(--status-danger); }

:deep(.el-dialog) {
  border-radius: var(--radius-lg);
  overflow: hidden;
}
</style>
