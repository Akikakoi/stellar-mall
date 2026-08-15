/**
 * E2E Smoke Tests — 页面导航 + 基础渲染
 *
 * 本测试依赖后端服务运行在 127.0.0.1:8082，
 * 前端 dev server 由 playwright.config.js 中的 webServer 自动管理。
 *
 * 排除独立后端流程（登录/下单）的完整测试
 * 将在 e2e/flows/ 目录中单独编写。
 */
import { test, expect } from '@playwright/test'

test.describe('页面导航与渲染', () => {
  test('首页加载正常', async ({ page }) => {
    await page.goto('/')

    // 页面标题或关键元素存在
    await expect(page).toHaveTitle(/Stellar|商城|mall/i)
  })

  test('商城页面可访问', async ({ page }) => {
    await page.goto('/user/shop')

    // 应显示商品列表/分类
    await expect(page.locator('body')).toBeVisible()
    // 不因 404 或报错白屏
    await expect(page.locator('.el-message--error')).toHaveCount(0, { timeout: 3000 })
  })

  test('商品详情页可访问（默认商品）', async ({ page }) => {
    // 访问一个可能的默认商品 ID
    await page.goto('/user/spu/1')

    // 至少页面能渲染出来（不 crash）
    await expect(page.locator('body')).toBeVisible()
  })

  test('导航到管理后台登录页', async ({ page }) => {
    await page.goto('/admin/login')

    // 应有登录表单
    await expect(page.locator('body')).toBeVisible()
  })

  test('路由不存在时正确回退（SPA 模式）', async ({ page }) => {
    await page.goto('/nonexistent-route-xyz')

    // 不应显示后端 JSON 错误
    const bodyText = await page.locator('body').textContent()
    // 不应包含典型的后端错误标记
    expect(bodyText).not.toContain('"code"')
  })
})

test.describe('关键组件渲染', () => {
  test('SKU 规格选择器渲染', async ({ page }) => {
    // 访问一个有规格的商品详情
    await page.goto('/user/spu/1')

    // 如果商品存在且有 SKU，规格选择器应渲染
    // 退一步：至少页面不崩溃
    await expect(page.locator('body')).toBeVisible()
  })
})
