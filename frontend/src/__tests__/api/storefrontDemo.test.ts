import { beforeEach, describe, expect, it, vi } from 'vitest'
import { userRequest } from '@/api/request'
import {
  fetchStorefrontCategories,
  fetchStorefrontProduct,
  fetchStorefrontProducts,
} from '@/api/storefrontDemo'

vi.mock('@/api/request', () => ({ userRequest: vi.fn() }))

const requestMock = vi.mocked(userRequest)

describe('storefront demo API contract', () => {
  beforeEach(() => {
    requestMock.mockReset()
    requestMock.mockResolvedValue({ records: [], total: 0 })
  })

  it('uses the Java DTO names for product search', async () => {
    await fetchStorefrontProducts({ page: 1, pageSize: 9, name: '耳机', categoryId: 2, status: 1 })

    expect(requestMock).toHaveBeenCalledWith(expect.objectContaining({
      url: '/user/spu/page',
      method: 'get',
      __silent: true,
      params: { page: 1, pageSize: 9, name: '耳机', categoryId: 2, status: 1 },
    }))
    const config = requestMock.mock.calls[0]?.[0]
    if (!config) throw new Error('Expected request config')
    expect(config.params).not.toHaveProperty('pageNum')
    expect(config.params).not.toHaveProperty('keyword')
  })

  it('keeps category and detail endpoints aligned with the user controllers', async () => {
    await fetchStorefrontCategories()
    await fetchStorefrontProduct(42)

    expect(requestMock).toHaveBeenNthCalledWith(1, expect.objectContaining({ url: '/user/category/list' }))
    expect(requestMock).toHaveBeenNthCalledWith(2, expect.objectContaining({ url: '/user/spu/42' }))
  })
})
