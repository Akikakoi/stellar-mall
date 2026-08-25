<template>
  <div class="records-page">
    <main class="container main-content">
      <!-- 积分概览条 -->
      <div class="top-bar">
        <div class="top-info">
          <span class="top-label">可用积分</span>
          <span class="top-value">{{ points?.availablePoints || 0 }}</span>
        </div>
        <router-link to="/points" class="back-link">返回积分商城</router-link>
      </div>

      <!-- 切换tab -->
      <div class="panel">
        <el-tabs v-model="activeTab" @tab-change="onTabChange">
          <el-tab-pane label="积分流水" name="records">
            <el-table :data="records" stripe v-loading="recLoading" empty-text="暂无积分流水">
              <el-table-column label="类型" width="80">
                <template #default="{ row }">
                  <el-tag :type="recTypeTag(row.type)" size="small">{{ row.typeText }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="积分变动" width="110">
                <template #default="{ row }">
                  <span :class="row.points >= 0 ? 'amount-plus' : 'amount-minus'">
                    {{ row.points >= 0 ? '+' : '' }}{{ row.points }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="余额" width="90">
                <template #default="{ row }">{{ row.balanceAfter }}</template>
              </el-table-column>
              <el-table-column prop="description" label="说明" min-width="160" show-overflow-tooltip />
              <el-table-column label="到期时间" width="110">
                <template #default="{ row }">{{ row.expiredTime || '--' }}</template>
              </el-table-column>
              <el-table-column prop="createTime" label="时间" width="160" />
            </el-table>

            <div class="pagination-wrapper" v-if="recTotal > recPageSize">
              <el-pagination v-model:current-page="recPage" :page-size="recPageSize"
                :total="recTotal" layout="prev, pager, next" @current-change="loadRecords" />
            </div>
          </el-tab-pane>

          <el-tab-pane label="兑换记录" name="redemptions">
            <el-table :data="redemptions" stripe v-loading="redLoading" empty-text="暂无兑换记录">
              <el-table-column prop="productName" label="商品" min-width="140" show-overflow-tooltip />
              <el-table-column label="消耗积分" width="100">
                <template #default="{ row }">
                  <span class="amount-minus">-{{ row.pointsCost }}</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="redStatusTag(row.status)" size="small">
                    {{ redStatusText(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="createTime" label="时间" width="160" />
            </el-table>

            <div class="pagination-wrapper" v-if="redTotal > redPageSize">
              <el-pagination v-model:current-page="redPage" :page-size="redPageSize"
                :total="redTotal" layout="prev, pager, next" @current-change="loadRedemptions" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getUserPoints, listPointsRecords, listPointsRedemptions } from '@/api/mall'
import { ElMessage } from 'element-plus'

const points = ref<any>(null)
const activeTab = ref('records')

const recLoading = ref(false)
const records = ref<any[]>([])
const recPage = ref(1)
const recPageSize = ref(15)
const recTotal = ref(0)

const redLoading = ref(false)
const redemptions = ref<any[]>([])
const redPage = ref(1)
const redPageSize = ref(15)
const redTotal = ref(0)

/**
 * 根据积分流水类型返回对应的 Element Plus Tag 样式
 * @param {number} type - 积分流水类型
 * @returns {string} Tag 类型
 */
function recTypeTag(type: any) {
  const map: Record<string, any> = { 1: 'success', 2: 'danger', 3: 'warning', 4: 'info' }
  return map[type] || 'info'
}

/**
 * 根据兑换状态返回对应的 Element Plus Tag 样式
 * @param {number} status - 兑换状态
 * @returns {string} Tag 类型
 */
function redStatusTag(status: any) {
  const map: Record<string, any> = { 1: 'warning', 2: 'success', 3: 'info' }
  return map[status] || 'info'
}

/**
 * 根据兑换状态码返回对应的中文文本
 * @param {number} status - 兑换状态
 * @returns {string} 状态文本
 */
function redStatusText(status: any) {
  const map: Record<string, any> = { 1: '已兑换', 2: '已发放', 3: '已取消' }
  return map[status] || '未知'
}

/**
 * 加载积分概览信息
 */
async function loadSummary() {
  try {
    const res = await getUserPoints()
    points.value = res || { availablePoints: 0, totalEarned: 0, totalSpent: 0 }
  } catch (e: any) {
    // silent
  }
}

/**
 * 加载积分流水记录，分页查询
 */
async function loadRecords() {
  recLoading.value = true
  try {
    const res = await listPointsRecords({ page: recPage.value, pageSize: recPageSize.value })
    const d: any = res || {}
    records.value = d.records || d.list || []
    recTotal.value = d.total || 0
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '加载流水失败')
  } finally {
    recLoading.value = false
  }
}

/**
 * 加载积分兑换记录，分页查询
 */
async function loadRedemptions() {
  redLoading.value = true
  try {
    const res = await listPointsRedemptions({ page: redPage.value, pageSize: redPageSize.value })
    const d: any = res || {}
    redemptions.value = d.records || d.list || []
    redTotal.value = d.total || 0
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '加载兑换记录失败')
  } finally {
    redLoading.value = false
  }
}

/**
 * Tab 切换回调，首次切换到对应 tab 时懒加载数据
 * @param {string} tab - 当前激活的 tab 名称
 */
function onTabChange(tab: any) {
  if (tab === 'records' && records.value.length === 0) loadRecords()
  if (tab === 'redemptions' && redemptions.value.length === 0) loadRedemptions()
}

onMounted(() => {
  loadSummary()
  loadRecords()
})
</script>

<style scoped>
.records-page { min-height: 100vh; }
.container { max-width: 800px; margin: 0 auto; padding: 0 20px; }
.main-content { padding: 32px 20px 60px; }

.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-lg);
  margin-bottom: 16px;
}
.top-label { font-size: 14px; color: var(--text-muted); margin-right: 12px; }
.top-value { font-size: 24px; font-weight: 700; color: #f0a040; }
.back-link { font-size: 13px; color: var(--color-primary, #409eff); }

.panel {
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-lg);
  padding: 20px 24px;
  box-shadow: var(--shadow-sm);
}

.amount-plus { color: #67c23a; font-weight: 600; }
.amount-minus { color: #e33; font-weight: 600; }

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
