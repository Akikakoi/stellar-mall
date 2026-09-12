package com.stellar.service.impl;

import com.stellar.config.AsyncExecutorConfig;
import com.stellar.entity.*;
import com.stellar.mapper.EmailCodeMapper;
import com.stellar.mapper.NotificationLogMapper;
import com.stellar.mapper.UserMessageMapper;
import com.stellar.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    /** 真实发信由独立 Bean 承载：既把阻塞 IO 挪出事务，也避开同 Bean 自调用导致 @Async 失效。 */
    private final MailDispatcher mailDispatcher;

    private static final int CODE_EXPIRE_MINUTES = 5;
    /** 验证码使用加密安全随机数，防止 java.util.Random 可预测导致验证码被爆破 */
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    // ======================== 邮箱验证码 ========================

    /**
     * 生成并发送邮箱验证码。
     *
     * <p>生成 6 位随机数字验证码并写入数据库，随后把「真实发信」注册到事务提交回调里，
     * 交由 {@link MailDispatcher} 在专用线程池中异步投递——SMTP 发送是阻塞网络 IO，
     * 留在本事务内会把数据库事务与连接一起拖住。配置了 SMTP（stellar.mail.enabled=true）
     * 时真实发送；否则为开发模式，仅记录日志并在日志中输出验证码。</p>
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

        // 提交后才发信：回滚时若已投递，用户会拿到一个数据库里并不存在的验证码
        dispatchMailAfterCommit(email, type, code);

        return emailCode;
    }

    /**
     * 在事务提交后触发异步发信。
     *
     * <p>没有事务上下文时（被非事务方法直接调用）立即发信，避免「验证码已入库却没发出去」。</p>
     */
    private void dispatchMailAfterCommit(String email, String type, String code) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            mailDispatcher.sendVerifyCodeMail(email, type, code);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                mailDispatcher.sendVerifyCodeMail(email, type, code);
            }
        });
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
    @Async(AsyncExecutorConfig.NOTIFICATION_EXECUTOR)
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
    @Async(AsyncExecutorConfig.NOTIFICATION_EXECUTOR)
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
    @Async(AsyncExecutorConfig.NOTIFICATION_EXECUTOR)
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
    @Async(AsyncExecutorConfig.NOTIFICATION_EXECUTOR)
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