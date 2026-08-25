import { ref, watch, type Ref } from 'vue'
import { storage } from '@/utils/storage'

const STORAGE_KEY = 'stellar_site_title'
const DEFAULT_TITLE = '星耀商城'

const siteTitle = ref<string>(storage.local.get(STORAGE_KEY) || DEFAULT_TITLE)

watch(siteTitle, (val) => {
  const title = val?.trim() || DEFAULT_TITLE
  if (title === DEFAULT_TITLE) {
    storage.local.remove(STORAGE_KEY)
  } else {
    storage.local.set(STORAGE_KEY, title)
  }
  document.title = title
})

export interface SiteTitleComposable {
  siteTitle: Ref<string>
  setSiteTitle: (val: string) => void
}

/**
 * 站点标题管理 composable。
 * 提供站点标题的读写能力,标题变更时自动同步到 document.title 并持久化到本地存储。
 * 默认标题为"星耀商城"。
 */
export function useSiteTitle(): SiteTitleComposable {
  /**
   * 设置站点标题。
   * 如果传入值为空或仅包含空白字符,则重置为默认标题"星耀商城"。
   */
  function setSiteTitle(val: string) {
    siteTitle.value = val?.trim() || DEFAULT_TITLE
  }
  return { siteTitle, setSiteTitle }
}
