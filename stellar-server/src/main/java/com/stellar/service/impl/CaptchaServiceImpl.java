package com.stellar.service.impl;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.IdUtil;
import com.stellar.service.CaptchaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 图形验证码服务实现（E3）。
 * <p>
 * Redis key 设计：{@code captcha:{captchaId}} → code（区分大小写不敏感），TTL = 2 分钟。
 * 校验通过后立即删除 key（一次性使用，防重放）。
 * <p>
 * 采用 hutool {@link LineCaptcha}（线干扰验证码），4 位字符，宽 120 高 40。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaServiceImpl implements CaptchaService {

    /** Redis key 前缀：captcha:{captchaId} → code */
    private static final String CAPTCHA_KEY_PREFIX = "captcha:";

    /** 验证码 TTL（分钟） */
    private static final long CAPTCHA_TTL_MINUTES = 2;

    /** 验证码图片宽度 */
    private static final int WIDTH = 120;

    /** 验证码图片高度 */
    private static final int HEIGHT = 40;

    /** 验证码字符数 */
    private static final int CODE_COUNT = 4;

    /** 干扰线数量 */
    private static final int LINE_COUNT = 30;

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Map<String, String> generate() {
        // 生成验证码图片
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(WIDTH, HEIGHT, CODE_COUNT, LINE_COUNT);
        String code = captcha.getCode();
        String captchaId = IdUtil.fastSimpleUUID();

        // 存入 Redis，TTL 2 分钟（code 统一转大写，校验时也转大写，做到大小写不敏感）
        stringRedisTemplate.opsForValue().set(
                CAPTCHA_KEY_PREFIX + captchaId,
                code.toUpperCase(),
                CAPTCHA_TTL_MINUTES,
                TimeUnit.MINUTES);

        // 转为 base64 data URI（前端 <img src="..."> 可直接用）
        byte[] imageBytes = captcha.getImageBytes();
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        String dataUri = "data:image/png;base64," + base64;

        log.debug("[Captcha] 生成 captchaId={}", captchaId);
        Map<String, String> result = new HashMap<>();
        result.put("captchaId", captchaId);
        result.put("imageBase64", dataUri);
        return result;
    }

    @Override
    public boolean validate(String captchaId, String captchaCode) {
        if (captchaId == null || captchaCode == null || captchaId.isEmpty() || captchaCode.isEmpty()) {
            return false;
        }
        String key = CAPTCHA_KEY_PREFIX + captchaId;
        String stored = stringRedisTemplate.opsForValue().get(key);
        if (stored == null) {
            // 不存在或已过期
            log.debug("[Captcha] 校验失败：captchaId={} 不存在或已过期", captchaId);
            return false;
        }
        // 一次性使用：无论对错都删除，防止暴力枚举
        stringRedisTemplate.delete(key);

        boolean ok = stored.equalsIgnoreCase(captchaCode);
        if (!ok) {
            log.debug("[Captcha] 校验失败：captchaId={} 验证码不匹配", captchaId);
        }
        return ok;
    }
}
