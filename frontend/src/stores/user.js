import { defineStore } from 'pinia'
import { loginUser, getCurrentUser } from '@/api/mall'
import { userRequest } from '@/api/request'
import { storage } from '@/utils/storage'

const TOKEN_KEY = 'stellar_user_token'
const USER_ID_KEY = 'stellar_user_id'
const NICKNAME_KEY = 'stellar_user_nickname'
const PHONE_KEY = 'stellar_user_phone'
const ROLE_KEY = 'stellar_user_role'

function safeGet(key) {
  return storage.local.get(key)
}

function safeSet(key, value) {
  if (value === null || value === undefined) {
    storage.local.remove(key)
  } else {
    storage.local.set(key, String(value))
  }
}

function safeRemove(key) {
  storage.local.remove(key)
}

export const useUserStore = defineStore('user', {
  state: () => ({
    userId: safeGet(USER_ID_KEY) ? Number(safeGet(USER_ID_KEY)) : null,
    token: safeGet(TOKEN_KEY) || '',
    nickname: safeGet(NICKNAME_KEY) || '',
    phone: safeGet(PHONE_KEY) || '',
    role: safeGet(ROLE_KEY) || ''
  }),

  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => state.role === 'admin',
    roleLabel: (state) => state.role === 'admin' ? '管理员' : '普通用户',
    userInfo: (state) => ({
      nickname: state.nickname,
      username: state.nickname || '用户'
    })
  },

  actions: {
    async login(payload) {
      const res = await loginUser(payload)
      this.token = res.token || ''
      this.userId = res.userId || res.USER_ID || res.id || null
      this.nickname = res.nickname || res.NICKNAME || res.name || ''
      this.phone = res.phone || payload.phone || ''
      this.role = res.role || res.ROLE || ''

      safeSet(TOKEN_KEY, this.token)
      safeSet(USER_ID_KEY, this.userId)
      safeSet(NICKNAME_KEY, this.nickname)
      safeSet(PHONE_KEY, this.phone)
      safeSet(ROLE_KEY, this.role)

      return res
    },

    async smsLogin(payload) {
      const res = await userRequest({
        url: '/user/user/sms-login',
        method: 'post',
        data: payload
      })
      this.token = res.token || ''
      this.userId = res.userId || res.USER_ID || res.id || null
      this.nickname = res.nickname || res.NICKNAME || res.name || ''
      this.phone = res.phone || payload.phone || ''
      this.role = res.role || res.ROLE || ''

      safeSet(TOKEN_KEY, this.token)
      safeSet(USER_ID_KEY, this.userId)
      safeSet(NICKNAME_KEY, this.nickname)
      safeSet(PHONE_KEY, this.phone)
      safeSet(ROLE_KEY, this.role)

      return res
    },

    async fetchProfile() {
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

    setToken(token) {
      this.token = token || ''
      safeSet(TOKEN_KEY, this.token)
    },

    setUserInfo(info) {
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

    logout() {
      this.userId = null
      this.token = ''
      this.nickname = ''
      this.phone = ''
      this.role = ''
      safeRemove(TOKEN_KEY)
      safeRemove(USER_ID_KEY)
      safeRemove(NICKNAME_KEY)
      safeRemove(PHONE_KEY)
      safeRemove(ROLE_KEY)
    }
  }
})
