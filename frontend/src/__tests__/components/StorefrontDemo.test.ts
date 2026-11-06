import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import StorefrontDemo from '@/views/demo/StorefrontDemo.vue'
import type { Spu } from '@/types/models'

const mocks = vi.hoisted(() => ({
  products: vi.fn(),
  categories: vi.fn(),
  detail: vi.fn(),
  push: vi.fn(),
}))

vi.mock('@/api/storefrontDemo', () => ({
  fetchStorefrontProducts: mocks.products,
  fetchStorefrontCategories: mocks.categories,
  fetchStorefrontProduct: mocks.detail,
}))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push }),
}))

const liveProduct: Spu = {
  id: 77,
  name: '接口旗舰耳机',
  subTitle: '来自模拟真实响应',
  categoryId: 2,
  categoryName: '手机数码',
  minPrice: 899,
  maxPrice: 899,
  totalStock: 12,
  status: 1,
  skuList: [
    { id: 7701, spuId: 77, name: '接口旗舰耳机 · 黑色', specs: '颜色:黑色', price: 899, stock: 12 },
  ],
}

describe('StorefrontDemo live API flow', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.products.mockResolvedValue({ records: [liveProduct], total: 1 })
    mocks.categories.mockResolvedValue([{ id: 2, name: '手机数码' }])
    mocks.detail.mockResolvedValue(liveProduct)
  })

  it('renders live products and reads SKU detail through the same contract', async () => {
    const wrapper = mount(StorefrontDemo, {
      attachTo: document.body,
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })
    await flushPromises()

    expect(wrapper.find('.source-badge').text()).toContain('LIVE')
    expect(wrapper.findAll('[data-testid="product-card"]')).toHaveLength(1)
    expect(wrapper.find('.product-name').text()).toBe('接口旗舰耳机')
    expect(mocks.products).toHaveBeenCalledWith({ page: 1, pageSize: 9, status: 1 })

    await wrapper.find('.product-image-button').trigger('click')
    await flushPromises()
    expect(mocks.detail).toHaveBeenCalledWith(77)
    expect(document.getElementById('drawer-title')?.textContent).toBe('接口旗舰耳机')
    expect(document.querySelector('.sku-options button.active')?.textContent).toContain('黑色')

    const closeButton = document.querySelector<HTMLButtonElement>('.drawer-close')
    expect(closeButton).not.toBeNull()
    closeButton!.click()
    await flushPromises()
    expect(document.querySelector('[data-testid="product-drawer"]')).toBeNull()

    await wrapper.find('input[type="search"]').setValue('旗舰')
    await wrapper.find('form.product-search').trigger('submit')
    await flushPromises()
    expect(mocks.products).toHaveBeenLastCalledWith({ page: 1, pageSize: 9, status: 1, name: '旗舰' })

    wrapper.unmount()
  })
})
