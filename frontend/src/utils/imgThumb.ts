/**
 * 阿里云 OSS 图片处理工具。
 *
 * 商城的商品主图直接存放在 OSS 公有桶里，原图动辄 100~760KB（1600×1600）。
 * OSS 的图片处理服务可以按 URL 参数在边缘节点完成缩放 + 转 webp，
 * 首屏环绕卡片实际只显示 ~200×230 CSS px（2x 屏约 400×460），
 * 用 w_480 的 webp 即可，单张从 ~150KB 降到 ~3~27KB。
 *
 * 只对 *.aliyuncs.com 的 OSS 地址追加参数：
 *  - 本地/三方图片原样返回，避免把无关 CDN 的 URL 改坏；
 *  - URL 已带 x-oss-process 时原样返回，避免参数重复。
 */

/** 匹配阿里云 OSS 公有访问域名（如 web-xxx.oss-cn-beijing.aliyuncs.com） */
const OSS_URL_PATTERN = /^https?:\/\/[^/]+\.aliyuncs\.com\//i

/** 环绕卡片默认缩略图宽度：覆盖 2x 屏下卡片内容区（约 406×464 物理像素） */
export const ORBIT_THUMB_WIDTH = 480

/**
 * 生成 OSS 缩略图 URL（等比缩放到指定宽度并转 webp）。
 *
 * @param url 原图 URL；空值返回空串，便于调用方用 `|| 占位图` 兜底
 * @param width 目标宽度（px）
 * @returns 缩略图 URL；非 OSS 地址/已处理地址原样返回
 */
export function ossThumb(url: string | null | undefined, width = ORBIT_THUMB_WIDTH): string {
  if (!url) return ''
  if (url.includes('x-oss-process=')) return url
  if (!OSS_URL_PATTERN.test(url)) return url
  const separator = url.includes('?') ? '&' : '?'
  return `${url}${separator}x-oss-process=image/resize,w_${width}/format,webp/quality,q_85`
}
