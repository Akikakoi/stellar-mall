<template>
  <div class="mgmt-page">
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">商品管理</span>
        <div class="panel-actions">
          <el-button @click="downloadTemplate">下载模板</el-button>
          <el-button @click="handleExport" :loading="exporting">导出 Excel</el-button>
          <el-button type="primary" @click="openImport">导入 Excel</el-button>
          <el-button type="primary" @click="openDialog('create')">+ 新增商品</el-button>
        </div>
      </div>

      <div class="filter-bar">
        <el-input v-model="query.name" placeholder="名称搜索" style="width: 240px" clearable :disabled="loading" @keyup.enter="loadPage" />
        <el-select v-model="query.categoryId" placeholder="分类" clearable style="width: 160px;" :disabled="loading">
          <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px;" :disabled="loading">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>

        <!-- ===== 排序控件 ===== -->
        <span style="color: var(--text-secondary); font-size:13px; white-space:nowrap;">排序：</span>
        <el-select v-model="query.sortBy" placeholder="排序字段" clearable style="width:130px;" :disabled="loading" @change="onSortByChange">
          <el-option label="创建时间" value="createTime" />
          <el-option label="商品名称" value="name" />
        </el-select>
        <el-select v-model="query.sortOrder" placeholder="排序方向" clearable style="width:110px;" :disabled="loading || !query.sortBy" @change="onSortChange">
          <el-option label="升序  ↑" value="asc" />
          <el-option label="降序  ↓" value="desc" />
        </el-select>

        <el-button type="primary" :disabled="loading" @click="onQueryClick">查询</el-button>
      </div>

      <!-- 批量操作工具栏 -->
      <div v-if="selectedRows.length > 0" class="batch-toolbar">
        <span class="batch-tip">已选择 {{ selectedRows.length }} 件商品</span>
        <el-button type="warning" size="small" @click="handleBatchShelfOff">批量下架</el-button>
        <el-button type="success" size="small" @click="handleBatchShelfOn">批量上架</el-button>
      </div>

      <el-table :data="records" v-loading="loading" stripe empty-text="暂无数据" style="width: 100%;"
        @selection-change="handleSelectionChange" :row-class-name="tableRowClassName">
        
        <el-table-column type="selection" width="45" />
        <el-table-column label="图片" width="80">
          <template #default="{ row }">
            <el-image :src="row.mainImage || row.image || row.pic" :preview-src-list="[row.mainImage || row.image || row.pic]" style="width: 48px; height: 48px; border-radius: var(--radius-sm);" fit="cover">
              <template #error><div class="img-placeholder">N/A</div></template>
            </el-image>
          </template>
        </el-table-column>
        <el-table-column prop="name" min-width="200" show-overflow-tooltip>
          <template #header>
            <span class="col-header-sort">
              商品名称
              <span v-if="query.sortBy === 'name'" class="sort-arrow" :class="query.sortOrder">
                {{ query.sortOrder === 'asc' ? '▲' : '▼' }}
              </span>
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="categoryName" label="分类" width="120" />
        <el-table-column label="价格" width="120">
          <template #default="{ row }">¥{{ Number(row.minPrice ?? row.price ?? 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '上架中' : '已下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 1"
              type="warning" link size="small"
              @click="handleShelfOff(row)"
            >下架</el-button>
            <el-button
              v-else
              type="success" link size="small"
              @click="handleShelfOn(row)"
            >上架</el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新增商品' : '编辑商品'" width="1000px" destroy-on-close>
      <el-form :model="form" :rules="formRules" ref="formRef" label-width="90px">
        <el-form-item label="商品名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="选择分类" style="width: 100%;">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="价格" prop="price">
          <el-input-number v-model="form.price" :min="0" :precision="2" style="width: 100%;" />
        </el-form-item>
        <!-- ===== 商品图片 ===== -->
        <el-form-item label="商品图片">
          <div class="image-upload-section">
            <!-- 已上传图片预览 -->
            <div class="image-preview-grid" v-if="uploadedImages.length > 0">
              <div
                v-for="(img, idx) in uploadedImages"
                :key="idx"
                class="image-preview-item"
                :class="{ 'is-main': idx === 0 }"
              >
                <el-image :src="img.url" fit="cover" class="image-preview-img" :preview-src-list="uploadedImages.map(i => i.url)" />
                <div class="image-preview-label">{{ idx === 0 ? '主图' : `副图${idx}` }}</div>
                <div class="image-preview-remove" @click="removeUploadedImage(idx)">
                  <el-icon><Close /></el-icon>
                </div>
              </div>
            </div>
            <!-- 上传按钮 -->
            <div class="image-upload-actions">
              <el-upload
                :show-file-list="false"
                :http-request="doImageUpload"
                accept="image/*"
                drag
              >
                <div class="upload-trigger">
                  <el-icon style="font-size:24px;color:var(--brand-primary)"><Upload /></el-icon>
                  <span>拖拽图片或<em>点击上传</em></span>
                  <span class="upload-hint">支持 JPG / PNG / WebP，单张不超过 10MB</span>
                </div>
              </el-upload>
              <span v-if="imageUploading" style="color:var(--brand-primary);font-size:13px;">上传中...</span>
              <span v-if="uploadedImages.length > 0" style="color:var(--text-muted);font-size:13px;">
                共 {{ uploadedImages.length }} 张（第1张为主图，其余为轮播副图）
              </span>
            </div>
            <!-- 手动输入URL（兜底） -->
            <el-collapse style="margin-top: 8px;">
              <el-collapse-item title="手动输入 URL（可选）" name="urls">
                <el-input v-model="form.mainImage" placeholder="主图 URL" style="margin-bottom: 8px;" @change="initUploadedImages" />
                <el-input v-model="form.subImages" placeholder="副图 URL（分号分隔，如 url1;url2;url3）" @change="initUploadedImages" />
              </el-collapse-item>
            </el-collapse>
          </div>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>

        <!-- ===== 知识库文档上传（创建时必填） ===== -->
        <el-form-item>
          <template #label>
            <span style="color: var(--status-error)">*</span>
            <span>关联文档</span>
            <el-tooltip placement="top" effect="dark">
              <template #content>上传商品参数/说明文档到知识库，AI 助手将基于此文档回答用户咨询。<br/>创建商品时<b>必须上传</b>，支持 .md / .txt / .pdf / .docx。</template>
              <span class="hint-icon">ⓘ</span>
            </el-tooltip>
          </template>
          <!-- 已有文档预览 -->
          <div v-if="kbExistingDoc" class="kb-existing-doc">
            <div class="kb-existing-doc-info">
              <el-icon style="color:var(--brand-primary);font-size:22px;flex-shrink:0;"><Document /></el-icon>
              <div class="kb-existing-doc-detail">
                <div class="kb-existing-doc-name">{{ kbExistingDoc.filename }}</div>
                <div class="kb-existing-doc-meta">
                  <el-tag size="small" :type="kbDocStatusTag(kbExistingDoc.status)">{{ kbDocStatusText(kbExistingDoc.status) }}</el-tag>
                  <template v-if="kbExistingDoc.file_ext">
                    <span class="kb-existing-doc-meta-item">{{ kbExistingDoc.file_ext.toUpperCase() }}</span>
                  </template>
                  <template v-if="kbExistingDoc.file_size">
                    <span class="kb-existing-doc-meta-item">{{ humanFileSize(kbExistingDoc.file_size) }}</span>
                  </template>
                  <template v-if="kbExistingDoc.chunk_count != null">
                    <span class="kb-existing-doc-meta-item">{{ kbExistingDoc.chunk_count }} Chunks</span>
                  </template>
                  <template v-if="kbExistingDoc.created_at">
                    <span class="kb-existing-doc-meta-item">上传于 {{ fmtTime(kbExistingDoc.created_at) }}</span>
                  </template>
                </div>
              </div>
            </div>
            <el-button type="danger" plain size="small" @click="removeExistingDoc">移除文档</el-button>
          </div>
          <!-- 分隔线 -->
          <el-divider v-if="kbExistingDoc" content-position="left" style="margin: 12px 0;">
            <span style="font-size:13px;color:var(--text-muted);">上传新文档替换</span>
          </el-divider>
          <div class="kb-upload-row">
            <el-upload
              ref="kbUploadRef"
              :auto-upload="false"
              :limit="1"
              :on-change="onKbFileChange"
              :on-remove="onKbFileRemove"
              :file-list="kbFileList"
              accept=".md,.txt,.pdf,.docx"
              drag
              style="flex:1"
            >
              <el-icon style="font-size:24px;color:var(--brand-primary)"><Upload /></el-icon>
              <div class="el-upload__text">拖拽文件到此处，或 <em>点击上传</em></div>
              <template #tip>
                <div class="el-upload__tip">支持 .md / .txt / .pdf / .docx，最大 20MB</div>
              </template>
            </el-upload>
            <div class="kb-params">
              <div class="kb-param-item">
                <span class="kb-param-label">Chunk 大小</span>
                <el-input-number v-model="kbChunkSize" :min="128" :max="4096" :step="64" size="small" style="width:120px" />
              </div>
              <div class="kb-param-item">
                <span class="kb-param-label">Overlap</span>
                <el-input-number v-model="kbChunkOverlap" :min="0" :max="1024" :step="16" size="small" style="width:120px" />
              </div>
              <div class="kb-param-item">
                <span class="kb-param-label">标签</span>
                <el-input v-model="kbTags" size="small" placeholder="逗号分隔，如：手机,旗舰" style="width:180px" />
              </div>
              <el-button size="small" :disabled="!kbFile" :loading="kbPreviewing" @click="doKbPreview">
                <el-icon v-if="!kbPreviewing"><Search /></el-icon> 预览切分
              </el-button>
            </div>
          </div>
          <div v-if="kbPreviewResult != null" class="kb-preview-result">
            <div class="kb-preview-head">
              <span>共 {{ kbPreviewResult.chunk_count }} 个 Chunk</span>
              <span style="font-size:12px;color:var(--text-muted)">（chunk_size={{ kbChunkSize }}, overlap={{ kbChunkOverlap }}）</span>
            </div>
            <div v-for="(chunk, ci) in kbPreviewResult.chunks" :key="ci" class="kb-preview-chunk">
              <div class="kb-preview-chunk-idx">#{{ ci + 1 }}</div>
              <div class="kb-preview-chunk-text">{{ chunk }}</div>
            </div>
          </div>
          <div v-if="kbUploadProgress > 0 && kbUploadProgress < 100" style="margin-top:8px">
            <el-progress :percentage="kbUploadProgress" :stroke-width="6" />
          </div>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>

        <!-- ===== 规格组定义 ===== -->
        <el-form-item>
          <template #label>
            <span>规格组定义</span>
            <el-tooltip placement="top" effect="dark">
              <template #content>规格组代表商品的可选维度，如「颜色」「存储容量」。<br/>添加多个规格组后，点击「生成 SKU 组合」会自动生成所有可能的规格组合。</template>
              <span class="hint-icon">ⓘ</span>
            </el-tooltip>
          </template>
          <div class="spec-groups-panel">
            <div v-for="(group, gi) in specGroups" :key="gi" class="spec-group-row">
              <el-select v-model="group.name" filterable allow-create placeholder="规格名" style="width: 120px;" class="spec-group-select">
                <el-option v-for="opt in presetSpecNames" :key="opt" :label="opt" :value="opt" />
              </el-select>
              <div class="spec-group-values">
                <el-tag
                  v-for="(v, vi) in group.values"
                  :key="vi"
                  closable
                  size="small"
                  type="info"
                  @close="group.values.splice(vi, 1)"
                >{{ v }}</el-tag>
                <el-input
                  v-model="group.newValue"
                  placeholder="输入值，回车添加"
                  size="small"
                  style="width: 140px;"
                  @keyup.enter="addSpecValue(gi)"
                  @blur="addSpecValue(gi)"
                />
              </div>
              <el-button type="danger" size="small" plain @click="specGroups.splice(gi, 1)" :disabled="specGroups.length <= 1">删除</el-button>
            </div>
            <div class="spec-group-actions">
              <el-button size="small" @click="addSpecGroup">+ 添加规格组</el-button>
              <el-tooltip placement="top" effect="dark">
                <template #content>根据规格组的所有组合自动生成 SKU 列表。<br/>例如：2 种颜色 x 3 种存储 = 6 个 SKU</template>
                <el-button type="primary" size="small" @click="generateSkus" :disabled="!canGenerateSkus">生成 SKU 组合</el-button>
              </el-tooltip>
              <span v-if="generatedCount" class="gen-hint">已生成 {{ generatedCount }} 个 SKU</span>
            </div>
          </div>
        </el-form-item>

        <!-- ===== SKU 规格列表 ===== -->
        <el-form-item>
          <template #label>
            <span>规格列表</span>
            <el-tooltip placement="top" effect="dark">
              <template #content>每个 SKU 代表一种具体的规格组合。<br/>可通过上方「规格组定义」批量生成，也可手动添加。</template>
              <span class="hint-icon">ⓘ</span>
            </el-tooltip>
          </template>
          <div class="sku-list">
            <div v-for="(sku, index) in form.skuList" :key="sku._uid || index" class="sku-item">
              <div class="sku-header">
                <span class="sku-index">规格 {{ index + 1 }}：{{ sku.name || '未命名' }}</span>
                <el-button type="danger" size="small" @click="removeSku(index)" v-if="form.skuList.length > 1">删除</el-button>
              </div>
              <div class="sku-fields">
                <el-tooltip placement="top" effect="dark" content="自动生成：SPU名 · 规格值1 · 规格值2。可手动修改">
                  <el-input v-model="sku.name" placeholder="规格名称" class="sku-field" />
                </el-tooltip>
                <el-tooltip placement="top" effect="dark" content="自动生成：规格1:值1;规格2:值2。可手动修改">
                  <el-input v-model="sku.specs" placeholder="规格描述（如：颜色:红色;存储:256GB）" class="sku-field" />
                </el-tooltip>
                <el-tooltip placement="top" effect="dark" content="设置该规格组合的售价">
                  <el-input-number v-model="sku.price" :min="0" :precision="2" placeholder="价格" class="sku-field" />
                </el-tooltip>
                <el-tooltip placement="top" effect="dark" content="设置该规格组合的初始库存数量">
                  <el-input-number v-model="sku.stock" :min="0" placeholder="库存" class="sku-field" />
                </el-tooltip>
                <el-tooltip placement="top" effect="dark" content="数值越小越靠前，默认 0">
                  <el-input-number v-model="sku.sort" :min="0" placeholder="排序" class="sku-field" />
                </el-tooltip>
                <el-switch v-model="sku.status" :active-value="1" :inactive-value="0" active-text="在售" inactive-text="停售" />
              </div>
            </div>
            <el-button type="primary" size="small" plain @click="addManualSku">+ 手动添加规格</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 导入对话框 -->
    <el-dialog v-model="importVisible" title="批量导入商品" width="600px" destroy-on-close>
      <div style="margin-bottom: 16px;">
        <p style="color: var(--text-secondary); margin-bottom: 12px;">请先下载模板，按格式填写后上传。同名 SPU 将复用已有商品，只追加 SKU。</p>
        <el-button @click="downloadTemplate" size="small">下载模板</el-button>
      </div>
      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :limit="1"
        accept=".xlsx"
        :on-change="onFileChange"
        :on-remove="onFileRemove"
        drag
      >
        <el-icon class="el-icon--upload"><Upload /></el-icon>
        <div class="el-upload__text">将 Excel 文件拖到此处，或<em>点击上传</em></div>
      </el-upload>
      <div v-if="importResult" style="margin-top: 16px;">
        <el-alert
          :title="importResult.success ? '导入完成' : '导入失败'"
          :type="importResult.success ? 'success' : 'error'"
          :closable="false"
        >
          <template v-if="importResult.success">
            新建 SPU: {{ importResult.newSpuCount }} 个，新建 SKU: {{ importResult.newSkuCount }} 个<br/>
            跳过行数: {{ importResult.skippedRows }}
          </template>
        </el-alert>
        <div v-if="importResult.errors" style="margin-top: 8px; max-height: 200px; overflow-y: auto;">
          <p v-for="(e, i) in importResult.errors" :key="i" style="color: var(--status-danger); font-size: 13px; margin: 4px 0;">{{ e }}</p>
          <p v-if="importResult.errorCount > importResult.errors.length" style="color: var(--text-muted); font-size: 12px;">... 还有 {{ importResult.errorCount - importResult.errors.length }} 条错误</p>
        </div>
      </div>
      <template #footer>
        <el-button @click="importVisible = false">关闭</el-button>
        <el-button type="primary" :loading="importing" @click="handleImport" :disabled="!importFile">开始导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { pageSpu, getAdminSpu, saveSpu, updateSpu, deleteSpu, setSpuStatus, batchSetSpuStatus, listAdminCategory, uploadImages } from '@/api/admin'
import { apiKbUploadWithChunks, apiKbPreview, apiKbGet } from '@/api/rag'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Search, Document } from '@element-plus/icons-vue'
import { storage } from '@/utils/storage'

const SORT_STORAGE_KEY = 'stellar:admin:spu-mgmt:sort:v1'
const SORT_BY_ALLOWED = new Set(['createTime', 'name'])
const SORT_ORDER_ALLOWED = new Set(['asc', 'desc'])

const loading = ref(false)
const submitting = ref(false)
const records = ref([])
const selectedRows = ref([])
const total = ref(0)
const categories = ref([])

const query = reactive({ page: 1, pageSize: 10, name: '', categoryId: null, status: null, sortBy: null, sortOrder: null })

/** 从 localStorage 恢复上次选择的排序维度与方向 */
function restoreSort() {
  const obj = storage.local.getObject(SORT_STORAGE_KEY)
  if (obj && SORT_BY_ALLOWED.has(obj.sortBy)) {
    query.sortBy = obj.sortBy
    if (SORT_ORDER_ALLOWED.has(obj.sortOrder)) query.sortOrder = obj.sortOrder
    else query.sortOrder = null
  }
}

/** 将当前排序维度与方向写入 localStorage，缺失任一则清除 */
function persistSort() {
  if (query.sortBy && SORT_BY_ALLOWED.has(query.sortBy) && query.sortOrder && SORT_ORDER_ALLOWED.has(query.sortOrder)) {
    storage.local.setObject(SORT_STORAGE_KEY, { sortBy: query.sortBy, sortOrder: query.sortOrder })
  } else {
    storage.local.remove(SORT_STORAGE_KEY)
  }
}

/** 切换排序字段，若未选方向则默认升序，重置到第1页并重新加载 */
function onSortByChange() {
  if (query.sortBy && !query.sortOrder) query.sortOrder = 'asc'
  query.page = 1
  persistSort()
  loadPage()
}

/** 切换排序方向，重置到第1页并重新加载 */
function onSortChange() {
  query.page = 1
  persistSort()
  loadPage()
}

/** 点击查询按钮，重置到第1页并加载数据 */
function onQueryClick() {
  query.page = 1
  loadPage()
}

const dialogVisible = ref(false)
const dialogMode = ref('create')
const formRef = ref(null)
const form = reactive({ id: null, name: '', categoryId: null, price: 0, image: '', mainImage: '', subImages: '', description: '', status: 1, skuList: [] })
const formRules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  price: [{ required: true, message: '请输入价格', trigger: 'blur' }]
}

// KB upload
const kbUploadRef = ref(null)
const kbFile = ref(null)
const kbFileList = ref([])
const kbChunkSize = ref(512)
const kbChunkOverlap = ref(64)
const kbTags = ref('')
const kbPreviewing = ref(false)
const kbPreviewResult = ref(null)
const kbUploadProgress = ref(0)
const kbExistingDoc = ref(null)
const kbExistingDocId = ref(null)

// 图片上传
const imageUploading = ref(false)
const uploadedImages = ref([])  // { url, name } 数组：第0项为主图，其余为副图

/** 移除指定索引的已上传图片，并同步到表单字段 */
function removeUploadedImage(idx) {
  uploadedImages.value.splice(idx, 1)
  syncImagesToForm()
}
/** 将已上传图片列表同步到表单的 mainImage 和 subImages 字段 */
function syncImagesToForm() {
  const urls = uploadedImages.value.map(i => i.url)
  form.mainImage = urls[0] || ''
  form.image = urls[0] || ''
  form.subImages = urls.slice(1).join(';')
}
/** 上传单张图片到服务器，成功后添加到已上传列表 */
async function doImageUpload(options) {
  const { file } = options
  imageUploading.value = true
  try {
    const urls = await uploadImages([file], 'spu')
    if (urls && urls.length > 0) {
      uploadedImages.value.push({ url: urls[0], name: file.name })
      syncImagesToForm()
    }
    ElMessage.success(`${file.name} 上传成功`)
  } catch (e) {
    ElMessage.error(`${file.name} 上传失败：${e?.message || '未知错误'}`)
  } finally {
    imageUploading.value = false
  }
}
/** 从表单的 mainImage 和 subImages 字段初始化已上传图片预览列表 */
function initUploadedImages() {
  const images = []
  const main = form.mainImage || form.image || ''
  if (main) images.push({ url: main, name: '主图' })
  const subs = form.subImages || ''
  if (subs) {
    subs.split(';').filter(Boolean).forEach((u, i) => {
      images.push({ url: u.trim(), name: `副图${i + 1}` })
    })
  }
  uploadedImages.value = images
}

// Spec groups
const presetSpecNames = ['颜色', '存储', '内存', '尺寸', '配置', '型号', '版本', '材质', '尺码', '容量', '规格']
const specGroups = ref([{ name: '', values: [], newValue: '' }])
const generatedCount = ref(0)

const canGenerateSkus = computed(() => {
  const valid = specGroups.value.filter(g => g.name && g.name.trim() && g.values.length > 0)
  return valid.length >= 1
})

/** 加载分类列表元数据 */
async function loadMeta() {
  try {
    const c = await listAdminCategory()
    categories.value = c || []
  } catch (e) {}
}

/** 加载商品分页列表，根据当前查询条件筛选 */
async function loadPage() {
  loading.value = true
  try {
    const res = await pageSpu({ ...query })
    const d = res || {}
    records.value = d.records || d.list || []
    total.value = d.total || 0
  } finally {
    loading.value = false
  }
}

/** 添加一个新的规格组 */
function addSpecGroup() {
  specGroups.value.push({ name: '', values: [], newValue: '' })
}

/** 向指定规格组添加一个规格值 */
function addSpecValue(gi) {
  const group = specGroups.value[gi]
  if (!group) return
  const val = (group.newValue || '').trim()
  if (!val) return
  if (!group.values.includes(val)) {
    group.values.push(val)
  }
  group.newValue = ''
}

/** 根据规格组的笛卡尔积自动生成 SKU 组合列表 */
function generateSkus() {
  const valid = specGroups.value.filter(g => g.name && g.name.trim() && g.values.length > 0)
  if (valid.length === 0) {
    ElMessage.warning('请至少添加一个有效的规格组（含名称和至少一个值）')
    return
  }
  const combinations = cartesianProduct(valid)
  const spuName = form.name || '商品'
  const basePrice = Number(form.price ?? 0)
  form.skuList = combinations.map((combo, idx) => ({
    _uid: Date.now() + idx + Math.random(),
    name: combo.name ? `${spuName} · ${combo.name}` : `${spuName} · 规格${idx + 1}`,
    specs: combo.specs,
    price: basePrice,
    stock: 0,
    sort: idx,
    status: 1
  }))
  generatedCount.value = form.skuList.length
  ElMessage.success(`已生成 ${form.skuList.length} 个 SKU 组合`)
}

/** 计算多个规格组的笛卡尔积，生成所有规格组合 */
function cartesianProduct(groups) {
  if (groups.length === 0) return []
  let result = [{ specs: '', name: '' }]
  for (const group of groups) {
    const next = []
    for (const item of result) {
      for (const val of group.values) {
        const specs = item.specs ? `${item.specs};${group.name}:${val}` : `${group.name}:${val}`
        const name = item.name ? `${item.name} · ${val}` : val
        next.push({ specs, name })
      }
    }
    result = next
  }
  return result
}

/** 从已有的 SKU 列表中反推规格组定义，用于编辑时回填 */
function inferSpecGroups(skuList) {
  if (!skuList || skuList.length === 0) {
    specGroups.value = [{ name: '', values: [], newValue: '' }]
    generatedCount.value = 0
    return
  }
  const groupMap = new Map()
  let allSameStructure = true
  for (const sku of skuList) {
    const specs = sku.specs || ''
    if (!specs) { allSameStructure = false; continue }
    const pairs = specs.split(';').map(s => s.trim()).filter(Boolean)
    for (const pair of pairs) {
      const colonIdx = pair.indexOf(':')
      if (colonIdx === -1) continue
      const key = pair.substring(0, colonIdx).trim()
      const val = pair.substring(colonIdx + 1).trim()
      if (!key || !val) continue
      if (!groupMap.has(key)) groupMap.set(key, new Set())
      groupMap.get(key).add(val)
    }
  }
  if (groupMap.size > 0 && allSameStructure) {
    specGroups.value = Array.from(groupMap.entries()).map(([name, values]) => ({
      name, values: Array.from(values), newValue: ''
    }))
    generatedCount.value = skuList.length
  } else {
    specGroups.value = [{ name: '', values: [], newValue: '' }]
    generatedCount.value = skuList.length
  }
}

/** 手动添加一个空 SKU 规格 */
function addManualSku() {
  form.skuList.push({
    _uid: Date.now() + Math.random(),
    name: '', specs: '',
    price: Number(form.price ?? 0), stock: 0, sort: form.skuList.length, status: 1
  })
}

/** 移除指定索引的 SKU */
function removeSku(index) {
  form.skuList.splice(index, 1)
}

// KB functions
/** 知识库文件变更时，保存文件引用并自动填充标签 */
function onKbFileChange(f) {
  const raw = f?.raw
  if (!raw) return
  kbFile.value = raw
  kbFileList.value = [f]
  kbPreviewResult.value = null
  const cat = categories.value.find(c => c.id === form.categoryId)
  const parts = []
  if (cat) parts.push(cat.name)
  if (form.name) parts.push(form.name)
  kbTags.value = parts.join(',')
}

/** 知识库文件被移除时，清空相关状态 */
function onKbFileRemove() {
  kbFile.value = null
  kbFileList.value = []
  kbPreviewResult.value = null
}

/** 预览知识库文档的分块切分结果 */
async function doKbPreview() {
  if (!kbFile.value) return
  kbPreviewing.value = true
  try {
    const fd = new FormData()
    fd.append('file', kbFile.value)
    fd.append('chunk_size', String(kbChunkSize.value))
    fd.append('chunk_overlap', String(kbChunkOverlap.value))
    fd.append('limit', '30')
    const res = await apiKbPreview(fd)
    kbPreviewResult.value = res?.data || res
  } catch (e) {
    ElMessage.error('预览失败：' + (e?.message || '未知错误'))
  } finally {
    kbPreviewing.value = false
  }
}

/** 上传知识库文档到 RAG 服务，返回文档 ID */
async function uploadKbDoc() {
  if (!kbFile.value) return null
  kbUploadProgress.value = 10
  try {
    const res = await apiKbUploadWithChunks(
      kbFile.value, kbTags.value || '', kbChunkSize.value, kbChunkOverlap.value,
      (pct) => { kbUploadProgress.value = 10 + Math.round(pct * 0.7) }
    )
    kbUploadProgress.value = 100
    return res?.data?.id || null
  } catch (e) {
    kbUploadProgress.value = 0
    throw e
  }
}

/** 打开新增/编辑商品对话框，create 模式初始化空表单，edit 模式加载详情 */
async function openDialog(mode, row) {
  dialogMode.value = mode
  specGroups.value = [{ name: '', values: [], newValue: '' }]
  generatedCount.value = 0
  kbFile.value = null
  kbFileList.value = []
  kbPreviewResult.value = null
  kbUploadProgress.value = 0
  kbTags.value = ''
  kbExistingDoc.value = null
  kbExistingDocId.value = null
  if (mode === 'create') {
    Object.assign(form, { id: null, name: '', categoryId: null, price: 0, image: '', mainImage: '', subImages: '', description: '', status: 1, skuList: [] })
    uploadedImages.value = []
    addManualSku()
    dialogVisible.value = true
  } else {
    submitting.value = true
    try {
      const detail = await getAdminSpu(row.id)
      const mdText = detail.descriptionMd || ''
      const kbMatch = mdText.match(/\[KB_DOC_ID:(\d+)\]/)
      if (kbMatch) {
        kbExistingDocId.value = kbMatch[1]
        try {
          const docRes = await apiKbGet(kbExistingDocId.value)
          kbExistingDoc.value = docRes?.data || docRes
        } catch (e) {
          kbExistingDocId.value = null
        }
      }
      const cleanDescription = mdText.replace(/\n*\[KB_DOC_ID:\d+\]\n*$/g, '').trim()
      Object.assign(form, {
        id: detail.id, name: detail.name, categoryId: detail.categoryId,
        price: Number(detail.minPrice ?? detail.price ?? 0),
        image: detail.mainImage || detail.image || detail.pic || '',
        mainImage: detail.mainImage || detail.image || detail.pic || '',
        subImages: detail.subImages || '',
        description: detail.description || cleanDescription,
        status: detail.status ?? 1, skuList: []
      })
      initUploadedImages()
      const skus = detail.skuList || detail.skus || []
      if (skus.length) {
        form.skuList = skus.map((s, idx) => ({
          _uid: s.id || (Date.now() + idx), id: s.id,
          name: s.name || '', specs: s.specs || '',
          price: Number(s.price ?? 0), stock: Number(s.stock ?? 0),
          sort: Number(s.sort ?? 0), status: s.status ?? 1,
          originalPrice: s.originalPrice, image: s.image
        }))
        inferSpecGroups(form.skuList)
      } else {
        addManualSku()
      }
      dialogVisible.value = true
    } catch (e) {
      ElMessage.error('加载商品详情失败')
    } finally {
      submitting.value = false
    }
  }
}

/** 构建提交给后端的商品数据对象 */
function buildPayload() {
  const priceNum = Number(form.price ?? 0)
  const imageStr = form.mainImage || form.image || ''
  const descStr = form.description || ''
  const subImagesStr = form.subImages || ''
  const skuPayload = form.skuList && form.skuList.length
    ? form.skuList.map(s => ({
        id: s.id, name: s.name || '', specs: s.specs || '',
        price: Number(s.price ?? 0) || 0, originalPrice: Number(s.price ?? 0) || 0,
        stock: Number(s.stock ?? 0) || 0, sort: Number(s.sort ?? 0) || 0,
        status: s.status ?? 1, image: s.image || imageStr
      }))
    : []
  return {
    id: form.id ?? undefined, name: form.name, categoryId: form.categoryId,
    status: form.status ?? 1, sort: form.sort ?? 0,
    subtitle: form.subtitle ?? '', image: imageStr, mainImage: imageStr,
    subImages: subImagesStr, description: descStr, descriptionMd: descStr,
    price: isNaN(priceNum) ? null : priceNum,
    minPrice: isNaN(priceNum) ? null : priceNum,
    maxPrice: isNaN(priceNum) ? null : priceNum,
    totalStock: form.totalStock ?? 0, skuList: skuPayload
  }
}

/** 提交商品表单：校验、上传知识库文档、保存或更新商品 */
async function submitForm() {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch (e) { return }
  if (dialogMode.value === 'create' && !kbFile.value) {
    ElMessage.warning('请上传商品关联文档（产品参数/说明）到知识库')
    return
  }
  submitting.value = true
  try {
    let kbDocId = null
    if (kbFile.value) {
      try {
        kbDocId = await uploadKbDoc()
        ElMessage.success('知识库文档上传成功')
      } catch (e) {
        ElMessage.error('知识库文档上传失败：' + (e?.message || '未知错误'))
        return
      }
    } else if (kbExistingDocId.value) {
      kbDocId = kbExistingDocId.value
    }
    const payload = buildPayload()
    if (kbDocId) {
      payload.descriptionMd = (payload.descriptionMd || '') + `\n\n[KB_DOC_ID:${kbDocId}]`
    }
    if (dialogMode.value === 'create') await saveSpu(payload)
    else await updateSpu(payload)
    ElMessage.success(dialogMode.value === 'create' ? '新增成功' : '更新成功')
    dialogVisible.value = false
    loadPage()
  } finally {
    submitting.value = false
  }
}

function handleSelectionChange(rows) { selectedRows.value = rows }
function tableRowClassName({ row }) { return row.status === 0 ? 'row-off-shelf' : '' }

/** 下架单个商品，二次确认后执行 */
async function handleShelfOff(row) {
  const name = row.name || ('商品#' + row.id)
  try { await ElMessageBox.confirm(`确定下架商品「${name}」？\n下架后用户将无法搜索和购买该商品。`, '下架确认', { type: 'warning' }) } catch (e) { return }
  try { await setSpuStatus(row.id, 0); ElMessage.success(`「${name}」已下架`); loadPage() }
  catch (e) { ElMessage.error((e?.response?.data?.msg) || e?.message || '下架失败') }
}

/** 上架单个商品，二次确认后执行 */
async function handleShelfOn(row) {
  const name = row.name || ('商品#' + row.id)
  try { await ElMessageBox.confirm(`确定上架商品「${name}」？`, '上架确认', { type: 'warning' }) } catch (e) { return }
  try { await setSpuStatus(row.id, 1); ElMessage.success(`「${name}」已上架`); loadPage() }
  catch (e) { ElMessage.error((e?.response?.data?.msg) || e?.message || '上架失败') }
}

/** 批量下架选中的商品，二次确认后执行 */
async function handleBatchShelfOff() {
  const names = selectedRows.value.map(r => r.name).join('、')
  try { await ElMessageBox.confirm(`确定批量下架以下 ${selectedRows.value.length} 件商品？\n${names}\n\n下架后用户将无法搜索和购买这些商品。`, '批量下架确认', { type: 'warning', confirmButtonText: '确定下架', cancelButtonText: '取消' }) } catch (e) { return }
  try {
    const ids = selectedRows.value.map(r => r.id)
    await batchSetSpuStatus(ids, 0)
    ElMessage.success(`已成功下架 ${selectedRows.value.length} 件商品`)
    selectedRows.value = []
    loadPage()
  } catch (e) { ElMessage.error((e?.response?.data?.msg) || e?.message || '批量下架失败') }
}

/** 批量上架选中的商品，二次确认后执行 */
async function handleBatchShelfOn() {
  const names = selectedRows.value.map(r => r.name).join('、')
  try { await ElMessageBox.confirm(`确定批量上架以下 ${selectedRows.value.length} 件商品？\n${names}`, '批量上架确认', { type: 'warning', confirmButtonText: '确定上架', cancelButtonText: '取消' }) } catch (e) { return }
  try {
    const ids = selectedRows.value.map(r => r.id)
    await batchSetSpuStatus(ids, 1)
    ElMessage.success(`已成功上架 ${selectedRows.value.length} 件商品`)
    selectedRows.value = []
    loadPage()
  } catch (e) { ElMessage.error((e?.response?.data?.msg) || e?.message || '批量上架失败') }
}

/** 删除商品，二次确认后执行 */
async function handleDelete(row) {
  try { await ElMessageBox.confirm(`确定删除商品「${row.name}」？`, '提示', { type: 'warning' }); await deleteSpu(row.id); ElMessage.success('已删除'); loadPage() } catch (e) {}
}

onMounted(() => { restoreSort(); loadMeta(); loadPage() })

// Import/Export
const exporting = ref(false); const importing = ref(false); const importVisible = ref(false)
const importFile = ref(null); const importResult = ref(null); const uploadRef = ref(null)

function openImport() { importVisible.value = true; importResult.value = null; importFile.value = null }
function onFileChange(file) { importFile.value = file.raw; importResult.value = null }
function onFileRemove() { importFile.value = null }

/** 下载商品导入模板 Excel 文件 */
async function downloadTemplate() {
  try {
    const resp = await fetch('/admin/spu/import-export/template', { headers: { token: localStorage.getItem('stellar_admin_token') || '' } })
    if (!resp.ok) throw new Error('下载失败')
    const blob = await resp.blob(); const url = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = url; a.download = '商品导入模板.xlsx'; a.click(); URL.revokeObjectURL(url)
    ElMessage.success('模板下载成功')
  } catch (e) { ElMessage.error('下载模板失败') }
}

/** 导出全部商品数据为 Excel 文件 */
async function handleExport() {
  exporting.value = true
  try {
    const resp = await fetch('/admin/spu/import-export/export', { headers: { token: localStorage.getItem('stellar_admin_token') || '' } })
    if (!resp.ok) throw new Error('导出失败')
    const blob = await resp.blob(); const url = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = url; a.download = '商品数据导出.xlsx'; a.click(); URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e) { ElMessage.error('导出失败') } finally { exporting.value = false }
}

/** 导入 Excel 文件批量创建商品和 SKU */
async function handleImport() {
  if (!importFile.value) { ElMessage.warning('请先选择文件'); return }
  importing.value = true; importResult.value = null
  try {
    const formData = new FormData(); formData.append('file', importFile.value)
    const token = localStorage.getItem('stellar_admin_token') || ''
    const resp = await fetch('/admin/spu/import-export/import', { method: 'POST', headers: { token }, body: formData })
    const json = await resp.json(); importResult.value = json?.data || json
    if (importResult.value?.success) {
      ElMessage.success(`导入成功：${importResult.value.newSpuCount} 个 SPU，${importResult.value.newSkuCount} 个 SKU`)
      loadPage(); importFile.value = null; uploadRef.value?.clearFiles()
    }
  } catch (e) { ElMessage.error('导入失败') } finally { importing.value = false }
}

function removeExistingDoc() { kbExistingDoc.value = null; kbExistingDocId.value = null }
function kbDocStatusText(s) { return { uploading: '上传中', parsing: '解析中', indexing: '向量化中', ready: '就绪', error: '失败' }[s] || s || '未知' }
function kbDocStatusTag(s) { return { ready: 'success', error: 'danger', parsing: 'warning', indexing: 'primary', uploading: 'info' }[s] || 'info' }
function humanFileSize(n) { if (!n) return '0 B'; const u = ['B', 'KB', 'MB', 'GB']; let i = 0; n = Number(n); while (n >= 1024 && i < u.length - 1) { n /= 1024; i++ }; return n.toFixed(i > 0 ? 1 : 0) + ' ' + u[i] }
function fmtTime(v) { return v ? new Date(v).toLocaleString('zh-CN', { hour12: false }) : '' }
</script>

<style scoped>
.mgmt-page { display: flex; flex-direction: column; gap: 16px; }
.panel { background: var(--bg-card); border: 1px solid var(--border-base); border-radius: var(--radius-lg); padding: 20px 24px; box-shadow: var(--shadow-sm); }
.panel-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.panel-actions { display: flex; gap: 8px; align-items: center; }
.panel-title { font-size: 18px; font-weight: 600; color: var(--text-primary); }
.filter-bar { margin-bottom: 16px; display: flex; flex-wrap: wrap; align-items: center; gap: 12px; background: var(--bg-card); border: 1px solid var(--border-base); border-radius: var(--radius-md); padding: 12px 16px; }
.batch-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; padding: 10px 16px; background: var(--brand-primary-soft); border: 1px solid var(--brand-primary); border-radius: var(--radius-md); }
.batch-tip { font-size: 14px; font-weight: 500; color: var(--brand-primary); flex: 1; }
.pagination-wrap { margin-top: 20px; display: flex; justify-content: flex-end; }
.img-placeholder { width: 48px; height: 48px; background: var(--bg-hover); color: var(--text-muted); display: flex; align-items: center; justify-content: center; border-radius: var(--radius-sm); font-size: 12px; }
.col-header-sort { display: inline-flex; align-items: center; gap: 4px; font-weight: 600; }
.sort-arrow { display: inline-block; color: var(--brand-primary); font-size: 10px; line-height: 1; padding: 2px 4px; border-radius: var(--radius-sm); background: var(--brand-primary-soft); transform: translateY(-1px); }
.sort-arrow.asc { color: var(--status-success); }
.sort-arrow.desc { color: var(--status-danger); }
.sku-list { margin-top: 8px; }
.sku-item { border: 1px solid var(--border-base); border-radius: var(--radius-md); padding: 12px; margin-bottom: 12px; }
.sku-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.sku-index { font-size: 14px; font-weight: 500; color: var(--text-primary); }
.sku-fields { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
.sku-field { flex: 1; min-width: 140px; max-width: 200px; }
.hint-icon { display: inline-flex; align-items: center; justify-content: center; width: 18px; height: 18px; border-radius: 50%; background: var(--brand-primary-soft); color: var(--brand-primary); font-size: 12px; font-weight: 700; cursor: help; margin-left: 4px; line-height: 1; }
.spec-groups-panel { background: var(--bg-hover); border: 1px solid var(--border-base); border-radius: var(--radius-md); padding: 14px; }
.spec-group-row { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; flex-wrap: wrap; }
.spec-group-select { flex-shrink: 0; }
.spec-group-values { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; flex: 1; min-width: 200px; }
.spec-group-actions { display: flex; align-items: center; gap: 10px; margin-top: 4px; }
.gen-hint { color: var(--status-success); font-size: 13px; font-weight: 500; }
:deep(.el-dialog) { border-radius: var(--radius-lg); overflow: hidden; }
.kb-upload-row { display: flex; gap: 16px; align-items: flex-start; flex-wrap: wrap; }
.kb-params { display: flex; flex-direction: column; gap: 8px; min-width: 200px; }
.kb-param-item { display: flex; align-items: center; gap: 8px; }
.kb-param-label { font-size: 13px; color: var(--text-secondary); white-space: nowrap; width: 80px; }
.kb-preview-result { margin-top: 10px; border: 1px solid var(--border-base); border-radius: var(--radius-md); padding: 10px; max-height: 300px; overflow-y: auto; background: var(--bg-hover); }
.kb-preview-head { font-size: 13px; font-weight: 600; color: var(--text-primary); margin-bottom: 8px; display: flex; gap: 8px; align-items: baseline; }
.kb-preview-chunk { display: flex; gap: 8px; margin-bottom: 6px; border-top: 1px dashed var(--border-base); padding-top: 6px; }
.kb-preview-chunk-idx { font-size: 12px; color: var(--brand-primary); font-weight: 600; min-width: 28px; }
.kb-preview-chunk-text { font-size: 12px; color: var(--text-secondary); line-height: 1.5; word-break: break-all; }
.kb-existing-doc { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; padding: 12px 16px; background: var(--bg-hover); border: 1px solid var(--border-base); border-radius: var(--radius-md); }
.kb-existing-doc-info { display: flex; align-items: flex-start; gap: 10px; flex: 1; min-width: 0; }
.kb-existing-doc-detail { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.kb-existing-doc-name { font-size: 14px; font-weight: 500; color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.kb-existing-doc-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 2px; }
.kb-existing-doc-meta-item { font-size: 12px; color: var(--text-muted); margin-left: 8px; white-space: nowrap; }
:deep(.row-off-shelf) { --el-table-tr-bg-color: var(--bg-hover, #fafafa); color: var(--text-muted, #999); }
:deep(.row-off-shelf td) { color: var(--text-muted, #999); }
:deep(.row-off-shelf .el-image) { opacity: 0.6; }

/* ===== 图片上传 ===== */
.image-upload-section { width: 100%; }
.image-preview-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
}
.image-preview-item {
  position: relative;
  width: 100px;
  height: 100px;
  border-radius: var(--radius-md);
  overflow: hidden;
  border: 2px solid var(--border-base);
  transition: border-color 0.2s;
}
.image-preview-item.is-main { border-color: var(--brand-primary); }
.image-preview-item:hover { border-color: var(--brand-primary); }
.image-preview-img {
  width: 100%;
  height: 100%;
}
.image-preview-label {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 11px;
  text-align: center;
  padding: 2px 0;
}
.image-preview-remove {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 12px;
  opacity: 0;
  transition: opacity 0.2s;
}
.image-preview-item:hover .image-preview-remove { opacity: 1; }
.image-upload-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.upload-trigger {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 16px 24px;
  color: var(--text-secondary);
  font-size: 13px;
}
.upload-trigger em { color: var(--brand-primary); font-style: normal; }
.upload-hint { font-size: 11px; color: var(--text-muted); }
</style>
