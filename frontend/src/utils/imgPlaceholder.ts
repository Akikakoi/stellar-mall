/**
 * v-placeholder：图片加载失败时替换为全局占位图（window.__PH），并解绑 onerror 防止死循环。
 *
 * 用法：<img :src="item.image" v-placeholder />
 * 等价于内联写法 onerror="this.src=window.__PH;this.onerror=null"，
 * 全站占位图逻辑收口在此，更换占位图策略只改这里。
 */
import type { Directive } from 'vue'

export const placeholder: Directive<HTMLImageElement> = {
  mounted(el) {
    el.addEventListener('error', () => {
      const ph = (window as any).__PH
      if (!ph || el.src === ph) return
      el.src = ph
    })
  }
}
