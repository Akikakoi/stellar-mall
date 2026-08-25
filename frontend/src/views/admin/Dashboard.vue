<template>
  <div class="dashboard-page">
    <div class="stat-row">
      <div class="stat-card accent-blue clickable" @click="goOrders('PAID')">
        <div class="stat-label">待处理订单</div>
        <div class="stat-value">{{ stats.pendingOrders || 0 }}</div>
      </div>
      <div class="stat-card accent-gray clickable" @click="goOrdersToday()">
        <div class="stat-label">今日订单</div>
        <div class="stat-value">{{ stats.todayOrders || 0 }}</div>
      </div>
      <div class="stat-card accent-green">
        <div class="stat-label">今日销售额</div>
        <div class="stat-value">¥{{ Number(stats.todaySales || 0).toFixed(0) }}</div>
      </div>
      <div class="stat-card accent-orange clickable" @click="router.push('/admin/inventory')">
        <div class="stat-label">低库存预警</div>
        <div class="stat-value">{{ stats.lowStockCount || 0 }}</div>
      </div>
    </div>

    <div class="grid-row">
      <div class="panel chart-panel">
        <div class="panel-head"><span class="panel-title">近7天订单趋势</span></div>
        <div ref="orderChartRef" v-show="orderTrendHasData" style="height: 260px;"></div>
        <el-empty v-if="!orderTrendHasData" description="暂无订单数据" :image-size="80" />
      </div>
      <div class="panel chart-panel">
        <div class="panel-head"><span class="panel-title">近7天销售额趋势</span></div>
        <div ref="salesChartRef" v-show="salesTrendHasData" style="height: 260px;"></div>
        <el-empty v-if="!salesTrendHasData" description="暂无销售数据" :image-size="80" />
      </div>
    </div>

    <div class="grid-row">
      <div class="panel">
        <div class="panel-head">
          <span class="panel-title">待处理订单</span>
          <el-button type="primary" link size="small" @click="router.push({ path: '/admin/orders', query: { status: 'PAID' } })">查看全部 →</el-button>
        </div>
        <div class="panel-stat">
          <div class="panel-stat-num">{{ stats.pendingOrders }}</div>
          <div class="panel-stat-label">笔订单待发货</div>
        </div>
      </div>
      <div class="panel">
        <div class="panel-head">
          <span class="panel-title">售后待处理</span>
          <el-button type="primary" link size="small" @click="router.push('/admin/aftersale')">查看全部 →</el-button>
        </div>
        <div class="panel-stat">
          <div class="panel-stat-num">{{ stats.pendingAfterSaleCount }}</div>
          <div class="panel-stat-label">条售后待处理</div>
        </div>
      </div>
    </div>

    <div class="panel">
      <div class="panel-head"><span class="panel-title">快捷操作</span></div>
      <div class="quick-actions">
          <div class="action-item action-blue" @click="router.push('/admin/spu')">
            <el-icon :size="28"><Goods /></el-icon><span>商品管理</span>
          </div>
          <div class="action-item action-red" @click="router.push('/admin/orders')">
            <el-icon :size="28"><Tickets /></el-icon><span>订单管理</span>
          </div>
          <div class="action-item action-orange" @click="router.push('/admin/inventory')">
            <el-icon :size="28"><Box /></el-icon><span>库存管理</span>
          </div>
          <div class="action-item action-green" @click="router.push('/admin/coupon')">
            <el-icon :size="28"><Discount /></el-icon><span>优惠券</span>
          </div>
          <div class="action-item action-gray" @click="router.push('/admin/recycle')">
            <el-icon :size="28"><Delete /></el-icon><span>回收站</span>
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
      <div class="panel-head"><span class="panel-title">数据导出</span></div>
      <div class="export-actions">
        <el-button @click="handleExport('orders')" :loading="exporting === 'orders'">导出订单</el-button>
        <el-button @click="handleExport('users')" :loading="exporting === 'users'">导出用户</el-button>
        <el-button @click="handleExport('finance')" :loading="exporting === 'finance'">导出报表</el-button>
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
import { getDashboardStats, generateDailyReport } from '@/api/admin'
import { adminRequest } from '@/api/request'
import { Goods, Menu, Refresh, User, Tickets, Box, Discount, Delete, Loading } from '@element-plus/icons-vue'
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
  pendingAfterSaleCount: 0
})
const orderTrendData = ref<any[]>([])
const salesTrendData = ref<any[]>([])

const orderTrendHasData = computed(() => orderTrendData.value.some((d: any) => (d.count || 0) > 0))
const salesTrendHasData = computed(() => salesTrendData.value.some((d: any) => (d.amount || 0) > 0))

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
    try {
      const res = await getDashboardStats()
      const d: any = res || {}
      stats.spuTotal = d.spuTotal || d.spuCount || 0
    } catch (e: any) {}
    try {
      const res: any = await adminRequest({ url: '/admin/dashboard/enhanced', method: 'get' })
      const d: any = (res as any)?.data || res || {}
      stats.todayOrders = d.todayOrders || 0
      stats.todaySales = d.todaySales || 0
      stats.lowStockCount = d.lowStockCount || 0
      stats.pendingOrders = d.pendingOrders || 0
      stats.pendingAfterSaleCount = d.pendingAfterSaleCount || 0
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
.dashboard-page { display: flex; flex-direction: column; gap: 16px; }
.stat-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.stat-card {
  padding: 22px 24px;
  border-radius: var(--radius-xl);
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  color: var(--text-primary);
  box-shadow: var(--shadow-sm);
  position: relative;
  overflow: hidden;
  transition: transform var(--transition-base), box-shadow var(--transition-base);
}
.stat-card:hover { transform: var(--hover-lift); box-shadow: var(--shadow-md); }
.stat-card.clickable { cursor: pointer; }
.stat-card.clickable:hover .stat-label { color: var(--brand-primary); }
.stat-card::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 3px;
}
.accent-blue::before { background: #0071e3; }
.accent-gray::before { background: #86868b; }
.accent-green::before { background: #34c759; }
.accent-orange::before { background: #ff9500; }
.stat-label { color: var(--text-secondary); font-size: 14px; margin-bottom: 10px; position: relative; z-index: 1; }
.stat-value { font-size: 32px; font-weight: 700; color: var(--text-primary); position: relative; z-index: 1; }
.grid-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.panel {
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-xl);
  padding: 20px 24px;
  box-shadow: var(--shadow-sm);
}
.panel-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.panel-title { font-size: 18px; font-weight: 600; color: var(--text-primary); }
.panel-stat { padding: 20px 0 16px; text-align: center; }
.panel-stat-num {
  font-size: 44px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--brand-primary);
}
.panel-stat-label { color: var(--text-muted); font-size: 14px; margin-top: 6px; }
.quick-actions { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; }
.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 18px 8px;
  border-radius: var(--radius-md);
  background: var(--bg-hover);
  cursor: pointer;
  transition: all var(--transition-base);
  border: 1px solid transparent;
}
.action-item:hover { background: var(--brand-primary-soft); border-color: var(--brand-primary-border); transform: var(--hover-lift); }
.action-item span { color: var(--text-secondary); font-size: 13px; transition: color var(--transition-base); }
.action-item:hover span { color: var(--brand-primary); }
.action-item .el-icon { color: var(--text-secondary); transition: color var(--transition-base); }
.action-blue:hover .el-icon { color: #0071e3; }
.action-red:hover .el-icon { color: #ff3b30; }
.action-orange:hover .el-icon { color: #ff9500; }
.action-green:hover .el-icon { color: #34c759; }
.action-gray:hover .el-icon { color: #86868b; }
.report-head-right { display: flex; align-items: center; gap: 12px; }
.report-meta { color: var(--text-secondary); font-size: 12px; }
.report-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 36px 0;
  color: var(--text-secondary);
  font-size: 14px;
}
.report-body { line-height: 2; font-size: 14px; color: var(--text-primary); }
.report-heading {
  font-weight: 600;
  font-size: 15px;
  color: var(--brand-primary);
  margin-top: 14px;
}
.report-body .report-heading:first-child { margin-top: 0; }
</style>