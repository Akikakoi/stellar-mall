package com.stellar.service.impl;

import com.stellar.entity.*;
import com.stellar.mapper.NotificationLogMapper;
import com.stellar.mapper.SmsCodeMapper;
import com.stellar.mapper.UserMessageMapper;
import com.stellar.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final SmsCodeMapper smsCodeMapper;
    private final NotificationLogMapper notificationLogMapper;
    private final UserMessageMapper userMessageMapper;

    private static final int CODE_EXPIRE_MINUTES = 5;
    private static final Random RANDOM = new Random();

    // ======================== 短信验证码 ========================

    @Override
    @Transactional
    public SmsCode sendSmsCode(String phone, String type) {
        String code = String.format("%06d", RANDOM.nextInt(1000000));
        LocalDateTime now = LocalDateTime.now();

        SmsCode smsCode = SmsCode.builder()
                .phone(phone)
                .code(code)
                .type(type)
                .used(0)
                .expireTime(now.plusMinutes(CODE_EXPIRE_MINUTES))
                .createTime(now)
                .build();
        smsCodeMapper.insert(smsCode);

        // 模拟发送短信（记录日志，开发环境直接打印）
        log.info("[短信验证码] 手机号:{} 类型:{} 验证码:{} 有效期:{}分钟", phone, type, code, CODE_EXPIRE_MINUTES);

        logNotification(null, phone, null, "SMS", "VERIFY_CODE",
                "验证码", "【星耀商城】您的验证码是 " + code + "，5分钟内有效。", 1, null);

        return smsCode;
    }

    @Override
    public boolean verifySmsCode(String phone, String type, String code) {
        SmsCode smsCode = smsCodeMapper.findLatest(phone, type);
        if (smsCode == null) {
            log.warn("验证码校验失败：无有效验证码 phone={} type={}", phone, type);
            return false;
        }
        if (!smsCode.getCode().equals(code)) {
            log.warn("验证码校验失败：验证码不匹配 phone={}", phone);
            return false;
        }
        smsCodeMapper.markUsed(smsCode.getId());
        return true;
    }

    // ======================== 业务通知 ========================

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

    @Override
    @Async
    public void sendOrderReceivedNotice(MallOrder order) {
        String content = String.format("【星耀商城】您的订单 %s 已确认收货，感谢您的惠顾！",
                order.getOrderNo());
        log.info("[收货通知] 订单:{} 用户:{}", order.getOrderNo(), order.getUserId());

        logNotification(order.getUserId(), null, null, "SMS", "ORDER_RECEIVED",
                "已确认收货", content, 1, null);
    }

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
