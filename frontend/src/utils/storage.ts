/**
 * 统一的浏览器存储工具。
 * 对 localStorage / sessionStorage 做异常包装,避免隐私模式、禁用存储、
 * 或存储超限导致整个调用链崩溃。
 */

const DEFAULT_NAMESPACE = ''

/** 最小 Storage 引擎形状(兼容 SSR/测试环境的 mock) */
interface StorageLike {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
  removeItem(key: string): void
  clear(): void
  key(index: number): string | null
  readonly length: number
}

/** 存储包装器对外接口 */
export interface StorageWrapper {
  get(key: string, defaultValue?: string | null): string | null
  set(key: string, value: unknown): boolean
  remove(key: string): boolean
  getObject<T>(key: string, defaultValue: T): T
  getObject<T = unknown>(key: string): T | null
  setObject(key: string, value: unknown): boolean
  clear(): boolean
  has(key: string): boolean
  clearNamespace(): boolean
}

function safeCall<T>(fn: () => T, fallback: T): T {
  try {
    return fn()
  } catch (e) {
    console.warn('[storage] operation failed:', e)
    return fallback
  }
}

/**
 * 创建一个带命名空间的存储包装器。
 * 所有操作都会通过 safeCall 进行异常保护,避免存储不可用时崩溃。
 * @param engine 浏览器存储引擎(localStorage 或 sessionStorage)
 * @param namespace 命名空间前缀,用于隔离不同模块的存储 key
 */
function makeStorage(engine: StorageLike, namespace?: string): StorageWrapper {
  const ns = namespace || DEFAULT_NAMESPACE
  const prefix = ns ? `${ns}_` : ''

  const namespaced = (key: string) => (prefix && !key.startsWith(prefix) ? `${prefix}${key}` : key)

  return {
    /** 读取字符串值 */
    get(key: string, defaultValue: string | null = null) {
      return safeCall(() => {
        const raw = engine.getItem(namespaced(key))
        return raw === null ? defaultValue : raw
      }, defaultValue)
    },

    /** 写入字符串值 */
    set(key: string, value: unknown) {
      return safeCall(() => {
        engine.setItem(namespaced(key), String(value))
        return true
      }, false)
    },

    /** 删除指定键 */
    remove(key: string) {
      return safeCall(() => {
        engine.removeItem(namespaced(key))
        return true
      }, false)
    },

    /** 读取并解析 JSON 对象 */
    getObject<T>(key: string, defaultValue?: T | null): T | null {
      const raw = this.get(key)
      if (raw === null || raw === undefined || raw === '') return defaultValue ?? null
      try {
        return JSON.parse(raw) as T
      } catch (e) {
        console.warn('[storage] JSON parse failed, key=', key, e)
        return defaultValue ?? null
      }
    },

    /** 将对象序列化为 JSON 后写入 */
    setObject(key: string, value: unknown) {
      return this.set(key, JSON.stringify(value))
    },

    /** 清空当前存储引擎的所有数据 */
    clear() {
      return safeCall(() => {
        engine.clear()
        return true
      }, false)
    },

    /** 检查指定键是否存在 */
    has(key: string) {
      return safeCall(() => engine.getItem(namespaced(key)) !== null, false)
    },

    /** 清除当前命名空间下的所有 key(仅当配置了 namespace 时有效) */
    clearNamespace() {
      if (!prefix) return false
      return safeCall(() => {
        for (let i = engine.length - 1; i >= 0; i--) {
          const key = engine.key(i)
          if (key && key.startsWith(prefix)) {
            engine.removeItem(key)
          }
        }
        return true
      }, false)
    }
  }
}

const memoryStorage: StorageLike = {
  getItem: () => null,
  setItem: () => {},
  removeItem: () => {},
  clear: () => {},
  key: () => null,
  length: 0
}

const local: StorageWrapper =
  typeof window !== 'undefined' && window.localStorage
    ? makeStorage(window.localStorage, DEFAULT_NAMESPACE)
    : makeStorage(memoryStorage, DEFAULT_NAMESPACE)

const session: StorageWrapper =
  typeof window !== 'undefined' && window.sessionStorage
    ? makeStorage(window.sessionStorage, DEFAULT_NAMESPACE)
    : makeStorage(memoryStorage, DEFAULT_NAMESPACE)

/**
 * 浏览器存储工具对象,提供 localStorage 和 sessionStorage 的统一操作接口。
 * 所有方法均做了异常保护,在隐私模式或存储不可用时不会抛出异常。
 */
export const storage = {
  local,
  session
}

export default storage
