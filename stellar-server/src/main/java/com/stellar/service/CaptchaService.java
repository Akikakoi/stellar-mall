package com.stellar.service;

import java.util.Map;

/**
 * 图形验证码服务（E3）。
 * <p>
 * 用于邮箱验证码发送前的机器人防护：前端先调 {@code generate} 拿到图片 + captchaId，
 * 用户识别后把 captchaId + captchaCode 一起提交到 send-code 接口，后端 {@link #validate}
 * 校验通过才放行。
 */
public interface CaptchaService {

    /**
     * 生成图形验证码：返回 captchaId 与 base64 编码的图片。
     *
     * @return Map 包含：
     *   <ul>
     *     <li>captchaId：唯一 ID（前端在 send-code 时回传）</li>
     *     <li>imageBase64：data URI 形式的图片，可直接放到 &lt;img src="..."&gt;</li>
     *   </ul>
     */
    Map<String, String> generate();

    /**
     * 校验图形验证码。校验通过后立即删除（一次性使用）。
     *
     * @param captchaId    generate 返回的 captchaId
     * @param captchaCode  用户识别出的验证码
     * @return true 校验通过；false 表示 captchaId 不存在、已过期、已使用或验证码不匹配
     */
    boolean validate(String captchaId, String captchaCode);
}
