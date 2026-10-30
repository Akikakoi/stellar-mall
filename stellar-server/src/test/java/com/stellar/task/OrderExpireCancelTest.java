package com.stellar.task;

import com.stellar.entity.MallOrder;
import com.stellar.service.OrderCancelService;
import com.stellar.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 订单超时自动取消链路（{@link OrderService#cancelExpiredOrders(int)}）的端到端验证。
 *
 * <p>这条链路此前只有纯 Mockito 用例（如 OrderCancelServiceTest 把 mapper 全部 mock 掉）：
 * 过期扫描、状态 CAS、库存回滚、退券、解冻积分的 SQL 从未被真正执行过。这里在 H2 上真跑一遍，
 * 守住三件出问题就直接掉钱的事：</p>
 * <ol>
 *   <li><b>取消成功必须归还库存</b>，并留下可对账的回滚流水；</li>
 *   <li><b>取消成功必须退还优惠券、解冻积分</b>，否则用户白丢券和积分；</li>
 *   <li><b>未支付→已付款竞态（CAS 失败）时，库存/券/积分一律不能动</b>，否则等于白送。</li>
 * </ol>
 */
@SpringBootTest
class OrderExpireCancelTest {

    /** 与 OrderServiceImpl.ORDER_EXPIRE_MINUTES 对齐：超过该分钟数未支付即过期。 */
    private static final int ORDER_EXPIRE_MINUTES = 15;

    private static final long TEST_USER_ID = 90001L;
    private static final long TEST_SPU_ID = 90001L;

    /** 本用例模拟的「下单时冻结的积分」，取消后应全额解冻。 */
    private static final int FROZEN_POINTS = 100;

    @Autowired(required = false)
    private OrderService orderService;

    @Autowired(required = false)
    private OrderCancelService orderCancelService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** ES 关闭后 ElasticsearchConfig 不再创建 ElasticsearchOperations，用 Mock 满足构造注入。 */
    @MockBean
    private ElasticsearchOperations elasticsearchOperations;

    @MockBean
    private co.elastic.clients.elasticsearch.ElasticsearchClient elasticsearchClient;

    private final List<Long> createdSkuIds = new ArrayList<>();
    private final List<Long> createdOrderIds = new ArrayList<>();
    private final List<Long> createdCouponIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long orderId : createdOrderIds) {
            jdbcTemplate.update("DELETE FROM stellar_mall_order_item WHERE order_id = ?", orderId);
            jdbcTemplate.update("DELETE FROM stellar_points_payment WHERE order_id = ?", orderId);
            jdbcTemplate.update("DELETE FROM stellar_user_coupon WHERE order_id = ?", orderId);
            jdbcTemplate.update("DELETE FROM stellar_mall_order WHERE id = ?", orderId);
        }
        for (Long couponId : createdCouponIds) {
            jdbcTemplate.update("DELETE FROM stellar_user_coupon WHERE coupon_id = ?", couponId);
            jdbcTemplate.update("DELETE FROM stellar_coupon WHERE id = ?", couponId);
        }
        for (Long skuId : createdSkuIds) {
            jdbcTemplate.update("DELETE FROM stellar_stock_log WHERE sku_id = ?", skuId);
            jdbcTemplate.update("DELETE FROM stellar_sku WHERE id = ?", skuId);
        }
        // user_points 对 user_id 唯一，必须清理，否则下一个用例插入会撞唯一键
        jdbcTemplate.update("DELETE FROM stellar_user_points WHERE user_id = ?", TEST_USER_ID);
        createdOrderIds.clear();
        createdSkuIds.clear();
        createdCouponIds.clear();
    }

    @Test
    @DisplayName("过期未支付订单：自动取消 + 归还库存 + 退还优惠券 + 解冻积分")
    void expiredPendingOrderIsCancelledAndAllResourcesRestored() {
        assertNotNull(orderService, "RED失败：OrderService 未注册");
        // 下单前 10 件，扣掉 3 件后剩 7 —— 取消应还原为 10
        long skuId = insertSku(7);
        OrderRow order = insertOrder("PENDING", LocalDateTime.now().minusMinutes(ORDER_EXPIRE_MINUTES + 5));
        insertOrderItem(order.id(), skuId, 3);

        long couponId = insertCoupon(1);                    // 已核销 1 张
        long userCouponId = insertUserCouponAsUsed(couponId, order.id());
        insertFrozenPointsPaidByOrder(order.id());           // 冻结 100 积分 + 一条 type=1 记录

        int cancelled = orderService.cancelExpiredOrders(200);

        assertTrue(cancelled >= 1, "应至少取消包含本用例的过期订单，实际 " + cancelled);

        // 1) 订单状态
        assertEquals("CANCELLED", orderStatusOf(order.id()), "过期订单状态应置为 CANCELLED");
        // 2) 库存归还 + 流水
        assertEquals(10, stockOf(skuId), "取消后库存必须归还（7 + 3）");
        assertEquals(1, rollbackLogCountOf(skuId), "应写入一条入库回滚流水，便于对账");
        // 3) 优惠券退回
        assertEquals(1, userCouponStatusOf(userCouponId), "优惠券应恢复为未使用（1）");
        assertNull(userCouponOrderIdOf(userCouponId), "退回后不应再挂在订单上");
        assertEquals(0, couponUsedCountOf(couponId), "券模板的已核销数应减回 0");
        // 4) 积分解冻
        assertEquals(0, frozenPointsOf(TEST_USER_ID), "冻结积分应清零");
        assertEquals(FROZEN_POINTS, availablePointsOf(TEST_USER_ID), "冻结积分应全额转为可用");
        assertEquals(1, unfreezePaymentCountOf(order.id()), "应写入一条 type=4 的解冻记录，便于追溯");
    }

    @Test
    @DisplayName("未到期订单：不被扫描，状态与库存均不变")
    void freshPendingOrderIsNotTouched() {
        long skuId = insertSku(7);
        OrderRow order = insertOrder("PENDING", LocalDateTime.now());
        insertOrderItem(order.id(), skuId, 3);

        orderService.cancelExpiredOrders(200);

        assertEquals("PENDING", orderStatusOf(order.id()), "未到期订单不应被取消");
        assertEquals(7, stockOf(skuId), "未取消时库存不应变化");
        assertEquals(0, rollbackLogCountOf(skuId), "未取消时不应写入回滚流水");
    }

    @Test
    @DisplayName("扫描后已被支付（CAS 失败）：跳过取消，库存/券/积分一律不动")
    void paidBeforeCasIsSkippedWithoutTouchingAnything() {
        long skuId = insertSku(7);
        // 库里已是 PAID，但传入的是「扫描时的快照」——模拟用户恰好在扫描与 CAS 之间完成支付
        OrderRow order = insertOrder("PAID", LocalDateTime.now().minusMinutes(ORDER_EXPIRE_MINUTES + 5));
        insertOrderItem(order.id(), skuId, 3);

        long couponId = insertCoupon(1);
        long userCouponId = insertUserCouponAsUsed(couponId, order.id());
        insertFrozenPointsPaidByOrder(order.id());

        MallOrder staleSnapshot = new MallOrder();
        staleSnapshot.setId(order.id());
        staleSnapshot.setOrderNo(order.orderNo());
        staleSnapshot.setUserId(TEST_USER_ID);
        staleSnapshot.setStatus("PENDING");

        boolean applied = orderCancelService.cancelExpiredOrder(staleSnapshot);

        assertFalse(applied, "CAS 竞争失败应返回 false");
        assertEquals("PAID", orderStatusOf(order.id()), "已支付订单不能被改成 CANCELLED");
        assertEquals(7, stockOf(skuId), "CAS 失败时绝不能回滚库存（否则等于白送 3 件）");
        assertEquals(2, userCouponStatusOf(userCouponId), "CAS 失败时不能退券");
        assertEquals(1, couponUsedCountOf(couponId), "CAS 失败时券模板核销数不能变");
        assertEquals(FROZEN_POINTS, frozenPointsOf(TEST_USER_ID), "CAS 失败时积分应保持冻结");
        assertEquals(0, unfreezePaymentCountOf(order.id()), "CAS 失败时不应写入解冻记录");
    }

    // ============================ 测试数据与查询辅助 ============================

    private record OrderRow(long id, String orderNo) {
    }

    private long insertSku(int stock) {
        jdbcTemplate.update("INSERT INTO stellar_sku "
                        + "(spu_id, name, price, stock, version, status, create_time, create_user, update_time, update_user) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                TEST_SPU_ID, "超时取消用例SKU", new BigDecimal("1.00"), stock, 0, 1,
                LocalDateTime.now(), 0L, LocalDateTime.now(), 0L);
        Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM stellar_sku", Long.class);
        assertNotNull(id, "插入 SKU 后应能取到自增主键");
        createdSkuIds.add(id);
        return id;
    }

    private OrderRow insertOrder(String status, LocalDateTime createTime) {
        String orderNo = "EXPIRE-UT-" + Long.toHexString(System.nanoTime());
        jdbcTemplate.update("INSERT INTO stellar_mall_order "
                        + "(order_no, user_id, total_amount, pay_amount, status, create_time, create_user, update_time, update_user) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                orderNo, TEST_USER_ID, new BigDecimal("3.00"), new BigDecimal("3.00"),
                status, createTime, 0L, LocalDateTime.now(), 0L);
        Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM stellar_mall_order", Long.class);
        assertNotNull(id, "插入订单后应能取到自增主键");
        createdOrderIds.add(id);
        return new OrderRow(id, orderNo);
    }

    private void insertOrderItem(long orderId, long skuId, int qty) {
        jdbcTemplate.update("INSERT INTO stellar_mall_order_item "
                        + "(order_id, spu_id, sku_id, spu_name, price, qty, subtotal) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                orderId, TEST_SPU_ID, skuId, "超时取消用例SPU快照", new BigDecimal("1.00"), qty,
                new BigDecimal("1.00").multiply(BigDecimal.valueOf(qty)));
    }

    /** 插入券模板，usedCount 为本用例已核销数。 */
    private long insertCoupon(int usedCount) {
        jdbcTemplate.update("INSERT INTO stellar_coupon "
                        + "(name, type, condition_amount, discount_amount, total_count, received_count, "
                        + "used_count, per_user_limit, start_time, end_time, status, "
                        + "create_time, create_user, update_time, update_user) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "超时取消用例券", 1, new BigDecimal("10.00"), new BigDecimal("2.00"), 100, 1,
                usedCount, 1, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7), 1,
                LocalDateTime.now(), 0L, LocalDateTime.now(), 0L);
        Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM stellar_coupon", Long.class);
        assertNotNull(id, "插入券模板后应能取到自增主键");
        createdCouponIds.add(id);
        return id;
    }

    /** 用户券置为「已使用（status=2）并挂在订单上」，模拟下单时核销。 */
    private long insertUserCouponAsUsed(long couponId, long orderId) {
        jdbcTemplate.update("INSERT INTO stellar_user_coupon "
                        + "(user_id, coupon_id, status, order_id, used_time, create_time, create_user, update_time, update_user) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                TEST_USER_ID, couponId, 2, orderId, LocalDateTime.now(),
                LocalDateTime.now(), 0L, LocalDateTime.now(), 0L);
        Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM stellar_user_coupon", Long.class);
        assertNotNull(id, "插入用户券后应能取到自增主键");
        return id;
    }

    /** 模拟下单冻结积分：可用 0 / 冻结 100，并写一条 type=1（冻结）记录。 */
    private void insertFrozenPointsPaidByOrder(long orderId) {
        jdbcTemplate.update("INSERT INTO stellar_user_points "
                        + "(user_id, total_points, available_points, frozen_points, total_earned, total_spent, "
                        + "version, create_time, update_time) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                TEST_USER_ID, FROZEN_POINTS, 0, FROZEN_POINTS, FROZEN_POINTS, 0,
                0, LocalDateTime.now(), LocalDateTime.now());
        jdbcTemplate.update("INSERT INTO stellar_points_payment "
                        + "(order_id, user_id, points, amount, type, biz_desc, create_time) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                orderId, TEST_USER_ID, FROZEN_POINTS, BigDecimal.ZERO, 1, "下单冻结积分", LocalDateTime.now());
    }

    private String orderStatusOf(long orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM stellar_mall_order WHERE id = ?", String.class, orderId);
    }

    private int stockOf(long skuId) {
        Integer stock = jdbcTemplate.queryForObject("SELECT stock FROM stellar_sku WHERE id = ?", Integer.class, skuId);
        return stock == null ? -1 : stock;
    }

    /** 入库类流水（type=1）条数：取消/超时关闭走的就是这个类型。 */
    private int rollbackLogCountOf(long skuId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stellar_stock_log WHERE sku_id = ? AND type = 1", Integer.class, skuId);
        return count == null ? -1 : count;
    }

    private int userCouponStatusOf(long userCouponId) {
        Integer status = jdbcTemplate.queryForObject(
                "SELECT status FROM stellar_user_coupon WHERE id = ?", Integer.class, userCouponId);
        return status == null ? -1 : status;
    }

    private Long userCouponOrderIdOf(long userCouponId) {
        return jdbcTemplate.queryForObject(
                "SELECT order_id FROM stellar_user_coupon WHERE id = ?", Long.class, userCouponId);
    }

    private int couponUsedCountOf(long couponId) {
        Integer used = jdbcTemplate.queryForObject(
                "SELECT used_count FROM stellar_coupon WHERE id = ?", Integer.class, couponId);
        return used == null ? -1 : used;
    }

    private int frozenPointsOf(long userId) {
        Integer frozen = jdbcTemplate.queryForObject(
                "SELECT frozen_points FROM stellar_user_points WHERE user_id = ?", Integer.class, userId);
        return frozen == null ? -1 : frozen;
    }

    private int availablePointsOf(long userId) {
        Integer available = jdbcTemplate.queryForObject(
                "SELECT available_points FROM stellar_user_points WHERE user_id = ?", Integer.class, userId);
        return available == null ? -1 : available;
    }

    /** 解冻记录（type=4）条数。 */
    private int unfreezePaymentCountOf(long orderId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stellar_points_payment WHERE order_id = ? AND type = 4", Integer.class, orderId);
        return count == null ? -1 : count;
    }
}
