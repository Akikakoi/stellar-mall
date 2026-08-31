package com.stellar.service.impl;

import com.stellar.entity.*;
import com.stellar.mapper.EmailCodeMapper;
import com.stellar.mapper.NotificationLogMapper;
import com.stellar.mapper.UserMessageMapper;
import com.stellar.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知服务实现。
 *
 * <p>提供邮箱验证码发送与校验、订单发货/收货通知、优惠券到期提醒等功能，
 * 并统一记录通知日志到 {@link NotificationLog} 表。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final EmailCodeMapper emailCodeMapper;
    private final NotificationLogMapper notificationLogMapper;
    private final UserMessageMapper userMessageMapper;
    private final JavaMailSender javaMailSender;
    private final MailProperties mailProperties;

    private static final int CODE_EXPIRE_MINUTES = 5;
    /** 验证码使用加密安全随机数，防止 java.util.Random 可预测导致验证码被爆破 */
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    /** 是否启用真实 SMTP 发送；false 时为开发模式，不真实发信 */
    @Value("${stellar.mail.enabled:false}")
    private boolean mailEnabled;

    // ======================== 邮箱验证码 ========================

    /**
     * 生成并发送邮箱验证码。
     *
     * <p>生成 6 位随机数字验证码，写入数据库。配置了 SMTP（stellar.mail.enabled=true）
     * 时通过 JavaMailSender 真实发送；否则为开发模式，仅记录日志并在日志中输出验证码。</p>
     *
     * @param email 邮箱地址
     * @param type  验证码类型
     * @return 持久化后的 EmailCode 实体
     */
    @Override
    @Transactional
    public EmailCode sendEmailCode(String email, String type) {
        String code = String.format("%06d", RANDOM.nextInt(1000000));
        LocalDateTime now = LocalDateTime.now();

        EmailCode emailCode = EmailCode.builder()
                .email(email)
                .code(code)
                .type(type)
                .used(0)
                .expireTime(now.plusMinutes(CODE_EXPIRE_MINUTES))
                .createTime(now)
                .build();
        emailCodeMapper.insert(emailCode);

        if (mailEnabled) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(mailProperties.getUsername());
                message.setTo(email);
                message.setSubject("【星耀商城】验证码");
                message.setText("【星耀商城】您的验证码是 " + code + "，5分钟内有效。若非本人操作请忽略。");
                javaMailSender.send(message);
                log.info("[邮箱验证码] 已发送 邮箱:{} 类型:{} 验证码:{}", email, type, code);
                logNotification(null, null, email, "EMAIL", "VERIFY_CODE",
                        "验证码", "【星耀商城】您的验证码是 " + code + "，5分钟内有效。", 1, null);
            } catch (Exception e) {
                log.error("[邮箱验证码] 发送失败 邮箱:{} 类型:{}", email, type, e);
                logNotification(null, null, email, "EMAIL", "VERIFY_CODE",
                        "验证码", "【星耀商城】您的验证码是 " + code + "，5分钟内有效。", 2, e.getMessage());
            }
        } else {
            // 开发模式：未配置 SMTP，不真实发信，验证码直接打印日志由前端兜底展示
            log.info("[邮箱验证码] 开发模式（未配置 SMTP，不真实发送） 邮箱:{} 类型:{} 验证码:{}", email, type, code);
            logNotification(null, null, email, "EMAIL", "VERIFY_CODE",
                    "验证码", "开发模式未真实发送，验证码 " + code, 0, "SMTP 未配置");
        }

        return emailCode;
    }

    /**
     * 校验邮箱验证码是否有效。
     *
     * @param email 邮箱地址
     * @param type  验证码类型
     * @param code  用户输入的验证码
     * @return true 表示验证通过，false 表示验证失败
     */
    @Override
    public boolean verifyEmailCode(String email, String type, String code) {
        EmailCode emailCode = emailCodeMapper.findLatest(email, type);
        if (emailCode == null) {
            log.warn("验证码校验失败：无有效验证码 email={} type={}", email, type);
            return false;
        }
        if (!emailCode.getCode().equals(code)) {
            log.warn("验证码校验失败：验证码不匹配 email={}", email);
            return false;
        }
        emailCodeMapper.markUsed(emailCode.getId());
        return true;
    }

    // ======================== 业务通知 ========================

    /**
     * 异步发送订单发货通知。
     *
     * <p>记录通知日志并写入用户消息表。</p>
     *
     * @param order 已发货的订单
     */
    @Override
    @Async
    public void sendOrderShippedNotice(MallOrder order) {
        String content = String.format("【星耀商城】您的订单 %s 已发货，请留意物流信息。",
                order.getOrderNo());
        log.info("[发货通知] 订单:{} 用户:{}", order.getOrderNo(), order.getUserId());

        logNotification(order.getUserId(), null, null, "SMS", "ORDER_SHIPPED",
                "订单已发货", content, 1, null);

        // 同时写消息表
        sendUserMessage(order.getUserId(), "订单通知", content, "ORDER_NOTICE", order.getId());
    }

    /**
     * 异步发送订单确认收货通知。
     *
     * @param order 已确认收货的订单
     */
    @Override
    @Async
    public void sendOrderReceivedNotice(MallOrder order) {
        String content = String.format("【星耀商城】您的订单 %s 已确认收货，感谢您的惠顾！",
                order.getOrderNo());
        log.info("[收货通知] 订单:{} 用户:{}", order.getOrderNo(), order.getUserId());

        logNotification(order.getUserId(), null, null, "SMS", "ORDER_RECEIVED",
                "已确认收货", content, 1, null);
    }

    /**
     * 异步发送优惠券即将过期提醒。
     *
     * <p>当优惠券列表为空时直接返回，不发送通知。</p>
     *
     * @param userId  用户 ID
     * @param coupons 即将过期的优惠券列表
     */
    @Override
    @Async
    public void sendCouponExpireNotice(Long userId, List<UserCoupon> coupons) {
        if (coupons.isEmpty()) return;
        StringBuilder sb = new StringBuilder("【星耀商城】您有 ");
        sb.append(coupons.size()).append(" 张优惠券即将过期：");
        for (UserCoupon uc : coupons) {
            sb.append(uc.getCouponName() != null ? uc.getCouponName() : "优惠券").append("(").append(uc.getId()).append(") ");
        }
        String content = sb.toString().trim();
        log.info("[优惠券过期提醒] 用户:{} 过期券数:{}", userId, coupons.size());

        logNotification(userId, null, null, "SMS", "COUPON_EXPIRE",
                "优惠券到期提醒", content, 1, null);

        sendUserMessage(userId, "优惠券通知", content, "COUPON_NOTICE", null);
    }

    /**
     * 异步记录通知日志到数据库。
     *
     * @param userId   用户 ID（可为 null）
     * @param phone    手机号（可为 null）
     * @param email    邮箱（可为 null）
     * @param channel  通知渠道（如 SMS、EMAIL）
     * @param type     通知类型（如 VERIFY_CODE、ORDER_SHIPPED）
     * @param title    通知标题
     * @param content  通知内容
     * @param status   发送状态（1 成功，0 失败）
     * @param errorMsg 错误信息（可为 null）
     */
    @Override
    @Async
    public void logNotification(Long userId, String phone, String email, String channel,
                                 String type, String title, String content, int status, String errorMsg) {
        NotificationLog logEntry = NotificationLog.builder()
                .userId(userId)
                .phone(phone)
                .email(email)
                .channel(channel)
                .type(type)
                .title(title)
                .content(content)
                .status(status)
                .sendTime(status == 1 ? LocalDateTime.now() : null)
                .errorMsg(errorMsg)
                .createTime(LocalDateTime.now())
                .build();
        notificationLogMapper.insert(logEntry);
    }

    // ======================== 用户消息（复用现有表） ========================

    private void sendUserMessage(Long userId, String title, String content, String type, Long referenceId) {
        try {
            UserMessage msg = UserMessage.builder()
                    .userId(userId)
                    .title(title)
                    .content(content)
                    .type(type)
                    .refId(referenceId)
                    .isRead(0)
                    .createTime(LocalDateTime.now())
                    .build();
            userMessageMapper.insert(msg);
        } catch (Exception e) {
            log.error("写入用户消息失败 userId={}", userId, e);
        }
    }
}