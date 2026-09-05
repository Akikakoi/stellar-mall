<template>
  <div class="flow-page">
    <el-tabs v-model="activeTab" type="border-card" @tab-change="onTabChange">
      <!-- ==================== 入库 ==================== -->
      <el-tab-pane label="入库" name="inbound">
        <div class="panel">
          <div class="panel-head">
            <span class="panel-title">入库操作</span>
          </div>
          <el-form :model="form" label-width="120px" class="flow-form">
            <el-row :gutter="24">
              <el-col :span="12">
                <el-form-item label="选择 SKU">
                  <el-select v-model="form.skuId" filterable remote
                    :remote-method="searchSku" placeholder="搜索 SKU 名称或 ID"
                    :loading="skuLoading" style="width: 100%;"
                    @change="onSkuChange">
                    <el-option v-for="s in skuOptions" :key="s.id"
                      :label="`${s.name} (库存: ${s.stock})`" :value="s.id" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="入库类型">
                  <el-select v-model="form.businessType" style="width: 100%;">
                    <el-option label="采购入库" value="PURCHASE_IN" />
                    <el-option label="盘盈入库" value="INVENTORY_PROFIT" />
                    <el-option label="退货入库" value="RETURN_IN" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="24">
              <el-col :span="6">
                <el-form-item label="入库数量">
                  <el-input-number v-model="form.quantity" :min="1" :max="99999" style="width: 100%;" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="当前库存">
                  <span v-if="form.currentStock !== null" style="line-height: 32px;">{{ form.currentStock }}</span>
                  <span v-else style="color: var(--text-muted); line-height: 32px;">请先选择 SKU</span>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="关联单号">
                  <el-input v-model="form.businessNo" :placeholder="businessNoPlaceholder" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="24">
              <el-col :span="24">
                <el-form-item label="备注">
                  <el-input v-model="form.remark" :placeholder="remarkPlaceholder" maxlength="255" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="24" style="text-align: right;">
                <el-button type="primary" :loading="submitting" @click="handleSubmit" :disabled="!form.skuId || !form.quantity">
                  确认{{ isInbound ? '入库' : '出库' }}
                </el-button>
              </el-col>
            </el-row>
          </el-form>
        </div>

        <!-- 记录 -->
        <div class="panel">
          <div class="panel-head">
            <span class="panel-title">{{ isInbound ? '入库' : '出库' }}记录</span>
          </div>
          <el-table :data="logList" v-loading="logLoading" stripe size="small">
            <el-table-column label="时间" width="160">
              <template #default="{ row }">{{ row.createTime }}</template>
            </el-table-column>
            <el-table-column label="SKU ID" width="80" prop="skuId" />
            <el-table-column :label="isInbound ? '入库类型' : '出库类型'" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ businessTypeLabel(row.businessType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="isInbound ? '入库数量' : '出库数量'" width="100">
              <template #default="{ row }">
                <span :style="{ color: isInbound ? 'var(--status-success)' : 'var(--status-danger)' }">
                  {{ isInbound ? '+' : '' }}{{ row.quantity }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="变动前" width="80" prop="stockBefore" />
            <el-table-column label="变动后" width="80" prop="stockAfter" />
            <el-table-column label="关联单号" width="140" prop="businessNo" />
            <el-table-column label="备注" min-width="160" prop="remark" />
            <el-table-column label="操作人" width="100" prop="createUser" />
          </el-table>
          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="logPageNum" v-model:page-size="logPageSize" :total="logTotal"
              layout="total, prev, pager, next" @current-change="loadLogs" @size-change="loadLogs" small
            />
          </div>
        </div>
      </el-tab-pane>

      <!-- ==================== 出库 ==================== -->
      <el-tab-pane label="出库" name="outbound">
        <div class="panel">
          <div class="panel-head">
            <span class="panel-title">出库操作</span>
          </div>
          <el-form :model="form" label-width="120px" class="flow-form">
            <el-row :gutter="24">
              <el-col :span="12">
                <el-form-item label="选择 SKU">
                  <el-select v-model="form.skuId" filterable remote
                    :remote-method="searchSku" placeholder="搜索 SKU 名称或 ID"
                    :loading="skuLoading" style="width: 100%;"
                    @change="onSkuChange">
                    <el-option v-for="s in skuOptions" :key="s.id"
                      :label="`${s.name} (库存: ${s.stock})`" :value="s.id" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="出库类型">
                  <el-select v-model="form.businessType" style="width: 100%;">
                    <el-option label="销售出库" value="SALE_OUT" />
                    <el-option label="报废出库" value="SCRAP_OUT" />
                    <el-option label="盘亏出库" value="INVENTORY_LOSS" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="24">
              <el-col :span="6">
                <el-form-item label="出库数量">
                  <el-input-number v-model="form.quantity" :min="1" :max="99999" style="width: 100%;" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="当前库存">
                  <span v-if="form.currentStock !== null" style="line-height: 32px;">{{ form.currentStock }}</span>
                  <span v-else style="color: var(--text-muted); line-height: 32px;">请先选择 SKU</span>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="关联单号">
                  <el-input v-model="form.businessNo" :placeholder="businessNoPlaceholder" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="24">
              <el-col :span="24">
                <el-form-item label="备注">
                  <el-input v-model="form.remark" :placeholder="remarkPlaceholder" maxlength="255" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="24" style="text-align: right;">
                <el-button type="danger" :loading="submitting" @click="handleSubmit" :disabled="!form.skuId || !form.quantity">
                  确认{{ isInbound ? '入库' : '出库' }}
                </el-button>
              </el-col>
            </el-row>
          </el-form>
        </div>

        <!-- 记录 -->
        <div class="panel">
          <div class="panel-head">
            <span class="panel-title">{{ isInbound ? '入库' : '出库' }}记录</span>
          </div>
          <el-table :data="logList" v-loading="logLoading" stripe size="small">
            <el-table-column label="时间" width="160">
              <template #default="{ row }">{{ row.createTime }}</template>
            </el-table-column>
            <el-table-column label="SKU ID" width="80" prop="skuId" />
            <el-table-column :label="isInbound ? '入库类型' : '出库类型'" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ businessTypeLabel(row.businessType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="isInbound ? '入库数量' : '出库数量'" width="100">
              <template #default="{ row }">
                <span :style="{ color: isInbound ? 'var(--status-success)' : 'var(--status-danger)' }">
                  {{ isInbound ? '+' : '' }}{{ row.quantity }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="变动前" width="80" prop="stockBefore" />
            <el-table-column label="变动后" width="80" prop="stockAfter" />
            <el-table-column label="关联单号" width="140" prop="businessNo" />
            <el-table-column label="备注" min-width="160" prop="remark" />
            <el-table-column label="操作人" width="100" prop="createUser" />
          </el-table>
          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="logPageNum" v-model:page-size="logPageSize" :total="logTotal"
              layout="total, prev, pager, next" @current-change="loadLogs" @size-change="loadLogs" small
            />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { adminRequest } from '@/api/request'

const activeTab = ref<'inbound' | 'outbound'>('inbound')
const isInbound = computed(() => activeTab.value === 'inbound')

const submitting = ref(false)
const skuLoading = ref(false)
const skuOptions = ref<any[]>([])
const form = reactive<any>({
  skuId: null,
  quantity: 1,
  businessType: 'PURCHASE_IN',
  businessNo: '',
  remark: '',
  currentStock: null
})

const businessNoPlaceholder = computed(() => {
  if (isInbound.value) return '采购单号（选填）'
  const map: Record<string, string> = { SALE_OUT: '订单号（选填）', SCRAP_OUT: '报废单号（选填）', INVENTORY_LOSS: '盘点单号（选填）' }
  return map[form.businessType] || '关联单号（选填）'
})
const remarkPlaceholder = computed(() => (isInbound.value ? '入库原因（选填）' : '出库原因（选填）'))

const logLoading = ref(false)
const logList = ref<any[]>([])
const logTotal = ref(0)
const logPageNum = ref(1)
const logPageSize = ref(15)

/** 切换 tab 时重置表单并加载对应记录 */
function onTabChange() {
  form.skuId = null
  form.quantity = 1
  form.businessNo = ''
  form.remark = ''
  form.currentStock = null
  form.businessType = isInbound.value ? 'PURCHASE_IN' : 'SALE_OUT'
  skuOptions.value = []
  logPageNum.value = 1
  loadLogs()
}

async function searchSku(query: string) {
  if (!query) return
  skuLoading.value = true
  try {
    const res: any = await adminRequest({ url: '/admin/inventory/page', method: 'get', params: { name: query, pageSize: 20 } })
    const d = res?.data || res || {}
    skuOptions.value = d.records || d.list || []
  } catch (e: any) { /* ignore */ } finally { skuLoading.value = false }
}

function onSkuChange(skuId: number) {
  const sku = skuOptions.value.find(s => s.id === skuId)
  form.currentStock = sku ? (sku.stock || 0) : null
}

async function handleSubmit() {
  if (!form.skuId) { ElMessage.warning('请选择 SKU'); return }
  if (!form.quantity || form.quantity < 1) { ElMessage.warning('请输入有效的数量'); return }
  submitting.value = true
  try {
    const url = isInbound.value ? '/admin/inventory/inbound' : '/admin/inventory/outbound'
    await adminRequest({ url, method: 'post', data: { ...form } })
    ElMessage.success(isInbound.value ? '入库成功' : '出库成功')
    form.quantity = 1
    form.businessNo = ''
    form.remark = ''
    form.currentStock = null
    form.skuId = null
    skuOptions.value = []
    logPageNum.value = 1
    await loadLogs()
  } catch (e: any) { ElMessage.error(isInbound.value ? '入库失败' : '出库失败') } finally { submitting.value = false }
}

function businessTypeLabel(type: string) {
  const map: Record<string, string> = {
    PURCHASE_IN: '采购入库', INVENTORY_PROFIT: '盘盈入库', RETURN_IN: '退货入库',
    SALE_OUT: '销售出库', SCRAP_OUT: '报废出库', INVENTORY_LOSS: '盘亏出库',
    ADJUSTMENT: '调整'
  }
  return map[type] || type || '-'
}

async function loadLogs() {
  logLoading.value = true
  try {
    const url = isInbound.value ? '/admin/inventory/inbound/page' : '/admin/inventory/outbound/page'
    const res: any = await adminRequest({ url, method: 'get', params: { page: logPageNum.value, pageSize: logPageSize.value } })
    const d = res?.data || res || {}
    logList.value = d.records || d.list || []
    logTotal.value = d.total || 0
  } catch (e: any) { /* ignore */ } finally { logLoading.value = false }
}

onTabChange()
</script>

<style scoped>
.flow-page { display: flex; flex-direction: column; gap: 16px; }
.flow-page :deep(.el-tabs__content) { padding: 8px 4px 4px; }
.panel {
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: var(--radius-lg);
  padding: 20px 24px;
  box-shadow: var(--shadow-sm);
}
.panel + .panel { margin-top: 16px; }
.panel-head {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;
}
.panel-title {
  font-size: 18px; font-weight: 600; color: var(--text-primary);
}
.flow-form { max-width: 900px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
