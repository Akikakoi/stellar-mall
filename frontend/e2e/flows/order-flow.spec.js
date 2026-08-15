/**
 * E2E 核心流程 — 登录 → 浏览 → 加购 → 下单
 *
 * ⚠️ 前置条件：
 *   1. 后端服务运行在 127.0.0.1:8082
 *   2. 已有测试账号：test@stellar.com / 123456
 *   3. 库存中至少有一个上架商品 (SPU ID=1)
 *
 * 运行：npx playwright test e2e/flows/
 */
import { test, expect } from '@playwright/test'

test.describe('核心下单流程', () => {
  test('用户登录', async ({ page }) => {
    await page.goto('/user/login')

    // 填写登录表单
    await page.fill('input[type="email"], input[placeholder*="邮箱"], input[placeholder*="email"]', 'test@stellar.com')
    await page.fill('input[type="password"]', '123456')

    await page.click('button:has-text("登录"), button:has-text("登 录")')

    // 登录后应跳转到首页或商城
    await page.waitForURL(/^\/($|user\/)/, { timeout: 10000 })

    // 登录后导航应显示用户信息（非登录页）
    await expect(page).not.toHaveURL(/\/login/)
  })

  test('浏览商品 → 进入详情', async ({ page }) => {
    // 假设已登录
    await page.goto('/user/shop')

    // 等商品列表加载
    await page.waitForTimeout(2000)

    // 点击第一个商品卡片
    const firstCard = page.locator('.spu-card, .product-card, [class*="spu"] a').first()
    if (await firstCard.isVisible({ timeout: 3000 })) {
      await firstCard.click()
      // 应跳转到商品详情
      await expect(page).toHaveURL(/\/user\/spu\//)
    }
  })

  test('选择规格 → 加入购物车', async ({ page }) => {
    await page.goto('/user/spu/1')
    await page.waitForTimeout(2000)

    // 如果页面有 SKU 选择器，选第一个选项
    const specOptions = page.locator('.spec-option:not(.disabled)')
    const count = await specOptions.count()
    if (count > 0) {
      // 选第一个维度
      await specOptions.first().click()
    }

    // 点击加入购物车
    const addToCartBtn = page.locator('button:has-text("加入购物车"), .add-to-cart-btn')
    if (await addToCartBtn.isVisible({ timeout: 3000 })) {
      await addToCartBtn.click()
      // 等待反馈
      await page.waitForTimeout(1000)
    }
  })
})
