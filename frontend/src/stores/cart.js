import { defineStore } from 'pinia'
import { listCart, addCart, updateCartQty, deleteCart, clearCart as apiClearCart } from '@/api/mall'
import { storage } from '@/utils/storage'

const CART_KEY = 'stellar_cart_items'

function safeGet() {
  return storage.local.getObject(CART_KEY, [])
}

function safeSet(items) {
  storage.local.setObject(CART_KEY, items)
}

function safeRemove() {
  storage.local.remove(CART_KEY)
}

export const useCartStore = defineStore('cart', {
  state: () => ({
    items: safeGet(),
    loading: false
  }),

  getters: {
    totalCount: (state) => state.items.reduce((sum, it) => sum + (it.quantity || 0), 0),

    totalAmount: (state) => state.items.reduce((sum, it) => {
      const price = Number(it.price || it.skuPrice || 0)
      const qty = Number(it.quantity || it.number || 0)
      const serviceFee = Number(it.serviceFee || 0) * qty
      return sum + price * qty + serviceFee
    }, 0),

    checkedItems: (state) => state.items.filter((it) => {
      const c = it.checked
      if (typeof c === 'number') return c === 1
      if (typeof c === 'string') return c === '1' || c === 'true'
      return !!c
    }),

    checkedCount() {
      return this.checkedItems.reduce((sum, it) => sum + (it.quantity || 0), 0)
    },

    checkedAmount() {
      return this.checkedItems.reduce((sum, it) => {
        const price = Number(it.price || it.skuPrice || 0)
        const qty = Number(it.quantity || it.number || 0)
        const serviceFee = Number(it.serviceFee || 0) * qty
        return sum + price * qty + serviceFee
      }, 0)
    },

    allChecked: (state) => state.items.length > 0 && state.items.every((it) => {
      const c = it.checked
      if (typeof c === 'number') return c === 1
      if (typeof c === 'string') return c === '1' || c === 'true'
      return !!c
    })
  },

  actions: {
    persist() {
      safeSet(this.items)
    },

    async load() {
      this.loading = true
      try {
        const res = await listCart()
        if (Array.isArray(res)) {
          // 服务端返回空但本地有数据，说明之前的 addCart 可能未成功同步到服务端
          // 保留本地数据，避免清空购物车
          if (res.length === 0 && this.items.length > 0) {
            return
          }
          this.items = res.map((it) => {
            const c = it.checked
            let checked = true
            if (typeof c === 'number') checked = c === 1
            else if (typeof c === 'string') checked = c === '1' || c === 'true'
            else if (typeof c === 'boolean') checked = c
            return {
              ...it,
              id: it.id || it.cartId || it.skuId,
              skuId: it.skuId || it.id,
              spuId: it.spuId,
              name: it.name || it.skuName || it.spuName,
              image: it.image || it.skuImage || it.pic,
              price: Number(it.price || it.skuPrice || 0),
              quantity: Number(it.qty || it.quantity || it.number || 1),
              checked,
              services: it.services || [],
              serviceFee: Number(it.serviceFee || it.extraAmount || 0)
            }
          })
          this.persist()
        }
      } catch (e) {
        // fallback: keep local
      } finally {
        this.loading = false
      }
    },

    async add(payload) {
      try {
        const res = await addCart(payload)
        await this.load()
        return res
      } catch (e) {
        // 服务端明确拒绝（SKU 不存在/停售等），不写本地回退，直接抛出让调用方展示错误
        const msg = (e?.message || e?.msg || '')
        if (msg.includes('SKU不存在') || msg.includes('停售')) {
          throw e
        }
        // 网络错误等场景：本地兜底
        const skuId = payload.skuId || payload.id
        const qty = Number(payload.qty || payload.quantity || 1)
        const existing = this.items.find((it) => it.skuId === skuId)
        if (existing) {
          existing.quantity = Number(existing.quantity || 0) + qty
          existing.services = payload.services || existing.services || []
          existing.serviceFee = Number(payload.serviceFee || existing.serviceFee || 0)
        } else {
          this.items.push({
            id: Date.now(),
            skuId,
            spuId: payload.spuId,
            name: payload.name || payload.skuName || '',
            image: payload.image || payload.pic || '',
            price: Number(payload.price || payload.skuPrice || 0),
            quantity: qty,
            checked: true,
            services: payload.services || [],
            serviceFee: Number(payload.serviceFee || 0)
          })
        }
        this.persist()
        throw e
      }
    },

    async updateQty(idOrSkuId, quantity) {
      const qty = Math.max(1, Number(quantity) || 1)
      try {
        const res = await updateCartQty({ id: idOrSkuId, quantity: qty })
        const item = this.items.find((it) => it.id === idOrSkuId || it.skuId === idOrSkuId)
        if (item) item.quantity = qty
        this.persist()
        return res
      } catch (e) {
        const item = this.items.find((it) => it.id === idOrSkuId || it.skuId === idOrSkuId)
        if (item) item.quantity = qty
        this.persist()
      }
    },

    async remove(idOrSkuId) {
      try {
        const res = await deleteCart(idOrSkuId)
        this.items = this.items.filter((it) => it.id !== idOrSkuId && it.skuId !== idOrSkuId)
        this.persist()
        return res
      } catch (e) {
        this.items = this.items.filter((it) => it.id !== idOrSkuId && it.skuId !== idOrSkuId)
        this.persist()
      }
    },

    toggleChecked(idOrSkuId) {
      const item = this.items.find((it) => it.id === idOrSkuId || it.skuId === idOrSkuId)
      if (item) {
        item.checked = item.checked === false ? true : false
        this.persist()
      }
    },

    toggleAllChecked(val) {
      const target = val !== undefined ? !!val : !this.allChecked
      this.items.forEach((it) => {
        it.checked = target
      })
      this.persist()
    },

    async clear() {
      try {
        const res = await apiClearCart()
        this.items = []
        safeRemove()
        return res
      } catch (e) {
        this.items = []
        safeRemove()
      }
    },

    clearChecked() {
      this.items = this.items.filter((it) => it.checked === false)
      this.persist()
    },

    async clearCheckedAndSync() {
      try {
        await apiClearCart()
      } catch (e) {
        // 后端已清除也没关系
      }
      this.items = []
      safeRemove()
    }
  }
})
