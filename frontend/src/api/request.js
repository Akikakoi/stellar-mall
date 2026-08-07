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

/** 从 localStorage 安全读取 */
function safeGetItem(key) {
  return storage.local.get(key)
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
  }, (error) => {
    if (error.config?.__silent) {
      return Promise.reject(error)
    }
    if (error.response) {
      const status = error.response.status
      if (status === 401) {
        if (type === 'user') {
          const userStore = useUserStore()
          userStore.logout()
          router.push('/login')
        } else if (type === 'admin') {
          const adminStore = useAdminStore()
          adminStore.logout()
          router.push('/admin/login')
        } else if (type === 'rag') {
          const userStore = useUserStore()
          userStore.logout()
          router.push('/login')
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
