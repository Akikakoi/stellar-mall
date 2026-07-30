package com.stellar.service;

import com.stellar.entity.SmsCode;
import com.stellar.entity.NotificationLog;
import com.stellar.entity.MallOrder;
import com.stellar.entity.UserCoupon;

import java.util.List;

public interface NotificationService {

    /** 发送短信验证码 */
    SmsCode sendSmsCode(String phone, String type);

    /** 校验短信验证码 */
    boolean verifySmsCode(String phone, String type, String code);

    /** 发送订单发货通知 */
    void sendOrderShippedNotice(MallOrder order);

    /** 发送订单签收提醒 */
    void sendOrderReceivedNotice(MallOrder order);

    /** 发送优惠券到期提醒 */
    void sendCouponExpireNotice(Long userId, List<UserCoupon> coupons);

    /** 异步记录通知日志 */
    void logNotification(Long userId, String phone, String email, String channel,
                         String type, String title, String content, int status, String errorMsg);
}
