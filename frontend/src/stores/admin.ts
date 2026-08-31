/**
 * 管理后台状态管理 Store。
 * 管理管理员登录态、个人信息,数据持久化到 localStorage。
 */
import { defineStore } from 'pinia'
import { loginAdmin, getAdminProfile } from '@/api/admin'
import { adminRequest } from '@/api/request'
import { storage } from '@/utils/storage'
import type { EmployeeLoginResult, Employee } from '@/types/models'

const TOKEN_KEY = 'stellar_admin_token'
const EMPID_KEY = 'stellar_admin_empid'
const USERNAME_KEY = 'stellar_admin_username'
const NAME_KEY = 'stellar_admin_name'
const REFRESH_TOKEN_KEY = 'stellar_admin_refresh_token'
const ROLE_KEY = 'stellar_admin_role'

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

interface AdminState {
  /** 员工 ID */
  empId: number | null
  /** 登录 Token(access) */
  token: string
  /** Refresh Token(用于 access 过期后换新) */
  refreshToken: string
  /** 用户名 */
  username: string
  /** 真实姓名 */
  name: string
  /** 角色：1 超级管理员 2 运营 3 客服 4 财务（服务端权威，仅用于前端路由/菜单展示） */
  role: number | null
}

export const useAdminStore = defineStore('admin', {
  state: (): AdminState => ({
    empId: safeGet(EMPID_KEY) ? Number(safeGet(EMPID_KEY)) : null,
    token: safeGet(TOKEN_KEY) || '',
    refreshToken: safeGet(REFRESH_TOKEN_KEY) || '',
    username: safeGet(USERNAME_KEY) || '',
    name: safeGet(NAME_KEY) || '',
    role: safeGet(ROLE_KEY) ? Number(safeGet(ROLE_KEY)) : null
  }),

  getters: {
    /** 是否已登录 */
    isLoggedIn: (state) => !!state.token,
    /** 是否超级管理员 */
    isSuperAdmin: (state) => state.role === 1
  },

  actions: {
    /** 管理员登录 */
    async login(payload: { username: string, password: string }): Promise<EmployeeLoginResult> {
      const res = await loginAdmin(payload)
      this.token = res.token || ''
      this.refreshToken = res.refreshToken || ''
      this.empId = res.empId || res.EMP_ID || res.id || null
      this.username = res.username || payload.username || ''
      this.name = res.name || res.NAME || ''
      // role 可能来自登录响应（如 res.role），否则由 fetchProfile 拉取补全
      this.role = res.role != null ? Number(res.role) : null

      safeSet(TOKEN_KEY, this.token)
      safeSet(REFRESH_TOKEN_KEY, this.refreshToken)
      safeSet(EMPID_KEY, this.empId)
      safeSet(USERNAME_KEY, this.username)
      safeSet(NAME_KEY, this.name)
      safeSet(ROLE_KEY, this.role)

      return res
    },

    /** 从服务端拉取最新管理员信息 */
    async fetchProfile(): Promise<Employee | null> {
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
        // 服务端 Employee 含 role（1超管 2运营 3客服 4财务），同步到 store 供路由守卫使用
        const serverRole = res.role != null ? Number(res.role) : null
        if (serverRole != null) {
          this.role = serverRole
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

    /** 手动设置管理员信息(用于跨页面同步) */
    setAdminInfo(info: any) {
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
      if (info.role != null) {
        this.role = Number(info.role)
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
          await adminRequest({
            url: '/admin/employee/logout',
            method: 'post',
            data: { refreshToken: this.refreshToken },
            __silent: true
          })
        } catch (e) {
          // 后端调用失败不阻断前端登出
        }
      }
      this.empId = null
      this.token = ''
      this.refreshToken = ''
      this.username = ''
      this.name = ''
      this.role = null
      safeRemove(TOKEN_KEY)
      safeRemove(REFRESH_TOKEN_KEY)
      safeRemove(EMPID_KEY)
      safeRemove(USERNAME_KEY)
      safeRemove(NAME_KEY)
      safeRemove(ROLE_KEY)
    }
  }
})
