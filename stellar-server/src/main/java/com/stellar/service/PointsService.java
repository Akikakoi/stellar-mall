package com.stellar.service;

import com.stellar.dto.PointsAdjustDTO;
import com.stellar.dto.PointsProductSaveDTO;
import com.stellar.dto.PointsRedeemDTO;
import com.stellar.entity.PointsRule;
import com.stellar.result.PageResult;
import com.stellar.vo.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 积分核心服务接口。
 */
public interface PointsService {

    // ===== 用户积分 =====

    /** 获取或创建用户积分账户 */
    UserPointsVO getOrCreateUserPoints(Long userId);

    /** 积分流水（分页） */
    PageResult pageRecords(Long userId, int page, int pageSize);

    /** 兑换记录（分页） */
    PageResult pageRedemptions(Long userId, int page, int pageSize);

    // ===== 签到 =====

    /** 每日签到 */
    CheckinVO checkin(Long userId);

    /** 查询本月签到日期 */
    List<String> getCheckinDates(Long userId);

    // ===== 积分赚取（内部调用） =====

    /** 下单获得积分 */
    void earnByOrder(Long userId, Long orderId, BigDecimal payAmount);

    /** 评价获得积分 */
    void earnByReview(Long userId, Long reviewId);

    // ===== 积分抵扣支付（内部调用） =====

    /**
     * 下单时冻结积分用于抵扣订单金额。
     * @param requestedAmount 用户请求的抵扣金额（元），会转换为积分数
     * @return 实际冻结的积分数
     */
    int freezePointsForOrder(Long userId, Long orderId, BigDecimal requestedAmount, BigDecimal orderPayAmount);

    /**
     * 支付成功后，将冻结的积分转为实际消费。
     */
    void consumeFrozenPointsForOrder(Long userId, Long orderId);

    /**
     * 取消订单时，解冻已冻结的积分。
     */
    void unfreezePointsForOrder(Long userId, Long orderId);

    /**
     * 退款时，按退款比例退还积分。
     * @param refundRatio 退款金额 / 订单实付金额（0~1）
     */
    int refundPointsForOrder(Long userId, Long orderId, BigDecimal refundRatio);

    /**
     * 退款时收回订单赠送的奖励积分。
     * @return 收回的积分数（0 表示无可收回）
     */
    int reclaimOrderEarnPoints(Long userId, Long orderId);

    // ===== 积分兑换 =====

    /** 积分商城商品列表 */
    List<PointsProductVO> listProducts();

    /** 积分兑换 */
    PointsRedeemVO redeem(Long userId, PointsRedeemDTO dto);

    // ===== 管理端 =====

    /** 管理员调整积分 */
    void adjustPoints(PointsAdjustDTO dto);

    /** 积分规则列表 */
    List<PointsRule> listRules();

    /** 保存/更新规则 */
    void saveRule(PointsRule rule);

    /** 删除规则 */
    void deleteRule(Long id);

    /** 积分商城商品管理（增删改查） */
    PageResult pageProducts(String name, Integer status, int page, int pageSize);
    void saveProduct(PointsProductSaveDTO dto);
    void deleteProduct(Long id);
    PointsProductVO getProductById(Long id);

    // ===== 定时任务 =====

    /** 处理过期积分 */
    int processExpiredPoints();
}
