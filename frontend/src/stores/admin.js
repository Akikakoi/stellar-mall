/**
 * 管理后台状态管理 Store。
 * 管理管理员登录态、个人信息，数据持久化到 localStorage。
 */
import { defineStore } from 'pinia'
import { loginAdmin, getAdminProfile } from '@/api/admin'
import { storage } from '@/utils/storage'

const TOKEN_KEY = 'stellar_admin_token'
const EMPID_KEY = 'stellar_admin_empid'
const USERNAME_KEY = 'stellar_admin_username'
const NAME_KEY = 'stellar_admin_name'

/** 从 localStorage 读取 */
function safeGet(key) {
  return storage.local.get(key)
}

/** 写入 localStorage，值为 null/undefined 时删除 */
function safeSet(key, value) {
  if (value === null || value === undefined) {
    storage.local.remove(key)
  } else {
    storage.local.set(key, String(value))
  }
}

/** 从 localStorage 删除 */
function safeRemove(key) {
  storage.local.remove(key)
}

export const useAdminStore = defineStore('admin', {
  state: () => ({
    /** 员工 ID */
    empId: safeGet(EMPID_KEY) ? Number(safeGet(EMPID_KEY)) : null,
    /** 登录 Token */
    token: safeGet(TOKEN_KEY) || '',
    /** 用户名 */
    username: safeGet(USERNAME_KEY) || '',
    /** 真实姓名 */
    name: safeGet(NAME_KEY) || ''
  }),

  getters: {
    /** 是否已登录 */
    isLoggedIn: (state) => !!state.token
  },

  actions: {
    /** 管理员登录 */
    async login(payload) {
      const res = await loginAdmin(payload)
      this.token = res.token || ''
      this.empId = res.empId || res.EMP_ID || res.id || null
      this.username = res.username || payload.username || ''
      this.name = res.name || res.NAME || ''

      safeSet(TOKEN_KEY, this.token)
      safeSet(EMPID_KEY, this.empId)
      safeSet(USERNAME_KEY, this.username)
      safeSet(NAME_KEY, this.name)

      return res
    },

    /** 从服务端拉取最新管理员信息 */
    async fetchProfile() {
      try {
        const res = await getAdminProfile()
        if (res.username) {
          this.username = res.username
          safeSet(USERNAME_KEY, this.username)
        }
        if (res.name || res.NAME) {
          this.name = res.name || res.NAME
          safeSet(NAME_KEY, this.name)
        }
        if (res.id || res.empId || res.EMP_ID) {
          this.empId = res.id || res.empId || res.EMP_ID
          safeSet(EMPID_KEY, this.empId)
        }
        return res
      } catch (e) {
        return null
      }
    },

    /** 手动设置 Token */
    setToken(token) {
      this.token = token || ''
      safeSet(TOKEN_KEY, this.token)
    },

    /** 手动设置管理员信息（用于跨页面同步） */
    setAdminInfo(info) {
      if (!info) return
      if (info.empId || info.id || info.EMP_ID) {
        this.empId = info.empId || info.id || info.EMP_ID
        safeSet(EMPID_KEY, this.empId)
      }
      if (info.username) {
        this.username = info.username
        safeSet(USERNAME_KEY, this.username)
      }
      if (info.name || info.NAME) {
        this.name = info.name || info.NAME
        safeSet(NAME_KEY, this.name)
      }
      if (info.token) {
        this.token = info.token
        safeSet(TOKEN_KEY, this.token)
      }
    },

    /** 退出登录，清除所有状态和本地存储 */
    logout() {
      this.empId = null
      this.token = ''
      this.username = ''
      this.name = ''
      safeRemove(TOKEN_KEY)
      safeRemove(EMPID_KEY)
      safeRemove(USERNAME_KEY)
      safeRemove(NAME_KEY)
    }
  }
})
