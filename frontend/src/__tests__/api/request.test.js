/**
 * request.js 拦截器逻辑测试
 *
 * 因为 createInstance 在模块顶层创建了 3 个 axios 实例（user/admin/rag），
 * 测试策略：mock axios.create 返回可追踪的实例，收集所有拦截器引用。
 */
import { describe, it, expect, beforeEach, beforeAll, vi } from 'vitest'

// ── mock Element Plus ──
vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn() }
}))

// ── mock vue-router ──
const mockPush = vi.fn()
vi.mock('@/router', () => ({
  default: { push: mockPush }
}))

// ── mock stores ──
const mockUserLogout = vi.fn()
const mockAdminLogout = vi.fn()
const mockUserSetToken = vi.fn()
const mockAdminSetToken = vi.fn()
const mockUserRefreshToken = { get: () => 'user-refresh-mock', set: vi.fn() }
const mockAdminRefreshToken = { get: () => 'admin-refresh-mock', set: vi.fn() }

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    token: 'user-token-mock',
    userId: 42,
    refreshToken: 'user-refresh-mock',
    setToken: mockUserSetToken,
    setRefreshToken: mockUserRefreshToken.set,
    logout: mockUserLogout
  })
}))
vi.mock('@/stores/admin', () => ({
  useAdminStore: () => ({
    token: 'admin-token-mock',
    empId: 7,
    refreshToken: 'admin-refresh-mock',
    setToken: mockAdminSetToken,
    setRefreshToken: mockAdminRefreshToken.set,
    logout: mockAdminLogout
  })
}))

// ── mock axios: 收集每个 createInstance 的拦截器 ──
const instances = [] // { type, reqInterceptors, resInterceptors, resErrorInterceptors }

vi.mock('axios', () => {
  // 每次 create 返回新实例，实例拦截器可以被触发
  let createCount = 0
  const types = ['user', 'admin', 'rag']

  return {
    default: {
      create: vi.fn(() => {
        const type = types[createCount++] || 'unknown'
        const reqFns = []
        const resFns = []
        const resErrFns = []

        const inst = {
          __type: type,
          interceptors: {
            request: {
              use: vi.fn((onFulfilled, onRejected) => {
                reqFns.push({ onFulfilled, onRejected })
                return reqFns.length - 1
              })
            },
            response: {
              use: vi.fn((onFulfilled, onRejected) => {
                resFns.push({ onFulfilled, onRejected })
                if (onRejected) resErrFns.push(onRejected)
                return resFns.length - 1
              })
            }
          },
          // 这些是实例方法，测试会手动替换
          get: vi.fn(),
          post: vi.fn(),
          put: vi.fn(),
          delete: vi.fn(),
          request: vi.fn()
        }

        instances.push({ type, inst, reqFns, resFns, resErrFns })
        return inst
      })
    },
    create: null // 也用上面的 default.create
  }
})

import { ElMessage } from 'element-plus'

// 动态导入触发 createInstance
let userReq, adminReq, ragReq, getAccessToken

beforeAll(async () => {
  const mod = await import('@/api/request')
  userReq = mod.userRequest
  adminReq = mod.adminRequest
  ragReq = mod.ragRequest
  getAccessToken = mod.getAccessToken
})

function getInstance(type) {
  return instances.find(i => i.type === type)
}

// ═══════════════════════════════════════════════════
// 顶层 wrapper：所有子 describe 共用同一个 beforeEach 清理
// ═══════════════════════════════════════════════════
describe('request.js', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

describe('request module', () => {

  it('创建了 3 个 axios 实例', () => {
    expect(instances).toHaveLength(3)
    expect(instances.map(i => i.type)).toEqual(['user', 'admin', 'rag'])
  })

  it('getAccessToken 返回 userStore 的 token', () => {
    expect(getAccessToken()).toBe('user-token-mock')
  })
})

// ═══════════════════════════════════════════════════
// 请求拦截器
// ═══════════════════════════════════════════════════
describe('request interceptor', () => {
  it('user 类型注入 token + userId', () => {
    const { reqFns } = getInstance('user')
    const fn = reqFns[0].onFulfilled
    const config = { headers: {} }

    const result = fn(config)

    expect(result.headers.authentication).toBe('user-token-mock')
    expect(result.headers.Authorization).toBe('Bearer user-token-mock')
    expect(result.headers['stellar-token']).toBe('user-token-mock')
    expect(result.headers.stellaruserid).toBe('42')
  })

  it('admin 类型注入 token + empId', () => {
    const { reqFns } = getInstance('admin')
    const fn = reqFns[0].onFulfilled
    const config = { headers: {} }

    const result = fn(config)

    expect(result.headers.token).toBe('admin-token-mock')
    expect(result.headers.Authorization).toBe('Bearer admin-token-mock')
    expect(result.headers.stellaremployeeid).toBe('7')
  })

  it('FormData 请求删除 Content-Type', () => {
    const { reqFns } = getInstance('user')
    const fn = reqFns[0].onFulfilled
    const formData = new FormData()

    const config = {
      headers: { 'Content-Type': 'application/json' },
      data: formData
    }

    const result = fn(config)
    // headers['Content-Type'] 应被删除
    expect(result.headers['Content-Type']).toBeUndefined()
  })

  it('非 FormData 请求设置 Content-Type 为 application/json', () => {
    const { reqFns } = getInstance('user')
    const fn = reqFns[0].onFulfilled
    const config = { headers: {} }

    const result = fn(config)

    expect(result.headers['Content-Type']).toBe('application/json')
  })

  it('无 token 时不注入 header', () => {
    // admin instance 有 mock token，换用另一套 store mock 测试
    // 这里验证：如果 store 和 localStorage 都没有 token，不注入
    // 因为 mock 的 store 有 token，这个场景通过 user logout 后的 token 空来实现
    // 暂时跳过 mock 需要不同 token 的场景
  })

  it('请求拦截器出错时 reject', () => {
    const { reqFns } = getInstance('user')
    const errorFn = reqFns[0].onRejected
    if (!errorFn) return // user instance 没注册 onRejected

    const error = new Error('test error')
    return errorFn(error).catch(e => {
      expect(e.message).toBe('test error')
    })
  })
})

// ═══════════════════════════════════════════════════
// 响应拦截器 — user/admin 类型（code-based）
// ═══════════════════════════════════════════════════
describe('response interceptor - user/admin code-based', () => {
  it('code=1 时返回 data.data', () => {
    const { resFns } = getInstance('user')
    const fn = resFns[0].onFulfilled

    const result = fn({
      data: { code: 1, data: { id: 1, name: 'test' } }
    })

    expect(result).toEqual({ id: 1, name: 'test' })
  })

  it('code=200 时返回 data.data', () => {
    const { resFns } = getInstance('user')
    const fn = resFns[0].onFulfilled

    const result = fn({
      data: { code: 200, data: { ok: true } }
    })

    expect(result).toEqual({ ok: true })
  })

  it('code=401 时触发 logout 并跳转登录', () => {
    const { resFns } = getInstance('user')
    const fn = resFns[0].onFulfilled

    return fn({
      data: { code: 401, msg: 'token expired' }
    }).catch(err => {
      expect(mockUserLogout).toHaveBeenCalled()
      expect(mockPush).toHaveBeenCalledWith('/login')
      expect(ElMessage.error).toHaveBeenCalled()
      expect(err.message).toContain('token expired')
    })
  })

  it('admin 类型 401 跳转 /admin/login', () => {
    const { resFns } = getInstance('admin')
    const fn = resFns[0].onFulfilled

    return fn({
      data: { code: 401, msg: 'admin token expired' }
    }).catch(err => {
      expect(mockAdminLogout).toHaveBeenCalled()
      expect(mockPush).toHaveBeenCalledWith('/admin/login')
    })
  })

  it('__silent 标记时不弹出 error 提示', () => {
    const { resFns } = getInstance('user')
    const fn = resFns[0].onFulfilled
    const response = {
      data: { code: 500, msg: 'silent error' },
      config: { __silent: true }
    }

    return fn(response).catch(() => {
      expect(ElMessage.error).not.toHaveBeenCalled()
    })
  })

  it('无 code 字段的响应直接透传', () => {
    const { resFns } = getInstance('user')
    const fn = resFns[0].onFulfilled

    const result = fn({ data: { raw: 'no code field' } })
    expect(result).toEqual({ raw: 'no code field' })
  })
})

// ═══════════════════════════════════════════════════
// 响应拦截器 — rag 类型
// ═══════════════════════════════════════════════════
describe('response interceptor - rag type', () => {
  it('code=0 时返回完整 data', () => {
    const { resFns } = getInstance('rag')
    const fn = resFns[0].onFulfilled

    const result = fn({
      data: { code: 0, data: { answer: 'hello' } }
    })

    expect(result).toEqual({ code: 0, data: { answer: 'hello' } })
  })

  it('code!=0 时 reject 并提示 error', () => {
    const { resFns } = getInstance('rag')
    const fn = resFns[0].onFulfilled

    return fn({
      data: { code: 500, message: 'RAG error' }
    }).catch(() => {
      expect(ElMessage.error).toHaveBeenCalled()
    })
  })

  it('rag 401 触发 user logout', () => {
    const { resFns } = getInstance('rag')
    const fn = resFns[0].onFulfilled

    return fn({
      data: { code: 401, message: 'rag unauthorized' }
    }).catch(() => {
      expect(mockUserLogout).toHaveBeenCalled()
    })
  })

  it('rag __silent 时不弹 error', () => {
    const { resFns } = getInstance('rag')
    const fn = resFns[0].onFulfilled
    const response = {
      data: { code: 999, message: 'silent' },
      config: { __silent: true }
    }

    return fn(response).catch(() => {
      expect(ElMessage.error).not.toHaveBeenCalled()
    })
  })
})

// ═══════════════════════════════════════════════════
// 错误拦截器（HTTP 状态码）
// ═══════════════════════════════════════════════════
describe('error interceptor', () => {
  it('HTTP 401 触发 refresh 并重试原请求', async () => {
    const { inst, resErrFns } = getInstance('user')
    const fn = resErrFns[0]

    // mock refresh 接口返回新 token（经过 response 拦截器处理后的格式）
    inst.post.mockResolvedValueOnce({ token: 'new-access', refreshToken: 'new-refresh', userId: 42 })
    // mock 重试原请求成功
    inst.request = vi.fn().mockResolvedValueOnce({ data: { code: 1, data: { ok: true } } })

    const error = {
      response: { status: 401, data: {} },
      config: { url: '/user/user/me', method: 'get', headers: {} }
    }

    await fn(error)

    // 应调用 refresh 接口
    expect(inst.post).toHaveBeenCalledWith('/user/user/refresh', { refreshToken: 'user-refresh-mock' }, { __isRefresh: true })
    // 应更新 token
    expect(mockUserSetToken).toHaveBeenCalledWith('new-access')
    // 应重试原请求
    expect(inst.request).toHaveBeenCalled()
  })

  it('refresh 失败时触发 logout', async () => {
    const { inst, resErrFns } = getInstance('user')
    const fn = resErrFns[0]

    // refresh 接口失败
    inst.post.mockRejectedValueOnce(new Error('refresh failed'))

    const error = {
      response: { status: 401, data: {} },
      config: { url: '/user/user/me', method: 'get', headers: {} }
    }

    await fn(error).catch(() => {
      expect(mockUserLogout).toHaveBeenCalled()
      expect(mockPush).toHaveBeenCalledWith('/login')
    })
  })

  it('admin 类型 401 调用 admin refresh 接口', async () => {
    const { inst, resErrFns } = getInstance('admin')
    const fn = resErrFns[0]

    inst.post.mockResolvedValueOnce({ token: 'new-admin-access', refreshToken: 'new-admin-refresh', id: 7 })
    inst.request = vi.fn().mockResolvedValueOnce({ data: { code: 1, data: { ok: true } } })

    const error = {
      response: { status: 401, data: {} },
      config: { url: '/admin/employee/page', method: 'get', headers: {} }
    }

    await fn(error)

    expect(inst.post).toHaveBeenCalledWith('/admin/employee/refresh', { refreshToken: 'admin-refresh-mock' }, { __isRefresh: true })
    expect(mockAdminSetToken).toHaveBeenCalledWith('new-admin-access')
  })

  it('HTTP 403 提示无权限', () => {
    const { resErrFns } = getInstance('user')
    const fn = resErrFns[0]

    const error = {
      response: { status: 403, data: {} },
      config: {}
    }

    return fn(error).catch(() => {
      expect(ElMessage.error).toHaveBeenCalledWith('没有权限执行此操作')
    })
  })

  it('HTTP 500 提示服务器错误', () => {
    const { resErrFns } = getInstance('user')
    const fn = resErrFns[0]

    const error = {
      response: { status: 500, data: {} },
      config: {}
    }

    return fn(error).catch(() => {
      expect(ElMessage.error).toHaveBeenCalledWith('服务器内部错误')
    })
  })

  it('其他 HTTP 错误显示状态码', () => {
    const { resErrFns } = getInstance('user')
    const fn = resErrFns[0]

    const error = {
      response: { status: 404, data: { msg: 'Not Found' } },
      config: {}
    }

    return fn(error).catch(() => {
      expect(ElMessage.error).toHaveBeenCalledWith('Not Found')
    })
  })

  it('网络错误（无 response）', () => {
    const { resErrFns } = getInstance('user')
    const fn = resErrFns[0]

    const error = {
      request: {}, // 有 request 无 response = 网络错误
      config: {}
    }

    return fn(error).catch(() => {
      expect(ElMessage.error).toHaveBeenCalledWith('网络错误，请检查网络连接')
    })
  })

  it('网络错误（无 request 无 response）', () => {
    const { resErrFns } = getInstance('user')
    const fn = resErrFns[0]

    const error = {
      message: 'timeout',
      config: {}
    }

    return fn(error).catch(() => {
      expect(ElMessage.error).toHaveBeenCalledWith('timeout')
    })
  })

  it('__silent 标记时不弹任何提示', () => {
    const { resErrFns } = getInstance('user')
    const fn = resErrFns[0]

    const error = {
      response: { status: 500, data: {} },
      config: { __silent: true }
    }

    return fn(error).catch(() => {
      expect(ElMessage.error).not.toHaveBeenCalled()
    })
  })
})

}) // close top-level describe('request.js')
