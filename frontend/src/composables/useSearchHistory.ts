/**
 * 搜索历史组合式函数(模块级共享状态)
 *
 * 模块职责:
 * - 统一管理搜索历史的 localStorage 读写(按用户 ID 隔离)
 * - 所有搜索入口(导航栏主页搜索框、商城搜索页大搜索框)共享同一份历史数据
 *
 * 使用示例:
 *   const { searchHistory, addToHistory, removeHistory } = useSearchHistory()
 */
import { ref, watch, type Ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'

const HISTORY_MAX = 20
const FALLBACK_KEY = 'stellar_search_history'

// 模块级共享状态:保证多个组件(导航栏 / 搜索页)拿到同一份历史数据
const searchHistory = ref<string[]>([])
let loadedFor: number | string | null = null

/**
 * 根据用户 ID 生成搜索历史存储键名(登录用户隔离历史记录)
 */
function historyKey(uid: number | string | null): string {
  return uid ? `stellar_search_history_${uid}` : FALLBACK_KEY
}

/** 从 localStorage 加载当前用户的历史记录到共享状态,解析失败时回退为空数组 */
function loadInto() {
  try {
    searchHistory.value = JSON.parse(localStorage.getItem(historyKey(loadedFor)) || '[]') as string[]
  } catch {
    searchHistory.value = []
  }
}

export interface SearchHistoryComposable {
  searchHistory: Ref<string[]>
  addToHistory: (kw: string) => void
  removeHistory: (kw: string) => Promise<boolean>
}

/**
 * 使用搜索历史:返回共享的历史列表与增删方法
 */
export function useSearchHistory(): SearchHistoryComposable {
  const userStore = useUserStore()

  // 首次调用时按当前登录用户加载历史
  if (loadedFor === null) {
    loadedFor = userStore.userId || null
    loadInto()
  }

  // 登录状态变化时切换为对应用户的历史记录
  watch(
    () => userStore.userId,
    (uid) => {
      loadedFor = uid || null
      loadInto()
    }
  )

  /**
   * 将搜索关键词添加到搜索历史
   * 去重后插入到数组头部,超出最大数量时截断,并同步写入 localStorage
   */
  function addToHistory(kw: string) {
    const arr = searchHistory.value.filter(h => h !== kw)
    arr.unshift(kw)
    if (arr.length > HISTORY_MAX) arr.length = HISTORY_MAX
    searchHistory.value = arr
    localStorage.setItem(historyKey(loadedFor), JSON.stringify(arr))
  }

  /**
   * 删除单条搜索历史记录(带确认弹窗)
   * 用户取消时返回 false,删除成功返回 true
   */
  async function removeHistory(kw: string): Promise<boolean> {
    try {
      await ElMessageBox.confirm('确定要删除这条搜索记录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch {
      return false
    }
    searchHistory.value = searchHistory.value.filter(h => h !== kw)
    localStorage.setItem(historyKey(loadedFor), JSON.stringify(searchHistory.value))
    return true
  }

  return { searchHistory, addToHistory, removeHistory }
}
