<template>
  <div class="aftersale-list-page">
    <main class="container main-content" v-loading="loading">
      <div class="page-header">
        <h2>我的售后</h2>
      </div>

      <div v-if="list.length === 0 && !loading" class="empty">
        <el-empty description="暂无售后记录" />
      </div>

      <div v-for="item in list" :key="item.id" class="card" @click="viewDetail(item)">
        <div class="card-head">
          <span class="as-no">售后单号 AS{{ item.id }}</span>
          <el-tag :type="statusTag(item.status)" size="small">{{ item.statusText }}</el-tag>
        </div>
        <div class="card-body">
          <img :src="item.spuImage || __PH" class="thumb" onerror="this.src=window.__PH;this.onerror=null" />
          <div class="item-info">
            <div class="item-name">{{ item.spuName || '商品' }}</div>
            <div class="item-spec">{{ item.skuSpecs || '默认规格' }}</div>
            <div class="item-meta">
              <span>{{ item.typeText }}</span>
              <span class="sep">|</span>
              <span>退款 ¥{{ Number(item.amount || 0).toFixed(2) }}</span>
            </div>
          </div>
          <div class="arrow"><el-icon><ArrowRight /></el-icon></div>
        </div>
        <div class="card-foot">
          <span>申请时间：{{ item.createTime || '-' }}</span>
          <template v-if="item.status === AFTER_SALE_STATUS.APPLIED || item.status === AFTER_SALE_STATUS.AUDITING">
            <el-button size="small" text type="danger" @click.stop="cancelItem(item)">取消申请</el-button>
          </template>
          <template v-if="item.status === AFTER_SALE_STATUS.RETURNING">
            <el-button size="small" type="primary" @click.stop="openReturnDialog(item)">填写物流</el-button>
          </template>
        </div>
      </div>

      <div class="pagination-wrapper" v-if="total > pageSize">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          @current-change="loadList"
        />
      </div>
    </main>

    <!-- 退货物流弹窗 -->
    <el-dialog v-model="returnVisible" title="填写退货物流" width="420px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="快递单号" required>
          <el-input v-model="returnTracking" placeholder="请输入退货快递单号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="returnVisible = false">取消</el-button>
        <el-button type="primary" :loading="returnSubmitting" @click="submitReturn">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listAfterSales, cancelAfterSale, submitReturnTracking } from '@/api/mall'
import { AFTER_SALE_STATUS } from '@/constants/order'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowRight } from '@element-plus/icons-vue'

const __PH = window.__PH
const router = useRouter()

const loading = ref(false)
const list = ref<any[]>([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 退货物流
const returnVisible = ref(false)
const returnItem = ref<any>(null)
const returnTracking = ref('')
const returnSubmitting = ref(false)

const STATUS_TAG_MAP: Record<string, any> = {
  [AFTER_SALE_STATUS.APPLIED]: 'warning',
  [AFTER_SALE_STATUS.AUDITING]: 'warning',
  [AFTER_SALE_STATUS.RETURNING]: 'primary',
  [AFTER_SALE_STATUS.REFUNDING]: 'danger',
  [AFTER_SALE_STATUS.COMPLETED]: 'success',
  [AFTER_SALE_STATUS.REJECTED]: 'info',
  [AFTER_SALE_STATUS.CANCELLED]: 'info'
}

function statusTag(s: any) { return STATUS_TAG_MAP[s] || 'info' }

/**
 * 加载售后列表数据，支持分页
 */
async function loadList() {
  loading.value = true
  try {
    const res = await listAfterSales({ page: page.value, pageSize: pageSize.value })
    const d: any = res || {}
    list.value = d.records || d.list || []
    total.value = d.total || 0
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '加载失败')
  } finally {
    loading.value = false
  }
}

/**
 * 跳转到售后详情页
 * @param {Object} item - 售后记录项
 */
function viewDetail(item: any) {
  router.push(`/aftersale/${item.id}`)
}

/**
 * 取消指定售后申请，弹出确认框后调用取消接口
 * @param {Object} item - 售后记录项
 */
async function cancelItem(item: any) {
  try {
    await ElMessageBox.confirm('确定取消该售后申请吗？', '提示', { type: 'warning' })
    await cancelAfterSale(item.id)
    ElMessage.success('已取消')
    loadList()
  } catch (e: any) { /* 用户取消 */ }
}

/**
 * 打开退货物流填写弹窗
 * @param {Object} item - 售后记录项
 */
function openReturnDialog(item: any) {
  returnItem.value = item
  returnTracking.value = ''
  returnVisible.value = true
}

/**
 * 提交退货物流单号，校验后调用接口并刷新列表
 */
async function submitReturn() {
  if (!returnTracking.value.trim()) {
    ElMessage.warning('请输入快递单号')
    return
  }
  returnSubmitting.value = true
  try {
    await submitReturnTracking({ id: returnItem.value.id, returnTracking: returnTracking.value.trim() })
    ElMessage.success('退货物流已提交')
    returnVisible.value = false
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '提交失败')
  } finally {
    returnSubmitting.value = false
  }
}

onMounted(loadList)
</script>

<style scoped>
.aftersale-list-page { min-height: 100vh; padding-bottom: 60px; }
.container { max-width: 900px; margin: 0 auto; padding: 0 20px; }
.main-content { padding: 32px 20px 60px; }
.page-header { margin-bottom: 20px; }
.page-header h2 { font-size: 22px; color: var(--text-primary); font-weight: 600; }
.empty { background: var(--bg-card); border-radius: var(--radius-lg); padding: 80px 0; border: 1px solid var(--border-base); }

.card {
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-lg);
  margin-bottom: 14px;
  cursor: pointer;
  transition: box-shadow .2s;
  box-shadow: var(--shadow-sm);
}
.card:hover { box-shadow: var(--shadow-md); }

.card-head {
  display: flex; justify-content: space-between; align-items: center;
  padding: 14px 20px;
  background: var(--bg-hover);
  border-bottom: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
}
.as-no { color: var(--text-secondary); font-size: 14px; font-family: 'Courier New', monospace; }

.card-body {
  display: flex; align-items: center; padding: 14px 20px;
}
.thumb { width: 64px; height: 64px; border-radius: var(--radius-sm); object-fit: cover; margin-right: 14px; }
.item-info { flex: 1; }
.item-name { color: var(--text-primary); font-size: 14px; font-weight: 500; margin-bottom: 4px; }
.item-spec { color: var(--text-muted); font-size: 12px; }
.item-meta { color: var(--text-secondary); font-size: 13px; margin-top: 6px; }
.sep { margin: 0 8px; color: var(--text-muted); }
.arrow { color: var(--text-muted); }

.card-foot {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 20px;
  border-top: 1px solid var(--border-subtle);
  color: var(--text-muted); font-size: 13px;
}
.pagination-wrapper { display: flex; justify-content: center; margin-top: 24px; }
</style>
