/**
 * Axios 请求封装模块。
 * 提供三种预配置的请求实例：
 *   userRequest  - C 端用户请求，自动携带用户 token 和 userId
 *   adminRequest - 管理后台请求，自动携带管理员 token 和 empId
 *   ragRequest   - RAG 服务请求，智能判断用户端/管理端 token
 * 统一处理认证、401 拦截、错误提示等逻辑。
 */
import axios from 'axios'
import { useUserStore } from '@/stores/user'
import { useAdminStore } from '@/stores/admin'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { storage } from '@/utils/storage'

const USER_TOKEN_KEY = 'stellar_user_token'
const USER_ID_KEY = 'stellar_user_id'
const ADMIN_TOKEN_KEY = 'stellar_admin_token'
const ADMIN_EMPID_KEY = 'stellar_admin_empid'

/** 写操作需要幂等键的 HTTP 方法集合 */
const WRITE_METHODS = new Set(['post', 'put', 'delete', 'patch'])

/** refresh 接口路径（按 type 区分） */
const REFRESH_URL = {
  user: '/user/user/refresh',
  admin: '/admin/employee/refresh',
}

/** 并发请求时的 refresh 去重：同一 type 只允许一个 refresh 进行中 */
const refreshPromises = { user: null, admin: null }

/** 统一登出处理：清 store + 跳转登录页 */
function handleLogout(type) {
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

/** 生成 UUID v4（优先用浏览器原生 crypto.randomUUID） */
function generateIdempotencyKey() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  // 兜底：简易 v4
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

/** 从 localStorage 安全读取 */
function safeGetItem(key) {
  return storage.local.get(key)
}

/**
 * 用 refresh token 换新 access + refresh token（带并发去重）。
 * 同一 type 并发请求只触发一次 refresh，其他请求 await 同一个 Promise。
 * @param {import('axios').AxiosInstance} instance - 当前 axios 实例（用于发 refresh 请求）
 * @param {string} type - 'user' | 'admin'
 * @returns {Promise<{token: string, refreshToken: string}>} 新的 token 对
 */
function doRefresh(instance, type) {
  if (refreshPromises[type]) {
    return refreshPromises[type]
  }
  const store = type === 'user' ? useUserStore() : useAdminStore()
  const refreshToken = store.refreshToken
  if (!refreshToken) {
    return Promise.reject(new Error('no refresh token'))
  }
  refreshPromises[type] = instance.post(REFRESH_URL[type], { refreshToken }, { __isRefresh: true })
    .then((res) => {
      // refresh 接口走的是 response 拦截器，code=1 时返回 data.data
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
      return { token: newToken, refreshToken: newRefresh }
    })
    .finally(() => {
      refreshPromises[type] = null
    })
  return refreshPromises[type]
}

/**
 * 创建 Axios 实例并配置拦截器。
 * @param {string} baseURL - 请求基础路径
 * @param {string} type - 请求类型：'user' | 'admin' | 'rag'
 * @returns {import('axios').AxiosInstance}
 */
function createInstance(baseURL, type) {
  const instance = axios.create({
    baseURL,
    timeout: 15000,
    withCredentials: true,
  })

  instance.interceptors.request.use((config) => {
    const headers = config.headers || {}
    // FormData 上传时删除 Content-Type，让浏览器自动设置 multipart/form-data + boundary
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

    // 写操作自动注入幂等键（业务方可在调用时手动覆盖 config.headers['X-Idempotency-Key']）
    // 用途：网络重试时同一逻辑请求复用同一 key；用户主动重发视为新请求
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
        // refresh 请求本身的 401 不再重试，直接登出
        if (error.config?.__isRefresh) {
          handleLogout(type)
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
          handleLogout('user')
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

/** C 端用户请求实例，自动携带用户 token 和 userId */
export const userRequest = createInstance('', 'user')
/** 管理后台请求实例，自动携带管理员 token 和 empId */
export const adminRequest = createInstance('', 'admin')
/** RAG 服务请求实例，智能判断 token 来源 */
export const ragRequest = createInstance('', 'rag')

export default userRequest

/** 获取当前用户 token 的便捷方法 */
export const getAccessToken = () => {
  const userStore = useUserStore()
  return userStore.token || safeGetItem(USER_TOKEN_KEY)
}
