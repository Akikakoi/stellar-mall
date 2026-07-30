import { defineStore } from 'pinia'
import { loginAdmin, getAdminProfile } from '@/api/admin'
import { storage } from '@/utils/storage'

const TOKEN_KEY = 'stellar_admin_token'
const EMPID_KEY = 'stellar_admin_empid'
const USERNAME_KEY = 'stellar_admin_username'
const NAME_KEY = 'stellar_admin_name'

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

export const useAdminStore = defineStore('admin', {
  state: () => ({
    empId: safeGet(EMPID_KEY) ? Number(safeGet(EMPID_KEY)) : null,
    token: safeGet(TOKEN_KEY) || '',
    username: safeGet(USERNAME_KEY) || '',
    name: safeGet(NAME_KEY) || ''
  }),

  getters: {
    isLoggedIn: (state) => !!state.token
  },

  actions: {
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

    setToken(token) {
      this.token = token || ''
      safeSet(TOKEN_KEY, this.token)
    },

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
