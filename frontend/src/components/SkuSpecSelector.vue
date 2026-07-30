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

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { Grid, ArrowDown } from '@element-plus/icons-vue'

const props = defineProps({
  spu: { type: Object, default: () => ({}) },
  skus: { type: Array, default: () => [] },
  modelValue: { type: Number, default: 1 },
  services: { type: Array, default: () => [] },
  selectedServices: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue', 'update:selectedServices', 'sku-change'])

const __PH = window.__PH

const imageMode = ref(true)
const selectedValues = ref({})
const serviceExpanded = ref(false)

const quantity = computed({
  get: () => props.modelValue,
  set: (val) => {
    const v = Number(val) || 1
    emit('update:modelValue', v)
  }
})

const selectedServices = computed({
  get: () => props.selectedServices || [],
  set: (val) => emit('update:selectedServices', val)
})

function normalizeSpecs(sku) {
  if (!sku) return {}
  if (sku.specsJson) {
    try {
      const parsed = typeof sku.specsJson === 'string' ? JSON.parse(sku.specsJson) : sku.specsJson
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) return parsed
    } catch (e) { /* 忽略异常，继续走 specs 解析 */ }
  }
  if (sku.specs) {
    const map = {}
    sku.specs.split(/;|；/).forEach((part) => {
      const [k, v] = part.split(/:|：/)
      if (k && v) map[k.trim()] = v.trim()
    })
    if (Object.keys(map).length > 0) return map
  }
  if (sku.name) return { 规格: sku.name }
  return {}
}

function formatSpecs(sku) {
  const map = normalizeSpecs(sku)
  return Object.entries(map).map(([k, v]) => `${k}:${v}`).join('；')
}

const parsedSkus = computed(() => {
  return props.skus.map((sku) => {
    const specs = normalizeSpecs(sku)
    return { ...sku, specsMap: specs }
  })
})

const specGroups = computed(() => {
  if (!parsedSkus.value.length) return []

  const groupMap = new Map()
  const order = []

  parsedSkus.value.forEach((sku) => {
    Object.entries(sku.specsMap).forEach(([key, value]) => {
      if (!groupMap.has(key)) {
        groupMap.set(key, { key, label: key, values: new Map() })
        order.push(key)
      }
      const group = groupMap.get(key)
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
        } catch (e) {}
      }
    })
  })

  return order.map((key) => {
    const g = groupMap.get(key)
    const showImage = Array.from(g.values.values()).some((o) => o.image) &&
      (key.includes('颜色') || key.includes('色') || key.toLowerCase() === 'color')
    return {
      key,
      label: g.label,
      showImage,
      options: Array.from(g.values.values())
    }
  })
})

const hasImageMode = computed(() => specGroups.value.some((g) => g.showImage))

const selectedSku = computed(() => {
  if (!parsedSkus.value.length) return null
  return parsedSkus.value.find((sku) => {
    return Object.entries(selectedValues.value).every(([key, val]) => sku.specsMap[key] === val)
  }) || null
})

const maxQty = computed(() => {
  return selectedSku.value?.stock > 0 ? Math.min(selectedSku.value.stock, 999) : 999
})

function isOptionDisabled(groupKey, value) {
  // 只要有任意 SKU 包含该选项，就允许点击。
  // 点击时 selectOption 会自动匹配最近的完整 SKU。
  return !parsedSkus.value.some((sku) => sku.specsMap[groupKey] === value)
}

function selectOption(groupKey, value) {
  if (selectedValues.value[groupKey] === value) return
  selectedValues.value[groupKey] = value
  syncQuantity()
  emit('sku-change', selectedSku.value)
}

function syncQuantity() {
  if (quantity.value > maxQty.value) {
    quantity.value = Math.max(1, maxQty.value)
  }
}

function increaseQty() {
  if (quantity.value < maxQty.value) quantity.value++
}

function decreaseQty() {
  if (quantity.value > 1) quantity.value--
}

function clampQty() {
  let v = Number(quantity.value) || 1
  v = Math.max(1, Math.min(v, maxQty.value))
  quantity.value = v
}

function initSelections() {
  if (!specGroups.value.length) return
  const defaults = {}
  specGroups.value.forEach((g) => {
    if (g.options.length) defaults[g.key] = g.options[0].value
  })
  // 尝试找一个完全匹配的 SKU
  let matched = parsedSkus.value.find((sku) => {
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

watch(() => selectedSku.value, (sku) => {
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

.qty-btn:first-child {
  border-radius: var(--radius-md) 0 0 var(--radius-md);
}

.qty-btn:last-child {
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
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
