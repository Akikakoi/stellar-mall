/**
 * 行政区划工具：把 china-area-data 转换为 Element Plus Cascader 需要的 options 格式。
 *
 * 两处使用：Address.vue（地址管理）、OrderSubmit.vue（下单收货地址）。
 * 此函数为全站唯一实现，改动时不要再复制副本。
 */
export function convertAreaDataToOptions(data: Record<string, any>, parentCode: string): any[] {
  const options: any[] = []
  const areas = data[parentCode]
  if (!areas) return options
  for (const code in areas) {
    const name = areas[code]
    // 跳过"市辖区"、"市辖县"等冗余中间节点
    if (name === '市辖区' || name === '市辖县') {
      // 把它的子节点直接提升到当前层级
      const children = convertAreaDataToOptions(data, code)
      options.push(...children)
      continue
    }
    const option: Record<string, any> = {
      value: name,
      label: name,
      code: code
    }
    const children = convertAreaDataToOptions(data, code)
    if (children.length > 0) {
      option.children = children
    }
    options.push(option)
  }
  return options
}
