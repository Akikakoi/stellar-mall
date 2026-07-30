import { ref, watch } from 'vue'
import { storage } from '@/utils/storage'

const STORAGE_KEY = 'stellar_site_title'
const DEFAULT_TITLE = '星耀商城'

const siteTitle = ref(storage.local.get(STORAGE_KEY) || DEFAULT_TITLE)

watch(siteTitle, (val) => {
  const title = val?.trim() || DEFAULT_TITLE
  if (title === DEFAULT_TITLE) {
    storage.local.remove(STORAGE_KEY)
  } else {
    storage.local.set(STORAGE_KEY, title)
  }
  document.title = title
})

export function useSiteTitle() {
  function setSiteTitle(val) {
    siteTitle.value = val?.trim() || DEFAULT_TITLE
  }
  return { siteTitle, setSiteTitle }
}
