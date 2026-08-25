<template>
  <div class="kb">
      <div class="toolbar panel">
        <div style="flex:1; display:flex; gap:8px; align-items:center;">
          <el-input
            v-model="kw" placeholder="按文件名搜索..."
            style="max-width: 300px;"
            :prefix-icon="Search"
            clearable
            @keyup.enter="reload"
            @clear="reload"
          />
          <el-select v-model="statusFilter" placeholder="状态筛选" clearable style="width:160px" @change="reload">
            <el-option value="" label="全部状态" />
            <el-option value="ready" label="就绪" />
            <el-option value="error" label="向量化失败" />
            <el-option value="parsing" label="解析中" />
            <el-option value="indexing" label="向量化中" />
            <el-option value="uploading" label="上传中" />
          </el-select>
        </div>
        <el-button type="primary" :icon="Upload" @click="openUploadDlg">上传文档</el-button>
        <el-button :icon="Refresh" @click="reload; loadStats()">刷新</el-button>
      </div>

      <div class="panel" style="margin-top:14px">
        <div class="panel-head">
          <span class="panel-title">知识库文档</span>
        </div>
        <div class="stats">
          <el-statistic title="文档总数" :value="stats.total_docs || 0" />
          <el-statistic title="总 Chunk 数" :value="stats.total_chunks || 0" />
          <el-statistic title="类型分布" :value="Object.keys(stats.by_ext || {}).length" />
          <el-statistic title="已就绪" :value="countReady" />
          <el-statistic title="失败待处理" :value="countError" />
        </div>

        <el-table :data="items" v-loading="loading" stripe style="margin-top:12px">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column label="文件名" min-width="260">
            <template #default="{row}">
              <div class="fn">
                <el-icon><Document /></el-icon>
                <span :title="row.filename">{{ row.filename }}</span>
                <el-tag size="small" style="margin-left:8px">{{ row.file_ext }}</el-tag>
                <el-tag v-if="row.tags" size="small" type="info" effect="plain" style="margin-left:6px">{{ row.tags }}</el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="大小" width="110" align="right">
            <template #default="{row}">{{ humanSize(row.file_size) }}</template>
          </el-table-column>
          <el-table-column label="Chunks" prop="chunk_count" width="90" align="center" />
          <el-table-column label="状态" width="120" align="center">
            <template #default="{row}">
              <el-tooltip v-if="row.status === 'error' && row.error_msg" :content="row.error_msg" placement="top">
                <el-tag :type="tagType(row.status)" size="small" effect="dark">{{ statusText(row.status) }}</el-tag>
              </el-tooltip>
              <el-tag v-else :type="tagType(row.status)" size="small" effect="dark">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="上传时间" width="180">
            <template #default="{row}">{{ fmt(row.created_at) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="280" align="center" fixed="right">
            <template #default="{row}">
              <el-button size="small" link type="primary" @click="viewChunks(row)">Chunks</el-button>
              <el-button size="small" link type="primary" @click="downloadFile(row)">下载</el-button>
              <el-button
                size="small" link type="warning"
                :disabled="!(row.status==='error' || row.status==='ready')"
                @click="reindexOne(row)">
                {{ row.status === 'error' ? '重新索引' : '重建索引' }}
              </el-button>
              <el-button size="small" link type="primary" @click="editTags(row)">编辑</el-button>
              <el-popconfirm title="删除后向量一并移除，确定？" @confirm="del(row)">
                <template #reference><el-button size="small" link type="danger">删除</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          style="margin-top: 14px; justify-content: flex-end; display:flex;"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total" v-model:current-page="page" v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          @current-change="reload"
          @size-change="reload"
        />
      </div>

      <el-dialog v-model="dlg" title="上传知识库文档" width="620px" :close-on-click-modal="false">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom:14px">
          支持 PDF / DOCX / TXT / MD / CSV，单文件 ≤ 20MB。上传后将自动向量化入库。
        </el-alert>
        <el-form label-width="96px">
          <el-form-item label="文件">
            <el-upload
              drag
              :auto-upload="false"
              :limit="1"
              :on-change="onUploadFileChange"
              accept=".pdf,.docx,.txt,.md,.csv"
            >
              <el-icon style="font-size: 32px"><UploadFilled /></el-icon>
              <div style="margin-top:8px">将文件拖到此处，或<em>点击选择</em></div>
              <template #tip>
                <div style="color: var(--text-muted); margin-top:6px;">
                  仅支持 1 次上传 1 份，如需多份请重复操作。
                </div>
              </template>
            </el-upload>
          </el-form-item>
          <el-form-item label="标签">
            <el-input v-model="tags" placeholder="逗号分隔，如: 手机,旗舰,影像" />
          </el-form-item>
          <el-divider content-position="left">分块参数（可选，上传时临时生效）</el-divider>
          <el-form-item label="Chunk 大小">
            <el-input-number v-model="chunkSize" :min="128" :max="4096" :step="64" style="width: 180px" />
            <span style="margin-left: 10px; color: var(--text-muted); font-size:12px;">
              默认 512 字符（越大块数越少，越小块语义越碎）
            </span>
          </el-form-item>
          <el-form-item label="Overlap">
            <el-input-number v-model="chunkOverlap" :min="0" :max="1024" :step="16" style="width: 180px" />
            <span style="margin-left: 10px; color: var(--text-muted); font-size:12px;">
              默认 64 字符，相邻 chunk 重叠长度，避免切分丢失语义
            </span>
          </el-form-item>
          <el-form-item label=" ">
            <el-button :disabled="!upFile || previewing" :loading="previewing" @click="doPreview">
              预览切分效果（不上传）
            </el-button>
          </el-form-item>
        </el-form>

        <el-progress
          v-if="uploading"
          style="margin-top: 8px;"
          :percentage="uploadPct"
          :status="uploadPct===100 ? 'success' : undefined"
        />
        <el-card v-if="previewResult" class="preview-card" shadow="never">
          <template #header>
            <div style="display:flex; justify-content:space-between; align-items:center;">
              <span>切分预览（共 {{ previewResult.total_chunks }} 个 Chunk，显示前 {{ previewResult.chunks.length }} 个）</span>
              <el-button size="small" link @click="previewResult = null">关闭</el-button>
            </div>
          </template>
          <el-scrollbar max-height="260px">
            <div v-for="c in previewResult.chunks" :key="c.index" class="chunk-item">
              <div class="chunk-head">
                <el-tag size="small">#{{ c.index }}</el-tag>
                <span class="chunk-meta">{{ c.char_count }} 字</span>
              </div>
              <div class="chunk-body">{{ c.content }}</div>
            </div>
          </el-scrollbar>
        </el-card>

        <template #footer>
          <el-button @click="closeUploadDlg">取消</el-button>
          <el-button type="primary" :loading="uploading" :disabled="!upFile" @click="doUpload">
            {{ uploading ? '上传并入库中...' : '上传并入库' }}
          </el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="editDlg" title="编辑文档元数据" width="420px">
        <el-form label-width="80px">
          <el-form-item label="文件名"><el-input v-model="editRow.filename" /></el-form-item>
          <el-form-item label="标签"><el-input v-model="editRow.tags" placeholder="逗号分隔，用于过滤检索范围" /></el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="editDlg=false">取消</el-button>
          <el-button type="primary" @click="doEdit">保存</el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="chunksDlg" :title="chunksDlgTitle" width="860px" top="8vh">
        <div v-if="chunksLoading" style="text-align:center; padding: 40px 0;">
          <el-icon class="is-loading" style="font-size:32px; color:var(--brand-primary)"><Loading /></el-icon>
          <div style="margin-top:10px; color:var(--text-muted);">加载中...</div>
        </div>
        <template v-else>
          <div style="margin-bottom: 10px;">
            <el-tag>共 {{ chunksTotal }} 个 Chunk</el-tag>
            <span style="margin-left: 12px; color: var(--text-muted);">
              文件名: {{ chunksFilename }}
            </span>
          </div>
          <el-empty v-if="chunksList.length === 0" description="该文档暂无 Chunk 数据（可能未入库或已清除）" />
          <el-scrollbar v-else max-height="60vh">
            <div v-for="c in chunksList" :key="c.id || c.index" class="chunk-item">
              <div class="chunk-head">
                <el-tag size="small">Chunk #{{ c.index }}</el-tag>
                <span class="chunk-meta">{{ c.char_count }} 字</span>
                <el-tag v-if="c.page != null" size="small" type="info" effect="plain" style="margin-left:6px;">
                  第 {{ c.page }} 页
                </el-tag>
              </div>
              <div class="chunk-body">{{ c.content }}</div>
            </div>
          </el-scrollbar>
        </template>
      </el-dialog>

    </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Search, Upload, Refresh, Document, UploadFilled, Loading } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  apiKbList, apiKbDelete, apiKbUpdate, apiKbUpload, apiKbStats,
  apiKbPreview, apiKbReindex, apiKbDownload, apiKbChunks,
} from '@/api/rag'

const kw = ref('')
const statusFilter = ref('')
const page = ref(1); const pageSize = ref(10); const total = ref(0)
const items = ref<any[]>([]); const loading = ref(false)
const stats = reactive<any>({})
const countReady = computed(() => items.value.filter((i: any) => i.status === 'ready').length)
const countError = computed(() => items.value.filter((i: any) => i.status === 'error').length)

onMounted(() => { reload(); loadStats() })

async function reload() {
  loading.value = true
  try {
    const params: Record<string, any> = { page: page.value, page_size: pageSize.value, keyword: kw.value }
    if (statusFilter.value) params.status = statusFilter.value
    const r = await apiKbList(params)
    items.value = r.data.items as any[]
    total.value = r.data.total as number
  } finally { loading.value = false }
}
async function loadStats() {
  const r = await apiKbStats()
  Object.assign(stats, r.data || {})
}

function humanSize(n: any) {
  if (!n) return '0B'
  const u = ['B','KB','MB','GB']; let i=0; n = Number(n)
  while (n >= 1024 && i < u.length-1) { n/=1024; i++ }
  return n.toFixed(i>0?1:0) + u[i]
}
function fmt(v: any) { return v ? new Date(v).toLocaleString('zh-CN', {hour12:false}) : '' }
function statusText(s: any) {
  return ({uploading:'上传中', parsing:'解析中', indexing:'向量化中', ready:'就绪', error:'失败'} as Record<string, string>)[s] || s
}
function tagType(s: any) {
  return ({ready:'success', error:'danger', parsing:'warning', indexing:'primary', uploading:'info'} as Record<string, string>)[s] || 'info'
}

const dlg = ref(false); const uploading = ref(false); const uploadPct = ref(0)
const upFile = ref<any>(null); const tags = ref('')
const chunkSize = ref(512); const chunkOverlap = ref(64)
const previewing = ref(false); const previewResult = ref<any>(null)

function openUploadDlg() {
  dlg.value = true
  upFile.value = null; tags.value = ''
  uploadPct.value = 0; previewResult.value = null
  chunkSize.value = 512; chunkOverlap.value = 64
}
function closeUploadDlg() {
  if (uploading.value) {
    ElMessage.warning('上传进行中，无法关闭')
    return
  }
  dlg.value = false
}
function onUploadFileChange(f: any) {
  upFile.value = f?.raw || null
  previewResult.value = null
}
async function doPreview() {
  if (!upFile.value) return ElMessage.warning('请先选择文件')
  previewing.value = true
  try {
    const fd = new FormData()
    fd.append('file', upFile.value)
    fd.append('chunk_size', chunkSize.value as any)
    fd.append('chunk_overlap', chunkOverlap.value as any)
    fd.append('limit', 50 as any)
    const r = await apiKbPreview(fd)
    previewResult.value = r.data
    ElMessage.success(`切分预览完成，共 ${r.data.total_chunks} 个 Chunk`)
  } catch (e: any) {
  } finally { previewing.value = false }
}
async function doUpload() {
  if (!upFile.value) return ElMessage.warning('请选择文件')
  uploading.value = true
  uploadPct.value = 0
  try {
    const fd = new FormData()
    fd.append('file', upFile.value)
    fd.append('tags', tags.value)
    const r = await apiKbUpload(fd, (p: any) => {
      uploadPct.value = p
    })
    const doc = r.data
    uploadPct.value = 100
    if (doc.indexed) {
      ElMessage.success(`上传并入库成功！切分 ${doc.chunk_count} 个 Chunk`)
      closeUploadDlg()
    } else {
      const err = doc.error_msg || '未知错误'
      ElMessageBox.alert(
        `文件已上传但向量化失败，您可点击"操作 → 重新索引"再次尝试。\n\n错误详情：\n${err}`,
        '上传成功但入库失败',
        { type: 'warning', confirmButtonText: '我知道了', dangerouslyUseHTMLString: false }
      )
      closeUploadDlg()
    }
    reload(); loadStats()
  } catch (e: any) {
  } finally {
    uploading.value = false
  }
}

const editDlg = ref(false)
const editRow = reactive<any>({ id: null, filename: '', tags: '' })
function editTags(row: any) {
  editRow.id = row.id; editRow.filename = row.filename; editRow.tags = row.tags || ''
  editDlg.value = true
}
async function doEdit() {
  await apiKbUpdate(editRow.id, { filename: editRow.filename, tags: editRow.tags })
  ElMessage.success('已保存')
  editDlg.value = false; reload()
}
async function del(row: any) {
  await apiKbDelete(row.id)
  ElMessage.success('已删除')
  reload(); loadStats()
}

async function reindexOne(row: any) {
  try {
    await ElMessageBox.confirm(
      `确定要"${row.status === 'error' ? '重新索引' : '重建索引'}"文档「${row.filename}」吗？\n旧的向量会被清除。`,
      '确认',
      { type: 'warning' }
    )
  } catch { return }
  const loadingMask = ElMessage({ message: '索引处理中，请稍候...', type: 'info', duration: 0 })
  try {
    const r = await apiKbReindex(row.id)
    const doc = r.data
    loadingMask.close()
    if (doc.indexed) {
      ElMessage.success(`重建索引成功，切分 ${doc.chunk_count} 个 Chunk`)
    } else {
      const err = doc.error_msg || '未知错误'
      ElMessageBox.alert(`索引失败：\n${err}`, '错误', { type: 'error' })
    }
    reload(); loadStats()
  } catch (e: any) {
    loadingMask.close()
  }
}

async function downloadFile(row: any) {
  try {
    const res = await apiKbDownload(row.id)
    const blob = res instanceof Blob ? res : new Blob([res])
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = row.filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    setTimeout(() => URL.revokeObjectURL(a.href), 1000)
  } catch (e: any) {
    ElMessage.error(e.message || '下载失败')
  }
}

const chunksDlg = ref(false)
const chunksDlgTitle = ref('')
const chunksFilename = ref('')
const chunksTotal = ref(0)
const chunksList = ref<any[]>([])
const chunksLoading = ref(false)
async function viewChunks(row: any) {
  chunksDlgTitle.value = `文档 Chunk 预览 —— ${row.filename}`
  chunksFilename.value = row.filename
  chunksList.value = []
  chunksTotal.value = 0
  chunksDlg.value = true
  chunksLoading.value = true
  try {
    const r = await apiKbChunks(row.id, 200)
    chunksTotal.value = r.data.total
    chunksList.value = r.data.chunks || []
  } catch (e: any) {
  } finally { chunksLoading.value = false }
}
</script>

<style scoped lang="scss">
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}
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
.stats {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
  padding: 8px 4px 4px;
  :deep(.el-statistic__head) {
    color: var(--text-muted);
    font-size: 13px;
  }
  :deep(.el-statistic__content) {
    color: var(--text-primary);
    font-size: 20px;
    font-weight: 600;
  }
}
.fn {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-primary);
  .el-icon {
    color: var(--brand-primary);
    flex-shrink: 0;
  }
  > span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 280px;
  }
}
.preview-card {
  margin-top: 12px;
  background: var(--bg-hover);
  border: 1px dashed var(--border-base);
  border-radius: var(--radius-md);
}
.chunk-item {
  padding: 10px 12px;
  border: 1px solid var(--border-base);
  border-radius: var(--radius-md);
  margin-bottom: 10px;
  background: var(--bg-card);
}
.chunk-head {
  display: flex;
  align-items: center;
  margin-bottom: 6px;
  gap: 4px;
}
.chunk-meta {
  font-size: 12px;
  color: var(--text-muted);
  margin-left: 8px;
}
.chunk-body {
  color: var(--text-primary);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 160px;
  overflow: auto;
}

:deep(.el-dialog) {
  border-radius: var(--radius-lg);
  overflow: hidden;
}
</style>
