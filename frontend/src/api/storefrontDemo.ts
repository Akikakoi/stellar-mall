/**
 * 商城重设计 Demo 的只读接口适配层。
 *
 * 字段与参数以 Java 端为准：
 * - SpuPageQueryDTO: page / pageSize / name / categoryId / status
 * - SearchResultVO: records / total
 * - Spu: minPrice / maxPrice / mainImage / skuList
 *
 * 独立于现有商城页面，便于确认设计后逐步替换，而不影响线上页面。
 */
import { userRequest } from './request'
import type { Category, PageResult, Spu } from '@/types/models'

export interface StorefrontProductQuery {
  page: number
  pageSize: number
  name?: string
  categoryId?: number
  status?: 1
  sortBy?: 'createTime' | 'name'
  sortOrder?: 'asc' | 'desc'
}

const silentGet = {
  method: 'get',
  timeout: 3500,
  __silent: true,
} as const

export function fetchStorefrontProducts(
  params: StorefrontProductQuery,
): Promise<PageResult<Spu>> {
  return userRequest({
    ...silentGet,
    url: '/user/spu/page',
    params,
  })
}

export function fetchStorefrontCategories(): Promise<Category[]> {
  return userRequest({
    ...silentGet,
    url: '/user/category/list',
  })
}

export function fetchStorefrontProduct(id: number): Promise<Spu> {
  return userRequest({
    ...silentGet,
    url: `/user/spu/${id}`,
  })
}
