<template>
  <div class="points-page">
    <main class="container main-content" v-loading="loading">
      <!-- 积分概览 + 签到 -->
      <div class="panel points-panel">
        <div class="points-overview">
          <div class="points-main">
            <div class="points-label">可用积分</div>
            <div class="points-amount">{{ points?.availablePoints || 0 }}</div>
          </div>
          <el-button type="primary" :disabled="todayCheckedIn" :loading="checkingIn" @click="doCheckin">
            {{ todayCheckedIn ? '今日已签到' : '每日签到 +5' }}
          </el-button>
        </div>
        <div class="points-extra">
          <span>累计获得 {{ points?.totalEarned || 0 }}</span>
          <span class="sep">|</span>
          <span>累计消费 {{ points?.totalSpent || 0 }}</span>
          <span class="sep">|</span>
          <router-link to="/points/records" class="link">积分流水</router-link>
        </div>
        <!-- 本月签到日历 -->
        <div class="checkin-calendar" v-if="checkinDates.length">
          <div class="cal-title">本月签到</div>
          <div class="cal-grid">
            <span v-for="d in monthDays" :key="'d' + d"
              class="cal-day" :class="{ checked: checkinDates.includes(d) }">
              {{ d.split('-')[2] }}
            </span>
          </div>
        </div>
      </div>

      <!-- 积分商城 -->
      <div class="panel">
        <div class="panel-head"><span class="panel-title">积分商城</span></div>
        <div class="product-grid" v-if="products.length">
          <div class="product-card" v-for="p in products" :key="p.id" @click="openRedeem(p)">
            <div class="product-img">
              <el-image v-if="p.imageUrl" :src="p.imageUrl" fit="cover" style="width:100%;height:140px" />
              <div v-else class="img-placeholder">
                <span>{{ p.productType === 'COUPON' ? '优惠券' : '实物' }}</span>
              </div>
            </div>
            <div class="product-info">
              <div class="product-name">{{ p.name }}</div>
              <div class="product-bottom">
                <span class="product-type-tag">{{ p.productTypeText }}</span>
                <span class="product-price">{{ p.pointsPrice }} 积分</span>
              </div>
              <div class="product-stock">库存: {{ p.stock }}</div>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无兑换商品" />
      </div>
    </main>

    <!-- 兑换确认弹窗 -->
    <el-dialog v-model="redeemVisible" title="确认兑换" width="400px" destroy-on-close>
      <div class="redeem-confirm" v-if="redeemTarget">
        <div class="redeem-name">{{ redeemTarget.name }}</div>
        <div class="redeem-detail">
          消耗 <strong>{{ redeemTarget.pointsPrice }}</strong> 积分
        </div>
        <div class="redeem-detail">
          当前可用积分: <strong>{{ points?.availablePoints || 0 }}</strong>
        </div>
        <div class="redeem-remain" v-if="points && points.availablePoints >= redeemTarget.pointsPrice">
          兑换后剩余: <strong>{{ points.availablePoints - redeemTarget.pointsPrice }}</strong> 积分
        </div>
        <div v-else class="redeem-warn">积分不足，无法兑换</div>
      </div>
      <template #footer>
        <el-button @click="redeemVisible = false">取消</el-button>
        <el-button type="primary" :loading="redeeming" :disabled="!canRedeem" @click="doRedeem">
          确认兑换
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getUserPoints, checkin, getCheckinDates, listPointsProducts, redeemPoints } from '@/api/mall'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const points = ref<any>(null)
const products = ref<any[]>([])
const checkinDates = ref<any[]>([])
const todayCheckedIn = ref(false)
const checkingIn = ref(false)

const redeemVisible = ref(false)
const redeemTarget = ref<any>(null)
const redeeming = ref(false)

/**
 * 是否可兑换 - 当前积分是否足够兑换选中的商品
 */
const canRedeem = computed(() => {
  if (!redeemTarget.value || !points.value) return false
  return points.value.availablePoints >= redeemTarget.value.pointsPrice
})

/**
 * 计算本月所有日期字符串，用于签到日历展示
 * @returns {string[]} 格式为 YYYY-MM-DD 的日期数组
 */
const monthDays = computed(() => {
  const now = new Date()
  const year = now.getFullYear()
  const month = now.getMonth()
  const days = new Date(year, month + 1, 0).getDate()
  const result: any[] = []
  for (let d = 1; d <= days; d++) {
    const s = `${year}-${String(month + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`
    result.push(s)
  }
  return result
})

/**
 * 加载页面数据：积分概览、兑换商品列表、签到记录
 */
async function loadData() {
  loading.value = true
  try {
    const [ptsRes, prodRes, datesRes] = await Promise.all([
      getUserPoints(),
      listPointsProducts(),
      getCheckinDates()
    ])
    points.value = ptsRes || { availablePoints: 0, totalEarned: 0, totalSpent: 0 }
    products.value = prodRes || []
    checkinDates.value = datesRes || []

    const today = new Date().toISOString().slice(0, 10)
    todayCheckedIn.value = (datesRes || []).includes(today)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '加载失败')
  } finally {
    loading.value = false
  }
}

/**
 * 每日签到，签到成功后刷新积分数据
 */
async function doCheckin() {
  checkingIn.value = true
  try {
    const res = await checkin()
    if (res?.success) {
      ElMessage.success(res.message || '签到成功')
      todayCheckedIn.value = true
      checkinDates.value.push(new Date().toISOString().slice(0, 10))
      await loadData()
    } else {
      ElMessage.info(res?.message || '今日已签到')
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '签到失败')
  } finally {
    checkingIn.value = false
  }
}

/**
 * 打开兑换确认弹窗
 * @param {Object} p - 要兑换的商品对象
 */
function openRedeem(p: any) {
  redeemTarget.value = p
  redeemVisible.value = true
}

/**
 * 执行积分兑换，扣减积分并兑换商品
 */
async function doRedeem() {
  if (!canRedeem.value) return
  redeeming.value = true
  try {
    const res = await redeemPoints({ productId: redeemTarget.value.id })
    ElMessage.success(`兑换成功！消耗 ${res.pointsCost} 积分`)
    redeemVisible.value = false
    await loadData()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '兑换失败')
  } finally {
    redeeming.value = false
  }
}

onMounted(() => loadData())
</script>

<style scoped>
.points-page { min-height: 100vh; }
.container { max-width: 800px; margin: 0 auto; padding: 0 20px; }
.main-content { padding: 32px 20px 60px; }

.panel {
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-lg);
  padding: 20px 24px;
  margin-bottom: 16px;
  box-shadow: var(--shadow-sm);
}

.points-panel { padding: 24px 28px; }
.points-overview {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.points-label { font-size: 14px; color: var(--text-muted); margin-bottom: 8px; }
.points-amount {
  font-size: 36px;
  font-weight: 700;
  color: #f0a040;
}
.points-extra {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid var(--border-subtle);
  font-size: 13px;
  color: var(--text-muted);
}
.sep { margin: 0 12px; color: var(--border-base); }
.link { color: var(--color-primary, #409eff); margin-left: 4px; }

.checkin-calendar {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid var(--border-subtle);
}
.cal-title { font-size: 13px; color: var(--text-muted); margin-bottom: 8px; }
.cal-grid { display: flex; flex-wrap: wrap; gap: 4px; }
.cal-day {
  width: 32px; height: 32px; line-height: 32px;
  text-align: center; font-size: 12px;
  border-radius: 4px;
  background: var(--bg-base, #f5f5f5);
  color: var(--text-muted);
}
.cal-day.checked {
  background: #f0a040;
  color: #fff;
  font-weight: 600;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--border-subtle);
}
.panel-title { font-size: 16px; font-weight: 600; color: var(--text-primary); }

.product-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 16px; }
.product-card {
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  overflow: hidden;
  cursor: pointer;
  transition: box-shadow 0.2s;
}
.product-card:hover { box-shadow: 0 2px 12px rgba(0,0,0,0.1); }
.img-placeholder {
  width: 100%; height: 140px;
  display: flex; align-items: center; justify-content: center;
  background: var(--bg-base, #f5f5f5);
  color: var(--text-muted); font-size: 14px;
}
.product-info { padding: 10px 12px 14px; }
.product-name {
  font-size: 14px; font-weight: 500;
  color: var(--text-primary);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  margin-bottom: 8px;
}
.product-bottom { display: flex; justify-content: space-between; align-items: center; }
.product-type-tag {
  font-size: 12px; padding: 1px 6px;
  background: #ecf5ff; color: #409eff;
  border-radius: 3px;
}
.product-price { font-size: 15px; font-weight: 700; color: #f0a040; }
.product-stock { font-size: 12px; color: var(--text-muted); margin-top: 4px; }

.redeem-confirm { text-align: center; }
.redeem-name { font-size: 16px; font-weight: 600; margin-bottom: 16px; }
.redeem-detail { font-size: 14px; color: var(--text-secondary); margin-bottom: 8px; }
.redeem-remain { font-size: 13px; color: var(--text-muted); margin-top: 12px; }
.redeem-warn { font-size: 14px; color: #e33; margin-top: 12px; }
</style>
