/**
 * 统一的浏览器存储工具。
 * 对 localStorage / sessionStorage 做异常包装，避免隐私模式、禁用存储、
 * 或存储超限导致整个调用链崩溃。
 */

const DEFAULT_NAMESPACE = ''

function safeCall(fn, fallback) {
  try {
    return fn()
  } catch (e) {
    console.warn('[storage] operation failed:', e)
    return fallback
  }
}

function makeStorage(engine, namespace) {
  const ns = namespace || DEFAULT_NAMESPACE
  const prefix = ns ? `${ns}_` : ''

  const namespaced = (key) => (prefix && !key.startsWith(prefix) ? `${prefix}${key}` : key)

  return {
    get(key, defaultValue = null) {
      return safeCall(() => {
        const raw = engine.getItem(namespaced(key))
        return raw === null ? defaultValue : raw
      }, defaultValue)
    },

    set(key, value) {
      return safeCall(() => {
        engine.setItem(namespaced(key), String(value))
        return true
      }, false)
    },

    remove(key) {
      return safeCall(() => {
        engine.removeItem(namespaced(key))
        return true
      }, false)
    },

    getObject(key, defaultValue = null) {
      const raw = this.get(key)
      if (raw === null || raw === undefined || raw === '') return defaultValue
      try {
        return JSON.parse(raw)
      } catch (e) {
        console.warn('[storage] JSON parse failed, key=', key, e)
        return defaultValue
      }
    },

    setObject(key, value) {
      return this.set(key, JSON.stringify(value))
    },

    clear() {
      return safeCall(() => {
        engine.clear()
        return true
      }, false)
    },

    has(key) {
      return safeCall(() => engine.getItem(namespaced(key)) !== null, false)
    },

    /** 清除当前命名空间下的所有 key（仅当配置了 namespace 时有效） */
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

const local = typeof window !== 'undefined' && window.localStorage
  ? makeStorage(window.localStorage, DEFAULT_NAMESPACE)
  : makeStorage({ getItem: () => null, setItem: () => {}, removeItem: () => {}, clear: () => {}, key: () => null, length: 0 }, DEFAULT_NAMESPACE)

const session = typeof window !== 'undefined' && window.sessionStorage
  ? makeStorage(window.sessionStorage, DEFAULT_NAMESPACE)
  : makeStorage({ getItem: () => null, setItem: () => {}, removeItem: () => {}, clear: () => {}, key: () => null, length: 0 }, DEFAULT_NAMESPACE)

export const storage = {
  local,
  session
}

export default storage
