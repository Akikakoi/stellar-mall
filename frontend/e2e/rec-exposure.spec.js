/**
 * 首页商品区曝光埋点的端到端验证。
 *
 * 覆盖两条渲染路径：
 *   1) 首页配了后台模块（modules）——这是真实数据下的主路径；
 *   2) 没有模块时的降级路径（sections 分类区块）。
 * 两条都必须“进入视口才算曝光”，否则会给负反馈制造虚曝光。
 *
 * 所有 /user/** 接口用结构化假数据拦截，不依赖后端。
 */
import { test, expect } from '@playwright/test'

const SPUS = Array.from({ length: 8 }, (_, i) => ({
  id: 100 + i,
  name: `测试商品${i + 1}`,
  mainImage: 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7',
  minPrice: 99 + i,
  maxPrice: 199 + i,
  saleCount: 10 + i,
  commentCount: i,
  categoryId: 1,
  categoryName: '测试分类A'
}))

const RECOMMEND_ITEMS = Array.from({ length: 8 }, (_, i) => ({
  ...SPUS[i],
  id: 300 + i,
  name: `推荐商品${i + 1}`,
  reason: '因为你浏览过「测试商品1」',
  source: i === 0 ? 'cf' : 'popular',
  score: 0.5
}))

/**
 * 拦截首页接口。modules 为空时页面走 sections 降级分支。
 * 只拦 xhr/fetch：范围放宽到全部路径会把 Vite 的 /src/views/user/Home.vue 模块也拦掉。
 */
async function setupRoutes(page, trackBodies, modules) {
  await page.route('**/*', async (route) => {
    const req = route.request()
    const url = req.url()
    if (!url.includes('/user/') || url.includes('/src/') || !['xhr', 'fetch'].includes(req.resourceType())) {
      return route.fallback()
    }
    // userRequest 的响应拦截器按 { code, msg, data } 解包，mock 必须用同一 envelope
    const ok = (data) => ({ code: 1, msg: 'ok', data })

    if (url.includes('/behavior/track')) {
      trackBodies.push(JSON.parse(req.postData() || '{}'))
      return route.fulfill({ json: ok(null) })
    }
    if (url.includes('/category/list')) {
      return route.fulfill({
        json: ok([
          { id: 1, name: '智能手机', children: [] },
          { id: 2, name: '智能穿戴', children: [] }
        ])
      })
    }
    if (url.includes('/home-module/list')) return route.fulfill({ json: ok(modules) })
    if (url.includes('/spu/page')) return route.fulfill({ json: ok({ records: SPUS, total: 8 }) })
    if (url.includes('/spu/recommend')) {
      return route.fulfill({
        json: ok({
          items: RECOMMEND_ITEMS,
          strategy: 'hybrid',
          personalized: true,
          abBucket: 'B',
          note: '基于近期行为的混合推荐',
          generatedAt: Date.now()
        })
      })
    }
    if (url.includes('/spu/orbit') || url.includes('/spu/random')) return route.fulfill({ json: ok([]) })
    return route.fulfill({ json: ok(null) })
  })
}

function collectEvents(trackBodies) {
  return trackBodies.flatMap((b) => (Array.isArray(b.events) ? b.events : []))
}

async function scrollToBottom(page) {
  await page.evaluate(async () => {
    const step = window.innerHeight * 0.8
    for (let y = 0; y < document.body.scrollHeight; y += step) {
      window.scrollTo(0, y)
      await new Promise((r) => setTimeout(r, 180))
    }
    window.scrollTo(0, document.body.scrollHeight)
  })
  await page.waitForTimeout(1200)
}

test.describe('首页商品曝光', () => {
  test('主路径：模块曝光只在进入视口后上报', async ({ page }) => {
    test.setTimeout(60000)
    const consoleErrors = []
    const trackBodies = []
    page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()) })

    await setupRoutes(page, trackBodies, [
      { id: 9, type: 'CATEGORY_SHOWCASE', title: '手机精选', config: JSON.stringify({ categoryIds: [1] }), status: 1 }
    ])

    await page.goto('/')
    await page.waitForSelector('.cat-section', { timeout: 15000 })

    // 首屏是 hero 区，模块在折叠线以下：不滚动时不应产生任何模块曝光
    await page.waitForTimeout(6500)
    const before = collectEvents(trackBodies).filter((e) => e.extra?.moduleType)
    expect(before, `未滚动不应有模块曝光：${JSON.stringify(before)}`).toHaveLength(0)

    await scrollToBottom(page)
    await page.screenshot({ path: '../.workbuddy/tmp/e2e-shots/home-module.png' })
    await page.waitForTimeout(6000)

    const moduleExposures = collectEvents(trackBodies).filter((e) => e.extra?.moduleType === 'CATEGORY_SHOWCASE')
    expect(moduleExposures.length).toBeGreaterThanOrEqual(1)
    expect(moduleExposures[0].scene).toBe('home')
    expect(moduleExposures[0].extra.spuIds.length).toBe(8)
    expect(consoleErrors, `控制台不应报错：${consoleErrors.join(' | ')}`).toHaveLength(0)
  })

  test('降级路径：无后台模块时分类区块曝光与推荐位桶字段', async ({ page }) => {
    test.setTimeout(60000)
    const consoleErrors = []
    const trackBodies = []
    page.on('console', (m) => { if (m.type() === 'error') consoleErrors.push(m.text()) })

    await setupRoutes(page, trackBodies, [])

    await page.goto('/')
    await page.waitForSelector('.cat-section', { timeout: 15000 })
    await page.screenshot({ path: '../.workbuddy/tmp/e2e-shots/home-top.png' })

    await scrollToBottom(page)
    await page.screenshot({ path: '../.workbuddy/tmp/e2e-shots/home-bottom.png' })
    await page.waitForTimeout(6000)

    const events = collectEvents(trackBodies)
    const sectionExposures = events.filter((e) => e.extra?.moduleType === 'CATEGORY_SECTION')
    expect(sectionExposures.length, '两个分类区块都应上报曝光').toBeGreaterThanOrEqual(2)
    expect(sectionExposures[0].extra.spuIds.length).toBe(8)
    expect(sectionExposures[0].scene).toBe('home')
    expect(sectionExposures[0].extra.categoryName).toBeTruthy()

    const railExposure = events.find((e) => e.scene === 'recommend_home')
    expect(railExposure, '推荐位应上报曝光').toBeTruthy()
    expect(railExposure.extra.abBucket).toBe('B')
    expect(railExposure.extra.itemIds.length).toBeGreaterThan(0)

    expect(consoleErrors, `控制台不应报错：${consoleErrors.join(' | ')}`).toHaveLength(0)
  })
})
