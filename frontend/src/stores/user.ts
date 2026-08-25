/**
 * 用户状态管理 Store。
 * 管理用户登录态、个人信息,数据持久化到 localStorage。
 */
import { defineStore } from 'pinia'
import { loginUser, getCurrentUser } from '@/api/mall'
import { userRequest } from '@/api/request'
import { storage } from '@/utils/storage'
import type { LoginPayload, LoginResult, UserInfo } from '@/types/models'

const TOKEN_KEY = 'stellar_user_token'
const USER_ID_KEY = 'stellar_user_id'
const NICKNAME_KEY = 'stellar_user_nickname'
const PHONE_KEY = 'stellar_user_phone'
const ROLE_KEY = 'stellar_user_role'
const REFRESH_TOKEN_KEY = 'stellar_user_refresh_token'

/** 从 localStorage 读取 */
function safeGet(key: string): string | null {
  return storage.local.get(key)
}

/** 写入 localStorage,值为 null/undefined 时删除 */
function safeSet(key: string, value: unknown) {
  if (value === null || value === undefined) {
    storage.local.remove(key)
  } else {
    storage.local.set(key, String(value))
  }
}

/** 从 localStorage 删除 */
function safeRemove(key: string) {
  storage.local.remove(key)
}

interface UserState {
  /** 用户 ID */
  userId: number | null
  /** 登录 Token(access) */
  token: string
  /** Refresh Token(用于 access 过期后换新) */
  refreshToken: string
  /** 用户昵称 */
  nickname: string
  /** 手机号 */
  phone: string
  /** 角色:admin / 普通用户 */
  role: string
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    userId: safeGet(USER_ID_KEY) ? Number(safeGet(USER_ID_KEY)) : null,
    token: safeGet(TOKEN_KEY) || '',
    refreshToken: safeGet(REFRESH_TOKEN_KEY) || '',
    nickname: safeGet(NICKNAME_KEY) || '',
    phone: safeGet(PHONE_KEY) || '',
    role: safeGet(ROLE_KEY) || ''
  }),

  getters: {
    /** 是否已登录 */
    isLoggedIn: (state) => !!state.token,
    /** 是否为管理员 */
    isAdmin: (state) => state.role === 'admin',
    /** 角色中文标签 */
    roleLabel: (state) => state.role === 'admin' ? '管理员' : '普通用户',
    /** 用户信息摘要 */
    userInfo: (state) => ({
      nickname: state.nickname,
      username: state.nickname || '用户'
    })
  },

  actions: {
    /** 密码登录 */
    async login(payload: LoginPayload): Promise<LoginResult> {
      const res = await loginUser(payload)
      this.token = res.token || ''
      this.refreshToken = res.refreshToken || ''
      this.userId = res.userId || res.USER_ID || res.id || null
      this.nickname = res.nickname || res.NICKNAME || res.name || ''
      this.phone = res.phone || payload.email || ''
      this.role = res.role || res.ROLE || ''

      safeSet(TOKEN_KEY, this.token)
      safeSet(REFRESH_TOKEN_KEY, this.refreshToken)
      safeSet(USER_ID_KEY, this.userId)
      safeSet(NICKNAME_KEY, this.nickname)
      safeSet(PHONE_KEY, this.phone)
      safeSet(ROLE_KEY, this.role)

      return res
    },

    /** 邮箱验证码登录 */
    async emailLogin(payload: any) {
      const res = await userRequest<LoginResult>({
        url: '/user/user/email-login',
        method: 'post',
        data: payload
      })
      this.token = res.token || ''
      this.refreshToken = res.refreshToken || ''
      this.userId = res.userId || res.USER_ID || res.id || null
      this.nickname = res.nickname || res.NICKNAME || res.name || ''
      this.phone = res.phone || payload.email || ''
      this.role = res.role || res.ROLE || ''

      safeSet(TOKEN_KEY, this.token)
      safeSet(REFRESH_TOKEN_KEY, this.refreshToken)
      safeSet(USER_ID_KEY, this.userId)
      safeSet(NICKNAME_KEY, this.nickname)
      safeSet(PHONE_KEY, this.phone)
      safeSet(ROLE_KEY, this.role)

      return res
    },

    /** 从服务端拉取最新用户信息并更新本地 */
    async fetchProfile(): Promise<UserInfo | null> {
      try {
        const res = await getCurrentUser()
        if (res.nickname) {
          this.nickname = res.nickname
          safeSet(NICKNAME_KEY, this.nickname)
        }
        if (res.phone) {
          this.phone = res.phone
          safeSet(PHONE_KEY, this.phone)
        }
        if (res.id || res.userId) {
          this.userId = res.id || res.userId
          safeSet(USER_ID_KEY, this.userId)
        }
        if (res.role || res.ROLE) {
          this.role = res.role || res.ROLE
          safeSet(ROLE_KEY, this.role)
        }
        return res
      } catch (e) {
        return null
      }
    },

    /** 手动设置 Token */
    setToken(token: string) {
      this.token = token || ''
      safeSet(TOKEN_KEY, this.token)
    },

    /** 手动设置 Refresh Token */
    setRefreshToken(refreshToken: string) {
      this.refreshToken = refreshToken || ''
      safeSet(REFRESH_TOKEN_KEY, this.refreshToken)
    },

    /** 手动设置用户信息(用于跨页面同步) */
    setUserInfo(info: any) {
      if (!info) return
      if (info.userId || info.id || info.USER_ID) {
        this.userId = info.userId || info.id || info.USER_ID
        safeSet(USER_ID_KEY, this.userId)
      }
      if (info.nickname || info.NICKNAME || info.name) {
        this.nickname = info.nickname || info.NICKNAME || info.name
        safeSet(NICKNAME_KEY, this.nickname)
      }
      if (info.phone) {
        this.phone = info.phone
        safeSet(PHONE_KEY, this.phone)
      }
      if (info.token) {
        this.token = info.token
        safeSet(TOKEN_KEY, this.token)
      }
      if (info.role || info.ROLE) {
        this.role = info.role || info.ROLE
        safeSet(ROLE_KEY, this.role)
      }
    },

    /**
     * 退出登录(E4:先调后端写黑名单,再清本地状态)
     * 即使后端调用失败,仍清本地,保证前端一定登出。
     */
    async logout() {
      // E4: 通知后端把 access+refresh token 加入黑名单
      if (this.token) {
        try {
          await userRequest({
            url: '/user/user/logout',
            method: 'post',
            data: { refreshToken: this.refreshToken },
            __silent: true
          })
        } catch (e) {
          // 后端调用失败不阻断前端登出(token 在本地清掉后即不可用)
        }
      }
      this.userId = null
      this.token = ''
      this.refreshToken = ''
      this.nickname = ''
      this.phone = ''
      this.role = ''
      safeRemove(TOKEN_KEY)
      safeRemove(REFRESH_TOKEN_KEY)
      safeRemove(USER_ID_KEY)
      safeRemove(NICKNAME_KEY)
      safeRemove(PHONE_KEY)
      safeRemove(ROLE_KEY)
      // 清除购物车本地缓存,避免换账号后串数据
      storage.local.remove('stellar_cart_items')
    }
  }
})
