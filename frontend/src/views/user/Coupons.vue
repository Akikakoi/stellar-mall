<template>
  <div class="coupons-page">

    <div class="page-header">
      <h2>优惠券中心</h2>
    </div>

    <el-tabs v-model="tab" class="tabs">
      <el-tab-pane label="我的优惠券" name="my" />
      <el-tab-pane label="可领取" name="available" />
      <el-tab-pane label="已过期" name="expired" />
    </el-tabs>

    <div class="coupon-list" v-loading="loading">
      <!-- 可领取 -->
      <template v-if="tab === 'available'">
        <div v-for="c in availableCoupons" :key="c.id" class="coupon-card">
          <div class="coupon-left">
            <div class="coupon-amount">
              <template v-if="c.type === 1">
                <span class="symbol">¥</span><span class="value">{{ c.discountAmount }}</span>
              </template>
              <template v-else>
                <span class="value">{{ (c.discountAmount * 10).toFixed(1) }}</span><span class="symbol">折</span>
              </template>
            </div>
            <div class="coupon-condition">满{{ c.conditionAmount }}可用</div>
          </div>
          <div class="coupon-right">
            <div class="coupon-name">{{ c.name }}</div>
            <div class="coupon-time">有效期至 {{ c.endTime?.substring(0, 10) }}</div>
            <el-button type="primary" size="small" :loading="c.claiming" :disabled="c.claiming" @click="handleClaim(c)">立即领取</el-button>
          </div>
        </div>
        <el-empty v-if="!loading && availableCoupons.length === 0" class="glass-empty" description="暂无可领取的优惠券" />
      </template>

      <!-- 我的优惠券（不含已过期） -->
      <template v-if="tab === 'my'">
        <div v-for="c in activeCoupons" :key="c.id" class="coupon-card" :class="{ used: c.status === 2 }">
          <div class="coupon-left">
            <div class="coupon-amount">
              <template v-if="c.couponType === 1">
                <span class="symbol">¥</span><span class="value">{{ c.discountAmount }}</span>
              </template>
              <template v-else>
                <span class="value">{{ (c.discountAmount * 10).toFixed(1) }}</span><span class="symbol">折</span>
              </template>
            </div>
            <div class="coupon-condition">满{{ c.conditionAmount }}可用</div>
          </div>
          <div class="coupon-right">
            <div class="coupon-name">{{ c.couponName }}</div>
            <div class="coupon-time">有效期至 {{ c.endTime?.substring(0, 10) }}</div>
            <el-tag v-if="c.status === 2" type="info" size="small">已使用</el-tag>
            <el-tag v-else type="success" size="small">可使用</el-tag>
          </div>
        </div>
        <el-empty v-if="!loading && activeCoupons.length === 0" class="glass-empty" description="暂无优惠券" />
      </template>

      <!-- 已过期 -->
      <template v-if="tab === 'expired'">
        <div v-for="c in expiredCoupons" :key="c.id" class="coupon-card expired">
          <div class="coupon-left">
            <div class="coupon-amount">
              <template v-if="c.couponType === 1">
                <span class="symbol">¥</span><span class="value">{{ c.discountAmount }}</span>
              </template>
              <template v-else>
                <span class="value">{{ (c.discountAmount * 10).toFixed(1) }}</span><span class="symbol">折</span>
              </template>
            </div>
            <div class="coupon-condition">满{{ c.conditionAmount }}可用</div>
          </div>
          <div class="coupon-right">
            <div class="coupon-name">{{ c.couponName }}</div>
            <div class="coupon-time">有效期至 {{ c.endTime?.substring(0, 10) }}</div>
            <el-tag type="info" size="small">已过期</el-tag>
          </div>
        </div>
        <el-empty v-if="!loading && expiredCoupons.length === 0" class="glass-empty" description="暂无已过期的优惠券" />
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { userRequest } from '@/api/request'

const tab = ref('my')
const loading = ref(false)
const availableCoupons = ref<any[]>([])
const myCoupons = ref<any[]>([])

/**
 * 判断优惠券是否已过期（后端不主动清算 status=3，前端按有效期实时判断）
 * @param {Object} c - 用户优惠券对象（含 endTime）
 * @returns {boolean} 已过期返回 true
 */
function isExpired(c: any) {
  return c.status === 3 || (!!c.endTime && new Date(c.endTime) < new Date())
}

/** 我的优惠券（未过期：可使用 + 已使用） */
const activeCoupons = computed(() => myCoupons.value.filter((c: any) => !isExpired(c)))

/** 已过期的优惠券，按过期时间倒序（最近过期的在前） */
const expiredCoupons = computed(() =>
  myCoupons.value.filter((c: any) => isExpired(c))
    .sort((a: any, b: any) => (new Date(b.endTime || 0) as any) - (new Date(a.endTime || 0) as any))
)

/**
 * 加载可领取的优惠券列表
 */
async function loadAvailable() {
  loading.value = true
  try {
    const res: any = await userRequest({ url: '/user/coupon/available', method: 'get' })
    availableCoupons.value = Array.isArray(res) ? res : ((res as any)?.data || [])
  } catch (e: any) {} finally { loading.value = false }
}

/**
 * 加载我的优惠券列表（已领取、已使用、已过期，过期分栏展示见 computed）
 */
async function loadMy() {
  loading.value = true
  try {
    const res: any = await userRequest({ url: '/user/coupon/my', method: 'get' })
    myCoupons.value = Array.isArray(res) ? res : ((res as any)?.data || [])
  } catch (e: any) {} finally { loading.value = false }
}

/**
 * 领取优惠券，领取成功后刷新可领取列表和我的优惠券列表
 * @param {Object} coupon - 要领取的优惠券对象
 */
async function handleClaim(coupon: any) {
  coupon.claiming = true
  try {
    await userRequest({ url: `/user/coupon/claim/${coupon.id}`, method: 'post' })
    ElMessage.success('领取成功')
    await loadAvailable()
    await loadMy()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '领取失败')
  } finally {
    coupon.claiming = false
  }
}

onMounted(() => { loadAvailable(); loadMy() })
</script>

<style scoped>
.coupons-page { min-height: 100vh; }
.container { max-width: 1200px; margin: 0 auto; padding: 0 20px; }
.page-header { max-width: 900px; margin: 0 auto; padding: 24px 20px 0; }
.page-header h2 { margin: 0; color: var(--text-primary); }
.tabs { max-width: 900px; margin: 0 auto; padding: 0 20px; }
.coupon-list { max-width: 900px; margin: 0 auto; padding: 0 20px; display: flex; flex-direction: column; gap: 12px; }
.coupon-card {
  display: flex;
  background: var(--glass-bg);
  backdrop-filter: var(--backdrop-blur);
  -webkit-backdrop-filter: var(--backdrop-blur);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: var(--glass-shadow), inset 0 1px 0 var(--glass-highlight);
  transition: transform var(--transition-base), box-shadow var(--transition-base);
}
.coupon-card:hover {
  transform: var(--hover-lift);
  box-shadow: var(--glass-shadow), inset 0 1px 0 var(--glass-highlight);
}
.coupon-card.used, .coupon-card.expired { opacity: 0.5; }
.coupon-left {
  width: 140px;
  background: var(--glass-hover);
  border-right: 1px solid var(--glass-border);
  color: var(--text-primary);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px 12px;
  flex-shrink: 0;
}
.coupon-amount { display: flex; align-items: baseline; }
.coupon-amount .symbol { font-size: 16px; color: var(--text-secondary); }
.coupon-amount .value { font-size: 32px; font-weight: 700; color: var(--text-primary); }
.coupon-condition { font-size: 12px; margin-top: 4px; color: var(--text-muted); }
.coupon-right { flex: 1; padding: 16px 20px; display: flex; flex-direction: column; justify-content: center; gap: 8px; }
.coupon-name { font-size: 16px; font-weight: 600; color: var(--text-primary); }
.coupon-time { font-size: 12px; color: var(--text-muted); }
</style>
