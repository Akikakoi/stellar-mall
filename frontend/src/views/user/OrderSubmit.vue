<template>
  <div class="order-submit-page">

    <main class="container main-content" v-loading="submitting">
      <div class="card address-card">
        <div class="card-title">收货信息</div>
        <Transition name="address-fade" mode="out-in">
          <div v-if="selectedAddress" key="selected" class="selected-address">
            <div class="addr-header">
              <span class="consignee">{{ selectedAddress.consignee }}</span>
              <span class="phone">{{ selectedAddress.phone }}</span>
              <el-tag v-if="selectedAddress.isDefault === 1" type="primary" size="small" disable-transitions>默认</el-tag>
            </div>
            <div class="addr-text">{{ selectedAddress.province }}{{ selectedAddress.city }}{{ selectedAddress.district }} {{ selectedAddress.detail }}</div>
          </div>
          <div v-else key="form" class="address-form">
            <el-input v-model="form.consignee" placeholder="收货人姓名" style="width: 200px; margin-right: 12px;" />
            <el-input v-model="form.phone" placeholder="联系电话" style="width: 240px; margin-right: 12px;" />
            <el-input v-model="form.address" placeholder="详细地址（省市区+街道门牌号）" style="flex: 1;" />
          </div>
        </Transition>
        <div class="addr-switch" v-if="addresses.length > 0">
          <el-select v-model="selectedAddressId" placeholder="选择收货地址" popper-class="glass-select-popper" class="addr-select" @change="handleAddressChange">
            <el-option v-for="addr in addresses" :key="addr.id" :label="addressLabel(addr)" :value="addr.id">
              <div class="addr-option">
                <span class="addr-option-main">{{ addr.consignee }} {{ addr.phone }}</span>
                <span class="addr-option-text">{{ addr.province }}{{ addr.city }}{{ addr.district }} {{ addr.detail }}</span>
                <el-tag v-if="addr.isDefault === 1" type="primary" size="small" disable-transitions>默认</el-tag>
              </div>
            </el-option>
          </el-select>
          <el-button type="primary" link @click="openAddressDialog">新增地址</el-button>
        </div>
        <div class="addr-switch" v-else-if="!selectedAddress">
          <el-button type="primary" link @click="openAddressDialog">
            新增收货地址
          </el-button>
        </div>
      </div>

      <!-- 优惠券 -->
      <div class="card coupon-card">
        <div class="card-title">优惠券</div>
        <div v-if="availableCoupons.length > 0" class="coupon-select">
          <el-select v-model="selectedCouponId" placeholder="选择优惠券" clearable style="width: 300px" popper-class="glass-select-popper" @change="calcAmount">
            <el-option v-for="c in availableCoupons" :key="c.id" :label="couponLabel(c)" :value="c.id" />
          </el-select>
          <span v-if="couponDiscount > 0" class="coupon-discount">-¥{{ couponDiscount.toFixed(2) }}</span>
        </div>
        <span v-else class="no-coupon">暂无可用优惠券</span>
      </div>

      <!-- 积分抵扣 -->
      <div class="card points-card">
        <div class="card-title">积分抵扣</div>
        <div class="points-toggle">
          <el-switch v-model="usePoints" :disabled="!canUsePoints" @change="calcPointsDeduction" />
          <span class="points-label">
            使用积分抵扣 
            <template v-if="userPoints > 0">
              （当前可用 <b>{{ userPoints }}</b> 积分 ≈ ¥{{ (userPoints / 100).toFixed(2) }}）
            </template>
            <template v-else>（暂无可用积分）</template>
          </span>
          <span v-if="usePoints && pointsDeduction > 0" class="points-discount">
            -¥{{ pointsDeduction.toFixed(2) }}（{{ pointsDeductedCount }}积分）
          </span>
        </div>
      </div>

      <!-- 新增地址弹窗 -->
      <el-dialog class="glass-dialog addr-dialog" modal-class="glass-overlay" v-model="showAddressDialog" title="新增收货地址" width="550px"  :lock-scroll="false" @closed="resetAddForm">
        <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="80px">
          <el-form-item label="收货人" prop="consignee">
            <el-input v-model="addForm.consignee" placeholder="请输入收货人姓名" />
          </el-form-item>
          <el-form-item label="联系电话" prop="phone">
            <el-input v-model="addForm.phone" placeholder="请输入联系电话" />
          </el-form-item>
          <el-form-item label="所在地区" required>
            <el-cascader
              v-model="selectedArea"
              :options="areaOptions"
              placeholder="请选择省/市/区"
              clearable
              style="width: 100%"
              popper-class="glass-select-popper"
            />
          </el-form-item>
          <el-form-item label="详细地址" prop="detail">
            <el-input v-model="addForm.detail" type="textarea" :rows="2" placeholder="街道、门牌号等" />
          </el-form-item>
          <el-form-item label="设为默认">
            <el-switch v-model="addForm.isDefault" :active-value="1" :inactive-value="0" />
          </el-form-item>
        </el-form>

        <template #footer>
          <div class="dialog-footer">
            <el-button @click="showAddressDialog = false">取消</el-button>
            <el-button type="primary" :loading="addingAddress" @click="handleAddAddress">保存</el-button>
          </div>
        </template>
      </el-dialog>

      <div class="card items-card">
        <div class="card-title">商品清单</div>
        <div class="row header">
          <div class="col col-product">商品</div>
          <div class="col col-price">单价</div>
          <div class="col col-qty">数量</div>
          <div class="col col-subtotal">价格</div>
        </div>
        <div v-for="item in orderItems" :key="item.id || item.skuId" class="row item">
          <div class="col col-product">
            <div class="product-card">
              <img :src="item.image || item.pic || __PH" class="thumb" onerror="this.src=window.__PH;this.onerror=null" />
              <div class="name">{{ item.name }}</div>
            </div>
          </div>
          <div class="col col-price">¥{{ Number(item.price || 0).toFixed(2) }}</div>
          <div class="col col-qty">x{{ item.quantity || 1 }}</div>
          <div class="col col-subtotal amount">
            ¥{{ (Number(item.price || 0) * Number(item.quantity || 0)).toFixed(2) }}
            <div v-if="item.serviceFee > 0" class="service-row">+ 服务费 ¥{{ (Number(item.serviceFee || 0) * Number(item.quantity || 0)).toFixed(2) }}</div>
          </div>
        </div>
      </div>

      <div class="card pay-card">
        <div class="card-title">支付方式</div>
        <el-radio-group v-model="form.payMethod">
          <el-radio value="WECHAT">微信支付</el-radio>
          <el-radio value="ALIPAY">支付宝</el-radio>
          <el-radio value="WALLET">钱包支付 <span class="wallet-balance">（余额 ¥{{ Number(walletBalance || 0).toFixed(2) }}）</span></el-radio>
        </el-radio-group>
      </div>

      <div class="card remark-card">
        <div class="card-title">订单备注</div>
        <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填，请输入订单备注" />
      </div>

      <div class="summary-bar">
        <div class="summary-info">
          <div class="line">
            <span>商品件数：</span>
            <b>{{ orderCount }}</b>
          </div>
          <div class="line">
            <span>商品总价：</span>
            <b>¥{{ (orderAmount - serviceAmount).toFixed(2) }}</b>
          </div>
          <div class="line" v-if="serviceAmount > 0">
            <span>保障服务：</span>
            <span>+ ¥{{ serviceAmount.toFixed(2) }}</span>
          </div>
          <div class="line" v-if="couponDiscount > 0">
            <span>优惠：</span>
            <span class="discount">-¥{{ couponDiscount.toFixed(2) }}</span>
          </div>
          <div class="line" v-if="usePoints && pointsDeduction > 0">
            <span>积分抵扣：</span>
            <span class="discount">-¥{{ pointsDeduction.toFixed(2) }}</span>
          </div>
          <div class="line total-line">
            <span>应付金额：</span>
            <span class="amount">¥{{ finalAmount.toFixed(2) }}</span>
          </div>
        </div>
        <el-button type="primary" size="large" :loading="submitting" :disabled="orderItems.length === 0" @click="handleSubmit">
          提交订单
        </el-button>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useCartStore } from '@/stores/cart'
import { submitOrder, payOrder, getWallet, listAddresses, saveAddress, getUserPoints } from '@/api/mall'
import { userRequest, getOrCreateIdempotencyKey, resetIdempotencyKey } from '@/api/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { track } from '@/utils/tracker'
import areaData from 'china-area-data'

// 将 china-area-data 转换为 Cascader 需要的 options 格式
function convertAreaDataToOptions(data: any, parentCode: string): any[] {
  const options: any[] = []
  const areas = data[parentCode]
  if (!areas) return options
  for (const code in areas) {
    const name = areas[code]
    // 跳过"市辖区"、"市辖县"等冗余中间节点
    if (name === '市辖区' || name === '市辖县') {
      // 把它的子节点直接提升到当前层级
      const children = convertAreaDataToOptions(data, code)
      options.push(...children)
      continue
    }
    const option: Record<string, any> = {
      value: name,
      label: name,
      code: code
    }
    const children = convertAreaDataToOptions(data, code)
    if (children.length > 0) {
      option.children = children
    }
    options.push(option)
  }
  return options
}

const areaOptions = convertAreaDataToOptions(areaData, '86')

const __PH = window.__PH
const DRAFT_KEY = 'stellar_mall_draft_address'

const router = useRouter()
const route = useRoute()
const cartStore = useCartStore()

const submitting = ref(false)

// 直接购买模式：从 query 参数解析商品
const directItems = ref<any[]>([])
const isDirect = computed(() => directItems.value.length > 0)

/**
 * 从路由 query 参数中解析直接购买的商品数据
 */
function parseDirectItems() {
  try {
    const raw = route.query.direct
    if (raw) {
      const parsed = JSON.parse(raw as string)
      if (Array.isArray(parsed) && parsed.length > 0) {
        directItems.value = parsed
      }
    }
  } catch (e: any) {
    // ignore parse error
  }
}
parseDirectItems()

const orderItems = computed(() => isDirect.value ? directItems.value : cartStore.checkedItems)

const orderCount = computed(() => {
  return orderItems.value.reduce((sum: any, it: any) => sum + (Number(it.quantity) || 0), 0)
})

const orderAmount = computed(() => {
  return orderItems.value.reduce((sum: any, it: any) => {
    const itemTotal = Number(it.price || 0) * Number(it.quantity || 0)
    const serviceFee = Number(it.serviceFee || 0) * Number(it.quantity || 0)
    return sum + itemTotal + serviceFee
  }, 0)
})

const serviceAmount = computed(() => {
  return orderItems.value.reduce((sum: any, it: any) => {
    return sum + Number(it.serviceFee || 0) * Number(it.quantity || 0)
  }, 0)
})

const form = reactive<any>({
  consignee: '',
  phone: '',
  address: '',
  payMethod: 'WECHAT',
  remark: ''
})

// ==================== 地址簿 ====================
const addresses = ref<any[]>([])
const selectedAddress = ref<any>(null)
const selectedAddressId = ref<any>(null)

// 新增地址
const showAddressDialog = ref(false)
const addingAddress = ref(false)
const addFormRef = ref<any>(null)
const addForm = reactive<any>({
  consignee: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  isDefault: 0
})

const selectedArea = ref<string[]>([])

const addRules: Record<string, any> = {
  consignee: [{ required: true, message: '请输入收货人', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入联系电话', trigger: 'blur' }],
  detail: [{ required: true, message: '请输入详细地址', trigger: 'blur' }]
}

// ==================== 优惠券 / 钱包 ====================
const availableCoupons = ref<any[]>([])
const selectedCouponId = ref<any>(null)
const couponDiscount = ref(0)
const walletBalance = ref(0)

const finalAmount = computed(() => Math.max(0, orderAmount.value - couponDiscount.value - pointsDeduction.value))

// ==================== 积分抵扣 ====================
const userPoints = ref(0)
const usePoints = ref(false)
const pointsDeduction = ref(0)
const pointsDeductedCount = ref(0)

const canUsePoints = computed(() => userPoints.value >= 100 && finalAmountWithoutPoints.value > 0)

// 不含积分抵扣的应付金额（用于计算最大可抵扣积分）
const finalAmountWithoutPoints = computed(() => Math.max(0, orderAmount.value - couponDiscount.value))

/**
 * 计算积分抵扣金额，积分不足或抵扣金额为 0 时自动关闭积分抵扣
 * @param {boolean} val - 是否开启积分抵扣
 */
function calcPointsDeduction(val: any) {
  if (!val) {
    pointsDeduction.value = 0
    pointsDeductedCount.value = 0
    return
  }
  // 积分抵扣金额不能超过应付金额
  const maxDeductAmount = finalAmountWithoutPoints.value
  // 100积分 = 1元，最多使用用户拥有的积分
  const maxPointsValue = userPoints.value / 100
  // 取两者最小值
  const deductAmount = Math.min(maxPointsValue, maxDeductAmount)
  // 向下取整到积分单位
  pointsDeductedCount.value = Math.floor(deductAmount * 100)
  pointsDeduction.value = Number((pointsDeductedCount.value / 100).toFixed(2))
  if (pointsDeductedCount.value <= 0) {
    usePoints.value = false
  }
}

// ==================== localStorage 缓存：手动填写的收货信息 ====================
/** 从 localStorage 恢复上次手动填写的收货信息草稿 */
function loadDraftAddress() {
  try {
    const raw = localStorage.getItem(DRAFT_KEY)
    if (raw) {
      const draft = JSON.parse(raw)
      if (draft.consignee) form.consignee = draft.consignee
      if (draft.phone) form.phone = draft.phone
      if (draft.address) form.address = draft.address
    }
  } catch (e: any) {
    // ignore
  }
}

/** 将手动填写的收货信息缓存到 localStorage，地址簿模式下不缓存 */
function saveDraftAddress() {
  if (selectedAddress.value) return // 使用了地址簿，不缓存
  const draft: Record<string, any> = { consignee: form.consignee, phone: form.phone, address: form.address }
  if (draft.consignee || draft.phone || draft.address) {
    localStorage.setItem(DRAFT_KEY, JSON.stringify(draft))
  }
}

// 手动输入时实时缓存
watch(
  () => [form.consignee, form.phone, form.address],
  () => {
    if (!selectedAddress.value) {
      saveDraftAddress()
    }
  }
)

// ==================== 地址簿 CRUD ====================
/** 加载用户地址簿，自动选中默认地址，若无地址则回退到 localStorage 草稿 */
async function loadAddresses() {
  try {
    const res = await listAddresses()
    addresses.value = Array.isArray(res) ? res : ((res as any)?.data || [])
    const def = addresses.value.find((a: any) => a.isDefault === 1) || addresses.value[0]
    if (def) {
      selectedAddress.value = def
      selectedAddressId.value = def.id
    } else {
      // 地址簿为空，尝试从 localStorage 恢复上次手动填写的信息
      loadDraftAddress()
    }
  } catch (e: any) {
    // 加载失败时也尝试读取草稿
    loadDraftAddress()
  }
}

/**
 * 生成地址选择器的选项标签
 * @param {Object} addr - 地址对象
 * @returns {string} 格式化的地址标签
 */
function addressLabel(addr: any) {
  return `${addr.consignee} ${addr.phone} · ${addr.province}${addr.city}${addr.district} ${addr.detail}`
}

/**
 * 根据选中的地址 ID 切换收货地址，切换后清除 localStorage 草稿
 * @param {number} id - 地址 ID
 */
function handleAddressChange(id: any) {
  const addr = addresses.value.find((a: any) => a.id === id)
  if (addr) {
    selectedAddress.value = addr
    localStorage.removeItem(DRAFT_KEY)
  }
}

/** 打开新增地址弹窗，若当前有手动填写的内容则预填到表单中 */
function openAddressDialog() {
  showAddressDialog.value = true
  // 如果当前有手动填写的内容，预填到新增表单
  if (!selectedAddress.value && (form.consignee || form.phone || form.address)) {
    addForm.consignee = form.consignee
    addForm.phone = form.phone
    addForm.detail = form.address
    addForm.province = ''
    addForm.city = ''
    addForm.district = ''
    addForm.isDefault = addresses.value.length === 0 ? 1 : 0
  }
  nextTick(() => addFormRef.value?.clearValidate())
}

/** 重置新增地址表单 */
function resetAddForm() {
  addForm.consignee = ''
  addForm.phone = ''
  addForm.province = ''
  addForm.city = ''
  addForm.district = ''
  addForm.detail = ''
  addForm.isDefault = 0
  selectedArea.value = []
}

/** 保存新地址并刷新地址列表，自动选中新增的地址 */
async function handleAddAddress() {
  const valid = await addFormRef.value?.validate().catch(() => false)
  if (!valid) return
  // 检查是否选择了省市区
  if (!selectedArea.value || selectedArea.value.length < 3) {
    ElMessage.warning('请选择省市区')
    return
  }
  // 从 cascader 中提取省市区名称并赋值到 addForm
  addForm.province = selectedArea.value[0] || ''
  addForm.city = selectedArea.value[1] || ''
  addForm.district = selectedArea.value[2] || ''

  addingAddress.value = true
  try {
    const res = await saveAddress({ ...addForm })
    ElMessage.success('地址已添加')
    resetAddForm()
    // 重新加载地址列表
    await loadAddresses()
    // 立即选中新地址
    const newAddr = addresses.value.find((a: any) => a.id === res?.id || a.id === res?.data?.id)
    if (newAddr) {
      selectedAddress.value = newAddr
      selectedAddressId.value = newAddr.id
    } else if (addresses.value.length > 0) {
      const fallback = addresses.value.find((a: any) => a.isDefault === 1) || addresses.value[0]
      selectedAddress.value = fallback
      selectedAddressId.value = fallback.id
    }
    showAddressDialog.value = false
    localStorage.removeItem(DRAFT_KEY)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '新增地址失败')
  } finally {
    addingAddress.value = false
  }
}

// ==================== 优惠券 ====================
/** 加载用户拥有的可用优惠券列表（过滤已过期/未到生效时间的券） */
async function loadCoupons() {
  try {
    const res: any = await userRequest({ url: '/user/coupon/my', method: 'get', params: { status: 1 } })
    const list: any[] = Array.isArray(res) ? (res as any[]) : ((res as any)?.data || [])
    const now = new Date()
    availableCoupons.value = list.filter((c: any) =>
      (!c.endTime || new Date(c.endTime) >= now) && (!c.startTime || new Date(c.startTime) <= now)
    )
  } catch (e: any) {}
}

/**
 * 生成优惠券选项的标签文本
 * @param {Object} c - 优惠券对象
 * @returns {string} 优惠券描述标签
 */
function couponLabel(c: any) {
  const type = c.couponType || c.type
  if (type === 1) return `${c.couponName || c.name} 满${c.conditionAmount}减${c.discountAmount}`
  return `${c.couponName || c.name} 满${c.conditionAmount}打${(c.discountAmount * 10).toFixed(1)}折`
}

/** 根据选中的优惠券计算优惠金额，券过期或订单金额不满足门槛则自动取消选中 */
function calcAmount() {
  couponDiscount.value = 0
  if (!selectedCouponId.value) return
  const c = availableCoupons.value.find((x: any) => x.id === selectedCouponId.value)
  if (!c) return
  // 兜底：页面停留期间所选券可能已过期
  if (c.endTime && new Date(c.endTime) < new Date()) {
    ElMessage.warning('优惠券已过期，无法使用')
    selectedCouponId.value = null
    return
  }
  const amount = orderAmount.value
  const cond = Number(c.conditionAmount || 0)
  if (amount < cond) {
    ElMessage.warning(`订单金额未满${cond}元，无法使用该优惠券`)
    selectedCouponId.value = null
    return
  }
  const type = c.couponType || c.type
  if (type === 1) {
    couponDiscount.value = Number(c.discountAmount || 0)
  } else {
    couponDiscount.value = amount * (1 - Number(c.discountAmount || 1))
  }
}

// ==================== 提交订单 ====================
/** 提交订单：校验收货信息、组装订单数据，提交成功后发起支付并跳转订单列表 */
async function handleSubmit() {
  // 业务动作维度的幂等键：一次点击 = 一次动作，重试 / 重复点击复用同一 key，
  // 防止后端幂等切面因 key 每次不同而拦截不住（会创建两笔订单）
  const SUBMIT_ACTION = 'order:submit'
  const idempotencyKey = getOrCreateIdempotencyKey(SUBMIT_ACTION)
  if (orderItems.value.length === 0) {
    ElMessage.warning('请先选择商品')
    return
  }
  if (!selectedAddress.value && (!form.consignee || !form.phone || !form.address)) {
    ElMessage.warning('请填写收货信息或选择已有地址')
    return
  }
  const addr = selectedAddress.value
  if (addr && (!addr.consignee || !addr.phone)) {
    ElMessage.warning('所选地址信息不完整')
    return
  }
  // 提交前兜底：所选优惠券若已过期，直接提示并中止
  if (selectedCouponId.value) {
    const c = availableCoupons.value.find((x: any) => x.id === selectedCouponId.value)
    if (c && c.endTime && new Date(c.endTime) < new Date()) {
      ElMessage.warning('优惠券已过期，无法使用')
      selectedCouponId.value = null
      couponDiscount.value = 0
      return
    }
  }
  submitting.value = true
  try {
    const payMethodMap: Record<string, any> = { WECHAT: 1, ALIPAY: 2, WALLET: 4 }
    const items = orderItems.value.map((it: any) => {
      const skuId = Number(it.skuId || it.id)
      const quantity = Number(it.quantity || 1)
      const price = Number(it.price || 0)
      if (isNaN(skuId) || skuId <= 0) throw new Error(`无效的商品ID: ${it.skuId || it.id}`)
      if (isNaN(quantity) || quantity <= 0) throw new Error(`无效的数量: ${it.quantity}`)
      if (isNaN(price) || price < 0) throw new Error(`无效的价格: ${it.price}`)
      const extraAmount = Number(it.serviceFee || 0) * quantity
      return { skuId, quantity, price, extraAmount: extraAmount > 0 ? extraAmount : undefined }
    })
    const payload: Record<string, any> = {
      address: addr ? `${addr.province || ''}${addr.city || ''}${addr.district || ''} ${addr.detail || ''}` : form.address,
      consignee: addr?.consignee || form.consignee,
      phone: addr?.phone || form.phone,
      payMethod: payMethodMap[form.payMethod] || 1,
      remark: form.remark,
      userCouponId: selectedCouponId.value || undefined,
      discountAmount: couponDiscount.value ? Number(couponDiscount.value.toFixed(2)) : undefined,
      usePoints: usePoints.value || undefined,
      pointsAmount: usePoints.value ? Number(pointsDeduction.value.toFixed(2)) : undefined,
      items,
      // 立即购买不清空购物车，购物车下单才清空
      clearCart: !isDirect.value
    }
    const res = await submitOrder(payload, idempotencyKey)
    const orderId = res?.id || res?.orderId
    const payAmount = Number(res?.payAmount ?? finalAmount.value)
    submitting.value = false

    // 下单成功 → 本次业务动作完成，重置幂等键（下一次点击是新动作，用新 key）。
    // 注意：仅在成功后重置；失败时保留 key，用户重试仍复用同一 key，不会重复下单
    resetIdempotencyKey(SUBMIT_ACTION)

    // 购物车下单：提交成功后立即清空本地已勾选项，不论用户是否付款
    if (!isDirect.value) {
      cartStore.clearChecked()
    }

    // 如果使用的是手动填写的地址，保存到 localStorage 以便下次使用
    if (!addr) {
      saveDraftAddress()
    }

    // 埋点：下单转化（order_placed），金额与商品件数供漏斗分析
    try {
      track('order_placed', {
        scene: 'order',
        amount: payAmount || null,
        extra: { orderId: orderId ?? null, items: Array.isArray(items) ? items.length : 0, direct: !!isDirect.value }
      })
    } catch { /* ignore */ }

    if (orderId) {
      try {
        await ElMessageBox.confirm(
          `订单已生成，应付金额：¥${payAmount.toFixed(2)}，确认支付？`,
          '支付确认',
          { type: 'warning', confirmButtonText: '确认支付', cancelButtonText: '暂不支付' }
        )
        await (payOrder as any)(orderId)
        ElMessage.success('支付成功，订单已提交')
      } catch (e: any) {
        if (e !== 'cancel' && e?.message) {
          ElMessage.error(e?.response?.data?.msg || e?.message || '支付失败')
        }
        ElMessage.info('订单已保存为待付款，可在订单列表中继续支付')
      }
    }

    router.push('/order/list')
  } catch (e: any) {
    console.error('submitOrder error:', e)
    ElMessage.error(e?.message || '提交订单失败')
  } finally {
    submitting.value = false
  }
}

// ==================== 生命周期 ====================
onMounted(async () => {
  if (!isDirect.value) {
    try { await cartStore.load() } catch (e: any) {}
  }
  await loadAddresses()
  await loadCoupons()
  try {
    const wallet = await getWallet()
    walletBalance.value = Number(wallet?.balance || 0)
  } catch (e: any) { /* ignore */ }
  try {
    const pts = await getUserPoints()
    userPoints.value = Number(pts?.availablePoints || (pts as any)?.data?.availablePoints || 0)
  } catch (e: any) { /* ignore */ }
})
</script>

<style scoped>
.order-submit-page { min-height: 100vh; padding-bottom: 120px; }
.container { max-width: 1080px; margin: 0 auto; padding: 0 20px; }

.main-content { padding: 24px 20px 40px; }

.card {
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-lg);
  padding: 20px 24px;
  margin-bottom: 16px;
  backdrop-filter: var(--backdrop-blur);
  -webkit-backdrop-filter: var(--backdrop-blur);
  box-shadow: var(--glass-shadow);
}
.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 16px;
}
.address-info { display: flex; flex-wrap: wrap; gap: 4px; }
.selected-address { padding: 4px 0; min-height: 56px; display: flex; flex-direction: column; justify-content: center; }
.addr-header { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
.addr-header .consignee { font-weight: 600; color: var(--text-primary); }
.addr-header .phone { color: var(--text-muted); }
.addr-text { color: var(--text-secondary); font-size: 14px; }
.addr-switch { margin-top: 10px; display: flex; align-items: center; gap: 12px; }
.addr-select { width: 560px; max-width: 100%; }
.addr-option { display: flex; align-items: center; gap: 8px; max-width: 520px; }
.addr-option-main { font-weight: 600; color: var(--text-primary); flex-shrink: 0; }
.addr-option-text { color: var(--text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.address-form { display: flex; flex-wrap: wrap; gap: 4px; align-items: center; min-height: 56px; }

.coupon-select { display: flex; align-items: center; gap: 12px; }
.coupon-discount { color: var(--text-secondary); font-weight: 600; font-size: 16px; }
.no-coupon { color: var(--text-muted); font-size: 14px; }

.points-toggle { display: flex; align-items: center; gap: 12px; }
.points-label { color: var(--text-secondary); font-size: 14px; }
.points-label b { color: var(--brand-primary); }
.points-discount { color: var(--text-secondary); font-weight: 600; font-size: 16px; }

.dialog-footer { display: flex; justify-content: flex-end; align-items: center; gap: 8px; }

.row {
  display: grid;
  grid-template-columns: 1fr 120px 120px 140px;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid var(--glass-border);
}
.row.header {
  background: var(--glass-bg);
  color: var(--text-secondary);
  font-weight: 600;
  padding: 12px 0;
  border-radius: var(--radius-sm);
  margin: -8px 0 0;
  border-bottom: none;
  backdrop-filter: var(--backdrop-blur);
  -webkit-backdrop-filter: var(--backdrop-blur);
}
.row.item:last-child { border-bottom: none; }
.product-card { display: flex; align-items: center; gap: 12px; }
.thumb { width: 64px; height: 64px; border-radius: var(--radius-sm); object-fit: cover; }
.name { color: var(--text-primary); font-size: 14px; }
.col-price, .col-subtotal { text-align: center; color: var(--text-primary); }
.service-row { font-size: 11px; color: var(--brand-primary); margin-top: 2px; font-weight: 400; }
.wallet-balance { color: var(--brand-primary); font-size: 12px; margin-left: 2px; }
.amount { color: var(--text-primary); font-weight: 600; }

.summary-bar {
  position: fixed;
  bottom: 0; left: 0; right: 0;
  height: 80px;
  background: var(--glass-bg);
  backdrop-filter: var(--backdrop-blur);
  -webkit-backdrop-filter: var(--backdrop-blur);
  border-top: 1px solid var(--glass-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 40px;
  box-shadow: var(--glass-shadow);
  z-index: 200;
}
.summary-info { display: flex; align-items: baseline; gap: 32px; }
.summary-info .line { color: var(--text-secondary); }
.summary-info .line b { color: var(--text-primary); }
.summary-info .amount { font-size: 28px; font-weight: 700; margin-left: 4px; }
.summary-info .discount { color: var(--text-secondary); font-weight: 600; }

/* Transition: 地址切换淡入淡出 */
.address-fade-enter-active,
.address-fade-leave-active {
  transition: opacity 0.15s ease;
}
.address-fade-enter-from,
.address-fade-leave-to {
  opacity: 0;
}
</style>

<style>
/* 新增地址弹窗：磨砂玻璃 */
.el-dialog.addr-dialog {
  border-radius: var(--radius-lg);
  background: var(--glass-bg);
  backdrop-filter: var(--backdrop-blur);
  -webkit-backdrop-filter: var(--backdrop-blur);
  border: 1px solid var(--glass-border);
  box-shadow: var(--glass-shadow);
}
.el-dialog.addr-dialog .el-dialog__header {
  color: var(--text-primary);
}
.el-dialog.addr-dialog .el-dialog__body {
  color: var(--text-secondary);
}
/* 弹窗蒙版 */
.glass-overlay {
  background: rgba(0, 0, 0, 0.3) !important;
  backdrop-filter: blur(4px) !important;
  -webkit-backdrop-filter: blur(4px) !important;
}
/* 下拉选择器磨砂 */
.glass-select-popper {
  background: rgba(255, 255, 255, 0.95) !important;
  backdrop-filter: blur(12px) !important;
  -webkit-backdrop-filter: blur(12px) !important;
  border: 1px solid var(--glass-border) !important;
}
html.theme-dark .glass-select-popper {
  background: rgba(29, 29, 31, 0.95) !important;
}
.glass-select-popper .el-select-dropdown__item {
  color: var(--text-primary) !important;
}
.glass-select-popper .el-select-dropdown__item.hover,
.glass-select-popper .el-select-dropdown__item:hover {
  background: rgba(255, 255, 255, 0.08) !important;
}
</style>
