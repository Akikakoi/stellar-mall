package com.stellar.service.impl;

import com.stellar.constant.MessageConstant;
import com.stellar.context.BaseContext;
import com.stellar.dto.PointsAdjustDTO;
import com.stellar.dto.PointsProductSaveDTO;
import com.stellar.dto.PointsRedeemDTO;
import com.stellar.entity.*;
import com.stellar.exception.BaseException;
import com.stellar.mapper.*;
import com.stellar.result.PageResult;
import com.stellar.service.PointsService;
import com.stellar.service.UserMessageService;
import com.stellar.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * 积分系统核心服务实现。
 * <p>
 * 涵盖用户积分账户管理、每日签到、消费赚取、积分+钱包组合支付（冻结/扣减/解冻/退款）、
 * 积分商城兑换、管理端积分规则与商品配置、积分过期处理等完整功能。
 * 积分兑换汇率为 100积分 = 1元，默认过期天数 365 天。
 * </p>
 */
public class PointsServiceImpl implements PointsService {

    private final UserPointsMapper userPointsMapper;
    private final PointsRecordMapper pointsRecordMapper;
    private final PointsRuleMapper pointsRuleMapper;
    private final PointsProductMapper pointsProductMapper;
    private final PointsRedemptionMapper pointsRedemptionMapper;
    private final CheckinRecordMapper checkinRecordMapper;
    private final CouponMapper couponMapper;
    private final PointsPaymentMapper pointsPaymentMapper;
    private final MallOrderMapper mallOrderMapper;
    private final UserMessageService userMessageService;

    /** 积分默认过期天数 */
    private static final int POINTS_EXPIRE_DAYS = 365;

    /** 积分抵扣汇率：100积分 = 1元 */
    private static final int POINTS_EXCHANGE_RATE = 100;

    // ================================================================
    // 用户积分账户
    // ================================================================

    @Override
    /**
     * 获取或创建用户积分账户。
     *
     * @param userId 用户ID
     * @return 用户积分视图对象
     */
    public UserPointsVO getOrCreateUserPoints(Long userId) {
        UserPoints up = ensureUserPoints(userId);
        return toVO(up);
    }

    @Override
    /**
     * 分页查询用户积分变动记录。
     *
     * @param userId   用户ID
     * @param page     页码
     * @param pageSize 每页条数
     * @return 积分记录分页结果
     */
    public PageResult pageRecords(Long userId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<PointsRecord> list = pointsRecordMapper.listByUser(userId, offset, pageSize);
        int total = pointsRecordMapper.countByUser(userId);

        List<PointsRecordVO> vos = new ArrayList<>();
        if (list != null) {
            for (PointsRecord r : list) {
                vos.add(toRecordVO(r));
            }
        }
        return new PageResult((long) total, vos);
    }

    @Override
    /**
     * 分页查询用户积分兑换记录。
     *
     * @param userId   用户ID
     * @param page     页码
     * @param pageSize 每页条数
     * @return 兑换记录分页结果
     */
    public PageResult pageRedemptions(Long userId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<PointsRedemption> list = pointsRedemptionMapper.listByUser(userId, offset, pageSize);
        int total = pointsRedemptionMapper.countByUser(userId);
        return new PageResult((long) total, list != null ? list : new ArrayList<>());
    }

    // ================================================================
    // 签到
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 每日签到。同一用户同一天只能签到一次，根据 CHECKIN 规则发放积分。
     *
     * @param userId 用户ID
     * @return 签到结果（含是否成功、获得积分、提示消息）
     */
    public CheckinVO checkin(Long userId) {
        LocalDate today = LocalDate.now();

        // 今日是否已签到
        CheckinRecord existing = checkinRecordMapper.getByUserAndDate(userId, today);
        if (existing != null) {
            return CheckinVO.builder()
                    .success(false)
                    .pointsEarned(0)
                    .message("今日已签到，明天再来吧~")
                    .build();
        }

        // 获取签到规则
        PointsRule rule = pointsRuleMapper.getByType("CHECKIN");
        if (rule == null || rule.getStatus() != 1) {
            return CheckinVO.builder()
                    .success(false)
                    .pointsEarned(0)
                    .message("签到功能暂未开放")
                    .build();
        }

        int earnPoints = rule.getEarnPoints();

        // 写入签到记录
        CheckinRecord record = CheckinRecord.builder()
                .userId(userId)
                .checkinDate(today)
                .pointsEarned(earnPoints)
                .createTime(LocalDateTime.now())
                .build();
        checkinRecordMapper.insert(record);

        // 发放积分
        addUserPoints(userId, earnPoints, "CHECKIN", record.getId().toString(),
                "每日签到 +" + earnPoints + "积分");

        log.info("[PointsService] 用户 {} 签到获得 {} 积分", userId, earnPoints);
        return CheckinVO.builder()
                .success(true)
                .pointsEarned(earnPoints)
                .message("签到成功！+" + earnPoints + "积分")
                .build();
    }

    @Override
    /**
     * 查询用户当月签到日期列表。
     *
     * @param userId 用户ID
     * @return 签到日期字符串列表（yyyy-MM-dd）
     */
    public List<String> getCheckinDates(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate startOfMonth = today.withDayOfMonth(1);
        LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth());

        List<LocalDate> dates = checkinRecordMapper.listDatesByMonth(userId, startOfMonth, endOfMonth);
        if (dates == null) return new ArrayList<>();
        return dates.stream().map(LocalDate::toString).collect(Collectors.toList());
    }

    // ================================================================
    // 积分赚取
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 下单赚取积分。根据 ORDER 规则按消费金额计算积分，受每单上限约束。
     * 发放成功后发送积分到账通知。
     *
     * @param userId    用户ID
     * @param orderId   订单ID
     * @param payAmount 实际支付金额
     */
    public void earnByOrder(Long userId, Long orderId, BigDecimal payAmount) {
        if (payAmount == null) {
            log.warn("[PointsService] payAmount 为空，跳过积分发放: orderId={}, userId={}", orderId, userId);
            return;
        }
        PointsRule rule = pointsRuleMapper.getByType("ORDER");
        if (rule == null || rule.getStatus() != 1) {
            log.info("[PointsService] 下单积分规则未启用，跳过");
            return;
        }

        // 计算积分：每消费 conditionValue 元得 earnPoints 积分
        BigDecimal condition = rule.getConditionValue() != null ? rule.getConditionValue() : BigDecimal.ONE;
        if (condition.compareTo(BigDecimal.ZERO) <= 0) condition = BigDecimal.ONE;

        int points = payAmount.divide(condition, 0, BigDecimal.ROUND_DOWN)
                .intValue() * rule.getEarnPoints();

        // 每单上限
        if (rule.getMaxPerOrder() != null && rule.getMaxPerOrder() > 0 && points > rule.getMaxPerOrder()) {
            points = rule.getMaxPerOrder();
        }

        if (points <= 0) return;

        addUserPoints(userId, points, "ORDER", orderId.toString(),
                "下单获得 +" + points + "积分");
        log.info("[PointsService] 用户 {} 下单 {} 获得 {} 积分", userId, orderId, points);

        // 发送积分获得通知
        try {
            String orderNo = getOrderNo(orderId);
            userMessageService.createMessage(userId, "POINTS_EARN",
                    "积分到账通知",
                    "您在订单 " + orderNo + " 的消费获得了 +" + points + "积分奖励",
                    orderId);
        } catch (Exception e) {
            log.warn("[PointsService] 积分通知发送失败: userId={}, orderId={}", userId, orderId, e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 评价赚取积分。根据 REVIEW 规则发放积分，受每日上限约束。
     *
     * @param userId   用户ID
     * @param reviewId 评价ID
     */
    public void earnByReview(Long userId, Long reviewId) {
        PointsRule rule = pointsRuleMapper.getByType("REVIEW");
        if (rule == null || rule.getStatus() != 1) {
            log.info("[PointsService] 评价积分规则未启用，跳过");
            return;
        }

        // 每日上限检查
        if (rule.getMaxPerDay() != null && rule.getMaxPerDay() > 0) {
            String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            int todayCount = pointsRecordMapper.countTodayByBizType(userId, "REVIEW", today);
            if (todayCount >= rule.getMaxPerDay()) {
                log.info("[PointsService] 用户 {} 今日评价已达上限 {}，跳过", userId, rule.getMaxPerDay());
                return;
            }
        }

        int earnPoints = rule.getEarnPoints();
        addUserPoints(userId, earnPoints, "REVIEW", reviewId.toString(),
                "发表评价 +" + earnPoints + "积分");
        log.info("[PointsService] 用户 {} 评价 {} 获得 {} 积分", userId, reviewId, earnPoints);
    }

    // ================================================================
    // 积分抵扣支付
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 冻结积分用于订单支付。将用户请求抵扣金额转换为积分并冻结，不足时用尽可用积分。
     * 100积分 = 1元，向下取整。
     *
     * @param userId          用户ID
     * @param orderId         订单ID
     * @param requestedAmount 用户请求抵扣金额
     * @param orderPayAmount  订单实际应付金额
     * @return 实际冻结的积分数
     */
    public int freezePointsForOrder(Long userId, Long orderId, BigDecimal requestedAmount, BigDecimal orderPayAmount) {
        if (requestedAmount == null || requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        if (orderPayAmount == null || orderPayAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }

        // 归一化浮点数精度，防止前端 JS 浮点漂移
        requestedAmount = requestedAmount.setScale(2, java.math.RoundingMode.HALF_UP);
        orderPayAmount = orderPayAmount.setScale(2, java.math.RoundingMode.HALF_UP);

        // 1) 积分抵扣金额不能超过实际应付金额
        BigDecimal actualAmount = requestedAmount.min(orderPayAmount);

        // 2) 将金额转换为积分数（向下取整，100积分=1元）
        int pointsToFreeze = actualAmount.multiply(BigDecimal.valueOf(POINTS_EXCHANGE_RATE)).intValue();
        if (pointsToFreeze <= 0) {
            return 0;
        }

        // 3) 修正：实际抵扣金额以积分数为准
        BigDecimal actualDeductAmount = BigDecimal.valueOf(pointsToFreeze)
                .divide(BigDecimal.valueOf(POINTS_EXCHANGE_RATE), 2, java.math.RoundingMode.HALF_UP);

        // 4) 校验积分余额并冻结
        UserPoints up = ensureUserPoints(userId);
        if (up.getAvailablePoints() < pointsToFreeze) {
            // 积分不足：用尽所有可用积分
            pointsToFreeze = up.getAvailablePoints();
            if (pointsToFreeze <= 0) {
                return 0;
            }
            actualDeductAmount = BigDecimal.valueOf(pointsToFreeze)
                    .divide(BigDecimal.valueOf(POINTS_EXCHANGE_RATE), 2, java.math.RoundingMode.HALF_UP);
        }

        // 5) 再确保抵扣金额不超过应付金额
        if (actualDeductAmount.compareTo(orderPayAmount) > 0) {
            // 按应付金额反向计算可用的最大积分数
            pointsToFreeze = orderPayAmount.multiply(BigDecimal.valueOf(POINTS_EXCHANGE_RATE)).intValue();
            if (pointsToFreeze <= 0) {
                return 0;
            }
            actualDeductAmount = BigDecimal.valueOf(pointsToFreeze)
                    .divide(BigDecimal.valueOf(POINTS_EXCHANGE_RATE), 2, java.math.RoundingMode.HALF_UP);
        }

        // 6) 冻结积分
        int result = userPointsMapper.freezePoints(userId, pointsToFreeze, up.getVersion());
        if (result == 0) {
            up = userPointsMapper.getByUserId(userId);
            if (up.getAvailablePoints() < pointsToFreeze) {
                pointsToFreeze = up.getAvailablePoints();
                if (pointsToFreeze <= 0) return 0;
            }
            result = userPointsMapper.freezePoints(userId, pointsToFreeze, up.getVersion());
            if (result == 0) {
                throw new BaseException("积分冻结失败，请稍后重试");
            }
        }

        // 7) 记录冻结流水（冻结不计为消费，仅记录操作日志到 points_payment）
        PointsPayment payment = PointsPayment.builder()
                .orderId(orderId)
                .userId(userId)
                .points(pointsToFreeze)
                .amount(actualDeductAmount)
                .type(1) // 冻结
                .bizDesc("积分抵扣冻结 " + pointsToFreeze + "积分 ≈ ¥" + actualDeductAmount.toPlainString())
                .createTime(LocalDateTime.now())
                .build();
        pointsPaymentMapper.insert(payment);

        log.info("[PointsService] 用户 {} 订单 {} 冻结积分 {}（约 ¥{}）", userId, orderId, pointsToFreeze, actualDeductAmount);
        return pointsToFreeze;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 实际扣除冻结积分。订单支付成功后将冻结积分转为实际消费，写入积分流水和支付追溯记录。
     *
     * @param userId  用户ID
     * @param orderId 订单ID
     */
    public void consumeFrozenPointsForOrder(Long userId, Long orderId) {
        // 从 points_payment 记录中获取已冻结的积分数
        List<PointsPayment> payments = pointsPaymentMapper.listByOrderAndUser(orderId, userId);
        int frozenPoints = 0;
        BigDecimal frozenAmount = BigDecimal.ZERO;
        for (PointsPayment p : payments) {
            if (p.getType() == 1) { // 冻结类型
                frozenPoints += p.getPoints();
                frozenAmount = frozenAmount.add(p.getAmount());
            }
        }
        if (frozenPoints <= 0) {
            return;
        }

        // 原子操作：frozen → 实际扣除
        UserPoints up = userPointsMapper.getByUserId(userId);
        if (up == null || up.getFrozenPoints() < frozenPoints) {
            log.warn("[PointsService] 冻结积分不足，跳过扣减: userId={}, orderId={}, need={}, frozen={}",
                    userId, orderId, frozenPoints, up != null ? up.getFrozenPoints() : 0);
            return;
        }

        int result = userPointsMapper.consumeFrozenPoints(userId, frozenPoints, up.getVersion());
        if (result == 0) {
            up = userPointsMapper.getByUserId(userId);
            if (up.getFrozenPoints() < frozenPoints) {
                log.warn("[PointsService] consumeFrozenPoints 重试仍失败: userId={}, orderId={}", userId, orderId);
                return;
            }
            result = userPointsMapper.consumeFrozenPoints(userId, frozenPoints, up.getVersion());
            if (result == 0) {
                throw new BaseException("积分扣减失败，请稍后重试");
            }
        }

        // 记录积分消费流水（PointsRecord）
        up = userPointsMapper.getByUserId(userId);
        PointsRecord record = PointsRecord.builder()
                .userId(userId)
                .type(2)
                .points(-frozenPoints)
                .balanceAfter(up.getAvailablePoints())
                .bizType("ORDER_DEDUCT")
                .bizId(orderId.toString())
                .description("订单抵扣 -" + frozenPoints + "积分（¥" + frozenAmount.setScale(2).toPlainString() + "）")
                .createTime(LocalDateTime.now())
                .build();
        pointsRecordMapper.insert(record);

        // 记录支付追溯
        PointsPayment consumed = PointsPayment.builder()
                .orderId(orderId)
                .userId(userId)
                .points(frozenPoints)
                .amount(frozenAmount)
                .type(2) // 实际扣除
                .bizDesc("积分实际扣除 " + frozenPoints + "积分")
                .createTime(LocalDateTime.now())
                .build();
        pointsPaymentMapper.insert(consumed);

        log.info("[PointsService] 用户 {} 订单 {} 积分实际扣除 {} 积分（约 ¥{}）",
                userId, orderId, frozenPoints, frozenAmount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 解冻积分。订单取消时将冻结积分归还用户可用余额，写入支付追溯记录。
     *
     * @param userId  用户ID
     * @param orderId 订单ID
     */
    public void unfreezePointsForOrder(Long userId, Long orderId) {
        List<PointsPayment> payments = pointsPaymentMapper.listByOrderAndUser(orderId, userId);
        int frozenPoints = 0;
        for (PointsPayment p : payments) {
            if (p.getType() == 1) {
                frozenPoints += p.getPoints();
            }
        }
        if (frozenPoints <= 0) {
            return;
        }

        UserPoints up = userPointsMapper.getByUserId(userId);
        if (up == null || up.getFrozenPoints() < frozenPoints) {
            log.warn("[PointsService] 解冻积分异常: userId={}, orderId={}", userId, orderId);
            return;
        }

        int result = userPointsMapper.unfreezePoints(userId, frozenPoints, up.getVersion());
        if (result == 0) {
            up = userPointsMapper.getByUserId(userId);
            result = userPointsMapper.unfreezePoints(userId, frozenPoints, up.getVersion());
            if (result == 0) {
                log.warn("[PointsService] 解冻积分重试仍失败: userId={}, orderId={}", userId, orderId);
                return;
            }
        }

        PointsPayment unfreeze = PointsPayment.builder()
                .orderId(orderId)
                .userId(userId)
                .points(frozenPoints)
                .amount(BigDecimal.ZERO)
                .type(4) // 取消解冻
                .bizDesc("订单取消，解冻积分 " + frozenPoints + "积分")
                .createTime(LocalDateTime.now())
                .build();
        pointsPaymentMapper.insert(unfreeze);

        log.info("[PointsService] 用户 {} 订单 {} 解冻积分 {} 积分", userId, orderId, frozenPoints);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 退款退还积分。按退款比例返还已扣除的积分，写入支付追溯记录。
     *
     * @param userId      用户ID
     * @param orderId     订单ID
     * @param refundRatio 退款比例（0~1）
     * @return 实际退还的积分数
     */
    public int refundPointsForOrder(Long userId, Long orderId, BigDecimal refundRatio) {
        if (refundRatio == null || refundRatio.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        // 获取该订单实际扣除的积分数
        List<PointsPayment> payments = pointsPaymentMapper.listByOrderAndUser(orderId, userId);
        int consumedPoints = 0;
        BigDecimal consumedAmount = BigDecimal.ZERO;
        for (PointsPayment p : payments) {
            if (p.getType() == 2) { // 实际扣除
                consumedPoints += p.getPoints();
                consumedAmount = consumedAmount.add(p.getAmount());
            }
        }
        if (consumedPoints <= 0) {
            return 0;
        }

        // 按比例退还积分
        BigDecimal refundPointsDecimal = BigDecimal.valueOf(consumedPoints).multiply(refundRatio);
        int refundPoints = refundPointsDecimal.setScale(0, java.math.RoundingMode.HALF_UP).intValue();
        if (refundPoints <= 0) {
            return 0;
        }
        // 不超过已消费的积分数
        refundPoints = Math.min(refundPoints, consumedPoints);

        // 退还积分：直接 addUserPoints
        String orderNo = getOrderNo(orderId);

        addUserPoints(userId, refundPoints, "ORDER_REFUND", orderId.toString(),
                "订单 " + orderNo + " 退款退还积分 +" + refundPoints + "（抵扣 ¥" + consumedAmount.setScale(2).toPlainString() + "，退款比例 " + refundRatio.multiply(BigDecimal.valueOf(100)).setScale(0).toPlainString() + "%）");

        // 记录支付追溯
        BigDecimal refundAmount = BigDecimal.valueOf(refundPoints)
                .divide(BigDecimal.valueOf(POINTS_EXCHANGE_RATE), 2, java.math.RoundingMode.HALF_UP);
        PointsPayment refund = PointsPayment.builder()
                .orderId(orderId)
                .userId(userId)
                .points(refundPoints)
                .amount(refundAmount)
                .type(3) // 退还
                .bizDesc("订单 " + orderNo + " 退款退还积分 " + refundPoints + "积分（抵扣 ¥" + consumedAmount.setScale(2).toPlainString() + "，退款比例 " + refundRatio.multiply(BigDecimal.valueOf(100)).setScale(0).toPlainString() + "%）")
                .createTime(LocalDateTime.now())
                .build();
        pointsPaymentMapper.insert(refund);

        log.info("[PointsService] 用户 {} 订单 {} 退款退还积分 {} 积分（比例 {}%）",
                userId, orderId, refundPoints, refundRatio.multiply(BigDecimal.valueOf(100)));
        return refundPoints;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 退款收回赠送积分。订单全额退款时收回该订单赠送的全部积分，并发送通知。
     *
     * @param userId  用户ID
     * @param orderId 订单ID
     * @return 实际收回的积分数
     */
    public int reclaimOrderEarnPoints(Long userId, Long orderId) {
        // 查询该订单赠送的积分流水
        List<PointsRecord> records = pointsRecordMapper.findByBiz(userId, "ORDER", orderId.toString());
        int totalEarned = 0;
        if (records != null) {
            for (PointsRecord r : records) {
                if (r.getPoints() > 0) totalEarned += r.getPoints();
            }
        }
        if (totalEarned <= 0) return 0;

        // 扣减积分
        deductUserPoints(userId, totalEarned, "ORDER_RECLAIM", orderId.toString(),
                "订单退款收回赠送积分 -" + totalEarned + "积分");

        log.info("[PointsService] 用户 {} 订单 {} 退款收回赠送积分 {} 积分", userId, orderId, totalEarned);

        // 发送积分收回通知
        try {
            String orderNo = getOrderNo(orderId);
            userMessageService.createMessage(userId, "POINTS_RECLAIM",
                    "积分收回通知",
                    "您的订单 " + orderNo + " 已退款，赠送的 " + totalEarned + "积分已收回",
                    orderId);
        } catch (Exception e) {
            log.warn("[PointsService] 积分收回通知发送失败: userId={}, orderId={}", userId, orderId, e);
        }

        return totalEarned;
    }

    // ================================================================
    // 积分兑换
    // ================================================================

    @Override
    /**
     * 查询在售积分商品列表。
     *
     * @return 积分商品视图对象列表
     */
    public List<PointsProductVO> listProducts() {
        List<PointsProduct> list = pointsProductMapper.listOnSale();
        if (list == null) return new ArrayList<>();
        return list.stream().map(this::toProductVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 积分兑换商品。校验商品状态、库存和用户积分余额，扣减库存和积分后创建兑换记录。
     * 若为优惠券类型商品，自动发放优惠券。
     *
     * @param userId 用户ID
     * @param dto    兑换请求参数
     * @return 兑换结果（含兑换ID、消耗积分、剩余积分、优惠券ID）
     */
    public PointsRedeemVO redeem(Long userId, PointsRedeemDTO dto) {
        PointsProduct product = pointsProductMapper.getById(dto.getProductId());
        if (product == null) {
            throw new BaseException("兑换商品不存在");
        }
        if (product.getStatus() != 1) {
            throw new BaseException("该商品已下架");
        }
        if (product.getStock() <= 0) {
            throw new BaseException("该商品已兑完");
        }

        // 检查积分是否足够
        UserPoints up = ensureUserPoints(userId);
        if (up.getAvailablePoints() < product.getPointsPrice()) {
            throw new BaseException("积分不足，当前可用积分: " + up.getAvailablePoints());
        }

        // 扣库存
        int rows = pointsProductMapper.deductStock(product.getId());
        if (rows == 0) {
            throw new BaseException("该商品已兑完，下手慢了一步~");
        }

        // 扣积分
        deductUserPoints(userId, product.getPointsPrice(), "REDEEM", product.getId().toString(),
                "兑换「" + product.getName() + "」-" + product.getPointsPrice() + "积分");

        // 创建兑换记录
        PointsRedemption redemption = PointsRedemption.builder()
                .userId(userId)
                .productId(product.getId())
                .productName(product.getName())
                .pointsCost(product.getPointsPrice())
                .status(1) // 已兑换
                .addressId(dto.getAddressId())
                .createTime(LocalDateTime.now())
                .build();
        pointsRedemptionMapper.insert(redemption);

        // 如果是优惠券类型，自动发放
        Long userCouponId = null;
        if ("COUPON".equals(product.getProductType()) && product.getCouponId() != null) {
            userCouponId = grantCoupon(userId, product.getCouponId());
            pointsRedemptionMapper.updateStatus(redemption.getId(), 2, userCouponId);
        }

        // 重新获取积分余额
        up = userPointsMapper.getByUserId(userId);
        log.info("[PointsService] 用户 {} 兑换「{}」消耗 {} 积分", userId, product.getName(), product.getPointsPrice());

        return PointsRedeemVO.builder()
                .redemptionId(redemption.getId())
                .pointsCost(product.getPointsPrice())
                .remainingPoints(up != null ? up.getAvailablePoints() : 0)
                .userCouponId(userCouponId)
                .build();
    }

    // ================================================================
    // 管理端
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 管理端积分调整。支持正向加积分和负向扣减积分。
     *
     * @param dto 积分调整参数（用户ID、调整积分数、说明）
     */
    public void adjustPoints(PointsAdjustDTO dto) {
        if (dto.getPoints() == 0) {
            throw new BaseException("调整积分不能为0");
        }
        int pts = dto.getPoints();
        String desc = dto.getDescription() != null ? dto.getDescription() : "管理员调整";

        if (pts > 0) {
            addUserPoints(dto.getUserId(), pts, "ADMIN", null, desc + " +" + pts);
        } else {
            deductUserPoints(dto.getUserId(), Math.abs(pts), "ADMIN", null, desc + " " + pts);
        }
        log.info("[PointsService] 管理员调整用户 {} 积分 {}", dto.getUserId(), pts);
    }

    @Override
    /**
     * 查询全部积分规则列表。
     *
     * @return 积分规则列表
     */
    public List<PointsRule> listRules() {
        return pointsRuleMapper.listAll();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 新增或更新积分规则。id 为空时新增，否则更新。
     *
     * @param rule 积分规则实体
     */
    public void saveRule(PointsRule rule) {
        Long userId = BaseContext.getCurrentId();
        rule.setUpdateTime(LocalDateTime.now());
        rule.setUpdateUser(userId);
        if (rule.getId() == null) {
            rule.setCreateTime(LocalDateTime.now());
            rule.setCreateUser(userId);
            pointsRuleMapper.insert(rule);
        } else {
            pointsRuleMapper.update(rule);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 删除积分规则。
     *
     * @param id 规则ID
     */
    public void deleteRule(Long id) {
        pointsRuleMapper.deleteById(id);
    }

    @Override
    /**
     * 管理端分页查询积分商品列表。
     *
     * @param name     商品名称（模糊搜索）
     * @param status   状态
     * @param page     页码
     * @param pageSize 每页条数
     * @return 积分商品分页结果
     */
    public PageResult pageProducts(String name, Integer status, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<PointsProduct> list = pointsProductMapper.page(name, status, offset, pageSize);
        int total = pointsProductMapper.count(name, status);
        List<PointsProductVO> vos = new ArrayList<>();
        if (list != null) {
            for (PointsProduct p : list) {
                vos.add(toProductVO(p));
            }
        }
        return new PageResult((long) total, vos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 新增或更新积分商品。id 为空时新增，否则更新。
     *
     * @param dto 积分商品保存参数
     */
    public void saveProduct(PointsProductSaveDTO dto) {
        Long userId = BaseContext.getCurrentId();
        PointsProduct product;
        if (dto.getId() != null) {
            product = pointsProductMapper.getById(dto.getId());
            if (product == null) throw new BaseException("商品不存在");
        } else {
            product = new PointsProduct();
            product.setCreateTime(LocalDateTime.now());
            product.setCreateUser(userId);
        }
        product.setName(dto.getName());
        product.setProductType(dto.getProductType());
        product.setPointsPrice(dto.getPointsPrice());
        product.setStock(dto.getStock() != null ? dto.getStock() : 0);
        product.setImageUrl(dto.getImageUrl());
        product.setDescription(dto.getDescription());
        product.setCouponId(dto.getCouponId());
        product.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        product.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        product.setUpdateTime(LocalDateTime.now());
        product.setUpdateUser(userId);

        if (dto.getId() == null) {
            pointsProductMapper.insert(product);
        } else {
            pointsProductMapper.update(product);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 删除积分商品。
     *
     * @param id 商品ID
     */
    public void deleteProduct(Long id) {
        pointsProductMapper.deleteById(id);
    }

    @Override
    /**
     * 根据ID查询积分商品详情。
     *
     * @param id 商品ID
     * @return 积分商品视图对象，不存在时返回 null
     */
    public PointsProductVO getProductById(Long id) {
        PointsProduct p = pointsProductMapper.getById(id);
        return p != null ? toProductVO(p) : null;
    }

    // ================================================================
    // 过期策略
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 处理过期积分。逐笔检查到期积分记录，从用户可用积分中扣除，写入过期流水。
     *
     * @return 本次过期的积分总数
     */
    public int processExpiredPoints() {
        LocalDate today = LocalDate.now();
        List<PointsRecord> expiringRecords = pointsRecordMapper.findExpiringPoints(today, today);
        int totalExpired = 0;

        if (expiringRecords == null || expiringRecords.isEmpty()) {
            return 0;
        }

        for (PointsRecord record : expiringRecords) {
            // 从该用户的可用积分中扣除过期积分
            // 注意：这里简化处理，只扣除 expired_time = today 的积分
            // 实际上 expired_time 记录的是该笔获得的积分到期日，到期时该笔积分剩余部分需扣除
            // 这里为简化实现，按逐笔过期扣除
            UserPoints up = userPointsMapper.getByUserId(record.getUserId());
            if (up == null || up.getAvailablePoints() <= 0) continue;

            int expireAmount = Math.min(record.getPoints(), up.getAvailablePoints());
            if (expireAmount <= 0) continue;

            int result = userPointsMapper.expirePoints(record.getUserId(), expireAmount, up.getVersion());
            if (result > 0) {
                // 记录过期流水
                UserPoints updated = userPointsMapper.getByUserId(record.getUserId());
                PointsRecord expireRecord = PointsRecord.builder()
                        .userId(record.getUserId())
                        .type(3)
                        .points(-expireAmount)
                        .balanceAfter(updated != null ? updated.getAvailablePoints() : 0)
                        .bizType("EXPIRE")
                        .bizId(record.getId().toString())
                        .description("积分过期 -" + expireAmount + "积分")
                        .createTime(LocalDateTime.now())
                        .build();
                pointsRecordMapper.insert(expireRecord);
                totalExpired += expireAmount;
            }
        }

        if (totalExpired > 0) {
            log.info("[PointsService] 处理积分过期完成，共过期 {} 积分", totalExpired);
        }
        return totalExpired;
    }

    // ================================================================
    // 内部工具方法
    // ================================================================

    /** 安全获取订单号，失败返回 "订单#id" */
    private String getOrderNo(Long orderId) {
        try {
            MallOrder order = mallOrderMapper.getById(orderId);
            if (order != null && order.getOrderNo() != null) return order.getOrderNo();
        } catch (Exception ignored) {}
        return "订单#" + orderId;
    }

    private UserPoints ensureUserPoints(Long userId) {
        UserPoints up = userPointsMapper.getByUserId(userId);
        if (up == null) {
            up = UserPoints.builder()
                    .userId(userId)
                    .totalPoints(0)
                    .availablePoints(0)
                    .frozenPoints(0)
                    .totalEarned(0)
                    .totalSpent(0)
                    .version(0)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            userPointsMapper.insert(up);
            up = userPointsMapper.getByUserId(userId);
        }
        return up;
    }

    /** 发放积分 */
    private void addUserPoints(Long userId, int points, String bizType, String bizId, String description) {
        UserPoints up = ensureUserPoints(userId);
        int result = userPointsMapper.addPoints(userId, points, up.getVersion());
        if (result == 0) {
            // 并发冲突，重试一次
            up = userPointsMapper.getByUserId(userId);
            result = userPointsMapper.addPoints(userId, points, up.getVersion());
            if (result == 0) {
                throw new BaseException("积分变动失败，请稍后重试");
            }
        }

        userPointsMapper.addTotalEarned(userId, points);

        up = userPointsMapper.getByUserId(userId);
        LocalDate expiredTime = LocalDate.now().plusDays(POINTS_EXPIRE_DAYS);

        PointsRecord record = PointsRecord.builder()
                .userId(userId)
                .type(1)
                .points(points)
                .balanceAfter(up.getAvailablePoints())
                .bizType(bizType)
                .bizId(bizId)
                .description(description)
                .expiredTime(expiredTime)
                .createTime(LocalDateTime.now())
                .build();
        pointsRecordMapper.insert(record);
    }

    /** 扣减积分 */
    private void deductUserPoints(Long userId, int points, String bizType, String bizId, String description) {
        UserPoints up = ensureUserPoints(userId);
        if (up.getAvailablePoints() < points) {
            throw new BaseException("积分不足");
        }

        int result = userPointsMapper.deductPoints(userId, points, up.getVersion());
        if (result == 0) {
            up = userPointsMapper.getByUserId(userId);
            result = userPointsMapper.deductPoints(userId, points, up.getVersion());
            if (result == 0) {
                throw new BaseException("积分扣减失败，请稍后重试");
            }
        }

        userPointsMapper.addTotalSpent(userId, points);

        up = userPointsMapper.getByUserId(userId);
        PointsRecord record = PointsRecord.builder()
                .userId(userId)
                .type(2)
                .points(-points)
                .balanceAfter(up.getAvailablePoints())
                .bizType(bizType)
                .bizId(bizId)
                .description(description)
                .createTime(LocalDateTime.now())
                .build();
        pointsRecordMapper.insert(record);
    }

    /** 发放优惠券 */
    private Long grantCoupon(Long userId, Long couponId) {
        Coupon coupon = couponMapper.getCouponById(couponId);
        if (coupon == null || coupon.getStatus() != 1) {
            throw new BaseException("关联优惠券不可用");
        }
        if (coupon.getReceivedCount() >= coupon.getTotalCount()) {
            throw new BaseException("关联优惠券已领完");
        }

        couponMapper.incrReceivedCount(couponId);

        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setCouponId(couponId);
        uc.setStatus(1);
        uc.setCreateTime(LocalDateTime.now());
        uc.setCreateUser(userId);
        uc.setUpdateTime(LocalDateTime.now());
        uc.setUpdateUser(userId);
        couponMapper.insertUserCoupon(uc);

        log.info("[PointsService] 积分兑换发放优惠券，用户 {} couponId {}", userId, couponId);
        return uc.getId();
    }

    // ================================================================
    // VO 转换
    // ================================================================

    private UserPointsVO toVO(UserPoints up) {
        if (up == null) return null;
        return UserPointsVO.builder()
                .id(up.getId())
                .totalPoints(up.getTotalPoints() != null ? up.getTotalPoints() : 0)
                .availablePoints(up.getAvailablePoints() != null ? up.getAvailablePoints() : 0)
                .frozenPoints(up.getFrozenPoints() != null ? up.getFrozenPoints() : 0)
                .totalEarned(up.getTotalEarned() != null ? up.getTotalEarned() : 0)
                .totalSpent(up.getTotalSpent() != null ? up.getTotalSpent() : 0)
                .exchangeRate(POINTS_EXCHANGE_RATE)
                .build();
    }

    private static final String[] RECORD_TYPE_TEXT = {"", "获得", "消费", "过期", "调整"};

    private PointsRecordVO toRecordVO(PointsRecord r) {
        String typeText = (r.getType() != null && r.getType() >= 1 && r.getType() <= 4)
                ? RECORD_TYPE_TEXT[r.getType()] : "未知";
        return PointsRecordVO.builder()
                .id(r.getId())
                .type(r.getType())
                .typeText(typeText)
                .points(r.getPoints())
                .balanceAfter(r.getBalanceAfter())
                .bizType(r.getBizType())
                .description(r.getDescription())
                .expiredTime(r.getExpiredTime() != null ? r.getExpiredTime().toString() : null)
                .createTime(r.getCreateTime() != null ? r.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : null)
                .build();
    }

    private static final String[] PRODUCT_TYPE_TEXT = {"", "优惠券", "实物"};

    private PointsProductVO toProductVO(PointsProduct p) {
        String typeText = "COUPON".equals(p.getProductType()) ? "优惠券" :
                "PHYSICAL".equals(p.getProductType()) ? "实物" : p.getProductType();
        return PointsProductVO.builder()
                .id(p.getId())
                .name(p.getName())
                .productType(p.getProductType())
                .productTypeText(typeText)
                .pointsPrice(p.getPointsPrice())
                .stock(p.getStock())
                .imageUrl(p.getImageUrl())
                .description(p.getDescription())
                .couponId(p.getCouponId())
                .status(p.getStatus())
                .sortOrder(p.getSortOrder())
                .createTime(p.getCreateTime() != null ? p.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : null)
                .build();
    }
}
