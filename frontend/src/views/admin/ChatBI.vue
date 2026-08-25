<template>
  <div class="chatbi-page">
    <div class="panel head-panel">
      <div class="head-info">
        <div class="head-title">AI 智能查数</div>
        <div class="head-desc">
          用自然语言提问，AI 自动生成分析 SQL 并绘制图表。例如"上周哪个类目卖得最好"、"近7天每日营业额趋势"。
          仅支持只读统计查询，敏感操作会被自动拦截。
        </div>
      </div>
      <div class="suggest-chips">
        <el-tag
          v-for="s in suggestions"
          :key="s"
          class="suggest-chip"
          effect="plain"
          @click="ask(s)"
        >{{ s }}</el-tag>
      </div>
    </div>

    <div ref="listRef" class="chat-list">
      <el-empty v-if="items.length === 0" description="试着问一个经营数据问题吧" :image-size="90" />
      <div v-for="item in items" :key="item.id" class="chat-item">
        <!-- 用户问题 -->
        <div class="question-row">
          <div class="question-bubble">{{ item.question }}</div>
        </div>
        <!-- AI 回答 -->
        <div class="answer-card">
          <template v-if="item.loading">
            <div class="answer-loading">
              <el-icon class="is-loading" :size="16"><Loading /></el-icon>
              正在理解问题、生成并执行查询，约需 10~30 秒…
            </div>
          </template>
          <template v-else-if="item.error">
            <el-alert :title="item.error" type="error" :closable="false" show-icon />
          </template>
          <template v-else>
            <div class="answer-summary">{{ item.summary }}</div>
            <div v-if="hasChartData(item)" class="chart-box" :ref="(el: any) => setChartRef(item.id, el)"></div>
            <el-collapse class="detail-collapse">
              <el-collapse-item title="查看数据明细" name="data">
                <el-table :data="item.rows" size="small" border max-height="320">
                  <el-table-column
                    v-for="col in item.columns"
                    :key="col"
                    :prop="col"
                    :label="col"
                    min-width="120"
                  >
                    <template #default="{ row }">{{ formatCell(row[col]) }}</template>
                  </el-table-column>
                </el-table>
              </el-collapse-item>
              <el-collapse-item title="查看生成的 SQL" name="sql">
                <pre class="sql-block">{{ item.sql }}</pre>
              </el-collapse-item>
            </el-collapse>
          </template>
        </div>
      </div>
    </div>

    <div class="input-bar panel">
      <el-input
        v-model="input"
        placeholder="输入经营数据问题，如：本月销售额最高的5个商品是哪些？"
        size="large"
        :disabled="loading"
        @keyup.enter="ask(input)"
        clearable
      />
      <el-button
        type="primary"
        size="large"
        :loading="loading"
        :disabled="!input.trim()"
        @click="ask(input)"
      >提问</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onUnmounted } from 'vue'
import { chatBiQuery } from '@/api/admin'
import { Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts/core'
import { LineChart, BarChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([LineChart, BarChart, PieChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const suggestions = [
  '上周哪个类目卖得最好',
  '近7天每日营业额趋势',
  '本月销售额最高的5个商品',
  '各状态订单数量分布',
  '待发货订单有多少'
]

const input = ref('')
const loading = ref(false)
const items = ref<any[]>([])
const listRef = ref<any>(null)
const chartEls: Record<number, any> = {}
const chartInstances: Record<number, any> = {}
let idSeq = 0

function setChartRef(id: number, el: any) {
  if (el) chartEls[id] = el
}

/** 是否有可绘制的双列数据（bar/line/pie 需要 x/y 两个有效字段） */
function hasChartData(item: any) {
  const t = item.chartType
  if (t !== 'bar' && t !== 'line' && t !== 'pie') return false
  return !!(item.xField && item.yField && item.rows && item.rows.length > 0
    && item.columns.includes(item.xField) && item.columns.includes(item.yField))
}

/** 提问：追加一条会话记录并调用后端 */
async function ask(question: string) {
  const q = (question || '').trim()
  if (!q || loading.value) return
  input.value = ''
  loading.value = true
  const item: Record<string, any> = { id: ++idSeq, question: q, loading: true, error: '', summary: '', sql: '', title: '', chartType: 'table', xField: '', yField: '', columns: [], rows: [] }
  items.value.push(item)
  scrollToBottom()
  try {
    const d = await chatBiQuery(q)
    Object.assign(item, {
      loading: false,
      summary: d?.summary || '（未生成总结）',
      sql: d?.sql || '',
      title: d?.title || '查询结果',
      chartType: d?.chartType || 'table',
      xField: d?.xField || '',
      yField: d?.yField || '',
      columns: d?.columns || [],
      rows: d?.rows || []
    })
    await nextTick()
    if (hasChartData(item)) renderChart(item)
  } catch (e: any) {
    item.loading = false
    item.error = (e && e.message) || '查询失败，请稍后重试'
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

/** 按后端返回的 chartType 渲染 echarts */
function renderChart(item: any) {
  const el = chartEls[item.id]
  if (!el) return
  if (chartInstances[item.id]) {
    chartInstances[item.id].dispose()
  }
  const chart = echarts.init(el)
  chartInstances[item.id] = chart
  const rows = item.rows
  const xData = rows.map((r: any) => String(r[item.xField] ?? ''))
  const yData = rows.map((r: any) => Number(r[item.yField] ?? 0))
  let option: any
  if (item.chartType === 'pie') {
    option = {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { orient: 'vertical', left: 'left', type: 'scroll' },
      series: [{
        type: 'pie',
        radius: ['35%', '65%'],
        center: ['55%', '50%'],
        data: rows.map((r: any) => ({ name: String(r[item.xField] ?? ''), value: Number(r[item.yField] ?? 0) })),
        label: { formatter: '{b} {d}%' }
      }]
    }
  } else {
    option = {
      tooltip: { trigger: 'axis' },
      grid: { left: 60, right: 24, top: 30, bottom: 40 },
      xAxis: { type: 'category', data: xData, axisLabel: { fontSize: 11, rotate: xData.length > 6 ? 30 : 0 } },
      yAxis: { type: 'value' },
      series: [{
        type: item.chartType === 'line' ? 'line' : 'bar',
        data: yData,
        smooth: item.chartType === 'line',
        areaStyle: item.chartType === 'line' ? { color: 'rgba(0, 113, 227, 0.12)' } : undefined,
        itemStyle: { color: item.chartType === 'line' ? '#0071e3' : '#34c759', borderRadius: item.chartType === 'bar' ? [6, 6, 0, 0] : 0 }
      }]
    }
  }
  chart.setOption(option, true)
}

/** 表格单元格格式化：数字保留 2 位小数，其余原样 */
function formatCell(v: any) {
  if (v === null || v === undefined) return '-'
  if (typeof v === 'number') return Number.isInteger(v) ? v : v.toFixed(2)
  return String(v)
}

function scrollToBottom() {
  nextTick(() => {
    if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
  })
}

onUnmounted(() => {
  Object.values(chartInstances).forEach((c: any) => c && c.dispose())
})
</script>

<style scoped>
.chatbi-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: 16px;
}
.panel {
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-xl);
  padding: 20px 24px;
  box-shadow: var(--shadow-sm);
}
.head-panel { display: flex; justify-content: space-between; align-items: center; gap: 24px; flex-wrap: wrap; }
.head-title { font-size: 20px; font-weight: 600; color: var(--text-primary); margin-bottom: 8px; }
.head-desc { color: var(--text-secondary); font-size: 13px; line-height: 1.6; max-width: 560px; }
.suggest-chips { display: flex; flex-wrap: wrap; gap: 8px; max-width: 420px; justify-content: flex-end; }
.suggest-chip { cursor: pointer; }
.suggest-chip:hover { color: var(--brand-primary); border-color: var(--brand-primary); }
.chat-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 4px;
}
.chat-item { display: flex; flex-direction: column; gap: 12px; }
.question-row { display: flex; justify-content: flex-end; }
.question-bubble {
  background: var(--brand-primary);
  color: #fff;
  padding: 10px 16px;
  border-radius: 16px 16px 4px 16px;
  max-width: 70%;
  font-size: 14px;
  line-height: 1.6;
}
.answer-card {
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-xl);
  padding: 18px 20px;
  box-shadow: var(--shadow-sm);
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.answer-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
  font-size: 14px;
  padding: 12px 0;
}
.answer-summary { font-size: 14px; line-height: 1.9; color: var(--text-primary); white-space: pre-wrap; }
.chart-box { width: 100%; height: 320px; }
.detail-collapse { border-top: 1px dashed var(--border-base); }
.detail-collapse :deep(.el-collapse-item__header) { font-size: 13px; color: var(--text-secondary); }
.sql-block {
  background: var(--bg-hover);
  border-radius: var(--radius-md);
  padding: 12px 14px;
  font-family: Consolas, Monaco, monospace;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--text-primary);
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}
.input-bar {
  display: flex;
  gap: 12px;
  position: sticky;
  bottom: 0;
}
</style>
