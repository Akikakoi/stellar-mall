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

/**
 * 创建一个带命名空间的存储包装器。
 * 所有操作都会通过 safeCall 进行异常保护，避免存储不可用时崩溃。
 * @param {Storage} engine - 浏览器存储引擎（localStorage 或 sessionStorage）
 * @param {string} [namespace] - 命名空间前缀，用于隔离不同模块的存储 key
 * @returns {object} 存储操作对象
 */
function makeStorage(engine, namespace) {
  const ns = namespace || DEFAULT_NAMESPACE
  const prefix = ns ? `${ns}_` : ''

  const namespaced = (key) => (prefix && !key.startsWith(prefix) ? `${prefix}${key}` : key)

  return {
    /**
     * 读取字符串值。
     * @param {string} key - 存储键名
     * @param {*} [defaultValue=null] - 键不存在时的默认返回值
     * @returns {string|null} 存储的值，或默认值
     */
    get(key, defaultValue = null) {
      return safeCall(() => {
        const raw = engine.getItem(namespaced(key))
        return raw === null ? defaultValue : raw
      }, defaultValue)
    },

    /**
     * 写入字符串值。
     * @param {string} key - 存储键名
     * @param {*} value - 要存储的值，会被转为字符串
     * @returns {boolean} 操作是否成功
     */
    set(key, value) {
      return safeCall(() => {
        engine.setItem(namespaced(key), String(value))
        return true
      }, false)
    },

    /**
     * 删除指定键。
     * @param {string} key - 存储键名
     * @returns {boolean} 操作是否成功
     */
    remove(key) {
      return safeCall(() => {
        engine.removeItem(namespaced(key))
        return true
      }, false)
    },

    /**
     * 读取并解析 JSON 对象。
     * @param {string} key - 存储键名
     * @param {*} [defaultValue=null] - 键不存在或解析失败时的默认返回值
     * @returns {*} 解析后的对象，或默认值
     */
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

    /**
     * 将对象序列化为 JSON 后写入。
     * @param {string} key - 存储键名
     * @param {*} value - 要存储的对象
     * @returns {boolean} 操作是否成功
     */
    setObject(key, value) {
      return this.set(key, JSON.stringify(value))
    },

    /**
     * 清空当前存储引擎的所有数据。
     * @returns {boolean} 操作是否成功
     */
    clear() {
      return safeCall(() => {
        engine.clear()
        return true
      }, false)
    },

    /**
     * 检查指定键是否存在。
     * @param {string} key - 存储键名
     * @returns {boolean} 键是否存在
     */
    has(key) {
      return safeCall(() => engine.getItem(namespaced(key)) !== null, false)
    },

    /**
     * 清除当前命名空间下的所有 key（仅当配置了 namespace 时有效）。
     * @returns {boolean} 操作是否成功，无命名空间时返回 false
     */
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

/**
 * 浏览器存储工具对象，提供 localStorage 和 sessionStorage 的统一操作接口。
 * 所有方法均做了异常保护，在隐私模式或存储不可用时不会抛出异常。
 * @property {object} local - localStorage 包装器，方法：get / set / remove / getObject / setObject / clear / has / clearNamespace
 * @property {object} session - sessionStorage 包装器，方法同上
 */
export const storage = {
  local,
  session
}

export default storage
