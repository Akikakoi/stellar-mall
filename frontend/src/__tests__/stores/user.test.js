/**
 * useUserStore 单元测试
 *
 * 覆盖：登录/登出状态流转、getter 计算、
 * setToken/setUserInfo 手动设置、localStorage 持久化。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// 必须在 store 导入前 mock API 模块
vi.mock('@/api/mall', () => ({
  loginUser: vi.fn(),
  getCurrentUser: vi.fn()
}))

vi.mock('@/api/request', () => ({
  // 默认返回 resolved promise（E4 logout 调用后端不阻断前端登出）
  userRequest: vi.fn().mockResolvedValue({})
}))

import { useUserStore } from '@/stores/user'
import { loginUser, getCurrentUser } from '@/api/mall'

function createStore() {
  setActivePinia(createPinia())
  return useUserStore()
}

describe('useUserStore', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  // ── 初始状态 ──
  describe('initial state', () => {
    it('未登录时 token 为空', () => {
      const store = createStore()
      expect(store.token).toBe('')
      expect(store.isLoggedIn).toBe(false)
    })

    it('未登录时 userId 为 null', () => {
      const store = createStore()
      expect(store.userId).toBeNull()
    })
  })

  // ── Getters ──
  describe('getters', () => {
    it('有 token 时 isLoggedIn 为 true', () => {
      const store = createStore()
      store.token = 'test-token-123'
      expect(store.isLoggedIn).toBe(true)
    })

    it('role 为 admin 时 isAdmin 为 true', () => {
      const store = createStore()
      store.role = 'admin'
      expect(store.isAdmin).toBe(true)
    })

    it('role 非 admin 时 isAdmin 为 false', () => {
      const store = createStore()
      store.role = 'user'
      expect(store.isAdmin).toBe(false)
    })

    it('roleLabel 返回中文标签', () => {
      const store = createStore()
      store.role = 'admin'
      expect(store.roleLabel).toBe('管理员')
      store.role = 'user'
      expect(store.roleLabel).toBe('普通用户')
    })

    it('userInfo 返回昵称摘要', () => {
      const store = createStore()
      store.nickname = '张三'
      expect(store.userInfo).toEqual({ nickname: '张三', username: '张三' })
    })

    it('无昵称时 userInfo 返回默认值', () => {
      const store = createStore()
      store.nickname = ''
      expect(store.userInfo).toEqual({ nickname: '', username: '用户' })
    })
  })

  // ── Actions: setToken ──
  describe('setToken', () => {
    it('设置 token 后 isLoggedIn 为 true', () => {
      const store = createStore()
      store.setToken('abc123')
      expect(store.token).toBe('abc123')
      expect(store.isLoggedIn).toBe(true)
    })

    it('设置空 token 后 isLoggedIn 为 false', () => {
      const store = createStore()
      store.setToken('')
      expect(store.token).toBe('')
      expect(store.isLoggedIn).toBe(false)
    })

    it('持久化到 localStorage', () => {
      const store = createStore()
      store.setToken('persisted-token')
      expect(localStorage.getItem('stellar_user_token')).toBe('persisted-token')
    })
  })

  // ── Actions: setUserInfo ──
  describe('setUserInfo', () => {
    it('批量设置用户信息', () => {
      const store = createStore()
      store.setUserInfo({
        userId: 42,
        nickname: '李四',
        phone: '13800001111',
        token: 'info-token',
        role: 'admin'
      })
      expect(store.userId).toBe(42)
      expect(store.nickname).toBe('李四')
      expect(store.phone).toBe('13800001111')
      expect(store.token).toBe('info-token')
      expect(store.role).toBe('admin')
    })

    it('传入 null 不改变状态', () => {
      const store = createStore()
      store.token = 'before'
      store.setUserInfo(null)
      expect(store.token).toBe('before')
    })

    it('接受 id 字段替代 userId', () => {
      const store = createStore()
      store.setUserInfo({ id: 99 })
      expect(store.userId).toBe(99)
    })

    it('接受 USER_ID 字段替代 userId', () => {
      const store = createStore()
      store.setUserInfo({ USER_ID: 77 })
      expect(store.userId).toBe(77)
    })

    it('接受 NICKNAME 字段替代 nickname', () => {
      const store = createStore()
      store.setUserInfo({ NICKNAME: '大写' })
      expect(store.nickname).toBe('大写')
    })

    it('接受 ROLE 字段替代 role', () => {
      const store = createStore()
      store.setUserInfo({ ROLE: 'admin' })
      expect(store.role).toBe('admin')
    })
  })

  // ── Actions: login ──
  describe('login', () => {
    it('登录成功后更新所有状态', async () => {
      loginUser.mockResolvedValue({
        token: 'login-token',
        userId: 1,
        nickname: '测试用户',
        phone: '13900000000',
        role: 'user'
      })

      const store = createStore()
      await store.login({ email: 'test@test.com', password: '123456' })

      expect(store.token).toBe('login-token')
      expect(store.userId).toBe(1)
      expect(store.nickname).toBe('测试用户')
      expect(store.isLoggedIn).toBe(true)
    })

    it('登录后 token 持久化到 localStorage', async () => {
      loginUser.mockResolvedValue({
        token: 'persist-login-token',
        userId: 2
      })

      const store = createStore()
      await store.login({ email: 'a@b.com', password: 'pass' })

      expect(localStorage.getItem('stellar_user_token')).toBe('persist-login-token')
      expect(localStorage.getItem('stellar_user_id')).toBe('2')
    })
  })

  // ── Actions: logout ──
  describe('logout', () => {
    it('清空所有状态', async () => {
      const store = createStore()
      store.token = 'active-token'
      store.userId = 5
      store.nickname = '用户'
      store.phone = '138'
      store.role = 'admin'

      await store.logout()

      expect(store.token).toBe('')
      expect(store.userId).toBeNull()
      expect(store.nickname).toBe('')
      expect(store.phone).toBe('')
      expect(store.role).toBe('')
      expect(store.isLoggedIn).toBe(false)
    })

    it('退出后清除 localStorage', async () => {
      const store = createStore()
      store.setToken('will-be-cleared')
      store.userId = 10
      store.nickname = 'n'
      store.phone = 'p'
      store.role = 'r'

      await store.logout()

      expect(localStorage.getItem('stellar_user_token')).toBeNull()
      expect(localStorage.getItem('stellar_user_id')).toBeNull()
      expect(localStorage.getItem('stellar_user_nickname')).toBeNull()
      expect(localStorage.getItem('stellar_user_phone')).toBeNull()
      expect(localStorage.getItem('stellar_user_role')).toBeNull()
      // 同时清除购物车缓存
      expect(localStorage.getItem('stellar_cart_items')).toBeNull()
    })
  })
})
