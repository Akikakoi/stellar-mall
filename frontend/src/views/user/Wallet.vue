<template>
  <div class="wallet-page">
    <main class="container main-content" v-loading="loading">
      <!-- 余额面板 -->
      <div class="panel balance-panel">
        <div class="balance-row">
          <div class="balance-info">
            <div class="balance-label">可用余额</div>
            <div class="balance-amount">¥{{ Number(wallet?.balance || 0).toFixed(2) }}</div>
          </div>
          <el-button type="primary" @click="openRecharge">充值</el-button>
        </div>
        <div class="balance-extra">
          <span>累计充值 ¥{{ Number(wallet?.totalRecharge || 0).toFixed(2) }}</span>
          <span class="sep">|</span>
          <span>累计消费 ¥{{ Number(wallet?.totalSpent || 0).toFixed(2) }}</span>
        </div>
      </div>

      <!-- 交易记录 -->
      <div class="panel">
        <div class="panel-head"><span class="panel-title">交易记录</span></div>
        <el-table :data="transactions" stripe v-loading="txLoading" empty-text="暂无交易记录">
          <el-table-column label="类型" width="90">
            <template #default="{ row }">
              <el-tag :type="txTypeTag(row.type)" size="small">{{ row.typeText }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="金额" width="130">
            <template #default="{ row }">
              <span :class="amountClass(row.amount)">
                {{ Number(row.amount || 0) >= 0 ? '+' : '' }}¥{{ Number(row.amount || 0).toFixed(2) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="余额" width="130">
            <template #default="{ row }">¥{{ Number(row.balanceAfter || 0).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
          <el-table-column prop="createTime" label="时间" width="170" />
        </el-table>

        <div class="pagination-wrapper" v-if="txTotal > txPageSize">
          <el-pagination
            v-model:current-page="txPage"
            :page-size="txPageSize"
            :total="txTotal"
            layout="prev, pager, next"
            @current-change="loadTx"
          />
        </div>
      </div>
    </main>

    <!-- 充值弹窗 -->
    <el-dialog v-model="rechargeVisible" title="钱包充值" width="420px" destroy-on-close>
      <el-form :model="rechargeForm" label-width="100px">
        <el-form-item label="充值金额" required>
          <el-input-number v-model="rechargeForm.amount" :min="0.01" :max="99999.99" :precision="2" style="width: 200px" />
        </el-form-item>
        <el-form-item label="充值方式" required>
          <el-radio-group v-model="rechargeForm.channel">
            <el-radio value="WECHAT">微信支付</el-radio>
            <el-radio value="ALIPAY">支付宝</el-radio>
          </el-radio-group>
        </el-form-item>
        <div class="recharge-hint">模拟充值，无需真实支付</div>
      </el-form>
      <template #footer>
        <el-button @click="rechargeVisible = false">取消</el-button>
        <el-button type="primary" :loading="recharging" @click="doRecharge">确认充值</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getWallet, rechargeWallet, listWalletTransactions } from '@/api/mall'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const wallet = ref(null)

const txLoading = ref(false)
const transactions = ref([])
const txPage = ref(1)
const txPageSize = ref(10)
const txTotal = ref(0)

const rechargeVisible = ref(false)
const recharging = ref(false)
const rechargeForm = ref({ amount: 100, channel: 'WECHAT' })

/**
 * 根据交易类型返回对应的 Element UI tag 样式
 * @param {number} type - 交易类型
 * @returns {string} tag 类型
 */
function txTypeTag(type) {
  const map = { 1: 'success', 2: 'danger', 3: 'primary', 4: 'warning' }
  return map[type] || 'info'
}

/**
 * 根据金额正负返回对应的 CSS 类名
 * @param {number} amount - 金额
 * @returns {string} CSS 类名
 */
function amountClass(amount) {
  return Number(amount || 0) >= 0 ? 'amount-plus' : 'amount-minus'
}

/** 加载钱包余额及累计充值/消费信息 */
async function loadWallet() {
  loading.value = true
  try {
    const res = await getWallet()
    wallet.value = res || { balance: 0, totalRecharge: 0, totalSpent: 0 }
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '加载失败')
  } finally {
    loading.value = false
  }
}

/** 分页加载钱包交易流水 */
async function loadTx() {
  txLoading.value = true
  try {
    const res = await listWalletTransactions({ page: txPage.value, pageSize: txPageSize.value })
    const d = res || {}
    transactions.value = d.records || d.list || []
    txTotal.value = d.total || 0
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '加载流水失败')
  } finally {
    txLoading.value = false
  }
}

/** 打开充值弹窗并重置充值表单 */
function openRecharge() {
  rechargeForm.value = { amount: 100, channel: 'WECHAT' }
  rechargeVisible.value = true
}

/** 执行钱包充值，成功后刷新余额和交易流水 */
async function doRecharge() {
  if (!rechargeForm.value.amount || rechargeForm.value.amount <= 0) {
    ElMessage.warning('请输入充值金额')
    return
  }
  recharging.value = true
  try {
    await rechargeWallet(rechargeForm.value)
    ElMessage.success('充值成功')
    rechargeVisible.value = false
    await loadWallet()
    txPage.value = 1
    await loadTx()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '充值失败')
  } finally {
    recharging.value = false
  }
}

onMounted(() => {
  loadWallet()
  loadTx()
})
</script>

<style scoped>
.wallet-page { min-height: 100vh; }
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

.balance-panel {
  padding: 24px 28px;
}
.balance-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.balance-label {
  font-size: 14px;
  color: var(--text-muted);
  margin-bottom: 8px;
}
.balance-amount {
  font-size: 32px;
  font-weight: 700;
  color: var(--text-primary);
}
.balance-extra {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid var(--border-subtle);
  font-size: 13px;
  color: var(--text-muted);
}
.sep { margin: 0 12px; color: var(--border-base); }

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--border-subtle);
}
.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.amount-plus { color: #67c23a; font-weight: 600; }
.amount-minus { color: #e33; font-weight: 600; }

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

.recharge-hint {
  color: var(--text-muted);
  font-size: 13px;
  margin-top: 12px;
  text-align: center;
}
</style>
