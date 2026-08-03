import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

/**
 * [SPA + Backend proxy 冲突解决]
 * Mall 前端路由前缀 '/user/*' '/admin/*' 恰好和 Java 后端的 API 路径前缀相同。
 * Vite 的 proxy 优先级高于 connect-history-api-fallback，
 * 浏览器直接导航到 5173/admin/login 会被代理到 Java 接口（返回 JSON 404），
 * 而不是返回 SPA index.html 给 Vue Router 接管。
 *
 * 用 bypass 区分两类请求：
 *  ① 浏览器导航（Sec-Fetch-Mode=navigate 或 Accept 首选项是 text/html）
 *     → 返回 '/index.html' 跳过代理，返回 SPA 入口让 Vue Router 接管
 *  ② XHR / fetch / 资源请求
 *     → 返回 undefined 走代理到 Java 后端
 */
function spaBypass(req) {
  const headers = req.headers || {}
  const secFetchMode = String(headers['sec-fetch-mode'] || '').toLowerCase()
  // 浏览器地址栏 / <a> 跳转 一般会发 sec-fetch-mode: navigate
  if (secFetchMode === 'navigate') {
    return '/index.html'
  }
  const accept = String(headers.accept || '')
  // 非 XHR：Accept 首项或优先项是 text/html 也当 SPA 导航处理
  if (accept && accept.indexOf('application/json') !== 0 && accept.indexOf('text/html') >= 0) {
    return '/index.html'
  }
  // 其余全部走后端代理（XHR/fetch，后端 API 一般 Accept 是 application/json 或不带 text/html 优先）
  return undefined
}

export default defineConfig({
  plugins: [
    vue(),
    Components({
      resolvers: [ElementPlusResolver({ importStyle: 'css' })],
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/user': {
        target: 'http://127.0.0.1:8082',
        changeOrigin: true,
        bypass: spaBypass
      },
      '/admin': {
        target: 'http://127.0.0.1:8082',
        changeOrigin: true,
        bypass: spaBypass
      },
      '/ragapi': {
        target: 'http://127.0.0.1:8000',
        changeOrigin: true,
        ws: true,
        rewrite: (path) => path.replace(/^\/ragapi/, '')
      }
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'element-plus': ['element-plus'],
          'echarts': ['echarts'],
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
          'markdown': ['markdown-it', 'highlight.js'],
          'axios': ['axios'],
        }
      }
    }
  }
})