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
          <span class="panel-title">RAG 同步状态</span>
          <el-button type="primary" link size="small" @click="router.push('/admin/rag-sync')">查看队列 →</el-button>
        </div>
        <el-descriptions :column="2" border v-loading="loading">
          <el-descriptions-item label="待同步">{{ ragStats.pending || 0 }}</el-descriptions-item>
          <el-descriptions-item label="处理中">{{ ragStats.processing || 0 }}</el-descriptions-item>
          <el-descriptions-item label="成功">{{ ragStats.success || 0 }}</el-descriptions-item>
          <el-descriptions-item label="失败">{{ ragStats.failed || 0 }}</el-descriptions-item>
        </el-descriptions>
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

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { useRouter } from 'vue-router'
import { getDashboardStats, getRagSyncStats } from '@/api/admin'
import { adminRequest } from '@/api/request'
import { Goods, Menu, Refresh, User, Tickets, Box, Discount, Delete } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts/core'
import { LineChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

// 按需注册：仅引入仪表盘用到的 line/bar + grid + tooltip，避免全量打包 echarts
echarts.use([LineChart, BarChart, GridComponent, TooltipComponent, CanvasRenderer])

const router = useRouter()
const loading = ref(false)
const orderChartRef = ref(null)
const salesChartRef = ref(null)
const stats = reactive({
  pendingOrders: 0, todayOrders: 0, todaySales: 0, lowStockCount: 0
})
const ragStats = reactive({ pending: 0, processing: 0, success: 0, failed: 0 })
const orderTrendData = ref([])
const salesTrendData = ref([])

const orderTrendHasData = computed(() => orderTrendData.value.some(d => (d.count || 0) > 0))
const salesTrendHasData = computed(() => salesTrendData.value.some(d => (d.amount || 0) > 0))

function todayStr() {
  const d = new Date()
  return d.getFullYear() + '-' +
    String(d.getMonth() + 1).padStart(2, '0') + '-' +
    String(d.getDate()).padStart(2, '0')
}

function goOrders(status) {
  router.push({ path: '/admin/orders', query: { status } })
}

function goOrdersToday() {
  const today = todayStr()
  router.push({ path: '/admin/orders', query: { startTime: today, endTime: today } })
}

let orderChart = null
let salesChart = null

async function load() {
  loading.value = true
  try {
    try {
      const res = await getDashboardStats()
      const d = res || {}
      stats.spuTotal = d.spuTotal || d.spuCount || 0
    } catch (e) {}
    try {
      const res = await adminRequest({ url: '/admin/dashboard/enhanced', method: 'get' })
      const d = res?.data || res || {}
      stats.todayOrders = d.todayOrders || 0
      stats.todaySales = d.todaySales || 0
      stats.lowStockCount = d.lowStockCount || 0
      stats.pendingOrders = d.pendingOrders || 0
      renderCharts(d.orderTrend || [], d.salesTrend || [])
    } catch (e) {
      console.error('加载仪表盘增强统计失败', e)
    }
    try {
      const res = await getRagSyncStats()
      const d = res || {}
      ragStats.pending = d.pending || d.pendingCount || 0
      ragStats.processing = d.processing || d.processingCount || 0
      ragStats.success = d.success || d.successCount || 0
      ragStats.failed = d.failed || d.failedCount || 0
    } catch (e) {}
  } finally { loading.value = false }
}

function renderCharts(orderTrend, salesTrend) {
  orderTrendData.value = Array.isArray(orderTrend) ? orderTrend : []
  salesTrendData.value = Array.isArray(salesTrend) ? salesTrend : []

  nextTick(() => {
    try {
      if (orderChartRef.value && orderTrendHasData.value) {
        if (!orderChart) orderChart = echarts.init(orderChartRef.value)
        const dates = orderTrendData.value.map(d => d.date)
        orderChart.setOption({
          tooltip: { trigger: 'axis' },
          grid: { left: 40, right: 20, top: 20, bottom: 30 },
          xAxis: { type: 'category', data: dates, axisLabel: { fontSize: 11 } },
          yAxis: { type: 'value', minInterval: 1 },
          series: [{
            data: orderTrendData.value.map(d => d.count || 0),
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
        const dates = salesTrendData.value.map(d => d.date)
        salesChart.setOption({
          tooltip: { trigger: 'axis' },
          grid: { left: 50, right: 20, top: 20, bottom: 30 },
          xAxis: { type: 'category', data: dates, axisLabel: { fontSize: 11 } },
          yAxis: { type: 'value', axisLabel: { formatter: '¥{value}' } },
          series: [{
            data: salesTrendData.value.map(d => Number(d.amount || 0)),
            type: 'bar',
            itemStyle: { color: '#34c759', borderRadius: [6, 6, 0, 0] }
          }]
        }, true)
      }
    } catch (e) {
      console.error('渲染趋势图失败', e)
    }
  })
}

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

const exporting = ref(null)
async function handleExport(type) {
  exporting.value = type
  try {
    const token = localStorage.getItem('stellar_admin_token') || ''
    const urls = { orders: '/admin/export/orders', users: '/admin/export/users', finance: '/admin/export/finance' }
    const names = { orders: '订单数据导出.xlsx', users: '用户数据导出.xlsx', finance: '财务报表.xlsx' }
    const resp = await fetch(urls[type], { headers: { token } })
    if (!resp.ok) throw new Error()
    const blob = await resp.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = url; a.download = names[type]; a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e) { ElMessage.error('导出失败') }
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
</style>