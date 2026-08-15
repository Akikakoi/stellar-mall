/**
 * useCartStore 单元测试
 *
 * 覆盖：购物车计算（总数/总金额/选中/全选）、
 * 勾选切换、本地增删、异常保护。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// mock API 模块 — cart 的 load/add/updateQty/remove/clear 都依赖这些
vi.mock('@/api/mall', () => ({
  listCart: vi.fn(),
  addCart: vi.fn(),
  updateCartQty: vi.fn(),
  deleteCart: vi.fn(),
  clearCart: vi.fn()
}))

import { useCartStore } from '@/stores/cart'
import { listCart } from '@/api/mall'

function createStore() {
  setActivePinia(createPinia())
  return useCartStore()
}

/** 构造一个 mock 购物车项 */
function mockItem(overrides = {}) {
  return {
    id: 1,
    skuId: 101,
    spuId: 1001,
    name: '测试商品',
    price: 99.00,
    quantity: 2,
    checked: true,
    services: [],
    serviceFee: 0,
    ...overrides
  }
}

describe('useCartStore', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  // ── Getters: 空购物车 ──
  describe('empty cart', () => {
    it('totalCount 为 0', () => {
      const store = createStore()
      expect(store.totalCount).toBe(0)
    })

    it('totalAmount 为 0', () => {
      const store = createStore()
      expect(store.totalAmount).toBe(0)
    })

    it('checkedItems 为空数组', () => {
      const store = createStore()
      expect(store.checkedItems).toEqual([])
    })

    it('checkedCount 为 0', () => {
      const store = createStore()
      expect(store.checkedCount).toBe(0)
    })

    it('checkedAmount 为 0', () => {
      const store = createStore()
      expect(store.checkedAmount).toBe(0)
    })

    it('空购物车 allChecked 为 false', () => {
      const store = createStore()
      expect(store.allChecked).toBe(false)
    })
  })

  // ── Getters: 有商品时 ──
  describe('with items', () => {
    beforeEach(() => {
      // 通过直接操作 state 来插入测试数据
    })

    it('totalCount 累加所有 quantity', () => {
      const store = createStore()
      store.items = [
        mockItem({ quantity: 3 }),
        mockItem({ id: 2, quantity: 5 })
      ]
      expect(store.totalCount).toBe(8)
    })

    it('totalCount 处理缺失 quantity 的项（兜底为 0）', () => {
      const store = createStore()
      store.items = [
        mockItem({ quantity: 2 }),
        { id: 2, price: 10 } // 无 quantity
      ]
      expect(store.totalCount).toBe(2)
    })

    it('totalAmount = Σ(price × qty + serviceFee × qty)', () => {
      const store = createStore()
      store.items = [
        mockItem({ price: 100, quantity: 2, serviceFee: 5 }),  // 100*2 + 5*2 = 210
        mockItem({ id: 2, price: 50, quantity: 1, serviceFee: 0 }) // 50*1 + 0 = 50
      ]
      // 210 + 50 = 260
      expect(store.totalAmount).toBe(260)
    })

    it('totalAmount 对非法价格兜底为 0', () => {
      const store = createStore()
      store.items = [
        mockItem({ price: null, quantity: 1 })
      ]
      expect(store.totalAmount).toBe(0)
    })

    it('totalAmount 使用 skuPrice 作为备选价格', () => {
      const store = createStore()
      store.items = [
        mockItem({ price: undefined, skuPrice: 88, quantity: 1 })
      ]
      expect(store.totalAmount).toBe(88)
    })

    it('totalAmount 使用 number 字段作为备选数量', () => {
      const store = createStore()
      store.items = [
        mockItem({ quantity: undefined, number: 3, price: 10 })
      ]
      expect(store.totalAmount).toBe(30)
    })

    it('checkedItems 过滤未选中的', () => {
      const store = createStore()
      store.items = [
        mockItem({ id: 1, checked: true }),
        mockItem({ id: 2, checked: false }),
        mockItem({ id: 3, checked: true })
      ]
      expect(store.checkedItems).toHaveLength(2)
      expect(store.checkedItems.map(i => i.id)).toEqual([1, 3])
    })

    it('checkedCount 只统计选中商品的 quantity', () => {
      const store = createStore()
      store.items = [
        mockItem({ id: 1, checked: true, quantity: 3 }),
        mockItem({ id: 2, checked: false, quantity: 10 }),
        mockItem({ id: 3, checked: true, quantity: 2 })
      ]
      expect(store.checkedCount).toBe(5)
    })

    it('全部选中时 allChecked 为 true', () => {
      const store = createStore()
      store.items = [
        mockItem({ id: 1, checked: true }),
        mockItem({ id: 2, checked: true })
      ]
      expect(store.allChecked).toBe(true)
    })

    it('部分选中时 allChecked 为 false', () => {
      const store = createStore()
      store.items = [
        mockItem({ id: 1, checked: true }),
        mockItem({ id: 2, checked: false })
      ]
      expect(store.allChecked).toBe(false)
    })
  })

  // ── Actions: toggleChecked ──
  describe('toggleChecked', () => {
    it('选中 → 取消选中', () => {
      const store = createStore()
      store.items = [mockItem({ id: 1, checked: true })]
      store.toggleChecked(1)
      expect(store.items[0].checked).toBe(false)
    })

    it('取消选中 → 选中', () => {
      const store = createStore()
      store.items = [mockItem({ id: 1, checked: false })]
      store.toggleChecked(1)
      expect(store.items[0].checked).toBe(true)
    })

    it('不存在的 id 不抛异常', () => {
      const store = createStore()
      store.items = [mockItem()]
      expect(() => store.toggleChecked(999)).not.toThrow()
    })

    it('通过 skuId 匹配', () => {
      const store = createStore()
      store.items = [mockItem({ id: 10, skuId: 200, checked: true })]
      store.toggleChecked(200)
      expect(store.items[0].checked).toBe(false)
    })
  })

  // ── Actions: toggleAllChecked ──
  describe('toggleAllChecked', () => {
    it('部分选中 → 传 true 全选', () => {
      const store = createStore()
      store.items = [
        mockItem({ id: 1, checked: true }),
        mockItem({ id: 2, checked: false })
      ]
      store.toggleAllChecked(true)
      expect(store.items.every(i => i.checked)).toBe(true)
    })

    it('全选 → 传 false 全取消', () => {
      const store = createStore()
      store.items = [
        mockItem({ id: 1, checked: true }),
        mockItem({ id: 2, checked: true })
      ]
      store.toggleAllChecked(false)
      expect(store.items.every(i => i.checked === false)).toBe(true)
    })

    it('不传参数 → 取反当前 allChecked', () => {
      const store = createStore()
      store.items = [
        mockItem({ id: 1, checked: false }),
        mockItem({ id: 2, checked: false })
      ]
      // allChecked = false，toggleAllChecked() 应全选
      store.toggleAllChecked()
      expect(store.items.every(i => i.checked)).toBe(true)
    })
  })

  // ── Actions: clearChecked ──
  describe('clearChecked', () => {
    it('删除所有已选中的商品', () => {
      const store = createStore()
      store.items = [
        mockItem({ id: 1, checked: true }),
        mockItem({ id: 2, checked: false }),
        mockItem({ id: 3, checked: true })
      ]
      store.clearChecked()
      expect(store.items).toHaveLength(1)
      expect(store.items[0].id).toBe(2)
    })
  })

  // ── Actions: reset ──
  describe('reset', () => {
    it('退出时清空购物车', () => {
      const store = createStore()
      store.items = [mockItem()]
      store.badgeSeen = false
      store.reset()
      expect(store.items).toEqual([])
      expect(store.badgeSeen).toBe(true)
    })
  })

  // ── Actions: load (mocked API) ──
  describe('load', () => {
    it('服务端返回数据时覆盖本地 items', async () => {
      listCart.mockResolvedValue([
        {
          cartId: 1, skuId: 101, spuId: 1001, skuName: '商品A',
          skuPrice: 50, qty: 2, checked: true
        }
      ])

      const store = createStore()
      await store.load()

      expect(store.items).toHaveLength(1)
      expect(store.items[0].name).toBe('商品A')
      expect(store.items[0].price).toBe(50)
      expect(store.items[0].quantity).toBe(2)
      expect(store.items[0].checked).toBe(true)
    })

    it('服务端返回空数组时清空本地', async () => {
      listCart.mockResolvedValue([])

      const store = createStore()
      // 先设一些本地数据
      store.items = [mockItem()]
      await store.load()

      expect(store.items).toHaveLength(0)
      expect(localStorage.getItem('stellar_cart_items')).toBeNull()
    })

    it('请求失败时保留本地数据不覆盖', async () => {
      listCart.mockRejectedValue(new Error('network'))

      const store = createStore()
      store.items = [mockItem()]
      await store.load()

      // 本地数据不丢
      expect(store.items).toHaveLength(1)
    })
  })
})
