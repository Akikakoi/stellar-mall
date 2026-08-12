import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath } from 'url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  test: {
    // jsdom 模拟浏览器环境（localStorage / sessionStorage / DOM）
    environment: 'jsdom',
    // 全局 vi / describe / it / expect，不用每个文件手动 import
    globals: true,
    // 测试文件匹配规则
    include: ['src/**/*.{test,spec}.{js,ts}'],
    // CSS 导入不报错
    css: false,
    // 覆盖率配置（后续开启）
    coverage: {
      provider: 'v8',
      include: ['src/utils/**', 'src/stores/**', 'src/composables/**'],
      reporter: ['text', 'lcov']
    }
  }
})
