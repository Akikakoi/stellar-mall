/// <reference types="vite/client" />

/**
 * 全局浏览器环境扩展。
 * __PH 是 main.ts 注入的"暂无图片"占位 SVG data URL,
 * 模板里通过 onerror="this.src=window.__PH" 使用。
 */
declare global {
  interface Window {
    __PH: string
  }
}

/**
 * Vue Router 路由元信息类型扩展。
 * 与 router/index.ts 中 meta 字段保持一致。
 */
declare module 'vue-router' {
  interface RouteMeta {
    /** 页面标题(守卫会拼成 "title - 星耀商城") */
    title?: string
    /** 需要用户登录 */
    requiresUserAuth?: boolean
    /** 需要管理员登录 */
    requiresAdminAuth?: boolean
  }
}

export {}
