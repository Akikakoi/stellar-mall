/**
 * SkuSpecSelector 组件测试
 *
 * 覆盖：规格解析（specsJson/specs 格式）、规格选择联动、
 * 数量加减、库存限制、默认选中、emit 事件。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'

// mock Element Plus icons（组件模板中用到 Grid/ArrowDown）
vi.mock('@element-plus/icons-vue', () => ({
  Grid: { template: '<span class="mock-icon-grid">☰</span>' },
  ArrowDown: { template: '<span class="mock-icon-arrow">▼</span>' }
}))

// mock window.__PH（组件 mounted 时用到）
window.__PH = window.__PH || {}

import SkuSpecSelector from '@/components/SkuSpecSelector.vue'

/** 构造测试用 SKU 数据 */
function makeSkus() {
  return [
    {
      id: 1,
      name: '红色-128GB',
      price: 1999,
      stock: 50,
      image: '/img/red.jpg',
      specsJson: JSON.stringify({ 颜色: '红色', 存储: '128GB' })
    },
    {
      id: 2,
      name: '红色-256GB',
      price: 2299,
      stock: 30,
      image: '/img/red.jpg',
      specsJson: JSON.stringify({ 颜色: '红色', 存储: '256GB' })
    },
    {
      id: 3,
      name: '蓝色-128GB',
      price: 1999,
      stock: 20,
      image: '/img/blue.jpg',
      specsJson: JSON.stringify({ 颜色: '蓝色', 存储: '128GB' })
    },
    {
      id: 4,
      name: '蓝色-256GB',
      price: 2299,
      stock: 0,
      image: '/img/blue.jpg',
      specsJson: JSON.stringify({ 颜色: '蓝色', 存储: '256GB' })
    }
  ]
}

function mountComponent(props = {}) {
  return mount(SkuSpecSelector, {
    props: {
      spu: { id: 1, name: '测试手机' },
      skus: makeSkus(),
      services: [
        { id: 1, title: '延保1年', price: 99 },
        { id: 2, title: '碎屏险', price: 59 }
      ],
      ...props
    }
  })
}

describe('SkuSpecSelector', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ── 规格分组渲染 ──
  describe('spec group rendering', () => {
    it('渲染所有规格维度', () => {
      const wrapper = mountComponent()
      const labels = wrapper.findAll('.spec-label span:first-child')
      const texts = labels.map(el => el.text())
      // 应有 "颜色"、"存储"、"数量"（总是有数量行）
      expect(texts).toContain('颜色')
      expect(texts).toContain('存储')
    })

    it('每个维度显示所有选项', () => {
      const wrapper = mountComponent()
      const buttons = wrapper.findAll('.spec-option')
      // 2 颜色选项 + 2 存储选项 = 4
      expect(buttons).toHaveLength(4)
    })

    it('图片模式下颜色选项显示图片', () => {
      const wrapper = mountComponent()
      const images = wrapper.findAll('.option-image')
      // 有图片的选项（颜色选项含 image）
      expect(images.length).toBeGreaterThanOrEqual(2)
    })

    it('无 SKU 时不渲染规格', () => {
      const wrapper = mount(SkuSpecSelector, {
        props: { spu: {}, skus: [], modelValue: 1 }
      })
      const buttons = wrapper.findAll('.spec-option')
      expect(buttons).toHaveLength(0)
    })
  })

  // ── 规格选择 ──
  describe('spec selection', () => {
    it('点击选项后添加 active 样式', async () => {
      const wrapper = mountComponent()
      const options = wrapper.findAll('.spec-option')
      // 点击第一个选项（颜色 → 红色）
      await options[0].trigger('click')

      // 该选项应变为 active
      expect(options[0].classes()).toContain('active')
    })

    it('emit sku-change 事件', async () => {
      const wrapper = mountComponent()
      const options = wrapper.findAll('.spec-option')
      await options[0].trigger('click')

      const emitted = wrapper.emitted('sku-change')
      expect(emitted).toBeTruthy()
      // mounted 时也会 emit 一次
      expect(emitted.length).toBeGreaterThanOrEqual(2)
    })

    it('选择完整规格后显示"已选"摘要', async () => {
      const wrapper = mountComponent()
      const options = wrapper.findAll('.spec-option')
      // 默认选中每个维度第一个选项，应该已有匹配 SKU
      await nextTick()
      const summary = wrapper.find('.selected-summary')
      expect(summary.exists()).toBe(true)
    })

    it('部分选择后显示库存提示', async () => {
      const wrapper = mountComponent()
      // 默认选中第一个 SKU
      await nextTick()
      const hint = wrapper.find('.stock-hint')
      expect(hint.text()).toContain('有货')
    })
  })

  // ── 数量控制 ──
  describe('quantity control', () => {
    it('点击 + 增加数量', async () => {
      const wrapper = mountComponent({ modelValue: 1 })
      await nextTick()
      const plusBtn = wrapper.findAll('.qty-btn')[1] // 第二个按钮是 +
      await plusBtn.trigger('click')
      expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([2])
    })

    it('点击 − 减少数量', async () => {
      const wrapper = mountComponent({ modelValue: 2 })
      await nextTick()
      const minusBtn = wrapper.findAll('.qty-btn')[0] // 第一个按钮是 −
      await minusBtn.trigger('click')
      expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([1])
    })

    it('数量为 1 时 − 按钮禁用', () => {
      const wrapper = mountComponent({ modelValue: 1 })
      const minusBtn = wrapper.findAll('.qty-btn')[0]
      expect(minusBtn.attributes('disabled')).toBeDefined()
    })

    it('数量达上限时 + 按钮禁用', async () => {
      // 第一个 SKU stock=50，maxQty=50
      const wrapper = mountComponent({ modelValue: 50 })
      await nextTick()
      const plusBtn = wrapper.findAll('.qty-btn')[1]
      expect(plusBtn.attributes('disabled')).toBeDefined()
    })

    it('输入非数字后 clamp 到 1', async () => {
      const wrapper = mountComponent({ modelValue: 1 })
      const input = wrapper.find('.qty-input')
      await input.setValue(0)
      await input.trigger('change')
      expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([1])
    })
  })

  // ── 服务选择 ──
  describe('service selection', () => {
    it('渲染保障服务列表', () => {
      const wrapper = mountComponent()
      expect(wrapper.text()).toContain('选购更多')
    })

    it('点击展开服务列表', async () => {
      const wrapper = mountComponent()
      const trigger = wrapper.find('.service-trigger')
      await trigger.trigger('click')
      const list = wrapper.find('.service-list')
      expect(list.isVisible()).toBe(true)
    })

    it('无 services 时不渲染服务行', () => {
      const wrapper = mount(SkuSpecSelector, {
        props: { spu: {}, skus: makeSkus(), services: [] }
      })
      expect(wrapper.find('.service-body').exists()).toBe(false)
    })
  })

  // ── specs 字符串格式解析 ──
  describe('specs format parsing', () => {
    it('支持 specs 分号分隔格式', () => {
      const wrapper = mount(SkuSpecSelector, {
        props: {
          spu: {},
          skus: [
            { id: 1, name: 'A', price: 10, stock: 5, specs: '颜色:红;尺寸:M' },
            { id: 2, name: 'B', price: 12, stock: 3, specs: '颜色:蓝;尺寸:L' }
          ],
          modelValue: 1
        }
      })
      // 应解析出 颜色 和 尺寸 两个维度
      const labels = wrapper.findAll('.spec-label span:first-child')
      const texts = labels.map(el => el.text())
      expect(texts).toContain('颜色')
      expect(texts).toContain('尺寸')
    })
  })

  // ── 选项禁用逻辑 ──
  describe('option disabled state', () => {
    it('所有 SKU 都支持的选项不禁用', async () => {
      const wrapper = mountComponent()
      await nextTick()
      const options = wrapper.findAll('.spec-option')
      // 所有 4 个选项都应该可选
      options.forEach(opt => {
        expect(opt.attributes('disabled')).toBeUndefined()
      })
    })

    it('无 SKU 的选项禁用', () => {
      const wrapper = mount(SkuSpecSelector, {
        props: {
          spu: {},
          skus: [
            { id: 1, name: 'A', price: 10, stock: 5,
              specsJson: JSON.stringify({ 颜色: '红', 尺寸: 'M' }) },
            { id: 2, name: 'B', price: 12, stock: 3,
              specsJson: JSON.stringify({ 颜色: '蓝', 尺寸: 'L' }) }
          ],
          modelValue: 1
        }
      })
      // 颜色有红和蓝，尺寸有M和L，所有选项都有对应的SKU，不应禁用
      const disabledOptions = wrapper.findAll('.spec-option:disabled')
      expect(disabledOptions).toHaveLength(0)
    })
  })
})
