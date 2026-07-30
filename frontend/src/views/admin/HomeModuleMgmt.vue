<template>
  <div class="home-module-mgmt">
    <!-- 顶部操作栏 -->
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">首页装修</span>
        <div class="panel-actions">
          <el-button type="primary" @click="openAdd">新增模块</el-button>
        </div>
      </div>
      <p class="panel-desc">拖拽模块调整排序，首页按此顺序从上到下展示。配置完成后即时生效。</p>
    </div>

    <!-- 模块列表 -->
    <div class="module-list" v-loading="loading">
      <el-empty v-if="!loading && list.length === 0" description="暂无模块，点击上方按钮新增" />

      <div
        v-for="(item, idx) in list"
        :key="item.id"
        class="module-card"
        draggable="true"
        @dragstart="onDragStart($event, idx)"
        @dragover.prevent="onDragOver($event, idx)"
        @dragend="onDragEnd"
        @drop="onDrop($event, idx)"
        :class="{ 'drag-over': dragOverIdx === idx, 'is-dragging': dragIdx === idx }"
      >
        <div class="card-header">
          <div class="drag-handle">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <line x1="8" y1="6" x2="16" y2="6" /><line x1="8" y1="10" x2="16" y2="10" />
              <line x1="8" y1="14" x2="16" y2="14" /><line x1="8" y1="18" x2="16" y2="18" />
            </svg>
          </div>
          <div class="card-meta">
            <el-tag :type="typeTagColor(item.type)" size="small" effect="plain">{{ typeLabel(item.type) }}</el-tag>
            <span class="card-title">{{ item.title || '未命名模块' }}</span>
            <span class="card-order">排序: {{ item.sortOrder }}</span>
          </div>
          <div class="card-status">
            <el-switch
              :model-value="item.status === 1"
              @change="toggleStatus(item)"
              size="small"
              :loading="item._saving"
            />
            <el-button type="primary" link size="small" @click="openEdit(item)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(item.id)">删除</el-button>
          </div>
        </div>
        <div class="card-preview">
          <div class="preview-mini">
            <div v-if="item.type === 'BANNER'" class="preview-banner">
              <span>轮播图区</span>
            </div>
            <div v-else-if="item.type === 'SINGLE_IMAGE' && parsedConfig(item).imageUrl" class="preview-image">
              <img :src="parsedConfig(item).imageUrl" alt="" />
            </div>
            <div v-else class="preview-products">
              <span class="preview-icon">{{ typeIcon(item.type) }}</span>
              <span class="preview-label">{{ item.type === 'COUPON_ENTRY' ? '优惠券入口' : `${parsedConfig(item).displayCount || 8} 个商品` }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑模块' : '新增模块'"
      width="560px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="模块类型" prop="type">
          <el-select v-model="form.type" placeholder="选择模块类型" style="width: 100%" :disabled="isEdit">
            <el-option
              v-for="t in MODULE_TYPES"
              :key="t.value"
              :label="`${t.icon} ${t.label}`"
              :value="t.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="模块标题" prop="title">
          <el-input v-model="form.title" :placeholder="titlePlaceholder" />
        </el-form-item>

        <!-- 分类展示：选择分类 -->
        <template v-if="form.type === 'CATEGORY_SHOWCASE'">
          <el-form-item label="展示分类" prop="categoryId">
            <el-select v-model="form.categoryId" placeholder="选择要展示的商品分类" style="width: 100%" filterable>
              <el-option
                v-for="c in allCategories"
                :key="c.id"
                :label="c.name"
                :value="c.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="展示数量" prop="displayCount">
            <el-input-number v-model="form.displayCount" :min="1" :max="20" />
          </el-form-item>
        </template>

        <!-- 商品展示：展示数量 -->
        <template v-if="form.type === 'HOT_PRODUCTS' || form.type === 'NEW_PRODUCTS'">
          <el-form-item label="展示数量" prop="displayCount">
            <el-input-number v-model="form.displayCount" :min="1" :max="20" />
          </el-form-item>
        </template>

        <!-- 精选商品：选择SPU -->
        <template v-if="form.type === 'PRODUCT_GRID'">
          <el-form-item label="精选商品" prop="spuIds">
            <el-select
              v-model="form.spuIds"
              multiple
              filterable
              remote
              reserve-keyword
              :remote-method="searchSpu"
              :loading="spuSearchLoading"
              placeholder="搜索并选择商品"
              style="width: 100%"
            >
              <el-option
                v-for="s in spuOptions"
                :key="s.id"
                :label="s.name"
                :value="s.id"
              />
            </el-select>
          </el-form-item>
        </template>

        <!-- 单图：图片URL + 链接 -->
        <template v-if="form.type === 'SINGLE_IMAGE'">
          <el-form-item label="图片URL" prop="imageUrl">
            <el-input v-model="form.imageUrl" placeholder="输入图片地址" />
            <el-image v-if="form.imageUrl" :src="form.imageUrl" fit="cover" style="width: 100%; height: 120px; margin-top: 8px; border-radius: 8px;" />
          </el-form-item>
          <el-form-item label="跳转链接">
            <el-input v-model="form.linkUrl" placeholder="点击跳转链接，留空不跳转" />
          </el-form-item>
        </template>

        <el-form-item label="排序值" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
          <span style="margin-left: 8px; color: var(--text-muted);">越小越靠前</span>
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
import { adminRequest, userRequest } from '@/api/request'

// ========== 模块类型定义 ==========
const MODULE_TYPES = [
  { value: 'BANNER',           label: '轮播图',      icon: '🎠' },
  { value: 'HOT_PRODUCTS',     label: '热门推荐',    icon: '🔥' },
  { value: 'NEW_PRODUCTS',     label: '新品上市',    icon: '✨' },
  { value: 'CATEGORY_SHOWCASE',label: '分类展示',    icon: '📂' },
  { value: 'COUPON_ENTRY',     label: '领券入口',    icon: '🎫' },
  { value: 'PRODUCT_GRID',     label: '精选商品',    icon: '⭐' },
  { value: 'SINGLE_IMAGE',     label: '单图广告',    icon: '🖼️' }
]

const TYPE_MAP = Object.fromEntries(MODULE_TYPES.map(t => [t.value, t]))
function typeLabel(type) { return TYPE_MAP[type]?.label || type }
function typeIcon(type) { return TYPE_MAP[type]?.icon || '📦' }
function typeTagColor(type) {
  const colors = { BANNER: 'danger', HOT_PRODUCTS: 'warning', NEW_PRODUCTS: 'success', CATEGORY_SHOWCASE: '', COUPON_ENTRY: 'primary', PRODUCT_GRID: 'warning', SINGLE_IMAGE: 'info' }
  return colors[type] || ''
}

// ========== 状态 ==========
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(null)
const list = ref([])
const formRef = ref(null)
const allCategories = ref([])
const spuOptions = ref([])
const spuSearchLoading = ref(false)

const form = reactive({
  type: 'HOT_PRODUCTS',
  title: '',
  categoryId: null,
  displayCount: 8,
  spuIds: [],
  imageUrl: '',
  linkUrl: '',
  sortOrder: 0,
  status: 1
})

const titlePlaceholder = {
  BANNER: '如：首页轮播',
  HOT_PRODUCTS: '如：热门推荐',
  NEW_PRODUCTS: '如：新品上市',
  CATEGORY_SHOWCASE: '如：手机数码',
  COUPON_ENTRY: '如：领券中心',
  PRODUCT_GRID: '如：精选推荐',
  SINGLE_IMAGE: '如：限时活动'
}

const rules = {
  type: [{ required: true, message: '请选择模块类型', trigger: 'change' }],
  title: [{ required: true, message: '请输入模块标题', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  spuIds: [{ type: 'array', required: true, message: '请选择商品', trigger: 'change' }],
  imageUrl: [{ required: true, message: '请输入图片URL', trigger: 'blur' }]
}

function parsedConfig(item) {
  try {
    return JSON.parse(item.config || '{}')
  } catch {
    return {}
  }
}

// ========== 拖拽排序 ==========
const dragIdx = ref(-1)
const dragOverIdx = ref(-1)

function onDragStart(e, idx) {
  dragIdx.value = idx
  e.dataTransfer.effectAllowed = 'move'
}
function onDragOver(e, idx) {
  dragOverIdx.value = idx
}
function onDragEnd() {
  dragIdx.value = -1
  dragOverIdx.value = -1
}
async function onDrop(e, toIdx) {
  dragOverIdx.value = -1
  const fromIdx = dragIdx.value
  dragIdx.value = -1
  if (fromIdx === toIdx || fromIdx < 0) return

  // 本地重排
  const moved = list.value.splice(fromIdx, 1)[0]
  list.value.splice(toIdx, 0, moved)

  // 更新 sort_order 并批量提交
  const items = list.value.map((item, i) => ({ id: item.id, sortOrder: i }))
  try {
    await adminRequest({ url: '/admin/home-module/batch-sort', method: 'put', data: { items } })
    ElMessage.success('排序已更新')
  } catch (e) {
    ElMessage.error('排序更新失败')
    await load()
  }
}

// ========== 数据加载 ==========
async function load() {
  loading.value = true
  try {
    const res = await adminRequest({ url: '/admin/home-module/list', method: 'get' })
    list.value = (Array.isArray(res) ? res : (res?.data || []))
  } catch (e) {
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  try {
    const res = await userRequest({ url: '/user/category/list', method: 'get', __silent: true })
    allCategories.value = flattenCats(res || [])
  } catch (e) {}
}

function flattenCats(tree) {
  const result = []
  function walk(list) {
    if (!list) return
    for (const c of list) {
      result.push({ id: c.id, name: c.name })
      walk(c.children)
    }
  }
  walk(tree)
  return result
}

async function searchSpu(query) {
  if (!query) { spuOptions.value = []; return }
  spuSearchLoading.value = true
  try {
    const res = await adminRequest({ url: '/admin/spu/page', method: 'get', params: { name: query, page: 1, pageSize: 20 } })
    const records = res?.records || res?.list || (res?.data?.records) || []
    spuOptions.value = records.map(r => ({ id: r.id, name: r.name }))
  } catch (e) {
  } finally {
    spuSearchLoading.value = false
  }
}

// ========== CRUD ==========
function resetForm() {
  form.type = 'HOT_PRODUCTS'
  form.title = ''
  form.categoryId = null
  form.displayCount = 8
  form.spuIds = []
  form.imageUrl = ''
  form.linkUrl = ''
  form.sortOrder = 0
  form.status = 1
  editId.value = null
  isEdit.value = false
}

function openAdd() {
  resetForm()
  form.sortOrder = list.value.length
  dialogVisible.value = true
}

function openEdit(item) {
  isEdit.value = true
  editId.value = item.id
  form.type = item.type
  form.title = item.title
  form.sortOrder = item.sortOrder
  form.status = item.status

  const cfg = parsedConfig(item)
  form.categoryId = cfg.categoryId || null
  form.displayCount = cfg.displayCount || 8
  form.spuIds = cfg.spuIds || []
  form.imageUrl = cfg.imageUrl || ''
  form.linkUrl = cfg.linkUrl || ''

  dialogVisible.value = true
}

function buildConfig() {
  const cfg = {}
  if (form.type === 'CATEGORY_SHOWCASE') {
    cfg.categoryId = form.categoryId
    cfg.displayCount = form.displayCount
  } else if (form.type === 'HOT_PRODUCTS' || form.type === 'NEW_PRODUCTS') {
    cfg.displayCount = form.displayCount
  } else if (form.type === 'PRODUCT_GRID') {
    cfg.spuIds = form.spuIds
  } else if (form.type === 'SINGLE_IMAGE') {
    cfg.imageUrl = form.imageUrl
    cfg.linkUrl = form.linkUrl
  }
  return JSON.stringify(cfg)
}

async function handleSave() {
  // 动态校验
  let valid = true
  if (!form.type || !form.title) valid = false
  if (form.type === 'CATEGORY_SHOWCASE' && !form.categoryId) valid = false
  if (form.type === 'PRODUCT_GRID' && (!form.spuIds || form.spuIds.length === 0)) valid = false
  if (form.type === 'SINGLE_IMAGE' && !form.imageUrl) valid = false
  if (!valid) {
    try { await formRef.value?.validate() } catch { return }
  }

  submitting.value = true
  try {
    const data = {
      type: form.type,
      title: form.title,
      config: buildConfig(),
      sortOrder: form.sortOrder,
      status: form.status
    }
    if (isEdit.value) {
      await adminRequest({ url: `/admin/home-module/${editId.value}`, method: 'put', data })
      ElMessage.success('已更新')
    } else {
      await adminRequest({ url: '/admin/home-module', method: 'post', data })
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (e) {
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id) {
  try {
    await ElMessageBox.confirm('确定删除该模块？', '提示', { type: 'warning' })
    await adminRequest({ url: `/admin/home-module/${id}`, method: 'delete' })
    ElMessage.success('已删除')
    await load()
  } catch (e) {}
}

async function toggleStatus(item) {
  item._saving = true
  const next = item.status === 1 ? 0 : 1
  try {
    await adminRequest({
      url: `/admin/home-module/${item.id}`,
      method: 'put',
      data: { type: item.type, title: item.title, config: item.config, sortOrder: item.sortOrder, status: next }
    })
    item.status = next
    ElMessage.success(next === 1 ? '已启用' : '已禁用')
  } catch (e) {
  } finally {
    item._saving = false
  }
}

onMounted(async () => {
  await load()
  loadCategories()
})
</script>

<style scoped>
.home-module-mgmt {
  display: flex;
  flex-direction: column;
  gap: 20px;
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
  margin-bottom: 6px;
}
.panel-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}
.panel-desc {
  color: var(--text-muted);
  font-size: 13px;
  margin: 0;
}

.module-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 200px;
}

.module-card {
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: all 0.2s;
  cursor: default;
}
.module-card:hover {
  box-shadow: var(--shadow-md);
  border-color: var(--brand-primary-border);
}
.module-card.drag-over {
  border-color: var(--brand-primary);
  background: var(--brand-primary-soft);
  transform: translateY(2px);
}
.module-card.is-dragging {
  opacity: 0.5;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
}

.drag-handle {
  cursor: grab;
  color: var(--text-muted);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding: 2px;
}
.drag-handle:hover { color: var(--text-secondary); }
.drag-handle:active { cursor: grabbing; }

.card-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}
.card-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--text-primary);
}
.card-order {
  font-size: 12px;
  color: var(--text-muted);
}

.card-status {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.card-preview {
  border-top: 1px solid var(--border-base);
  padding: 12px 18px;
  background: var(--bg-hover);
}

.preview-mini {
  display: flex;
  align-items: center;
  gap: 10px;
}
.preview-banner {
  width: 100%;
  height: 48px;
  background: linear-gradient(135deg, var(--brand-primary-soft), var(--brand-primary-border));
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--brand-primary);
  font-size: 13px;
  font-weight: 500;
}
.preview-image {
  width: 100%;
}
.preview-image img {
  width: 100%;
  height: 64px;
  object-fit: cover;
  border-radius: 6px;
}
.preview-products {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
  font-size: 13px;
}
.preview-icon {
  font-size: 18px;
}
.preview-label {
  color: var(--text-muted);
}
</style>
