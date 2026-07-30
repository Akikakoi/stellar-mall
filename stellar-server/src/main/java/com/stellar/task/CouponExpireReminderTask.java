package com.stellar.task;

import com.stellar.entity.UserCoupon;
import com.stellar.mapper.CouponMapper;
import com.stellar.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 优惠券到期提醒定时任务：每天 10:00 检查未来 3 天内到期的优惠券
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponExpireReminderTask {

    private final CouponMapper couponMapper;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 10 * * ?")
    public void remindExpiringCoupons() {
        log.info("[定时任务] 开始检查即将过期的优惠券");

        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime threeDaysLater = now.plusDays(3);

            // 查询未使用且 3 天内到期的优惠券
            List<UserCoupon> expiringList = couponMapper.findExpiringSoon(now, threeDaysLater);

            if (expiringList.isEmpty()) {
                log.info("[定时任务] 没有即将过期的优惠券");
                return;
            }

            // 按 userId 分组
            Map<Long, List<UserCoupon>> userGroups = expiringList.stream()
                    .collect(Collectors.groupingBy(UserCoupon::getUserId));

            for (Map.Entry<Long, List<UserCoupon>> entry : userGroups.entrySet()) {
                notificationService.sendCouponExpireNotice(entry.getKey(), entry.getValue());
            }

            log.info("[定时任务] 优惠券到期提醒完成，共通知 {} 个用户，{} 张券",
                    userGroups.size(), expiringList.size());

        } catch (Exception e) {
            log.error("[定时任务] 优惠券到期提醒执行失败", e);
        }
    }
}
