/**
 * Vue Router 路由配置
 *
 * 模块职责：
 * - 定义前端所有路由（用户端 + 管理后台）
 * - 路由元信息（title、权限标识）
 * - 全局前置守卫：登录态校验、页面标题设置
 *
 * 路由分组：
 * - 公开路由：登录、注册、首页、搜索、商品详情、购物车
 * - 用户路由：需登录的订单、售后、个人中心等
 * - 管理后台路由：嵌入 /admin/_Layout 的子路由，需管理员权限
 * - 兜底路由：未匹配路径重定向到登录页
 *
 * @module router
 */
import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAdminStore } from '@/stores/admin'
import { storage } from '@/utils/storage'

const USER_TOKEN_KEY = 'stellar_user_token'
const ADMIN_TOKEN_KEY = 'stellar_admin_token'

/**
 * 安全地从 localStorage 读取值，封装 storage.local.get
 * @param {string} key - 存储键名
 * @returns {string|null} 存储的值
 */
function safeGetItem(key) {
  return storage.local.get(key)
}

const routes = [
  // ==================== 公开路由（无需登录） ====================
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '用户登录' }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { title: '用户注册' }
  },
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/user/Home.vue'),
    meta: { title: '星耀商城' }
  },
  {
    path: '/shop/search',
    name: 'ShopSearch',
    component: () => import('@/views/user/Shop.vue'),
    meta: { title: '商品搜索' }
  },
  {
    path: '/spu/:id',
    name: 'SpuDetail',
    component: () => import('@/views/user/SpuDetail.vue'),
    meta: { title: '商品详情' }
  },
  {
    path: '/cart',
    name: 'Cart',
    component: () => import('@/views/user/Cart.vue'),
    meta: { title: '购物车' }
  },
  {
    path: '/favorites',
    name: 'Favorites',
    component: () => import('@/views/user/Favorites.vue'),
    meta: { title: '我的收藏', requiresUserAuth: true }
  },
  {
    path: '/order/list',
    name: 'OrderList',
    component: () => import('@/views/user/OrderList.vue'),
    meta: { title: '我的订单', requiresUserAuth: true }
  },
  {
    path: '/order/submit',
    name: 'OrderSubmit',
    component: () => import('@/views/user/OrderSubmit.vue'),
    meta: { title: '提交订单', requiresUserAuth: true }
  },
  {
    path: '/order/:id',
    name: 'OrderDetail',
    component: () => import('@/views/user/OrderDetail.vue'),
    meta: { title: '订单详情', requiresUserAuth: true }
  },
  {
    path: '/me',
    name: 'Profile',
    component: () => import('@/views/user/Profile.vue'),
    meta: { title: '个人中心', requiresUserAuth: true }
  },
  {
    path: '/me/messages',
    name: 'Messages',
    component: () => import('@/views/user/Messages.vue'),
    meta: { title: '我的消息', requiresUserAuth: true }
  },
  {
    path: '/aftersale/apply',
    name: 'AfterSaleApply',
    component: () => import('@/views/user/AfterSaleApply.vue'),
    meta: { title: '申请售后', requiresUserAuth: true }
  },
  {
    path: '/aftersale/list',
    name: 'AfterSaleList',
    component: () => import('@/views/user/AfterSaleList.vue'),
    meta: { title: '我的售后', requiresUserAuth: true }
  },
  {
    path: '/aftersale/:id',
    name: 'AfterSaleDetail',
    component: () => import('@/views/user/AfterSaleDetail.vue'),
    meta: { title: '售后详情', requiresUserAuth: true }
  },
  {
    path: '/wallet',
    name: 'Wallet',
    component: () => import('@/views/user/Wallet.vue'),
    meta: { title: '我的钱包', requiresUserAuth: true }
  },
  {
    path: '/address',
    name: 'Address',
    component: () => import('@/views/user/Address.vue'),
    meta: { title: '收货地址', requiresUserAuth: true }
  },
  {
    path: '/coupons',
    name: 'Coupons',
    component: () => import('@/views/user/Coupons.vue'),
    meta: { title: '优惠券中心', requiresUserAuth: true }
  },
  {
    path: '/points',
    name: 'PointsMall',
    component: () => import('@/views/user/PointsMall.vue'),
    meta: { title: '积分商城', requiresUserAuth: true }
  },
  {
    path: '/points/records',
    name: 'PointsRecords',
    component: () => import('@/views/user/PointsRecords.vue'),
    meta: { title: '积分流水', requiresUserAuth: true }
  },
  {
    path: '/rag',
    redirect: '/rag/chat'
  },
  {
    path: '/rag/chat/:conversationId?',
    name: 'RagChat',
    component: () => import('@/views/user/RagChat.vue'),
    meta: { title: '智能问答', requiresUserAuth: true }
  },
  // ==================== 管理后台路由（需管理员权限） ====================
  {
    path: '/admin/login',
    name: 'AdminLogin',
    component: () => import('@/views/admin/Login.vue'),
    meta: { title: '管理后台登录' }
  },
  {
    path: '/admin',
    component: () => import('@/views/admin/_Layout.vue'),
    meta: { requiresAdminAuth: true },
    children: [
      {
        path: '',
        redirect: '/admin/dashboard'
      },
      {
        path: 'dashboard',
        name: 'AdminDashboard',
        component: () => import('@/views/admin/Dashboard.vue'),
        meta: { title: '控制台', requiresAdminAuth: true }
      },
      {
        path: 'chatbi',
        name: 'AdminChatBI',
        component: () => import('@/views/admin/ChatBI.vue'),
        meta: { title: '智能查数', requiresAdminAuth: true }
      },
      {
        path: 'spu',
        name: 'AdminSpuMgmt',
        component: () => import('@/views/admin/SpuMgmt.vue'),
        meta: { title: '商品管理', requiresAdminAuth: true }
      },
      {
        path: 'category',
        name: 'AdminCategoryMgmt',
        component: () => import('@/views/admin/CategoryMgmt.vue'),
        meta: { title: '分类管理', requiresAdminAuth: true }
      },
      {
        path: 'rag-sync',
        name: 'AdminRagSyncQueue',
        component: () => import('@/views/admin/RagSyncQueue.vue'),
        meta: { title: 'RAG 同步队列', requiresAdminAuth: true }
      },
      {
        path: 'employee',
        name: 'AdminEmployeeMgmt',
        component: () => import('@/views/admin/EmployeeMgmt.vue'),
        meta: { title: '员工管理', requiresAdminAuth: true }
      },
      {
        path: 'kb',
        name: 'AdminKb',
        component: () => import('@/views/admin/KnowledgeBase.vue'),
        meta: { title: '知识库管理', requiresAdminAuth: true }
      },
      {
        path: 'logs',
        name: 'AdminLogs',
        component: () => import('@/views/admin/OperationLogs.vue'),
        meta: { title: '操作日志', requiresAdminAuth: true }
      },
      {
        path: 'settings',
        name: 'AdminSettings',
        component: () => import('@/views/admin/SystemSettings.vue'),
        meta: { title: '系统设置', requiresAdminAuth: true }
      },
      {
        path: 'orders',
        name: 'AdminOrderMgmt',
        component: () => import('@/views/admin/OrderMgmt.vue'),
        meta: { title: '订单管理', requiresAdminAuth: true }
      },
      {
        path: 'coupon',
        name: 'AdminCouponMgmt',
        component: () => import('@/views/admin/CouponMgmt.vue'),
        meta: { title: '优惠券管理', requiresAdminAuth: true }
      },
      {
        path: 'review',
        name: 'AdminReviewMgmt',
        component: () => import('@/views/admin/ReviewMgmt.vue'),
        meta: { title: '评价管理', requiresAdminAuth: true }
      },
      {
        path: 'inventory',
        name: 'AdminInventory',
        component: () => import('@/views/admin/Inventory.vue'),
        meta: { title: '库存管理', requiresAdminAuth: true }
      },
      {
        path: 'recycle',
        name: 'AdminRecycleBin',
        component: () => import('@/views/admin/RecycleBin.vue'),
        meta: { title: '商品回收站', requiresAdminAuth: true }
      },
      {
        path: 'banner',
        name: 'AdminBannerMgmt',
        component: () => import('@/views/admin/BannerMgmt.vue'),
        meta: { title: '轮播图管理', requiresAdminAuth: true }
      },
      {
        path: 'aftersale',
        name: 'AdminAfterSaleMgmt',
        component: () => import('@/views/admin/AfterSaleMgmt.vue'),
        meta: { title: '售后管理', requiresAdminAuth: true }
      },
      {
        path: 'points-rules',
        name: 'AdminPointsRuleMgmt',
        component: () => import('@/views/admin/PointsRuleMgmt.vue'),
        meta: { title: '积分规则管理', requiresAdminAuth: true }
      },
      {
        path: 'points-products',
        name: 'AdminPointsProductMgmt',
        component: () => import('@/views/admin/PointsProductMgmt.vue'),
        meta: { title: '积分商城管理', requiresAdminAuth: true }
      },
      {
        path: 'home-module',
        name: 'AdminHomeModuleMgmt',
        component: () => import('@/views/admin/HomeModuleMgmt.vue'),
        meta: { title: '首页装修', requiresAdminAuth: true }
      }
    ]
  },
  // ==================== 兜底路由（未匹配路径） ====================
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/Login.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 全局前置守卫：页面标题设置 + 用户/管理员登录态校验
 * - 根据路由 meta.title 设置 document.title
 * - requiresUserAuth：未登录用户重定向到 /login
 * - requiresAdminAuth：未登录管理员重定向到 /admin/login
 */
router.beforeEach((to, from, next) => {
  const title = to.meta?.title
  if (title) {
    document.title = `${title} - 星耀商城`
  }

  if (to.meta?.requiresUserAuth) {
    const userStore = useUserStore()
    const token = userStore.token || safeGetItem(USER_TOKEN_KEY)
    if (!token) {
      next({ path: '/login', query: { redirect: to.fullPath } })
      return
    }
  }

  if (to.meta?.requiresAdminAuth) {
    const adminStore = useAdminStore()
    const token = adminStore.token || safeGetItem(ADMIN_TOKEN_KEY)
    if (!token && to.path !== '/admin/login') {
      next({ path: '/admin/login', query: { redirect: to.fullPath } })
      return
    }
  }

  next()
})

export default router
