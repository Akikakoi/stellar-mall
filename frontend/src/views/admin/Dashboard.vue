<template>
  <div class="dashboard-page">
    <div class="stat-strip">
      <div class="stat-cell clickable" @click="goOrders('PAID')">
        <div class="stat-label">待处理订单</div>
        <div class="stat-value">{{ stats.pendingOrders || 0 }}</div>
        <div class="stat-hint">{{ (stats.pendingOrders || 0) > 0 ? '待发货' : '暂无积压' }}</div>
      </div>
      <div class="stat-cell clickable" @click="goOrdersToday()">
        <div class="stat-label">今日订单</div>
        <div class="stat-value">{{ stats.todayOrders || 0 }}</div>
        <div class="stat-hint">查看今日明细</div>
      </div>
      <div class="stat-cell">
        <div class="stat-label">今日销售额</div>
        <div class="stat-value">¥{{ Number(stats.todaySales || 0).toLocaleString() }}</div>
        <div class="stat-hint">实时统计</div>
      </div>
      <div class="stat-cell clickable" @click="router.push('/admin/inventory')">
        <div class="stat-label">低库存预警</div>
        <div class="stat-value" :class="{ 'value-warn': (stats.lowStockCount || 0) > 0 }">{{ stats.lowStockCount || 0 }}</div>
        <div class="stat-hint">{{ (stats.lowStockCount || 0) > 0 ? '需要补货' : '库存健康' }}</div>
      </div>
    </div>

    <div class="grid-row">
      <div class="panel chart-panel">
        <div class="panel-head"><span class="panel-title">近7天有效订单量</span></div>
        <div ref="orderChartRef" v-show="orderTrendHasData" style="height: 240px;"></div>
        <el-empty v-if="!orderTrendHasData" description="暂无订单数据" :image-size="80" />
      </div>
      <div class="panel chart-panel">
        <div class="panel-head"><span class="panel-title">近7天销售额趋势</span></div>
        <div ref="salesChartRef" v-show="salesTrendHasData" style="height: 240px;"></div>
        <el-empty v-if="!salesTrendHasData" description="暂无销售数据" :image-size="80" />
      </div>
    </div>

    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">用户行为（近 7 天）</span>
        <span class="panel-sub">来自前端埋点，用于观察搜索与浏览热点</span>
      </div>
      <div class="behavior-summary">
        <div class="behavior-num">
          <span class="behavior-label">事件总量</span>
          <span class="behavior-value">{{ stats.behaviorEvents || 0 }}</span>
        </div>
        <div class="behavior-num">
          <span class="behavior-label">活跃访客</span>
          <span class="behavior-value">{{ stats.behaviorVisitors || 0 }}</span>
        </div>
      </div>
      <div class="behavior-grid">
        <div class="behavior-block">
          <div class="behavior-title">热门事件</div>
          <div v-if="behaviorTopEvents.length" class="behavior-tags">
            <el-tag v-for="e in behaviorTopEvents" :key="e.eventType" size="small" effect="plain">
              {{ eventTypeText(e.eventType) }} {{ e.count }}
            </el-tag>
          </div>
          <div v-else class="behavior-empty">暂无事件</div>
        </div>
        <div class="behavior-block">
          <div class="behavior-title">热门搜索</div>
          <div v-if="behaviorTopKeywords.length" class="behavior-list">
            <div v-for="(k, i) in behaviorTopKeywords" :key="k.keyword" class="behavior-row">
              <span class="behavior-rank">{{ i + 1 }}</span>
              <span class="behavior-name">{{ k.keyword }}</span>
              <span class="behavior-count">{{ k.count }}</span>
            </div>
          </div>
          <div v-else class="behavior-empty">暂无搜索</div>
        </div>
        <div class="behavior-block">
          <div class="behavior-title">热门浏览商品</div>
          <div v-if="behaviorTopProducts.length" class="behavior-list">
            <div v-for="(p, i) in behaviorTopProducts" :key="p.spuId" class="behavior-row">
              <span class="behavior-rank">{{ i + 1 }}</span>
              <span class="behavior-name">{{ p.name }}</span>
              <span class="behavior-count">{{ p.count }}</span>
            </div>
          </div>
          <div v-else class="behavior-empty">暂无浏览</div>
        </div>
      </div>
    </div>

    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">待处理事项</span>
        <span class="panel-sub">按优先级排列，点击直达处理页</span>
      </div>
      <div v-if="taskCount > 0" class="task-list">
        <div v-if="(stats.pendingOrders || 0) > 0" class="task-row clickable" @click="router.push({ path: '/admin/orders', query: { status: 'PAID' } })">
          <div class="task-main">
            <span class="task-dot dot-danger"></span>
            <span class="task-title">{{ stats.pendingOrders }} 笔订单待发货</span>
          </div>
          <div class="task-right">
            <span class="task-meta">订单管理</span>
            <span class="task-action">去处理 →</span>
          </div>
        </div>
        <div v-if="(stats.pendingAfterSaleCount || 0) > 0" class="task-row clickable" @click="router.push('/admin/aftersale')">
          <div class="task-main">
            <span class="task-dot dot-warn"></span>
            <span class="task-title">{{ stats.pendingAfterSaleCount }} 条售后待审核</span>
          </div>
          <div class="task-right">
            <span class="task-meta">售后管理</span>
            <span class="task-action">去处理 →</span>
          </div>
        </div>
        <div v-if="(stats.lowStockCount || 0) > 0" class="task-row clickable" @click="router.push('/admin/inventory')">
          <div class="task-main">
            <span class="task-dot dot-warn"></span>
            <span class="task-title">{{ stats.lowStockCount }} 个规格库存低于阈值</span>
          </div>
          <div class="task-right">
            <span class="task-meta">出入库日志</span>
            <span class="task-action">去处理 →</span>
          </div>
        </div>
      </div>
      <div v-else class="task-empty">暂无待处理事项，一切正常</div>
    </div>

    <div class="grid-row">
      <div class="panel">
        <div class="panel-head"><span class="panel-title">快捷操作</span></div>
        <div class="quick-actions">
          <div class="action-item" @click="router.push('/admin/spu')">
            <el-icon :size="20"><Goods /></el-icon><span>商品管理</span>
          </div>
          <div class="action-item" @click="router.push('/admin/orders')">
            <el-icon :size="20"><Tickets /></el-icon><span>订单管理</span>
          </div>
          <div class="action-item" @click="router.push('/admin/inventory')">
            <el-icon :size="20"><Box /></el-icon><span>库存管理</span>
          </div>
          <div class="action-item" @click="router.push('/admin/coupon')">
            <el-icon :size="20"><Discount /></el-icon><span>优惠券</span>
          </div>
        </div>
      </div>

      <div class="panel">
        <div class="panel-head"><span class="panel-title">数据导出</span></div>
        <div class="export-actions">
          <el-button @click="handleExport('orders')" :loading="exporting === 'orders'">导出订单</el-button>
          <el-button @click="handleExport('users')" :loading="exporting === 'users'">导出用户</el-button>
          <el-button @click="handleExport('finance')" :loading="exporting === 'finance'">导出报表</el-button>
        </div>
      </div>
    </div>

    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">AI 经营日报</span>
        <div class="report-head-right">
          <span v-if="reportGeneratedAt" class="report-meta">生成于 {{ reportGeneratedAt }}</span>
          <el-button type="primary" size="small" :loading="reportLoading" @click="handleGenerateReport">
            {{ reportLoading ? 'AI 分析中…' : (reportText ? '重新生成' : '生成日报') }}
          </el-button>
        </div>
      </div>
      <div v-if="reportLoading" class="report-loading">
        <el-icon class="is-loading" :size="18"><Loading /></el-icon>
        正在汇总经营数据并调用 AI 分析，约需 5~30 秒，请稍候…
      </div>
      <el-empty
        v-else-if="!reportText"
        description="点击「生成日报」，AI 将基于当日订单、销售、库存等数据生成经营分析报告"
        :image-size="80"
      />
      <div v-else class="report-body">
        <div
          v-for="(line, i) in reportLines"
          :key="i"
          :class="{ 'report-heading': isReportHeading(line) }"
        >{{ line }}</div>
      </div>
    </div>

    <div class="panel">
      <div class="panel-head"><span class="panel-title">系统信息</span></div>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="前端框架">Vue 3 + Vite + Pinia</el-descriptions-item>
        <el-descriptions-item label="后端服务">stellar-server (Java)</el-descriptions-item>
        <el-descriptions-item label="RAG 服务">Python LangChain (端口 8000)</el-descriptions-item>
        <el-descriptions-item label="JWT 管理端 Claim">EMP_ID (header: token)</el-descriptions-item>
        <el-descriptions-item label="JWT C端 Claim">USER_ID (header: authentication)</el-descriptions-item>
        <el-descriptions-item label="API 代理端口">8082 (dev proxy)</el-descriptions-item>
      </el-descriptions>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { useRouter } from 'vue-router'
import { generateDailyReport } from '@/api/admin'
import { adminRequest } from '@/api/request'
import { Goods, Tickets, Box, Discount, Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts/core'
import { LineChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

// 按需注册：仅引入仪表盘用到的 line/bar + grid + tooltip，避免全量打包 echarts
echarts.use([LineChart, BarChart, GridComponent, TooltipComponent, CanvasRenderer])

const router = useRouter()
const orderChartRef = ref<any>(null)
const salesChartRef = ref<any>(null)
const stats = reactive<any>({
  pendingOrders: 0, todayOrders: 0, todaySales: 0, lowStockCount: 0,
  pendingAfterSaleCount: 0, behaviorEvents: 0, behaviorVisitors: 0
})
const orderTrendData = ref<any[]>([])
const salesTrendData = ref<any[]>([])
const behaviorTopEvents = ref<any[]>([])
const behaviorTopKeywords = ref<any[]>([])
const behaviorTopProducts = ref<any[]>([])

// 埋点事件类型 → 中文展示名，未知类型原样显示
const EVENT_TYPE_TEXT: Record<string, string> = {
  view_item: '浏览商品',
  view_item_list: '浏览列表',
  search: '搜索',
  add_to_cart: '加购',
  order_placed: '下单',
  favorite: '收藏',
  page_view: '页面访问'
}
function eventTypeText(type: string) {
  return EVENT_TYPE_TEXT[type] || type || '未知'
}

const orderTrendHasData = computed(() => orderTrendData.value.some((d: any) => (d.count || 0) > 0))
const salesTrendHasData = computed(() => salesTrendData.value.some((d: any) => (d.amount || 0) > 0))

/** 待处理事项总数：三个待办源任一大于 0 就渲染任务流 */
const taskCount = computed(() =>
  (Number(stats.pendingOrders) || 0) +
  (Number(stats.pendingAfterSaleCount) || 0) +
  (Number(stats.lowStockCount) || 0)
)

/** 获取今天的日期字符串，格式为 YYYY-MM-DD */
function todayStr() {
  const d = new Date()
  return d.getFullYear() + '-' +
    String(d.getMonth() + 1).padStart(2, '0') + '-' +
    String(d.getDate()).padStart(2, '0')
}

/** 跳转到订单管理页面，并按指定状态筛选 */
function goOrders(status: any) {
  router.push({ path: '/admin/orders', query: { status } })
}

/** 跳转到订单管理页面，并按今天日期筛选 */
function goOrdersToday() {
  const today = todayStr()
  router.push({ path: '/admin/orders', query: { startTime: today, endTime: today } })
}

let orderChart: any = null
let salesChart: any = null

/** 加载仪表盘全部数据：核心统计、增强统计 */
async function load() {
  try {
    // 模板消费的统计全部来自 /admin/dashboard/enhanced；
    // 原 getDashboardStats() 请求结果无人渲染，属无效请求，已移除
    try {
      const res: any = await adminRequest({ url: '/admin/dashboard/enhanced', method: 'get' })
      const d: any = (res as any)?.data || res || {}
      stats.todayOrders = d.todayOrders || 0
      stats.todaySales = d.todaySales || 0
      stats.lowStockCount = d.lowStockCount || 0
      stats.pendingOrders = d.pendingOrders || 0
      stats.pendingAfterSaleCount = d.pendingAfterSaleCount || 0
      stats.behaviorEvents = d.behaviorEvents || 0
      stats.behaviorVisitors = d.behaviorVisitors || 0
      behaviorTopEvents.value = Array.isArray(d.behaviorTopEvents) ? d.behaviorTopEvents : []
      behaviorTopKeywords.value = Array.isArray(d.behaviorTopKeywords) ? d.behaviorTopKeywords : []
      behaviorTopProducts.value = Array.isArray(d.behaviorTopProducts) ? d.behaviorTopProducts : []
      renderCharts(d.orderTrend || [], d.salesTrend || [])
    } catch (e: any) {
      console.error('加载仪表盘增强统计失败', e)
    }
  } catch (e: any) {}
}

/** 使用 ECharts 渲染近7天订单趋势和销售额趋势图 */
function renderCharts(orderTrend: any, salesTrend: any) {
  orderTrendData.value = Array.isArray(orderTrend) ? orderTrend : []
  salesTrendData.value = Array.isArray(salesTrend) ? salesTrend : []

  nextTick(() => {
    try {
      if (orderChartRef.value && orderTrendHasData.value) {
        if (!orderChart) orderChart = echarts.init(orderChartRef.value)
        const dates = orderTrendData.value.map((d: any) => d.date)
        orderChart.setOption({
          animationDuration: 400,
          animationDurationUpdate: 200,
          tooltip: { trigger: 'axis' },
          grid: { left: 40, right: 20, top: 20, bottom: 30 },
          xAxis: { type: 'category', data: dates, axisLabel: { fontSize: 11 } },
          yAxis: { type: 'value', minInterval: 1 },
          series: [{
            data: orderTrendData.value.map((d: any) => d.count || 0),
            type: 'line',
            smooth: true,
            areaStyle: { color: 'rgba(0, 113, 227, 0.12)' },
            itemStyle: { color: '#0071e3' },
            lineStyle: { color: '#0071e3', width: 3 }
          }]
        }, true)
      }
      if (salesChartRef.value && salesTrendHasData.value) {
        if (!salesChart) salesChart = echarts.init(salesChartRef.value)
        const dates = salesTrendData.value.map((d: any) => d.date)
        salesChart.setOption({
          animationDuration: 400,
          animationDurationUpdate: 200,
          tooltip: { trigger: 'axis' },
          grid: { left: 50, right: 20, top: 20, bottom: 30 },
          xAxis: { type: 'category', data: dates, axisLabel: { fontSize: 11 } },
          yAxis: { type: 'value', axisLabel: { formatter: '¥{value}' } },
          series: [{
            data: salesTrendData.value.map((d: any) => Number(d.amount || 0)),
            type: 'bar',
            itemStyle: { color: '#34c759', borderRadius: [6, 6, 0, 0] }
          }]
        }, true)
      }
    } catch (e: any) {
      console.error('渲染趋势图失败', e)
    }
  })
}

/** 响应窗口大小变化，调整图表尺寸 */
function handleResize() {
  orderChart && orderChart.resize()
  salesChart && salesChart.resize()
}

onMounted(() => {
  load()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  orderChart && orderChart.dispose()
  salesChart && salesChart.dispose()
})

const exporting = ref<any>(null)

// ---------- AI 经营日报 ----------
const reportLoading = ref(false)
const reportText = ref('')
const reportGeneratedAt = ref('')

/** 把日报文本按行拆开（去掉空行），标题行单独渲染样式 */
const reportLines = computed(() =>
  reportText.value.split('\n').map((l: any) => l.trim()).filter(Boolean)
)

/** 识别小节标题行：【xxx】 或 markdown # 标题（模型可能不严格遵守格式） */
function isReportHeading(line: any) {
  return /^【.+】$/.test(line) || /^#{1,3}\s+/.test(line)
}

/** 调用后端生成 AI 经营日报（后端会汇总数据并调用 RAG 端 LLM） */
async function handleGenerateReport() {
  reportLoading.value = true
  try {
    const d = await generateDailyReport()
    reportText.value = (d && d.report) || ''
    reportGeneratedAt.value = (d && d.generatedAt) || ''
    if (!reportText.value) {
      ElMessage.warning('未生成日报内容，请稍后重试')
    }
  } catch (e: any) {
    console.error('生成 AI 经营日报失败', e)
  } finally {
    reportLoading.value = false
  }
}

/** 导出数据为 Excel 文件，支持 orders / users / finance 三种类型 */
async function handleExport(type: any) {
  exporting.value = type
  try {
    const token = localStorage.getItem('stellar_admin_token') || ''
    const urls: Record<string, any> = { orders: '/admin/export/orders', users: '/admin/export/users', finance: '/admin/export/finance' }
    const names: Record<string, any> = { orders: '订单数据导出.xlsx', users: '用户数据导出.xlsx', finance: '财务报表.xlsx' }
    const resp = await fetch(urls[type], { headers: { token } })
    if (!resp.ok) throw new Error()
    const blob = await resp.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = url; a.download = names[type]; a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e: any) { ElMessage.error('导出失败') }
  finally { exporting.value = null }
}
</script>

<style scoped>
/* ===== 方案 D：高密度极简仪表盘 =====
 * 扁平实底卡片 + 发丝线边框，无投影无渐变；
 * 圆角/投影令牌已在 _Layout.vue 的 .admin-layout 子树覆写（radius≤12 / shadow=none）。 */
.dashboard-page { display: flex; flex-direction: column; gap: 18px; }

/* 统计条：一个容器切四格，发丝线分隔，替代原来的四张独立大卡 */
.stat-strip {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-lg);
  overflow: hidden;
}
.stat-cell {
  padding: 20px 22px 16px;
  min-width: 0;
}
.stat-cell + .stat-cell { border-left: 1px solid var(--border-base); }
.stat-cell.clickable { cursor: pointer; transition: background var(--transition-color); }
@media (hover: hover) {
  .stat-cell.clickable:hover { background: var(--bg-hover); }
}
.stat-label { color: var(--text-secondary); font-size: 12px; margin-bottom: 8px; }
.stat-value { font-size: 22px; font-weight: 600; color: var(--text-primary); line-height: 1.2; }
.stat-value.value-warn { color: var(--el-color-danger, #d5304f); }
.stat-hint { font-size: 11px; color: var(--text-muted); margin-top: 6px; }

.grid-row { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
.panel-head { display: flex; align-items: baseline; gap: 10px; margin-bottom: 16px; }
.panel-title { font-size: 15px; font-weight: 500; color: var(--text-primary); }
.panel-sub { font-size: 12px; color: var(--text-muted); }

/* 待处理事项流：发丝线分行，左状态点右动作，高信息密度 */
.task-list { display: flex; flex-direction: column; }
.task-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 4px;
  border-top: 1px solid var(--border-base);
  font-size: 13px;
}
.task-row:first-child { border-top: none; }
.task-row.clickable { cursor: pointer; }
@media (hover: hover) {
  .task-row.clickable:hover { background: var(--bg-hover); }
}
.task-main { display: flex; align-items: center; gap: 10px; min-width: 0; }
.task-dot { width: 7px; height: 7px; border-radius: 50%; flex-shrink: 0; }
.dot-danger { background: var(--el-color-danger, #d5304f); }
.dot-warn { background: var(--el-color-warning, #e6a23c); }
.task-title { color: var(--text-primary); }
.task-right { display: flex; align-items: center; gap: 14px; flex-shrink: 0; }
.task-meta { font-size: 12px; color: var(--text-muted); }
.task-action { font-size: 12px; color: var(--brand-primary); }
.task-empty {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: var(--text-muted);
}

/* 快捷操作：发丝线小格子，中性 hover，不再按色系染色 */
.quick-actions { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 18px 10px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-base);
  cursor: pointer;
  transition: background var(--transition-color);
}
@media (hover: hover) {
  .action-item:hover { background: var(--bg-hover); }
}
.action-item span { color: var(--text-secondary); font-size: 12px; }
.action-item .el-icon { color: var(--text-secondary); }
@media (hover: hover) {
  .action-item:hover span, .action-item:hover .el-icon { color: var(--text-primary); }
}

.export-actions { display: flex; gap: 10px; padding-top: 2px; }

.report-head-right { display: flex; align-items: center; gap: 12px; margin-left: auto; }
.report-meta { color: var(--text-secondary); font-size: 12px; }
.report-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 32px 0;
  color: var(--text-secondary);
  font-size: 14px;
}
.report-body { line-height: 1.9; font-size: 13px; color: var(--text-primary); }
.report-heading {
  font-weight: 600;
  font-size: 14px;
  color: var(--text-primary);
  margin-top: 16px;
}
.report-body .report-heading:first-child { margin-top: 0; }

/* 近 7 天用户行为 */
.behavior-summary { display: flex; gap: 40px; margin-bottom: 20px; }
.behavior-num { display: flex; align-items: baseline; gap: 8px; }
.behavior-label { font-size: 12px; color: var(--text-secondary); }
.behavior-value { font-size: 24px; font-weight: 600; color: var(--text-primary); line-height: 1.2; }
.behavior-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 28px; }
.behavior-block { min-width: 0; }
.behavior-title { font-size: 12px; color: var(--text-secondary); margin-bottom: 12px; }
.behavior-tags { display: flex; flex-wrap: wrap; gap: 8px; }
.behavior-list { display: flex; flex-direction: column; gap: 10px; }
.behavior-row { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.behavior-rank { width: 16px; flex-shrink: 0; color: var(--text-muted); }
.behavior-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--text-primary); }
.behavior-count { flex-shrink: 0; color: var(--text-secondary); }
.behavior-empty { font-size: 13px; color: var(--text-muted); }
@media (max-width: 1100px) {
  .behavior-grid { grid-template-columns: 1fr; gap: 16px; }
}
</style>