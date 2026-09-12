package com.stellar.task;

import com.stellar.entity.UserCoupon;
import com.stellar.mapper.CouponMapper;
import com.stellar.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
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

    /**
     * 单机重入保护。
     * <p>调度池扩容后定时任务可能并行调度，多实例部署也会各触发一次；重复执行会把同一批
     * 用户重复通知一遍，这里用 CAS 保证同一时刻只有一轮在跑。</p>
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** 每天 10:00 执行一次。 */
    @Scheduled(cron = "0 0 10 * * ?")
    public void remindExpiringCoupons() {
        if (!running.compareAndSet(false, true)) {
            log.warn("[定时任务] 上一轮提醒尚未结束，跳过本轮");
            return;
        }
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
        } finally {
            running.set(false);
        }
    }
}
