/**
 * mall.js API 单元测试
 * 重点验证 E3 图形验证码接口 getCaptcha 的请求配置正确性。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

// vi.hoisted 保证 mock factory 中能引用到 userRequest（vi.mock 会被提升到文件顶部）
const { userRequest } = vi.hoisted(() => ({
  userRequest: vi.fn()
}))

vi.mock('@/api/request', () => ({
  userRequest
}))

import { getCaptcha } from '@/api/mall'

describe('mall.js API', () => {
  beforeEach(() => {
    userRequest.mockReset()
  })

  describe('getCaptcha (E3)', () => {
    it('调用 GET /captcha/image', async () => {
      userRequest.mockResolvedValueOnce({
        captchaId: 'cap-123',
        imageBase64: 'data:image/png;base64,xxxxx'
      })

      const res = await getCaptcha()

      expect(userRequest).toHaveBeenCalledTimes(1)
      const config = userRequest.mock.calls[0]![0]!
      expect(config.url).toBe('/captcha/image')
      expect(config.method).toBe('get')
      expect(res.captchaId).toBe('cap-123')
      expect(res.imageBase64).toContain('data:image/png;base64,')
    })

    it('请求失败时抛出错误', async () => {
      userRequest.mockRejectedValueOnce(new Error('network'))
      await expect(getCaptcha()).rejects.toThrow('network')
    })
  })
})
