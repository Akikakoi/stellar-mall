/**
 * imgThumb 单元测试
 *
 * 覆盖：OSS 地址追加缩放参数（? / & 两种拼接）、已有处理参数不重复追加、
 * 非 OSS 地址原样返回、空值返回空串、自定义宽度。
 */
import { describe, it, expect } from 'vitest'
import { ossThumb, ORBIT_THUMB_WIDTH } from '@/utils/imgThumb'

describe('ossThumb', () => {
  const oss = 'https://web-akikakoi.oss-cn-beijing.aliyuncs.com/stellar-mall/spu/a.png'

  it('OSS 地址追加 resize+webp 处理参数', () => {
    expect(ossThumb(oss)).toBe(
      `${oss}?x-oss-process=image/resize,w_${ORBIT_THUMB_WIDTH}/format,webp/quality,q_85`
    )
  })

  it('已带查询参数的 OSS 地址用 & 拼接', () => {
    expect(ossThumb(`${oss}?v=1`)).toBe(
      `${oss}?v=1&x-oss-process=image/resize,w_${ORBIT_THUMB_WIDTH}/format,webp/quality,q_85`
    )
  })

  it('已带 x-oss-process 的地址原样返回，避免参数重复', () => {
    const processed = `${oss}?x-oss-process=image/resize,w_300`
    expect(ossThumb(processed)).toBe(processed)
  })

  it('非 OSS 地址（本地/三方 CDN）原样返回', () => {
    expect(ossThumb('http://127.0.0.1:8082/img/a.png')).toBe('http://127.0.0.1:8082/img/a.png')
    expect(ossThumb('https://cdn.example.com/a.png')).toBe('https://cdn.example.com/a.png')
    expect(ossThumb('/images/background-light.webp')).toBe('/images/background-light.webp')
  })

  it('空值返回空串，便于调用方 || 占位图', () => {
    expect(ossThumb('')).toBe('')
    expect(ossThumb(null)).toBe('')
    expect(ossThumb(undefined)).toBe('')
  })

  it('支持自定义宽度', () => {
    expect(ossThumb(oss, 320)).toContain('image/resize,w_320/format,webp')
  })
})
