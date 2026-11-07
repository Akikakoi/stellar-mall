import { defineConfig, devices } from '@playwright/test'

const channel = process.env.PLAYWRIGHT_CHANNEL || undefined
const video = process.env.PLAYWRIGHT_VIDEO || 'retain-on-failure'

export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  retries: 0,
  workers: 1,
  reporter: 'list',

  use: {
    baseURL: 'http://127.0.0.1:5173',
    headless: true,
    screenshot: 'only-on-failure',
    video,
    trace: 'on-first-retry',
    ...(channel ? { channel } : {})
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] }
    }
  ],

  // 自动启动 Vite dev server。
  // 显式绑定 127.0.0.1：Ubuntu runner 上 localhost 可能只解析到 ::1，
  // 而 Playwright 探测的是 127.0.0.1，会一直等到超时。
  webServer: {
    command: 'npx vite --host 127.0.0.1 --port 5173',
    url: 'http://127.0.0.1:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000
  }
})
