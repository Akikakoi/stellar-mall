package com.stellar.service;

import com.stellar.dto.OrderSubmitDTO;
import com.stellar.entity.MallOrder;

import java.util.List;

public interface OrderService {

    /**
     * 提交订单（从购物车查询）：
     *   1) 读购物车 checked 项 → 空抛 SHOPPING_CART_IS_NULL
     *   2) 循环校验 SKU 在售、库存够不够
     *   3) 逐条 skuStockService.deduct(skuId, qty) — 乐观锁版本，失败抛 StockInsufficientException
     *   4) 写 Order + 逐条 OrderItem（快照：SPU 名/SKU 规格/单价）
     *   5) 删除对应购物车项
     *
     * 任何一步抛异常 → 事务回滚（扣库存的 SQL 已经和外层事务绑在一起，
     * 对于纯 JDBC 本地事务，只要外层 @Transactional 就能整体回滚）。
     */
    MallOrder submit(Long userId, OrderSubmitDTO dto);

    /**
     * 提交订单（前端直传模式）：
     *   当前端直接传商品列表时使用此方法，适用于未登录用户或立即购买场景。
     */
    MallOrder submitDirect(Long userId, OrderSubmitDTO dto);

    /**
     * 模拟支付：PENDING → PAID。归属校验 + 状态校验。
     * @param payMethod 支付方式：1微信 2支付宝 4钱包，传 null 则沿用订单原支付方式
     */
    void pay(Long orderId, Long userId, Integer payMethod);

    /**
     * 取消订单：
     *   1) 归属校验 + 只有 PENDING 能取消
     *   2) 逐条读明细 → 调 skuStockService.rollback(skuId, qty)
     *   3) 订单状态 → CANCELLED
     */
    void cancel(Long orderId, Long userId);

    /** 订单详情（含明细）。 */
    com.stellar.vo.MallOrderVO getDetail(Long orderId, Long userId);

    /** 当前用户订单列表。 */
    java.util.List<com.stellar.vo.MallOrderVO> listByUser(Long userId);

    /** 当前用户订单列表（按前端数字 status 过滤，1=待付款,3=待收货,5=已完成,0=已取消；null 表示全部）。 */
    java.util.List<com.stellar.vo.MallOrderVO> listByUser(Long userId, Integer statusCode);

    /**
     * 确认收货/完成订单：校验归属 + 状态必须为 SHIPPED → 改为 COMPLETED。
     */
    void confirm(Long orderId, Long userId);

    /**
     * 管理端：发货 — PAID → SHIPPED，写入物流信息并自动通知用户。
     */
    void ship(Long orderId, String trackingNo, String deliveryCompany);

    /**
     * 管理端：分页查询所有订单（支持按状态、订单号和日期范围筛选）。
     */
    com.stellar.result.PageResult pageOrders(int page, int pageSize, String status, String orderNo,
                                            String startTime, String endTime);

    /**
     * 管理端：删除已完成或已取消的订单（先删明细，再删主单）。
     */
    void deleteOrder(Long orderId);

    /**
     * C 端：删除自己的已完成或已取消的订单（校验归属 + 先删明细，再删主单）。
     */
    void deleteOrder(Long orderId, Long userId);

    /**
     * 将订单状态标记为退款中（REFUNDING），由售后模块调用。
     */
    void markRefunding(Long orderId);

    /**
     * 完成退款：订单 REFUNDING → COMPLETED，回滚库存，由售后模块调用。
     */
    void completeRefund(Long orderId);

    /**
     * 自动取消过期的待付款订单（由定时任务调用，15 分钟超时）。
     * 逐条处理，单条失败不影响后续订单。
     */
    int cancelExpiredOrders(int limit);
}
