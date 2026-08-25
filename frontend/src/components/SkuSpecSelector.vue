<template>
  <div class="sku-spec-selector">
    <!-- 规格维度 -->
    <div
      v-for="(group, groupIndex) in specGroups"
      :key="group.key"
      class="spec-row"
    >
      <div class="spec-label">
        <span>{{ group.label }}</span>
        <span v-if="group.key === 'color' && hasImageMode" class="view-toggle" @click="imageMode = !imageMode">
          <el-icon><Grid /></el-icon>
          {{ imageMode ? '切换小图模式' : '切换大图模式' }}
        </span>
      </div>
      <div class="spec-options" :class="{ 'image-mode': group.showImage && imageMode }">
        <button
          v-for="option in group.options"
          :key="option.value"
          class="spec-option"
          :class="{
            active: selectedValues[group.key] === option.value,
            disabled: isOptionDisabled(group.key, option.value),
            'has-image': group.showImage
          }"
          :disabled="isOptionDisabled(group.key, option.value)"
          @click="selectOption(group.key, option.value)"
        >
          <img
            v-if="group.showImage && option.image"
            :src="option.image"
            class="option-image"
            alt=""
            onerror="this.style.display='none'"
          />
          <span class="option-text">{{ option.value }}</span>
          <span v-if="option.badge" class="option-badge" :class="option.badgeType">{{ option.badge }}</span>
        </button>
      </div>
    </div>

    <!-- 已选摘要 -->
    <div class="selected-summary" v-if="selectedSku">
      <span class="summary-label">已选</span>
      <span class="summary-text">{{ selectedSku.name || formatSpecs(selectedSku) }}</span>
    </div>

    <!-- 数量 -->
    <div class="spec-row qty-row">
      <div class="spec-label">数量</div>
      <div class="qty-control">
        <button class="qty-btn" :disabled="quantity <= 1" @click="decreaseQty">−</button>
        <input
          v-model.number="quantity"
          class="qty-input"
          type="number"
          :min="1"
          :max="maxQty"
          @change="clampQty"
        />
        <button class="qty-btn" :disabled="quantity >= maxQty" @click="increaseQty">+</button>
        <span class="stock-hint">
          <template v-if="selectedSku">
            {{ selectedSku.stock > 0 ? `有货(限购${maxQty}件)` : '暂时缺货' }}
          </template>
          <template v-else>请选择完整规格</template>
        </span>
      </div>
    </div>

    <!-- 保障服务 -->
    <div class="spec-row service-row" v-if="services && services.length">
      <div class="spec-label">保障服务</div>
      <div class="service-body">
        <div class="service-trigger" @click="serviceExpanded = !serviceExpanded">
          <span>选购更多</span>
          <el-icon :class="{ rotated: serviceExpanded }"><ArrowDown /></el-icon>
        </div>
        <div v-show="serviceExpanded" class="service-list">
          <label
            v-for="svc in services"
            :key="svc.id"
            class="service-option"
            :class="{ active: selectedServices.includes(svc.id) }"
          >
            <input
              type="checkbox"
              :value="svc.id"
              v-model="selectedServices"
            />
            <span class="service-title">{{ svc.title }}</span>
            <span class="service-price">¥{{ Number(svc.price || 0).toFixed(2) }}</span>
          </label>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import type { PropType } from 'vue'
import { Grid, ArrowDown } from '@element-plus/icons-vue'
import type { Sku } from '@/types/models'

const props = defineProps({
  spu: { type: Object as PropType<Record<string, any>>, default: () => ({}) },
  skus: { type: Array as PropType<Sku[]>, default: () => [] },
  modelValue: { type: Number, default: 1 },
  services: { type: Array as PropType<any[]>, default: () => [] },
  selectedServices: { type: Array as PropType<number[]>, default: () => [] }
})

const emit = defineEmits(['update:modelValue', 'update:selectedServices', 'sku-change'])

const __PH = window.__PH

const imageMode = ref(true)
const selectedValues = ref<Record<string, any>>({})
const serviceExpanded = ref(false)

const quantity = computed({
  get: () => props.modelValue,
  set: (val: any) => {
    const v = Number(val) || 1
    emit('update:modelValue', v)
  }
})

const selectedServices = computed({
  get: () => props.selectedServices || [],
  set: (val: any) => emit('update:selectedServices', val)
})

/**
 * 将 SKU 的规格信息归一化为键值对对象
 * 支持 specsJson 和 specs 两种格式
 * @param {Object} sku - SKU 对象
 * @returns {Object} 规格键值对，如 { 颜色: '红色', 尺寸: 'M' }
 */
function normalizeSpecs(sku: any) {
  if (!sku) return {}
  if (sku.specsJson) {
    try {
      const parsed = typeof sku.specsJson === 'string' ? JSON.parse(sku.specsJson) : sku.specsJson
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) return parsed
    } catch (e: any) { /* 忽略异常，继续走 specs 解析 */ }
  }
  if (sku.specs) {
    const map: Record<string, any> = {}
    sku.specs.split(/;|；/).forEach((part: string) => {
      const [k, v] = part.split(/:|：/)
      if (k && v) map[k.trim()] = v.trim()
    })
    if (Object.keys(map).length > 0) return map
  }
  if (sku.name) return { 规格: sku.name }
  return {}
}

/**
 * 将 SKU 规格格式化为可读字符串
 * @param {Object} sku - SKU 对象
 * @returns {string} 格式化后的规格字符串，如 "颜色:红色；尺寸:M"
 */
function formatSpecs(sku: any) {
  const map = normalizeSpecs(sku)
  return Object.entries(map).map(([k, v]) => `${k}:${v}`).join('；')
}

/**
 * 解析后的 SKU 列表，每个 SKU 附加归一化的 specsMap
 * @returns {Array<Object>} 包含 specsMap 属性的 SKU 数组
 */
const parsedSkus = computed(() => {
  return props.skus.map((sku: any) => {
    const specs = normalizeSpecs(sku)
    return { ...sku, specsMap: specs }
  })
})

/**
 * 规格分组：从所有 SKU 的规格中提取维度，按规格键分组
 * 自动识别颜色类规格以支持图片展示模式
 * @returns {Array<Object>} 规格分组数组，每组包含 key、label、showImage、options
 */
const specGroups = computed(() => {
  if (!parsedSkus.value.length) return []

  const groupMap = new Map<string, any>()
  const order: string[] = []

  parsedSkus.value.forEach((sku: any) => {
    Object.entries(sku.specsMap).forEach(([key, value]) => {
      if (!groupMap.has(key)) {
        groupMap.set(key, { key, label: key, values: new Map() })
        order.push(key)
      }
      const group: any = groupMap.get(key)
      if (!group.values.has(value)) {
        group.values.set(value, { value, skuIds: new Set(), image: null, badge: null, badgeType: null })
      }
      const opt = group.values.get(value)
      opt.skuIds.add(sku.id)
      if (sku.image && (key.includes('颜色') || key.includes('色') || key.toLowerCase() === 'color')) {
        opt.image = opt.image || sku.image
      }
      if (sku.specsJson) {
        try {
          const meta = typeof sku.specsJson === 'string' ? JSON.parse(sku.specsJson) : sku.specsJson
          if (meta && meta[key]) {
            const m = meta[key]
            if (typeof m === 'object') {
              if (m.image) opt.image = m.image
              if (m.badge) {
                opt.badge = m.badge
                opt.badgeType = m.badgeType || 'hot'
              }
            }
          }
        } catch (e: any) {}
      }
    })
  })

  return order.map((key: any) => {
    const g: any = groupMap.get(key)
    const showImage = Array.from(g.values.values()).some((o: any) => o.image) &&
      (key.includes('颜色') || key.includes('色') || key.toLowerCase() === 'color')
    return {
      key,
      label: g.label,
      showImage,
      options: Array.from(g.values.values()) as any[]
    }
  })
})

/**
 * 是否显示图片模式切换按钮
 * @returns {boolean}
 */
const hasImageMode = computed(() => specGroups.value.some((g: any) => g.showImage))

/**
 * 当前选中的 SKU：根据 selectedValues 匹配完整 SKU
 * @returns {Object|null} 匹配到的 SKU 对象，未匹配时返回 null
 */
const selectedSku = computed(() => {
  if (!parsedSkus.value.length) return null
  return parsedSkus.value.find((sku: any) => {
    return Object.entries(selectedValues.value).every(([key, val]) => sku.specsMap[key] === val)
  }) || null
})

/**
 * 当前可购买的最大数量，基于选中 SKU 的库存
 * @returns {number}
 */
const maxQty = computed(() => {
  return (selectedSku.value?.stock as number) > 0 ? Math.min(selectedSku.value!.stock!, 999) : 999
})

/**
 * 判断规格选项是否禁用（无任何 SKU 包含该选项时禁用）
 * @param {string} groupKey - 规格键名
 * @param {string} value - 规格值
 * @returns {boolean}
 */
function isOptionDisabled(groupKey: string, value: string) {
  // 只要有任意 SKU 包含该选项，就允许点击。
  // 点击时 selectOption 会自动匹配最近的完整 SKU。
  return !parsedSkus.value.some((sku: any) => sku.specsMap[groupKey] === value)
}

/**
 * 选择规格选项，更新选中值并同步数量
 * @param {string} groupKey - 规格键名
 * @param {string} value - 规格值
 */
function selectOption(groupKey: string, value: string) {
  if (selectedValues.value[groupKey] === value) return
  selectedValues.value[groupKey] = value
  syncQuantity()
  emit('sku-change', selectedSku.value)
}

/**
 * 同步购买数量：当数量超过最大可购数量时自动修正
 */
function syncQuantity() {
  if (quantity.value > maxQty.value) {
    quantity.value = Math.max(1, maxQty.value)
  }
}

/**
 * 增加购买数量
 */
function increaseQty() {
  if (quantity.value < maxQty.value) quantity.value++
}

/**
 * 减少购买数量
 */
function decreaseQty() {
  if (quantity.value > 1) quantity.value--
}

/**
 * 限制购买数量在有效范围内
 */
function clampQty() {
  let v = Number(quantity.value) || 1
  v = Math.max(1, Math.min(v, maxQty.value))
  quantity.value = v
}

/**
 * 初始化规格选择：默认选中每个规格维度的第一个选项
 * 优先匹配完整 SKU，若默认组合不存在则回退到第一个 SKU
 */
function initSelections() {
  if (!specGroups.value.length) return
  const defaults: Record<string, any> = {}
  specGroups.value.forEach((g: any) => {
    if (g.options.length) defaults[g.key] = g.options[0].value
  })
  // 尝试找一个完全匹配的 SKU
  let matched = parsedSkus.value.find((sku: any) => {
    return Object.entries(defaults).every(([k, v]) => sku.specsMap[k] === v)
  })
  // 默认选项组合不存在时，回退到第一个 SKU
  if (!matched && parsedSkus.value.length) {
    matched = parsedSkus.value[0]
  }
  if (matched) {
    Object.assign(defaults, matched.specsMap)
  }
  selectedValues.value = defaults
  syncQuantity()
  emit('sku-change', selectedSku.value)
}

watch(() => props.skus, () => {
  initSelections()
}, { immediate: true, deep: true })

watch(() => selectedSku.value, (sku: any) => {
  emit('sku-change', sku)
})

onMounted(() => {
  emit('sku-change', selectedSku.value)
})
</script>

<style scoped>
.sku-spec-selector {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.spec-row {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.spec-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  color: var(--text-muted);
  line-height: 20px;
}

.view-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
}
.view-toggle:hover {
  color: var(--spec-active, var(--brand-primary));
}

.spec-options {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.spec-option {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 16px;
  min-height: 36px;
  border: 1px solid var(--border-base);
  border-radius: var(--radius-md);
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
  line-height: 1.4;
}

.spec-option:hover:not(:disabled) {
  border-color: var(--spec-active, var(--brand-primary));
  color: var(--text-primary);
}

.spec-option.active {
  border-color: var(--spec-active, var(--brand-primary));
  background: var(--spec-active-soft, var(--brand-primary-soft));
  color: var(--spec-active, var(--brand-primary));
  font-weight: 500;
}

.spec-option:disabled {
  opacity: 0.45;
  cursor: not-allowed;
  background: var(--bg-hover);
}

.spec-option.has-image {
  padding: 4px 12px 4px 4px;
}

.option-image {
  width: 32px;
  height: 32px;
  object-fit: cover;
  border-radius: 4px;
  background: var(--bg-hover);
}

.spec-options.image-mode .option-image {
  width: 48px;
  height: 48px;
}

.option-badge {
  position: absolute;
  top: -1px;
  right: -1px;
  transform: translate(30%, -30%);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 600;
  white-space: nowrap;
  z-index: 1;
}

.option-badge.hot {
  background: var(--spec-active, var(--brand-primary));
  color: #fff;
}

.option-badge.recommend {
  background: var(--status-success);
  color: #fff;
}

.selected-summary {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 14px;
  color: var(--text-secondary);
  padding: 8px 0;
}

.summary-label {
  color: var(--text-muted);
  min-width: 42px;
}

.summary-text {
  color: var(--text-primary);
  font-weight: 500;
}

.qty-control {
  display: inline-flex;
  align-items: center;
}

.qty-btn {
  width: 36px;
  height: 36px;
  border: 1px solid var(--border-base);
  background: var(--bg-card);
  color: var(--text-primary);
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
}

.qty-btn:first-of-type {
  border-radius: var(--radius-lg) 0 0 var(--radius-lg);
}

.qty-btn:nth-of-type(2) {
  border-radius: 0 var(--radius-lg) var(--radius-lg) 0;
}

.qty-btn:hover:not(:disabled) {
  border-color: var(--spec-active, var(--brand-primary));
  color: var(--spec-active, var(--brand-primary));
}

.qty-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.qty-input {
  width: 56px;
  height: 36px;
  border: 1px solid var(--border-base);
  border-left: none;
  border-right: none;
  text-align: center;
  font-size: 14px;
  color: var(--text-primary);
  background: var(--bg-card);
  outline: none;
}

.qty-input::-webkit-inner-spin-button,
.qty-input::-webkit-outer-spin-button {
  -webkit-appearance: none;
  margin: 0;
}

.stock-hint {
  margin-left: 12px;
  font-size: 13px;
  color: var(--text-muted);
}

.service-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.service-trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  color: var(--text-primary);
  cursor: pointer;
  width: fit-content;
}

.service-trigger .el-icon {
  transition: transform 0.2s;
}

.service-trigger .el-icon.rotated {
  transform: rotate(180deg);
}

.service-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px 0;
}

.service-option {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border: 1px solid var(--border-base);
  border-radius: var(--radius-md);
  background: var(--bg-card);
  cursor: pointer;
  transition: all 0.2s;
  width: fit-content;
  font-size: 14px;
}

.service-option:hover,
.service-option.active {
  border-color: var(--spec-active, var(--brand-primary));
  background: var(--spec-active-soft, var(--brand-primary-soft));
}

.service-option input[type='checkbox'] {
  accent-color: var(--spec-active, var(--brand-primary));
  cursor: pointer;
}

.service-title {
  color: var(--text-secondary);
}

.service-price {
  color: var(--spec-active, var(--brand-primary));
  font-weight: 500;
}
</style>
