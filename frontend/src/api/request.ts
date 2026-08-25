/**
 * Axios 请求封装模块。
 * 提供三种预配置的请求实例:
 *   userRequest  - C 端用户请求,自动携带用户 token 和 userId
 *   adminRequest - 管理后台请求,自动携带管理员 token 和 empId
 *   ragRequest   - RAG 服务请求,智能判断用户端/管理端 token
 * 统一处理认证、401 拦截、错误提示等逻辑。
 *
 * 类型说明:
 *   userRequest<T>/adminRequest<T> 的 T 是"拦截器解包后"的业务载荷
 *   (后端 {code,msg,data} 里的 data);ragRequest<T> 的 T 是整个
 *   {code,message,data} envelope(见 RagResponse)。
 */
import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { useUserStore } from '@/stores/user'
import { useAdminStore } from '@/stores/admin'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { storage } from '@/utils/storage'

// ===== axios 自定义配置字段的类型增强 =====
declare module 'axios' {
  export interface AxiosRequestConfig {
    /** 静默请求:失败时不弹错误提示 */
    __silent?: boolean
    /** refresh 请求标记:401 时不再递归刷新 */
    __isRefresh?: boolean
    /** RAG 请求已重试过一次标记:避免无限 401→refresh→retry 循环 */
    __ragRetried?: boolean
  }
}

const USER_TOKEN_KEY = 'stellar_user_token'
const USER_ID_KEY = 'stellar_user_id'
const ADMIN_TOKEN_KEY = 'stellar_admin_token'
const ADMIN_EMPID_KEY = 'stellar_admin_empid'

/** 写操作需要幂等键的 HTTP 方法集合 */
const WRITE_METHODS = new Set(['post', 'put', 'delete', 'patch'])

/** refresh 接口路径(按 type 区分) */
const REFRESH_URL = {
  user: '/user/user/refresh',
  admin: '/admin/employee/refresh',
} as const

type RefreshType = 'user' | 'admin'

interface TokenPair {
  token: string
  refreshToken: string
}

/** 并发请求时的 refresh 去重:同一 type 只允许一个 refresh 进行中 */
const refreshPromises: Record<RefreshType, Promise<TokenPair> | null> = { user: null, admin: null }

/** 统一登出处理:清 store + 跳转登录页 */
function handleLogout(type: RefreshType) {
  if (type === 'admin') {
    const adminStore = useAdminStore()
    adminStore.logout()
    router.push('/admin/login')
  } else {
    const userStore = useUserStore()
    userStore.logout()
    router.push('/login')
  }
}

/** 生成 UUID v4(优先用浏览器原生 crypto.randomUUID) */
function generateIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  // 兜底:简易 v4
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

/** 从 localStorage 安全读取 */
function safeGetItem(key: string): string | null {
  return storage.local.get(key)
}

/**
 * 用 refresh token 换新 access + refresh token(带并发去重)。
 * 同一 type 并发请求只触发一次 refresh,其他请求 await 同一个 Promise。
 */
function doRefresh(instance: AxiosInstance, type: RefreshType): Promise<TokenPair> {
  if (refreshPromises[type]) {
    return refreshPromises[type]!
  }
  const store = type === 'user' ? useUserStore() : useAdminStore()
  const refreshToken = store.refreshToken
  if (!refreshToken) {
    return Promise.reject(new Error('no refresh token'))
  }
  refreshPromises[type] = instance.post(REFRESH_URL[type], { refreshToken }, { __isRefresh: true })
    .then((res) => {
      // refresh 接口走的是 response 拦截器,code=1 时返回 data.data
      const data = res && res.data ? res.data : res
      const newToken = data.token || data.TOKEN
      const newRefresh = data.refreshToken || data.REFRESH_TOKEN
      if (!newToken) {
        throw new Error('refresh response missing token')
      }
      store.setToken(newToken)
      if (newRefresh && typeof store.setRefreshToken === 'function') {
        store.setRefreshToken(newRefresh)
      }
      return { token: newToken, refreshToken: newRefresh } as TokenPair
    })
    .finally(() => {
      refreshPromises[type] = null
    })
  return refreshPromises[type]!
}

/**
 * 创建 Axios 实例并配置拦截器。
 * 注意:拦截器对 code===1/200 会直接返回 data.data(解包),
 * 因此业务层拿到的不是 AxiosResponse。
 */
function createInstance(baseURL: string, type: 'user' | 'admin' | 'rag'): AxiosInstance {
  const instance = axios.create({
    baseURL,
    timeout: 15000,
    withCredentials: true,
  })

  instance.interceptors.request.use((config) => {
    const headers = config.headers || {}
    // FormData 上传时删除 Content-Type,让浏览器自动设置 multipart/form-data + boundary
    if (config.data instanceof FormData) {
      delete headers['Content-Type']
      if (config.headers) {
        delete config.headers['Content-Type']
      }
    } else {
      headers['Content-Type'] = 'application/json'
    }
    if (type === 'user') {
      const userStore = useUserStore()
      const token = userStore.token || safeGetItem(USER_TOKEN_KEY)
      const userId = userStore.userId || safeGetItem(USER_ID_KEY)
      if (token) {
        headers['authentication'] = token
        headers['Authorization'] = `Bearer ${token}`
        headers['stellar-token'] = token
      }
      if (userId) {
        headers['stellaruserid'] = String(userId)
      }
    } else if (type === 'admin') {
      const adminStore = useAdminStore()
      const token = adminStore.token || safeGetItem(ADMIN_TOKEN_KEY)
      const empId = adminStore.empId || safeGetItem(ADMIN_EMPID_KEY)
      if (token) {
        headers['token'] = token
        headers['Authorization'] = `Bearer ${token}`
        headers['stellar-token'] = token
      }
      if (empId) {
        headers['stellaremployeeid'] = String(empId)
      }
    } else if (type === 'rag') {
      const userStore = useUserStore()
      const adminStore = useAdminStore()
      const isAdminEndpoint = config.url && (
        config.url.startsWith('/ragapi/api/admin') ||
        config.url.startsWith('/ragapi/api/kb')
      )
      const token = isAdminEndpoint
        ? (adminStore.token || safeGetItem(ADMIN_TOKEN_KEY) || userStore.token || safeGetItem(USER_TOKEN_KEY))
        : (userStore.token || safeGetItem(USER_TOKEN_KEY) || adminStore.token || safeGetItem(ADMIN_TOKEN_KEY))
      if (token) {
        headers['Authorization'] = `Bearer ${token}`
      }
    }

    // 写操作自动注入幂等键(业务方可在调用时手动覆盖 config.headers['X-Idempotency-Key'])
    // 用途:网络重试时同一逻辑请求复用同一 key;用户主动重发视为新请求
    const method = String(config.method || 'get').toLowerCase()
    if (WRITE_METHODS.has(method) && headers['X-Idempotency-Key'] === undefined) {
      headers['X-Idempotency-Key'] = generateIdempotencyKey()
    }

    config.headers = headers
    return config
  }, (error) => {
    return Promise.reject(error)
  })

  instance.interceptors.response.use((response) => {
    const data = response.data
    if (data && typeof data === 'object' && 'code' in data) {
      if (type === 'rag') {
        if (data.code === 0) {
          return data
        }
        if (data.code === 401) {
          const userStore = useUserStore()
          userStore.logout()
          router.push('/login')
          ElMessage.error(data.message || '登录已过期，请重新登录')
          return Promise.reject(new Error(data.message || 'Unauthorized'))
        }
        if (!response.config?.__silent) {
          ElMessage.error(data.message || '请求失败')
        }
        return Promise.reject(new Error(data.message || 'Request failed'))
      } else {
        if (data.code === 1 || data.code === 200) {
          return data.data
        }
        if (data.code === 401) {
          if (type === 'user') {
            const userStore = useUserStore()
            userStore.logout()
            router.push('/login')
          } else if (type === 'admin') {
            const adminStore = useAdminStore()
            adminStore.logout()
            router.push('/admin/login')
          }
          ElMessage.error(data.msg || '登录已过期，请重新登录')
          return Promise.reject(new Error(data.msg || 'Unauthorized'))
        }
        if (!response.config?.__silent) {
          ElMessage.error(data.msg || '请求失败')
        }
        return Promise.reject(new Error(data.msg || 'Request failed'))
      }
    }
    return data
  }, async (error) => {
    if (error.config?.__silent) {
      return Promise.reject(error)
    }
    if (error.response) {
      const status = error.response.status
      if (status === 401) {
        // refresh 请求本身的 401 不再重试,直接登出
        if (error.config?.__isRefresh) {
          handleLogout(type as RefreshType)
          return Promise.reject(error)
        }
        // 尝试用 refresh token 换新 token 后重试原请求
        if (type === 'user' || type === 'admin') {
          try {
            const { token } = await doRefresh(instance, type)
            // 用新 token 重试原请求
            const retryConfig = { ...error.config, headers: { ...error.config.headers } }
            if (type === 'user') {
              retryConfig.headers.authentication = token
              retryConfig.headers.Authorization = `Bearer ${token}`
              retryConfig.headers['stellar-token'] = token
            } else {
              retryConfig.headers.token = token
              retryConfig.headers.Authorization = `Bearer ${token}`
              retryConfig.headers['stellar-token'] = token
            }
            return instance.request(retryConfig)
          } catch (refreshErr) {
            handleLogout(type)
            ElMessage.error('登录已过期，请重新登录')
            return Promise.reject(refreshErr)
          }
        } else if (type === 'rag') {
          // RAG 请求 401:先尝试用 refresh token 换新 token 再重试(与 user/admin 行为对齐)
          const url = String(error.config?.url || '')
          const isAdminEndpoint = url.startsWith('/ragapi/api/admin') || url.startsWith('/ragapi/api/kb')
          const refreshType: RefreshType = isAdminEndpoint ? 'admin' : 'user'
          // 已重试过一次仍 401(如 RAG 端密钥/算法不匹配、账号被禁用),
          // 不再刷新重试,避免无限 401→refresh→retry 循环导致页面一直转圈
          if (error.config?.__ragRetried) {
            handleLogout(refreshType)
            ElMessage.error('登录已过期，请重新登录')
            return Promise.reject(error)
          }
          const refreshInstance = refreshType === 'user' ? userRaw : adminRaw
          try {
            const { token } = await doRefresh(refreshInstance, refreshType)
            const retryConfig = { ...error.config, headers: { ...error.config.headers }, __ragRetried: true }
            retryConfig.headers.Authorization = `Bearer ${token}`
            return instance.request(retryConfig)
          } catch (refreshErr) {
            handleLogout(refreshType)
            ElMessage.error('登录已过期，请重新登录')
            return Promise.reject(refreshErr)
          }
        }
        ElMessage.error('登录已过期，请重新登录')
      } else if (status === 403) {
        ElMessage.error('没有权限执行此操作')
      } else if (status === 500) {
        ElMessage.error('服务器内部错误')
      } else {
        ElMessage.error(error.response.data?.msg || `请求失败 (${status})`)
      }
    } else if (error.request) {
      ElMessage.error('网络错误，请检查网络连接')
    } else {
      ElMessage.error(error.message || '请求失败')
    }
    return Promise.reject(error)
  })

  return instance
}

/**
 * 业务层请求函数类型。
 * 泛型 T = 拦截器解包后的返回载荷(user/admin 为 data.data,rag 为整个 envelope)。
 */
export type ApiRequestFn = <T = unknown>(config: AxiosRequestConfig) => Promise<T>

const userRaw = createInstance('', 'user')
const adminRaw = createInstance('', 'admin')
const ragRaw = createInstance('', 'rag')

/** C 端用户请求实例,自动携带用户 token 和 userId */
export const userRequest: ApiRequestFn = ((config: AxiosRequestConfig) => userRaw.request(config)) as ApiRequestFn
/** 管理后台请求实例,自动携带管理员 token 和 empId */
export const adminRequest: ApiRequestFn = ((config: AxiosRequestConfig) => adminRaw.request(config)) as ApiRequestFn
/** RAG 服务请求实例,智能判断 token 来源 */
export const ragRequest: ApiRequestFn = ((config: AxiosRequestConfig) => ragRaw.request(config)) as ApiRequestFn

export default userRequest

/** 获取当前用户 token 的便捷方法 */
export const getAccessToken = (): string | null => {
  const userStore = useUserStore()
  return userStore.token || safeGetItem(USER_TOKEN_KEY)
}
