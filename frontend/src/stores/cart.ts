/**
 * 购物车状态管理 Store。
 * 对于已登录用户,服务端是唯一数据源;localStorage 仅作加载期间的瞬时占位。
 */
import { defineStore } from 'pinia'
import { listCart, addCart, updateCartQty, deleteCart, clearCart as apiClearCart } from '@/api/mall'
import { storage } from '@/utils/storage'
import type { CartItem, CartRowVO } from '@/types/models'

const CART_KEY = 'stellar_cart_items'
const BADGE_SEEN_KEY = 'stellar_cart_badge_seen'

/** 从 localStorage 读取购物车数据 */
function safeGet(): CartItem[] {
  return storage.local.getObject<CartItem[]>(CART_KEY, [])
}

/** 将购物车数据写入 localStorage */
function safeSet(items: CartItem[]) {
  storage.local.setObject(CART_KEY, items)
}

/** 清除 localStorage 中的购物车数据 */
function safeRemove() {
  storage.local.remove(CART_KEY)
}

interface CartState {
  /** 购物车商品列表(归一化后的条目,运行时仍携带原始行字段以便兼容兜底) */
  items: Array<CartItem & Partial<CartRowVO>>
  /** 加载状态标识 */
  loading: boolean
  /** 购车角标是否已被查看(点击购物车按钮后置 true,加购时重置为 false) */
  badgeSeen: boolean
}

/**
 * 将服务端购物车行归一化为前端条目。
 * 兼容后端 CartVO 与历史冗余字段,保证旧 localStorage 快照也能渲染。
 */
function normalizeCartRow(it: CartRowVO): CartItem {
  const c = it.checked
  let checked = true
  if (typeof c === 'number') checked = c === 1
  else if (typeof c === 'string') checked = c === '1' || c === 'true'
  else if (typeof c === 'boolean') checked = c
  return {
    ...it,
    id: it.id || it.cartId || it.skuId!,
    skuId: it.skuId || it.id!,
    spuId: it.spuId,
    name: it.name || it.skuName || it.spuName || '',
    image: it.image || it.skuImage || it.pic || '',
    price: Number(it.price || it.skuPrice || 0),
    quantity: Number(it.qty || it.quantity || it.number || 1),
    checked,
    services: it.services || [],
    serviceFee: Number(it.serviceFee || it.extraAmount || 0)
  }
}

export const useCartStore = defineStore('cart', {
  state: (): CartState => ({
    items: safeGet(),
    loading: false,
    badgeSeen: storage.local.getObject<boolean>(BADGE_SEEN_KEY, false)
  }),

  getters: {
    /** 购物车商品总数量 */
    totalCount: (state) => state.items.reduce((sum, it) => sum + (it.quantity || it.number || 0), 0),

    /** 购物车商品总金额(含服务费) */
    totalAmount: (state) => state.items.reduce((sum, it) => {
      const price = Number(it.price || it.skuPrice || 0)
      const qty = Number(it.quantity || it.number || 0)
      const serviceFee = Number(it.serviceFee || 0) * qty
      return sum + price * qty + serviceFee
    }, 0),

    /** 已选中的商品列表 */
    checkedItems: (state) => state.items.filter((it) => it.checked),

    /** 已选中商品总数量 */
    checkedCount: (state) => state.items.filter((it) => it.checked)
      .reduce((sum, it) => sum + (it.quantity || it.number || 0), 0),

    /** 已选中商品总金额(含服务费) */
    checkedAmount: (state) => state.items.filter((it) => it.checked)
      .reduce((sum, it) => {
        const price = Number(it.price || it.skuPrice || 0)
        const qty = Number(it.quantity || it.number || 0)
        const serviceFee = Number(it.serviceFee || 0) * qty
        return sum + price * qty + serviceFee
      }, 0),

    /** 是否全选 */
    allChecked: (state) => state.items.length > 0 && state.items.every((it) => it.checked)
  },

  actions: {
    /** 将当前购物车数据持久化到 localStorage */
    persist() {
      safeSet(this.items)
    },

    /**
     * 从服务端加载购物车数据。
     * 服务端是唯一数据源:无论返回空还是有数据,都以服务端为准。
     */
    async load() {
      this.loading = true
      try {
        const res = await listCart()
        if (Array.isArray(res)) {
          // 服务端是唯一数据源:无论返回空还是有数据,都覆盖本地
          this.items = res.map(normalizeCartRow)
          if (this.items.length === 0) {
            // 服务端返回空 → 清掉本地缓存,避免跨端删除后残留幽灵数据
            safeRemove()
          } else {
            this.persist()
          }
        }
      } catch (e) {
        // 请求失败:保留本地数据作占位,但不 persist 覆盖
        console.warn('[cart] load failed, keep local snapshot:', e)
      } finally {
        this.loading = false
      }
    },

    /**
     * 添加商品到购物车。
     * 服务端失败时直接抛出错误,不再本地创建幽灵商品。
     */
    async add(payload: any) {
      const res = await addCart(payload)
      this.badgeSeen = false
      storage.local.setObject(BADGE_SEEN_KEY, false)
      await this.load()
      return res
    },

    /**
     * 更新购物车商品数量。
     * @param idOrSkuId 购物车项 ID 或 SKU ID
     * @param quantity 新数量(最小为 1)
     */
    async updateQty(idOrSkuId: number, quantity: number) {
      const qty = Math.max(1, Number(quantity) || 1)
      const res = await updateCartQty({ id: idOrSkuId, quantity: qty })
      const item = this.items.find((it) => it.id === idOrSkuId || it.skuId === idOrSkuId)
      if (item) item.quantity = qty
      this.persist()
      return res
    },

    /** 从购物车中移除指定商品 */
    async remove(idOrSkuId: number) {
      const res = await deleteCart(idOrSkuId)
      this.items = this.items.filter((it) => it.id !== idOrSkuId && it.skuId !== idOrSkuId)
      this.persist()
      return res
    },

    /** 切换单个商品的选中状态 */
    toggleChecked(idOrSkuId: number) {
      const item = this.items.find((it) => it.id === idOrSkuId || it.skuId === idOrSkuId)
      if (item) {
        item.checked = item.checked === false ? true : false
        this.persist()
      }
    },

    /** 全选/取消全选 */
    toggleAllChecked(val?: boolean) {
      const target = val !== undefined ? !!val : !this.allChecked
      this.items.forEach((it) => {
        it.checked = target
      })
      this.persist()
    },

    /** 清空购物车 */
    async clear() {
      const res = await apiClearCart()
      this.items = []
      safeRemove()
      return res
    },

    /** 删除已选中的商品(仅本地) */
    clearChecked() {
      this.items = this.items.filter((it) => it.checked === false)
      this.persist()
    },

    /** 清空购物车并同步后端(下单后调用) */
    async clearCheckedAndSync() {
      try {
        await apiClearCart()
      } catch (e) {
        // 后端已清除也没关系
      }
      this.items = []
      safeRemove()
    },

    /** 标记购物车角标为已查看(点击购物车按钮后调用) */
    markBadgeSeen() {
      this.badgeSeen = true
      storage.local.setObject(BADGE_SEEN_KEY, true)
    },

    /** 登出时清空本地购物车数据 */
    reset() {
      this.items = []
      this.badgeSeen = true
      storage.local.setObject(BADGE_SEEN_KEY, true)
      safeRemove()
    }
  }
})
