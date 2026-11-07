/**
 * CI Smoke — 前端路由与基础渲染，API 全部本地 mock。
 *
 * 不依赖 Java/RAG/MySQL；用于在 CI 上稳定验证 SPA 不白屏、路由守卫和关键页面可渲染。
 * 真实后端链路见 e2e/smoke.spec.js 与 e2e/flows/。
 */
import { test, expect } from '@playwright/test'

const PRODUCT = {
  id: 1,
  name: 'CI 测试商品',
  mainImage: '',
  subImages: [],
  price: 99,
  originalPrice: 99,
  minPrice: 99,
  maxPrice: 99,
  skus: [],
  description: 'CI mock 商品',
  detail: 'CI mock 商品',
  specs: []
}

async function mockApi(page) {
  await page.route('**/user/**', async (route) => {
    if (route.request().resourceType() === 'document') return route.continue()
    const path = new URL(route.request().url()).pathname
    let data = null
    if (path === '/user/spu/page') {
      data = { records: [], total: 0, page: 1, pageSize: 12 }
    } else if (path === '/user/category/list' || path === '/user/home-module/list') {
      data = []
    } else if (path === '/user/spu/orbit' || path === '/user/spu/random') {
      data = []
    } else if (path === '/user/site-config/bg') {
      data = { bgImage: '' }
    } else if (path === '/user/site-config/orbit') {
      data = { spuIds: [] }
    } else if (/^\/user\/spu\/\d+$/.test(path)) {
      data = PRODUCT
    } else if (/^\/user\/review\/spu\/\d+\/rating$/.test(path)) {
      data = 5
    } else if (/^\/user\/review\/spu\/\d+$/.test(path)) {
      data = { records: [], total: 0, page: 1, pageSize: 10 }
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 1, msg: 'ok', data })
    })
  })
  await page.route('**/admin/**', (route) => {
    if (route.request().resourceType() === 'document') return route.continue()
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 1, msg: 'ok', data: null })
    })
  })
  await page.route('**/ragapi/**', (route) => {
    if (route.request().resourceType() === 'document') return route.continue()
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 1, msg: 'ok', data: null })
    })
  })
}

test.describe('CI smoke（API mocked）', () => {
  test.beforeEach(async ({ page }) => {
    await mockApi(page)
  })

  test('首页加载正常', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveTitle(/Stellar|商城|mall/i)
    await expect(page.locator('body')).toBeVisible()
  })

  test('商城页面可访问且无错误提示', async ({ page }) => {
    await page.goto('/user/shop')
    await expect(page.locator('body')).toBeVisible()
    await expect(page.locator('.el-message--error')).toHaveCount(0, { timeout: 3000 })
  })

  test('商品详情页可渲染', async ({ page }) => {
    await page.goto('/user/spu/1')
    await expect(page.locator('body')).toBeVisible()
    await expect(page.locator('.el-message--error')).toHaveCount(0, { timeout: 3000 })
  })

  test('管理端登录页可渲染', async ({ page }) => {
    await page.goto('/admin/login')
    await expect(page.locator('body')).toBeVisible()
    await expect(page.locator('input').first()).toBeVisible({ timeout: 5000 })
  })

  test('未知路由回退到登录页', async ({ page }) => {
    await page.goto('/nonexistent-route-xyz')
    await expect(page).toHaveURL(/\/login/)
    const bodyText = await page.locator('body').textContent()
    expect(bodyText).not.toContain('"code"')
  })
})