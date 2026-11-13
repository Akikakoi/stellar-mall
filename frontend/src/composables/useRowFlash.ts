/**
 * 表格行反馈：对「刚被改过的行」做一次高亮闪动。
 *
 * 后台的批量上下架、发货、审核通过都是"提交完刷新列表"，
 * 用户知道成功了，但不知道具体是哪几行被改了。闪一下就能对上号。
 *
 * 用法：
 *   const { flashRows, rowClass } = useRowFlash()
 *   // 接口成功后、刷新列表前调用
 *   flashRows(ids)
 *   // el-table 上挂 :row-class-name="({ row }) => rowClass(row.id, 其它类名)"
 *
 * 注意：el-table 的 row-class-name 在表格自己的渲染里执行，
 * 所以这里返回的类名会跟着 keys 变化触发重绘，不需要手动 forceUpdate。
 */
import { onBeforeUnmount, ref } from 'vue'

export interface RowFlashApi {
  /** 让这些键对应的行闪一次（键会被转成字符串比较） */
  flashRows: (keys: Array<string | number>) => void
  /** 拼出该行的类名：闪动中追加 row-flash */
  rowClass: (key: string | number, base?: string) => string
  /** 当前正在闪的键 */
  flashing: () => Set<string>
}

/**
 * @param duration 闪动时长，默认 1100ms（与样式里的 animation 时长保持一致）
 */
export function useRowFlash(duration = 1100): RowFlashApi {
  const active = ref<Set<string>>(new Set())
  let timer: ReturnType<typeof setTimeout> | undefined

  function flashRows(keys: Array<string | number>) {
    const list = keys.filter((k) => k !== null && k !== undefined).map((k) => String(k))
    if (list.length === 0) return
    const next = new Set(active.value)
    list.forEach((k) => next.add(k))
    active.value = next

    // 一批操作只保留一个计时器：连续操作时以最后一次为准
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      active.value = new Set()
      timer = undefined
    }, duration)
  }

  function rowClass(key: string | number, base = '') {
    if (key === null || key === undefined) return base
    if (!active.value.has(String(key))) return base
    return base ? `${base} row-flash` : 'row-flash'
  }

  onBeforeUnmount(() => {
    if (timer) clearTimeout(timer)
  })

  return { flashRows, rowClass, flashing: () => active.value }
}
