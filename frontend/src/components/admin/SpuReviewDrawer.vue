<template>
  <el-drawer
    v-model="visible"
    direction="rtl"
    :size="520"
    :with-header="false"
    class="srd-drawer"
    append-to-body
  >
    <div class="srd">
      <!-- ===== 头部：商品 + 汇总 ===== -->
      <div class="srd-head">
        <el-image v-if="cover" :src="cover" fit="cover" class="srd-thumb" />
        <div class="srd-head-text">
          <div class="srd-name" :title="spu?.name">{{ spu?.name || '商品评价' }}</div>
          <div class="srd-meta">
            共 {{ spu?.commentCount ?? total }} 条评价
            <template v-if="unrepliedTotal > 0">
              · <span class="srd-todo">未回复 {{ unrepliedTotal }} 条</span>
            </template>
          </div>
        </div>
        <el-button text class="srd-close" aria-label="关闭" @click="visible = false">
          <el-icon :size="16"><Close /></el-icon>
        </el-button>
      </div>

      <!-- ===== 筛选 ===== -->
      <div class="srd-filter">
        <el-radio-group v-model="tab" size="small" @change="onTabChange">
          <el-radio-button value="all">全部</el-radio-button>
          <el-radio-button value="unreplied">未回复</el-radio-button>
          <el-radio-button value="bad">差评</el-radio-button>
          <el-radio-button value="hidden">已隐藏</el-radio-button>
        </el-radio-group>
      </div>

      <!-- ===== 列表 ===== -->
      <div v-loading="loading" class="srd-list">
        <div v-if="!loading && !list.length" class="srd-empty">
          <el-icon :size="22"><ChatLineSquare /></el-icon>
          <span>{{ emptyText }}</span>
        </div>

        <div v-for="row in list" :key="row.id" class="srd-item">
          <div class="srd-item-head">
            <el-avatar :size="26" :src="row.avatar || undefined">
              {{ (row.username || 'U').charAt(0).toUpperCase() }}
            </el-avatar>
            <span class="srd-user">{{ row.username || '匿名用户' }}</span>
            <el-rate :model-value="row.rating || 0" disabled size="small" class="srd-rate" />
            <el-tag v-if="row.status === 0" size="small" type="info">已隐藏</el-tag>
            <span class="srd-time">{{ fmtTime(row.createTime) }}</span>
          </div>

          <div
            class="srd-content"
            :class="{ 'is-expanded': expanded.has(row.id) }"
            :title="expanded.has(row.id) ? '' : '点击展开全文'"
            @click="toggleExpand(row.id)"
          >{{ row.content || '（该用户未填写文字评价）' }}</div>

          <div v-if="picsOf(row).length" class="srd-pics">
            <el-image
              v-for="(p, i) in picsOf(row).slice(0, 3)"
              :key="i"
              :src="p"
              :preview-src-list="picsOf(row)"
              :initial-index="i"
              preview-teleported
              fit="cover"
              class="srd-pic"
            />
            <span v-if="picsOf(row).length > 3" class="srd-pic-more">+{{ picsOf(row).length - 3 }}</span>
          </div>

          <div v-if="row.reply" class="srd-reply">
            <span class="srd-reply-label">商家回复</span>
            <span class="srd-reply-text">{{ row.reply }}</span>
            <span v-if="row.replyTime" class="srd-reply-time">{{ fmtTime(row.replyTime) }}</span>
          </div>

          <div v-if="canOperate" class="srd-actions">
            <el-button link type="primary" size="small" @click="openReply(row)">
              {{ row.reply ? '修改回复' : '回复' }}
            </el-button>
            <el-button
              v-if="row.status === 1"
              link type="warning" size="small"
              @click="setStatus(row, 0)"
            >隐藏</el-button>
            <el-button
              v-else
              link type="success" size="small"
              @click="setStatus(row, 1)"
            >显示</el-button>
          </div>
        </div>
      </div>

      <div v-if="total > pageSize" class="srd-foot">
        <el-pagination
          small
          layout="prev, pager, next"
          :current-page="page"
          :page-size="pageSize"
          :total="total"
          @current-change="onPageChange"
        />
      </div>
    </div>
  </el-drawer>

  <!-- ===== 回复弹窗 ===== -->
  <el-dialog
    v-model="replyVisible"
    :title="replyTarget.reply ? '修改回复' : '回复评价'"
    width="480px"
    append-to-body
  >
    <div class="srd-quote">
      <div class="srd-quote-user">{{ replyTarget.username || '匿名用户' }} · {{ fmtTime(replyTarget.createTime) }}</div>
      <div class="srd-quote-text">{{ replyTarget.content || '（该用户未填写文字评价）' }}</div>
    </div>
    <el-input
      v-model="replyText"
      type="textarea"
      :rows="3"
      maxlength="500"
      show-word-limit
      placeholder="回复会展示在商品详情页该条评价下方"
      style="margin-top: 12px"
    />
    <template #footer>
      <el-button @click="replyVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitReply">提交</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * 商品评价抽屉：商品视角查看/回复/隐藏某个 SPU 的评价。
 * 与「评价管理」页的分工：本组件只服务单个商品（带 spuId 查询），
 * 全站的待处理队列仍由 /admin/review 页面承担，两边复用同一组接口。
 */
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Close, ChatLineSquare } from '@element-plus/icons-vue'
import { adminRequest } from '@/api/request'
import { useAdminStore } from '@/stores/admin'

type TabKey = 'all' | 'unreplied' | 'bad' | 'hidden'

const props = defineProps<{ modelValue: boolean; spu: any }>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'changed'): void
}>()

const adminStore = useAdminStore()
/** 评价接口限定 admin/运营/客服（后端 @RequireRole({1,2,3})），财务只读不可操作 */
const canOperate = computed(() => [1, 2, 3].includes(Number(adminStore.role)))

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v)
})

/** 筛选项 → 后端参数：未回复/已隐藏/差评都由接口过滤，不依赖前端切页 */
const FILTERS: Record<TabKey, Record<string, any>> = {
  all: {},
  unreplied: { hasReply: false },
  bad: { maxRating: 2 },
  hidden: { status: 0 }
}

const tab = ref<TabKey>('all')
const page = ref(1)
const pageSize = ref(10)
const list = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const unrepliedTotal = ref(0)
const expanded = ref<Set<any>>(new Set())

const cover = computed(() => props.spu?.mainImage || props.spu?.image || '')
const emptyText = computed(() => {
  const map: Record<TabKey, string> = {
    all: '该商品暂无评价',
    unreplied: '没有待回复的评价',
    bad: '没有 1-2 星的差评',
    hidden: '没有被隐藏的评价'
  }
  return map[tab.value]
})

/** 图片字段是逗号分隔的 URL 串 */
function picsOf(row: any): string[] {
  const raw = row?.pics
  if (!raw) return []
  return String(raw).split(',').map((s) => s.trim()).filter(Boolean)
}

function fmtTime(v?: string) {
  if (!v) return ''
  return String(v).replace('T', ' ').slice(0, 16)
}

function toggleExpand(id: any) {
  const next = new Set(expanded.value)
  next.has(id) ? next.delete(id) : next.add(id)
  expanded.value = next
}

/** 拉当前筛选条件下的分页数据 */
async function fetchList() {
  if (!props.spu?.id) return
  loading.value = true
  try {
    const res: any = await adminRequest({
      url: '/admin/review/page',
      method: 'get',
      params: {
        spuId: props.spu.id,
        page: page.value,
        pageSize: pageSize.value,
        ...FILTERS[tab.value]
      }
    })
    const d = res?.data || res || {}
    list.value = d.records || d.list || []
    total.value = d.total || 0
  } catch (e: any) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 头部「未回复 N 条」：单独取一次未回复的总数，pageSize 压到 1 只为了拿 total */
async function fetchUnrepliedCount() {
  if (!props.spu?.id) return
  try {
    const res: any = await adminRequest({
      url: '/admin/review/page',
      method: 'get',
      params: { spuId: props.spu.id, page: 1, pageSize: 1, hasReply: false }
    })
    unrepliedTotal.value = (res?.data || res || {}).total || 0
  } catch (e: any) {
    unrepliedTotal.value = 0
  }
}

function onTabChange() {
  page.value = 1
  expanded.value = new Set()
  fetchList()
}

function onPageChange(p: number) {
  page.value = p
  fetchList()
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    tab.value = 'all'
    page.value = 1
    expanded.value = new Set()
    list.value = []
    total.value = 0
    unrepliedTotal.value = 0
    fetchList()
    fetchUnrepliedCount()
  },
  { immediate: true }
)

/* ===== 回复 ===== */
const replyVisible = ref(false)
const submitting = ref(false)
const replyTarget = reactive<any>({})
const replyText = ref('')

function openReply(row: any) {
  Object.assign(replyTarget, row)
  replyText.value = row.reply || ''
  replyVisible.value = true
}

async function submitReply() {
  const text = replyText.value.trim()
  if (!text) {
    ElMessage.warning('请填写回复内容')
    return
  }
  submitting.value = true
  try {
    await adminRequest({
      url: `/admin/review/${replyTarget.id}/reply`,
      method: 'post',
      data: { reply: text }
    })
    ElMessage.success('已回复')
    replyVisible.value = false
    emit('changed')
    fetchList()
    fetchUnrepliedCount()
  } catch (e: any) {
    ElMessage.error('回复失败')
  } finally {
    submitting.value = false
  }
}

/* ===== 隐藏 / 显示 =====
 * 后端会同步调整商品 comment_count（hide -1 / show +1），
 * 所以必须通知父级在抽屉关闭后刷新商品列表，否则评价数是旧的。 */
async function setStatus(row: any, status: number) {
  try {
    await adminRequest({
      url: `/admin/review/${row.id}/${status === 0 ? 'hide' : 'show'}`,
      method: 'post'
    })
    ElMessage.success(status === 0 ? '已隐藏' : '已显示')
    emit('changed')
    fetchList()
    fetchUnrepliedCount()
  } catch (e: any) {
    ElMessage.error('操作失败')
  }
}
</script>

<style scoped>
:deep(.el-drawer__body) {
  padding: 0;
  overflow: hidden;
}
.srd {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-card);
}

/* ===== 头部 ===== */
.srd-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 18px;
  border-bottom: 1px solid var(--border-base);
  flex-shrink: 0;
}
.srd-thumb {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-base);
  flex-shrink: 0;
}
.srd-head-text { flex: 1; min-width: 0; }
.srd-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.srd-meta { margin-top: 4px; font-size: 12px; color: var(--text-muted); }
.srd-todo { color: var(--el-color-warning, #e6a23c); }
.srd-close { padding: 4px; color: var(--text-muted); }

/* ===== 筛选 ===== */
.srd-filter {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 18px;
  border-bottom: 1px solid var(--border-base);
  flex-shrink: 0;
}

/* ===== 列表 ===== */
.srd-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 4px 18px 12px;
}
.srd-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 64px 0;
  color: var(--text-muted);
  font-size: 13px;
}
.srd-item {
  padding: 14px 0;
  border-bottom: 1px solid var(--border-base);
}
.srd-item:last-child { border-bottom: none; }
.srd-item-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}
.srd-user { color: var(--text-primary); font-size: 13px; }
.srd-rate { --el-rate-disabled-void-color: var(--border-base); }
.srd-time { margin-left: auto; color: var(--text-muted); }
.srd-content {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-primary);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  cursor: pointer;
  word-break: break-word;
}
.srd-content.is-expanded {
  -webkit-line-clamp: unset;
  display: block;
}
.srd-pics {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}
.srd-pic {
  width: 64px;
  height: 64px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-base);
  cursor: zoom-in;
}
.srd-pic-more { font-size: 12px; color: var(--text-muted); }
.srd-reply {
  margin-top: 10px;
  padding: 8px 12px;
  background: var(--bg-hover);
  border-radius: var(--radius-sm);
  font-size: 12px;
  line-height: 1.6;
}
.srd-reply-label {
  color: var(--brand-primary);
  margin-right: 6px;
  font-weight: 500;
}
.srd-reply-text { color: var(--text-secondary); }
.srd-reply-time { margin-left: 8px; color: var(--text-muted); }
.srd-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 10px;
}
.srd-foot {
  display: flex;
  justify-content: center;
  padding: 10px 0 14px;
  border-top: 1px solid var(--border-base);
  flex-shrink: 0;
}

/* ===== 回复弹窗内的引用块 ===== */
.srd-quote {
  padding: 10px 12px;
  background: var(--bg-hover);
  border-radius: var(--radius-sm);
  font-size: 12px;
  line-height: 1.6;
}
.srd-quote-user { color: var(--text-muted); margin-bottom: 4px; }
.srd-quote-text { color: var(--text-primary); word-break: break-word; }
</style>
