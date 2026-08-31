package com.stellar.userflow;

import com.stellar.dto.OrderSubmitDTO;
import com.stellar.entity.*;
import com.stellar.enumeration.OrderStatus;
import com.stellar.exception.BaseException;
import com.stellar.exception.StockInsufficientException;
import com.stellar.mapper.*;
import com.stellar.result.PageResult;
import com.stellar.service.*;
import com.stellar.service.impl.OrderServiceImpl;
import com.stellar.vo.MallOrderVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 订单模块完整代理测试。
 * <p>
 * 所有外部依赖（11 个 Mapper/Service）全部通过 Mockito 代理，
 * 不依赖 MySQL / Redis / ES / OSS / 微信支付 / 邮件服务。
 * <p>
 * 覆盖范围：
 *   submit（购物车下单）    — 2 场景
 *   submitDirect（直购）    — 2 场景
 *   pay（模拟支付）         — 3 场景
 *   cancel（取消订单）      — 2 场景
 *   getDetail（订单详情）   — 1 场景
 *   listByUser（用户列表）  — 2 场景
 *   confirm（确认收货）     — 2 场景
 *   ship（管理端发货）      — 2 场景
 *   deleteOrder（删除）     — 3 场景
 *   markRefunding（标记退款） — 1 场景
 *   completeRefund（完成退款） — 3 场景
 *   cancelExpiredOrders（自动过期） — 2 场景
 *   pageOrders（管理端分页） — 1 场景
 *
 * 总计 26 个代理测试场景。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("订单模块 — 全接口代理测试")
class OrderServiceProxyTest {

    // ================================================================
    // 11 个外部依赖全部 Mock
    // ================================================================
    @Mock private CartMapper cartMapper;
    @Mock private SkuMapper skuMapper;
    @Mock private SpuMapper spuMapper;
    @Mock private SkuStockService skuStockService;
    @Mock private MallOrderMapper mallOrderMapper;
    @Mock private MallOrderItemMapper mallOrderItemMapper;
    @Mock private CouponService couponService;
    @Mock private UserMessageService userMessageService;
    @Mock private WalletService walletService;
    @Mock private NotificationService notificationService;
    @Mock private PointsService pointsService;
    @Mock private OrderCancelService orderCancelService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private static final Long USER_ID = 8888L;
    private static final Long ORDER_ID = 1000L;

    // ---- 工厂方法 ----

    private Sku sku(Long id, Long spuId, String name, String specs, BigDecimal price) {
        return Sku.builder().id(id).spuId(spuId).name(name).specs(specs)
                .price(price).stock(100).version(0).status(1).build();
    }

    private Spu spu(Long id, String name) {
        return Spu.builder().id(id).name(name)
                .mainImage("https://img.example.com/" + id + ".jpg").status(1).build();
    }

    private Cart cart(Long id, Long skuId, Long spuId, int qty) {
        Cart c = new Cart();
        c.setId(id); c.setUserId(USER_ID); c.setSkuId(skuId);
        c.setSpuId(spuId); c.setQty(qty); c.setChecked(1);
        return c;
    }

    private OrderSubmitDTO submitDto(String address, Integer payMethod) {
        OrderSubmitDTO dto = new OrderSubmitDTO();
        dto.setAddress(address);
        dto.setPayMethod(payMethod == null ? 1 : payMethod);
        return dto;
    }

    private MallOrder order(Long id, String status, Long userId, BigDecimal total, BigDecimal payAmount) {
        return MallOrder.builder().id(id).orderNo("SO" + id).userId(userId)
                .totalAmount(total).payAmount(payAmount).status(status)
                .address("测试地址").payMethod(1).build();
    }

    private MallOrderItem orderItem(Long id, Long orderId, Long skuId, Long spuId,
                                     String spuName, int qty, BigDecimal price) {
        return MallOrderItem.builder().id(id).orderId(orderId).skuId(skuId).spuId(spuId)
                .spuName(spuName).qty(qty).price(price)
                .subtotal(price.multiply(BigDecimal.valueOf(qty))).build();
    }

    // ---- 通用 Stub：Mock Mapper insert 后回填 ID ----

    private void stubOrderInsert() {
        when(mallOrderMapper.insert(any(MallOrder.class))).thenAnswer(inv -> {
            MallOrder o = inv.getArgument(0);
            o.setId(ORDER_ID);
            return 1;
        });
    }

    // ================================================================
    @Nested @DisplayName("1. submit — 购物车下单")
    class SubmitTests {

        @Test @DisplayName("正常提交：2个SKU，验证订单金额=400，库存扣减2次")
        void normalSubmit_twoSkus_success() {
            Cart c1 = cart(1L, 10L, 1L, 2);
            Cart c2 = cart(2L, 20L, 2L, 1);
            when(cartMapper.listCheckedByUserId(USER_ID)).thenReturn(Arrays.asList(c1, c2));

            Sku s1 = sku(10L, 1L, "SPU1·红", "颜色:红", BigDecimal.valueOf(100));
            Sku s2 = sku(20L, 2L, "SPU2·蓝", "颜色:蓝", BigDecimal.valueOf(200));
            when(skuMapper.listByIds(anyList())).thenReturn(Arrays.asList(s1, s2));
            when(spuMapper.listByIds(anyList())).thenReturn(Arrays.asList(spu(1L, "SPU1"), spu(2L, "SPU2")));

            doNothing().when(skuStockService).deduct(anyLong(), anyInt());
            stubOrderInsert();
            when(mallOrderItemMapper.insertBatch(anyList())).thenReturn(2);
            when(cartMapper.deleteByIds(anyList())).thenReturn(2);

            MallOrder result = orderService.submit(USER_ID, submitDto("地址A", 1));

            assertNotNull(result);
            assertEquals(ORDER_ID, result.getId());
            assertEquals(0, result.getTotalAmount().compareTo(BigDecimal.valueOf(400)));
            assertEquals(0, result.getPayAmount().compareTo(BigDecimal.valueOf(400)));
            verify(skuStockService).deduct(10L, 2);
            verify(skuStockService).deduct(20L, 1);
            verify(cartMapper).deleteByIds(argThat(ids -> ids.contains(1L) && ids.contains(2L)));
        }

        @Test @DisplayName("第二个SKU库存不足 → 抛StockInsufficientException，不写订单")
        void secondSkuOutOfStock_throwsAndNoOrder() {
            Cart c1 = cart(1L, 10L, 1L, 1);
            Cart c2 = cart(2L, 20L, 2L, 1);
            when(cartMapper.listCheckedByUserId(USER_ID)).thenReturn(Arrays.asList(c1, c2));

            when(skuMapper.listByIds(anyList())).thenReturn(Arrays.asList(
                    sku(10L, 1L, "S1", "默认", BigDecimal.valueOf(100)),
                    sku(20L, 2L, "S2", "默认", BigDecimal.valueOf(50))));
            when(spuMapper.listByIds(anyList())).thenReturn(Arrays.asList(spu(1L, "P1"), spu(2L, "P2")));

            doNothing().when(skuStockService).deduct(10L, 1);
            doThrow(new StockInsufficientException("库存不足")).when(skuStockService).deduct(20L, 1);

            assertThrows(StockInsufficientException.class,
                    () -> orderService.submit(USER_ID, submitDto("地址", 1)));
            verify(mallOrderMapper, never()).insert(any());
            verify(mallOrderItemMapper, never()).insertBatch(anyList());
        }

        @Test @DisplayName("购物车为空 → BaseException")
        void emptyCart_throwsBaseException() {
            when(cartMapper.listCheckedByUserId(USER_ID)).thenReturn(Collections.emptyList());
            assertThrows(BaseException.class,
                    () -> orderService.submit(USER_ID, submitDto("地址", 1)));
            verify(skuStockService, never()).deduct(anyLong(), anyInt());
        }

        @Test @DisplayName("购物车SKU已下架 → BaseException")
        void skuOffShelf_throwsBaseException() {
            Cart c = cart(1L, 30L, 3L, 1);
            when(cartMapper.listCheckedByUserId(USER_ID)).thenReturn(Collections.singletonList(c));

            Sku offShelf = sku(30L, 3L, "下架品", "默认", BigDecimal.ONE);
            offShelf.setStatus(0);
            when(skuMapper.listByIds(anyList())).thenReturn(Collections.singletonList(offShelf));

            assertThrows(BaseException.class,
                    () -> orderService.submit(USER_ID, submitDto("地址", 1)));
            verify(skuStockService, never()).deduct(anyLong(), anyInt());
        }

        @Test @DisplayName("使用优惠券下单 → 抵扣金额正确，券被标记已使用")
        void withCoupon_discountApplied() {
            Cart c = cart(1L, 10L, 1L, 1);
            when(cartMapper.listCheckedByUserId(USER_ID)).thenReturn(Collections.singletonList(c));

            Sku s = sku(10L, 1L, "S1", "默认", BigDecimal.valueOf(300));
            when(skuMapper.listByIds(anyList())).thenReturn(Collections.singletonList(s));
            when(spuMapper.listByIds(anyList())).thenReturn(Collections.singletonList(spu(1L, "P1")));

            doNothing().when(skuStockService).deduct(anyLong(), anyInt());
            stubOrderInsert();
            when(mallOrderItemMapper.insertBatch(anyList())).thenReturn(1);
            when(cartMapper.deleteByIds(anyList())).thenReturn(1);

            // Mock 优惠券
            UserCoupon uc = UserCoupon.builder().id(100L).userId(USER_ID).status(1)
                    .conditionAmount(BigDecimal.valueOf(200)).build();
            when(couponService.getUserCoupon(100L)).thenReturn(uc);

            OrderSubmitDTO dto = submitDto("地址", 1);
            dto.setUserCouponId(100L);
            dto.setDiscountAmount(BigDecimal.valueOf(50));

            MallOrder result = orderService.submit(USER_ID, dto);

            // 原价300 - 优惠50 = 实付250
            assertEquals(0, result.getTotalAmount().compareTo(BigDecimal.valueOf(300)));
            assertEquals(0, result.getPayAmount().compareTo(BigDecimal.valueOf(250)));
            verify(couponService).useCoupon(100L, ORDER_ID);
        }

        @Test @DisplayName("使用已过期优惠券下单 → BaseException，不创建订单")
        void withExpiredCoupon_throwsBaseException() {
            Cart c = cart(1L, 10L, 1L, 1);
            when(cartMapper.listCheckedByUserId(USER_ID)).thenReturn(Collections.singletonList(c));
            when(skuMapper.listByIds(anyList())).thenReturn(
                    Collections.singletonList(sku(10L, 1L, "S1", "默认", BigDecimal.valueOf(300))));
            when(spuMapper.listByIds(anyList())).thenReturn(Collections.singletonList(spu(1L, "P1")));

            // 有效期已于昨天结束
            UserCoupon uc = UserCoupon.builder().id(100L).userId(USER_ID).status(1)
                    .startTime(LocalDateTime.now().minusDays(10))
                    .endTime(LocalDateTime.now().minusDays(1))
                    .conditionAmount(BigDecimal.valueOf(200)).build();
            when(couponService.getUserCoupon(100L)).thenReturn(uc);

            OrderSubmitDTO dto = submitDto("地址", 1);
            dto.setUserCouponId(100L);
            dto.setDiscountAmount(BigDecimal.valueOf(50));

            BaseException ex = assertThrows(BaseException.class,
                    () -> orderService.submit(USER_ID, dto));
            assertTrue(ex.getMessage().contains("过期"));
            // 库存扣减发生在 createOrder 之前（失败后由事务回滚补偿），此处只断言订单未落库、券未核销
            verify(mallOrderMapper, never()).insert(any());
            verify(couponService, never()).useCoupon(anyLong(), anyLong());
        }

        @Test @DisplayName("使用未到生效时间优惠券下单 → BaseException，不创建订单")
        void withNotStartedCoupon_throwsBaseException() {
            Cart c = cart(1L, 10L, 1L, 1);
            when(cartMapper.listCheckedByUserId(USER_ID)).thenReturn(Collections.singletonList(c));
            when(skuMapper.listByIds(anyList())).thenReturn(
                    Collections.singletonList(sku(10L, 1L, "S1", "默认", BigDecimal.valueOf(300))));
            when(spuMapper.listByIds(anyList())).thenReturn(Collections.singletonList(spu(1L, "P1")));

            // 明天才生效
            UserCoupon uc = UserCoupon.builder().id(100L).userId(USER_ID).status(1)
                    .startTime(LocalDateTime.now().plusDays(1))
                    .endTime(LocalDateTime.now().plusDays(10))
                    .conditionAmount(BigDecimal.valueOf(200)).build();
            when(couponService.getUserCoupon(100L)).thenReturn(uc);

            OrderSubmitDTO dto = submitDto("地址", 1);
            dto.setUserCouponId(100L);
            dto.setDiscountAmount(BigDecimal.valueOf(50));

            BaseException ex = assertThrows(BaseException.class,
                    () -> orderService.submit(USER_ID, dto));
            assertTrue(ex.getMessage().contains("未到"));
            verify(mallOrderMapper, never()).insert(any());
            verify(couponService, never()).useCoupon(anyLong(), anyLong());
        }
    }

    // ================================================================
    @Nested @DisplayName("2. submitDirect — 立即购买")
    class SubmitDirectTests {

        @Test @DisplayName("正常直购 → 不走购物车查询，写订单成功")
        void normalDirect_success() {
            OrderSubmitDTO.OrderItemDTO item = new OrderSubmitDTO.OrderItemDTO();
            item.setSkuId(10L); item.setQuantity(2);
            item.setPrice(BigDecimal.valueOf(150));

            OrderSubmitDTO dto = submitDto("直购地址", 1);
            dto.setItems(Collections.singletonList(item));
            dto.setClearCart(false);

            when(skuMapper.listByIds(anyList())).thenReturn(
                    Collections.singletonList(sku(10L, 1L, "直购SKU", "默认", BigDecimal.valueOf(150))));
            when(spuMapper.listByIds(anyList())).thenReturn(Collections.singletonList(spu(1L, "直购SPU")));

            doNothing().when(skuStockService).deduct(anyLong(), anyInt());
            stubOrderInsert();
            when(mallOrderItemMapper.insertBatch(anyList())).thenReturn(1);

            MallOrder result = orderService.submitDirect(USER_ID, dto);

            assertNotNull(result);
            assertEquals(0, result.getTotalAmount().compareTo(BigDecimal.valueOf(300)));
            verify(cartMapper, never()).listCheckedByUserId(anyLong());
            verify(cartMapper, never()).deleteByIds(anyList());
        }

        @Test @DisplayName("直购时 clearCart=true → 清理购物车")
        void directWithClearCart_deletesCart() {
            OrderSubmitDTO.OrderItemDTO item = new OrderSubmitDTO.OrderItemDTO();
            item.setSkuId(10L); item.setQuantity(1);
            item.setPrice(BigDecimal.valueOf(100));

            OrderSubmitDTO dto = submitDto("地址", 1);
            dto.setItems(Collections.singletonList(item));
            dto.setClearCart(true);

            when(skuMapper.listByIds(anyList())).thenReturn(
                    Collections.singletonList(sku(10L, 1L, "SKU", "默认", BigDecimal.valueOf(100))));
            when(spuMapper.listByIds(anyList())).thenReturn(Collections.singletonList(spu(1L, "SPU")));

            doNothing().when(skuStockService).deduct(anyLong(), anyInt());
            stubOrderInsert();
            when(mallOrderItemMapper.insertBatch(anyList())).thenReturn(1);

            Cart existing = cart(99L, 10L, 1L, 3);
            when(cartMapper.listCheckedByUserId(USER_ID)).thenReturn(Collections.singletonList(existing));
            when(cartMapper.deleteByIds(anyList())).thenReturn(1);

            MallOrder result = orderService.submitDirect(USER_ID, dto);
            assertNotNull(result);
            verify(cartMapper).deleteByIds(argThat(ids -> ids.contains(99L)));
        }
    }

    // ================================================================
    @Nested @DisplayName("3. pay — 模拟支付")
    class PayTests {

        @Test @DisplayName("待付款订单 → 微信支付 → PAID，累加销量+积分")
        void payByWechat_success() {
            MallOrder o = order(ORDER_ID, OrderStatus.PENDING.getBackendValue(), USER_ID,
                    BigDecimal.valueOf(400), BigDecimal.valueOf(400));
            when(mallOrderMapper.getById(ORDER_ID)).thenReturn(o);
            when(mallOrderMapper.casUpdateStatus(ORDER_ID,
                    OrderStatus.PENDING.getBackendValue(), OrderStatus.PAID.getBackendValue())).thenReturn(1);

            List<MallOrderItem> items = Collections.singletonList(
                    orderItem(1L, ORDER_ID, 10L, 1L, "SPU1", 2, BigDecimal.valueOf(100)));
            when(mallOrderItemMapper.listByOrderId(ORDER_ID)).thenReturn(items);
            when(spuMapper.incrSaleCount(1L, 2)).thenReturn(1);

            orderService.pay(ORDER_ID, USER_ID, 1);

            verify(mallOrderMapper).casUpdateStatus(ORDER_ID,
                    OrderStatus.PENDING.getBackendValue(), OrderStatus.PAID.getBackendValue());
            verify(spuMapper).incrSaleCount(1L, 2);
            verify(pointsService).consumeFrozenPointsForOrder(USER_ID, ORDER_ID);
            verify(pointsService).earnByOrder(eq(USER_ID), eq(ORDER_ID), any());
        }

        @Test @DisplayName("钱包支付 → payMethod=4 → 走 walletService.payByWallet")
        void payByWallet_success() {
            MallOrder o = order(ORDER_ID, OrderStatus.PENDING.getBackendValue(), USER_ID,
                    BigDecimal.valueOf(200), BigDecimal.valueOf(200));
            when(mallOrderMapper.getById(ORDER_ID)).thenReturn(o);
            when(mallOrderMapper.updatePayMethod(ORDER_ID, 4)).thenReturn(1);

            when(mallOrderItemMapper.listByOrderId(ORDER_ID)).thenReturn(Collections.emptyList());

            orderService.pay(ORDER_ID, USER_ID, 4);

            verify(walletService).payByWallet(USER_ID, ORDER_ID);
            verify(mallOrderMapper, never()).casUpdateStatus(eq(ORDER_ID), anyString(), anyString());
        }

        @Test @DisplayName("非待付款订单 → BaseException")
        void payNonPending_throws() {
            MallOrder o = order(ORDER_ID, OrderStatus.PAID.getBackendValue(), USER_ID,
                    BigDecimal.valueOf(400), BigDecimal.valueOf(400));
            when(mallOrderMapper.getById(ORDER_ID)).thenReturn(o);

            assertThrows(BaseException.class,
                    () -> orderService.pay(ORDER_ID, USER_ID, 1));
            verify(mallOrderMapper, never()).casUpdateStatus(anyLong(), anyString(), anyString());
        }
    }

    // ================================================================
    @Nested @DisplayName("4. cancel — 取消订单")
    class CancelTests {

        @Test @DisplayName("正常取消 → CANCELLED，库存回滚+积分解冻+券退还")
        void cancelPending_success() {
            MallOrder o = order(ORDER_ID, OrderStatus.PENDING.getBackendValue(), USER_ID,
                    BigDecimal.valueOf(400), BigDecimal.valueOf(400));
            when(mallOrderMapper.getById(ORDER_ID)).thenReturn(o);

            List<MallOrderItem> items = Collections.singletonList(
                    orderItem(1L, ORDER_ID, 10L, 1L, "SPU1", 2, BigDecimal.valueOf(100)));
            when(mallOrderItemMapper.listByOrderId(ORDER_ID)).thenReturn(items);
            when(mallOrderMapper.casUpdateStatus(ORDER_ID,
                    OrderStatus.PENDING.getBackendValue(), OrderStatus.CANCELLED.getBackendValue())).thenReturn(1);

            orderService.cancel(ORDER_ID, USER_ID);

            verify(skuStockService).rollback(10L, 2);
            verify(mallOrderMapper).casUpdateStatus(ORDER_ID,
                    OrderStatus.PENDING.getBackendValue(), OrderStatus.CANCELLED.getBackendValue());
            verify(pointsService).unfreezePointsForOrder(USER_ID, ORDER_ID);
            verify(couponService).returnCouponByOrderId(ORDER_ID);
        }

        @Test @DisplayName("已支付订单 → 不可取消，抛 BaseException")
        void cancelPaid_throws() {
            MallOrder o = order(ORDER_ID, OrderStatus.PAID.getBackendValue(), USER_ID,
                    BigDecimal.valueOf(400), BigDecimal.valueOf(400));
            when(mallOrderMapper.getById(ORDER_ID)).thenReturn(o);

            assertThrows(BaseException.class,
                    () -> orderService.cancel(ORDER_ID, USER_ID));
            verify(skuStockService, never()).rollback(anyLong(), anyInt());
        }
    }

    // ================================================================
    @Nested @DisplayName("5. getDetail — 订单详情")
    class GetDetailTests {

        @Test @DisplayName("查询详情 → 返回含明细和SPU主图的VO")
        void getDetail_success() {
            MallOrder o = order(ORDER_ID, OrderStatus.PAID.getBackendValue(), USER_ID,
                    BigDecimal.valueOf(400), BigDecimal.valueOf(400));
            o.setCreateTime(LocalDateTime.of(2026, 8, 8, 10, 0));
            when(mallOrderMapper.getById(ORDER_ID)).thenReturn(o);

            MallOrderItem it = orderItem(1L, ORDER_ID, 10L, 1L, "SPU1", 2, BigDecimal.valueOf(100));
            when(mallOrderItemMapper.listByOrderIds(anyList())).thenReturn(Collections.singletonList(it));

            Spu spu = spu(1L, "SPU1");
            spu.setMainImage("https://img.example.com/1.jpg");
            when(spuMapper.listByIds(anyList())).thenReturn(Collections.singletonList(spu));

            MallOrderVO vo = orderService.getDetail(ORDER_ID, USER_ID);

            assertNotNull(vo);
            assertEquals(ORDER_ID, vo.getId());
            assertEquals(1, vo.getItems().size());
            assertEquals("SPU1", vo.getItems().get(0).getSpuName());
            assertEquals("https://img.example.com/1.jpg", vo.getItems().get(0).getPic());
        }
    }

    // ================================================================
    @Nested @DisplayName("6. listByUser — 用户订单列表")
    class ListByUserTests {

        @Test @DisplayName("查询全部 → 返回所有订单VO")
        void listAll_success() {
            MallOrder o1 = order(1L, OrderStatus.PENDING.getBackendValue(), USER_ID,
                    BigDecimal.valueOf(100), BigDecimal.valueOf(100));
            o1.setCreateTime(LocalDateTime.now());
            when(mallOrderMapper.listByUserId(USER_ID)).thenReturn(Collections.singletonList(o1));
            when(mallOrderItemMapper.listByOrderIds(anyList())).thenReturn(Collections.emptyList());

            List<MallOrderVO> vos = orderService.listByUser(USER_ID);

            assertNotNull(vos);
            assertEquals(1, vos.size());
            assertEquals(1L, vos.get(0).getId());
        }

        @Test @DisplayName("按状态筛选 → 传入statusCode=1(待付款)")
        void listByStatus_success() {
            MallOrder o = order(1L, OrderStatus.PENDING.getBackendValue(), USER_ID,
                    BigDecimal.valueOf(100), BigDecimal.valueOf(100));
            o.setCreateTime(LocalDateTime.now());
            when(mallOrderMapper.listByUserIdAndStatus(eq(USER_ID), anyList()))
                    .thenReturn(Collections.singletonList(o));
            when(mallOrderItemMapper.listByOrderIds(anyList())).thenReturn(Collections.emptyList());

            List<MallOrderVO> vos = orderService.listByUser(USER_ID, 1);

            assertNotNull(vos);
            assertEquals(1, vos.size());
        }
    }

    // ================================================================
    @Nested @DisplayName("7. confirm — 确认收货")
    class ConfirmTests {

        @Test @DisplayName("已发货订单 → COMPLETED，发送通知")
        void confirmShipped_success() {
            MallOrder o = order(ORDER_ID, OrderStatus.SHIPPED.getBackendValue(), USER_ID,
                    BigDecimal.valueOf(400), BigDecimal.valueOf(400));
            when(mallOrderMapper.getById(ORDER_ID)).thenReturn(o);
            when(mallOrderMapper.casUpdateStatus(ORDER_ID,
                    OrderStatus.SHIPPED.getBackendValue(), OrderStatus.COMPLETED.getBackendValue())).thenReturn(1);

            orderService.confirm(ORDER_ID, USER_ID);

            verify(mallOrderMapper).casUpdateStatus(ORDER_ID,
                    OrderStatus.SHIPPED.getBackendValue(), OrderStatus.COMPLETED.getBackendValue());
            verify(notificationService).sendOrderReceivedNotice(any(MallOrder.class));
        }

        @Test @DisplayName("待付款订单 → 不可确认收货")
        void confirmPending_throws() {
            MallOrder o = order(ORDER_ID, OrderStatus.PENDING.getBackendValue(), USER_ID,
                    BigDecimal.valueOf(400), BigDecimal.valueOf(400));
            when(mallOrderMapper.getById(ORDER_ID)).thenReturn(o);

            assertThrows(BaseException.class,
                    () -> orderService.confirm(ORDER_ID, USER_ID));
        }
    }

    // ================================================================
    @Nested @DisplayName("8. ship — 管理端发货")
    class ShipTests {

        @Test @DisplayName("已支付订单 → SHIPPED，写物流+发消息+发通知")
        void shipPaid_success() {
            MallOrder o = order(ORDER_ID, OrderStatus.PAID.getBackendValue(), USER_ID,
                    BigDecimal.valueOf(400), BigDecimal.valueOf(400));
            o.setOrderNo("SO20260808120000001ABC");
            when(mallOrderMapper.getById(ORDER_ID)).thenReturn(o);
            when(mallOrderMapper.ship(ORDER_ID, "SF123456", "顺丰")).thenReturn(1);

            orderService.ship(ORDER_ID, "SF123456", "顺丰");

            verify(mallOrderMapper).ship(ORDER_ID, "SF123456", "顺丰");
            verify(userMessageService).createMessage(eq(USER_ID), eq("ORDER_SHIPPED"),
                    eq("订单已发货"), contains("SF123456"), eq(ORDER_ID));
            verify(notificationService).sendOrderShippedNotice(any(MallOrder.class));
        }

        @Test @DisplayName("待付款订单 → 不可发货")
        void shipPending_throws() {
            MallOrder o = order(ORDER_ID, OrderStatus.PENDING.getBackendValue(), USER_ID,
                    BigDecimal.valueOf(400), BigDecimal.valueOf(400));
            when(mallOrderMapper.getById(ORDER_ID)).thenReturn(o);

            assertThrows(BaseException.class,
                    () -> orderService.ship(ORDER_ID, "SF123", "顺丰"));
            verify(mallOrderMapper, never()).ship(anyLong(), anyString(), anyString());
        }
    }

    // ================================================================
    @Nested @DisplayName("9. deleteOrder — 删除订单")
    class DeleteOrderTests {

        @Test @DisplayName("管理端删除已完成订单 → 先删明细再删主单")
        void adminDeleteCompleted_success() {
            MallOrder o = order(ORDER_ID, OrderStatus.COMPLETED.getBackendValue(), USER_ID,
                    BigDecimal.valueOf(400), BigDecimal.valueOf(400));
            when(mallOrderMapper.getById(ORDER_ID)).thenReturn(o);
            when(mallOrderItemMapper.deleteByOrderId(ORDER_ID)).thenReturn(1);
            when(mallOrderMapper.deleteById(ORDER_ID)).thenReturn(1);

            orderService.deleteOrder(ORDER_ID);

            verify(mallOrderItemMapper).deleteByOrderId(ORDER_ID);
            verify(mallOrderMapper).deleteById(ORDER_ID);
        }

        @Test @DisplayName("用户端删除自己的已取消订单 → 成功")
        void userDeleteCancelled_success() {
            MallOrder o = order(ORDER_ID, OrderStatus.CANCELLED.getBackendValue(), USER_ID,
                    BigDecimal.valueOf(400), BigDecimal.valueOf(400));
            when(mallOrderMapper.getById(ORDER_ID)).thenReturn(o);
            when(mallOrderItemMapper.deleteByOrderId(ORDER_ID)).thenReturn(1);
            when(mallOrderMapper.deleteById(ORDER_ID)).thenReturn(1);

            orderService.deleteOrder(ORDER_ID, USER_ID);

            verify(mallOrderItemMapper).deleteByOrderId(ORDER_ID);
            verify(mallOrderMapper).deleteById(ORDER_ID);
        }

        @Test @DisplayName("用户端删除待付款订单 → 拒绝（只允许已完成/已取消）")
        void userDeletePending_throws() {
            MallOrder o = order(ORDER_ID, OrderStatus.PENDING.getBackendValue(), USER_ID,
                    BigDecimal.valueOf(400), BigDecimal.valueOf(400));
            when(mallOrderMapper.getById(ORDER_ID)).thenReturn(o);

            assertThrows(BaseException.class,
                    () -> orderService.deleteOrder(ORDER_ID, USER_ID));
            verify(mallOrderMapper, never()).deleteById(anyLong());
        }
    }

    // ================================================================
    @Nested @DisplayName("10. markRefunding — 标记退款中")
    class MarkRefundingTests {

        @Test @DisplayName("已支付订单 → CAS 变更为 REFUNDING")
        void markRefunding_success() {
            MallOrder o = order(ORDER_ID, OrderStatus.PAID.getBackendValue(), USER_ID,
                    BigDecimal.valueOf(400), BigDecimal.valueOf(400));
            when(mallOrderMapper.getById(ORDER_ID)).thenReturn(o);
            when(mallOrderMapper.casUpdateStatus(ORDER_ID,
                    OrderStatus.PAID.getBackendValue(), OrderStatus.REFUNDING.getBackendValue())).thenReturn(1);

            orderService.markRefunding(ORDER_ID);

            verify(mallOrderMapper).casUpdateStatus(ORDER_ID,
                    OrderStatus.PAID.getBackendValue(), OrderStatus.REFUNDING.getBackendValue());
            // 不再走无条件 updateStatus
            verify(mallOrderMapper, never()).updateStatus(anyLong(), anyString());
        }

        @Test @DisplayName("待付款订单 → 不可标记退款，抛 BaseException")
        void markRefundingPending_throws() {
            MallOrder o = order(ORDER_ID, OrderStatus.PENDING.getBackendValue(), USER_ID,
                    BigDecimal.valueOf(400), BigDecimal.valueOf(400));
            when(mallOrderMapper.getById(ORDER_ID)).thenReturn(o);

            assertThrows(BaseException.class, () -> orderService.markRefunding(ORDER_ID));
            verify(mallOrderMapper, never()).casUpdateStatus(anyLong(), anyString(), anyString());
        }

        @Test @DisplayName("已是退款中 → 幂等返回，不重复更新")
        void markRefundingAlreadyRefunding_idempotent() {
            MallOrder o = order(ORDER_ID, OrderStatus.REFUNDING.getBackendValue(), USER_ID,
                    BigDecimal.valueOf(400), BigDecimal.valueOf(400));
            when(mallOrderMapper.getById(ORDER_ID)).thenReturn(o);

            orderService.markRefunding(ORDER_ID);

            verify(mallOrderMapper, never()).casUpdateStatus(anyLong(), anyString(), anyString());
        }

        @Test @DisplayName("订单不存在 → BaseException")
        void markRefundingNonExistent_throws() {
            when(mallOrderMapper.getById(ORDER_ID)).thenReturn(null);
            assertThrows(BaseException.class, () -> orderService.markRefunding(ORDER_ID));
        }
    }

    // ================================================================
    @Nested @DisplayName("11. completeRefund — 完成退款")
    class CompleteRefundTests {

        @Test @DisplayName("已完成订单退款 → 回滚库存+标记已退款")
        void refundCompleted_success() {
            // 新实现：markRefunded 幂等占位成功（返回1）后直接回滚库存，
            // 无需预先 getById（getById 仅在占位失败时用于区分已退款/已取消）
            MallOrderItem it = orderItem(1L, ORDER_ID, 10L, 1L, "SPU1", 3, BigDecimal.valueOf(100));
            when(mallOrderItemMapper.listByOrderId(ORDER_ID)).thenReturn(Collections.singletonList(it));
            when(mallOrderMapper.markRefunded(ORDER_ID)).thenReturn(1);

            orderService.completeRefund(ORDER_ID);

            verify(skuStockService).rollback(10L, 3);
            verify(mallOrderMapper).markRefunded(ORDER_ID);
            verify(mallOrderMapper, never()).getById(anyLong());
        }

        @Test @DisplayName("已取消订单 → 不可退款")
        void refundCancelled_throws() {
            MallOrder o = order(ORDER_ID, OrderStatus.CANCELLED.getBackendValue(), USER_ID,
                    BigDecimal.valueOf(400), BigDecimal.valueOf(400));
            when(mallOrderMapper.getById(ORDER_ID)).thenReturn(o);

            assertThrows(BaseException.class,
                    () -> orderService.completeRefund(ORDER_ID));
            verify(skuStockService, never()).rollback(anyLong(), anyInt());
        }

        @Test @DisplayName("订单不存在 → BaseException")
        void refundNonExistent_throws() {
            when(mallOrderMapper.getById(ORDER_ID)).thenReturn(null);
            assertThrows(BaseException.class,
                    () -> orderService.completeRefund(ORDER_ID));
        }

        @Test @DisplayName("已退款订单再次退款 → 幂等跳过，不重复回滚库存")
        void refundAlreadyRefunded_idempotent() {
            MallOrder o = order(ORDER_ID, OrderStatus.REFUNDED.getBackendValue(), USER_ID,
                    BigDecimal.valueOf(400), BigDecimal.valueOf(400));
            o.setIsRefunded(1);
            when(mallOrderMapper.getById(ORDER_ID)).thenReturn(o);
            // markRefunded 未 stub → 默认返回 0（SQL 幂等条件 is_refunded=0 不满足）

            orderService.completeRefund(ORDER_ID);

            // 幂等返回，不回滚库存，避免双倍回滚导致库存虚增
            verify(skuStockService, never()).rollback(anyLong(), anyInt());
            verify(mallOrderItemMapper, never()).listByOrderId(anyLong());
        }
    }

    // ================================================================
    @Nested @DisplayName("12. cancelExpiredOrders — 自动取消过期订单")
    class CancelExpiredOrdersTests {

        @Test @DisplayName("有2笔过期订单，limit=1 → 只处理第1笔")
        void twoExpired_limitOne_cancelsOne() {
            MallOrder o1 = order(1L, OrderStatus.PENDING.getBackendValue(), USER_ID,
                    BigDecimal.valueOf(100), BigDecimal.valueOf(100));
            MallOrder o2 = order(2L, OrderStatus.PENDING.getBackendValue(), USER_ID,
                    BigDecimal.valueOf(200), BigDecimal.valueOf(200));

            when(mallOrderMapper.listExpiredPending(any())).thenReturn(Arrays.asList(o1, o2));
            when(orderCancelService.cancelExpiredOrder(o1)).thenReturn(true);

            int cancelled = orderService.cancelExpiredOrders(1);

            assertEquals(1, cancelled);
            // 逐笔取消已委托给 OrderCancelService（独立 REQUIRES_NEW 事务），只处理第 1 笔
            verify(orderCancelService).cancelExpiredOrder(o1);
            verify(orderCancelService, never()).cancelExpiredOrder(o2);
        }

        @Test @DisplayName("过期列表中某笔取消被跳过 → cancelled计数不虚高")
        void expiredButPaid_skipped() {
            MallOrder o1 = order(1L, OrderStatus.PENDING.getBackendValue(), USER_ID,
                    BigDecimal.valueOf(100), BigDecimal.valueOf(100));
            // 第2笔虽在过期列表中，但状态已变为PAID（极端情况）
            MallOrder o2 = order(2L, OrderStatus.PAID.getBackendValue(), USER_ID,
                    BigDecimal.valueOf(200), BigDecimal.valueOf(200));

            when(mallOrderMapper.listExpiredPending(any())).thenReturn(Arrays.asList(o1, o2));
            when(orderCancelService.cancelExpiredOrder(o1)).thenReturn(true);
            // 第2笔返回 false（CAS 竞争失败被跳过），不计入取消数
            when(orderCancelService.cancelExpiredOrder(o2)).thenReturn(false);

            int cancelled = orderService.cancelExpiredOrders(10);

            assertEquals(1, cancelled);
            verify(orderCancelService).cancelExpiredOrder(o1);
            verify(orderCancelService).cancelExpiredOrder(o2);
        }

        @Test @DisplayName("某笔取消抛异常 → 被捕获不影响其他笔，cancelled不虚高")
        void oneThrows_othersStillProcessed() {
            MallOrder o1 = order(1L, OrderStatus.PENDING.getBackendValue(), USER_ID,
                    BigDecimal.valueOf(100), BigDecimal.valueOf(100));
            MallOrder o2 = order(2L, OrderStatus.PENDING.getBackendValue(), USER_ID,
                    BigDecimal.valueOf(200), BigDecimal.valueOf(200));

            when(mallOrderMapper.listExpiredPending(any())).thenReturn(Arrays.asList(o1, o2));
            // 第1笔事务内抛异常（如回滚库存失败）→ REQUIRES_NEW 事务回滚，异常传播到此处被捕获
            when(orderCancelService.cancelExpiredOrder(o1)).thenThrow(new RuntimeException("库存回滚失败"));
            when(orderCancelService.cancelExpiredOrder(o2)).thenReturn(true);

            int cancelled = orderService.cancelExpiredOrders(10);

            assertEquals(1, cancelled);
            verify(orderCancelService).cancelExpiredOrder(o1);
            verify(orderCancelService).cancelExpiredOrder(o2);
        }
    }

    // ================================================================
    @Nested @DisplayName("13. pageOrders — 管理端分页")
    class PageOrdersTests {

        @Test @DisplayName("按状态筛选 → 返回分页结果")
        void pageByStatus_success() {
            MallOrder o = order(1L, OrderStatus.PENDING.getBackendValue(), USER_ID,
                    BigDecimal.valueOf(100), BigDecimal.valueOf(100));
            o.setCreateTime(LocalDateTime.now());

            when(mallOrderMapper.listAll(eq(0), eq(10), eq("PENDING"), isNull(), isNull(), isNull()))
                    .thenReturn(Collections.singletonList(o));
            when(mallOrderMapper.count("PENDING", null, null, null)).thenReturn(1);
            when(mallOrderItemMapper.listByOrderIds(anyList())).thenReturn(Collections.emptyList());

            PageResult result = orderService.pageOrders(1, 10, "PENDING", null, null, null);

            assertNotNull(result);
            assertEquals(1L, result.getTotal());
            assertEquals(1, ((List<?>) result.getRecords()).size());
        }
    }

    // ================================================================
    @Nested @DisplayName("14. 积分抵扣 — 下单+支付全流程")
    class PointsIntegrationTests {

        @Test @DisplayName("下单使用积分 → freezePointsForOrder 被调用，payAmount 更新")
        void submitWithPoints_freezesPoints() {
            Cart c = cart(1L, 10L, 1L, 1);
            when(cartMapper.listCheckedByUserId(USER_ID)).thenReturn(Collections.singletonList(c));

            Sku s = sku(10L, 1L, "S1", "默认", BigDecimal.valueOf(300));
            when(skuMapper.listByIds(anyList())).thenReturn(Collections.singletonList(s));
            when(spuMapper.listByIds(anyList())).thenReturn(Collections.singletonList(spu(1L, "P1")));

            doNothing().when(skuStockService).deduct(anyLong(), anyInt());
            stubOrderInsert();
            when(mallOrderItemMapper.insertBatch(anyList())).thenReturn(1);
            when(cartMapper.deleteByIds(anyList())).thenReturn(1);

            // 100积分 = 1元，冻结200积分 = 抵扣2元
            when(pointsService.freezePointsForOrder(eq(USER_ID), eq(ORDER_ID), any(), any()))
                    .thenReturn(200);

            OrderSubmitDTO dto = submitDto("地址", 1);
            dto.setUsePoints(true);
            dto.setPointsAmount(BigDecimal.valueOf(3));

            MallOrder result = orderService.submit(USER_ID, dto);

            verify(pointsService).freezePointsForOrder(eq(USER_ID), eq(ORDER_ID), any(), any());
            // 300 - 2 = 298
            assertEquals(0, result.getPayAmount().compareTo(BigDecimal.valueOf(298)));
        }

        @Test @DisplayName("积分冻结异常 → 静默跳过，payAmount不变")
        void submitPointsFreezeFails_gracefulDegradation() {
            Cart c = cart(1L, 10L, 1L, 1);
            when(cartMapper.listCheckedByUserId(USER_ID)).thenReturn(Collections.singletonList(c));

            Sku s = sku(10L, 1L, "S1", "默认", BigDecimal.valueOf(300));
            when(skuMapper.listByIds(anyList())).thenReturn(Collections.singletonList(s));
            when(spuMapper.listByIds(anyList())).thenReturn(Collections.singletonList(spu(1L, "P1")));

            doNothing().when(skuStockService).deduct(anyLong(), anyInt());
            stubOrderInsert();
            when(mallOrderItemMapper.insertBatch(anyList())).thenReturn(1);
            when(cartMapper.deleteByIds(anyList())).thenReturn(1);

            // 积分服务抛异常
            when(pointsService.freezePointsForOrder(anyLong(), anyLong(), any(), any()))
                    .thenThrow(new RuntimeException("积分服务挂了"));

            OrderSubmitDTO dto = submitDto("地址", 1);
            dto.setUsePoints(true);
            dto.setPointsAmount(BigDecimal.valueOf(3));

            MallOrder result = orderService.submit(USER_ID, dto);

            // 异常被捕获，payAmount保持原值300
            assertNotNull(result);
            assertEquals(0, result.getPayAmount().compareTo(BigDecimal.valueOf(300)));
            assertEquals(0, result.getPointsDeducted());
        }
    }

    // ================================================================
    @Nested @DisplayName("15. 权限校验 — 跨用户操作")
    class PermissionTests {

        @Test @DisplayName("查询他人订单 → 抛 BaseException(NO_PERMISSION)")
        void getOthersOrder_throws() {
            MallOrder o = order(ORDER_ID, OrderStatus.PAID.getBackendValue(), 9999L, // 订单属于9999
                    BigDecimal.valueOf(400), BigDecimal.valueOf(400));
            when(mallOrderMapper.getById(ORDER_ID)).thenReturn(o);

            assertThrows(BaseException.class,
                    () -> orderService.getDetail(ORDER_ID, USER_ID)); // USER_ID=8888
        }

        @Test @DisplayName("取消他人订单 → 抛 BaseException")
        void cancelOthersOrder_throws() {
            MallOrder o = order(ORDER_ID, OrderStatus.PENDING.getBackendValue(), 9999L,
                    BigDecimal.valueOf(400), BigDecimal.valueOf(400));
            when(mallOrderMapper.getById(ORDER_ID)).thenReturn(o);

            assertThrows(BaseException.class,
                    () -> orderService.cancel(ORDER_ID, USER_ID));
        }

        @Test @DisplayName("支付他人订单 → 抛 BaseException")
        void payOthersOrder_throws() {
            MallOrder o = order(ORDER_ID, OrderStatus.PENDING.getBackendValue(), 9999L,
                    BigDecimal.valueOf(400), BigDecimal.valueOf(400));
            when(mallOrderMapper.getById(ORDER_ID)).thenReturn(o);

            assertThrows(BaseException.class,
                    () -> orderService.pay(ORDER_ID, USER_ID, 1));
        }
    }

    // ================================================================
    @Nested @DisplayName("16. 边界条件")
    class EdgeCaseTests {

        @Test @DisplayName("userId为null → submit使用0L兜底")
        void submitNullUserId_usesZero() {
            Cart c = cart(1L, 10L, 1L, 1);
            c.setUserId(0L);
            when(cartMapper.listCheckedByUserId(0L)).thenReturn(Collections.singletonList(c));

            when(skuMapper.listByIds(anyList())).thenReturn(
                    Collections.singletonList(sku(10L, 1L, "SKU", "默认", BigDecimal.valueOf(100))));
            when(spuMapper.listByIds(anyList())).thenReturn(Collections.singletonList(spu(1L, "SPU")));

            doNothing().when(skuStockService).deduct(anyLong(), anyInt());
            stubOrderInsert();
            when(mallOrderItemMapper.insertBatch(anyList())).thenReturn(1);
            when(cartMapper.deleteByIds(anyList())).thenReturn(1);

            MallOrder result = orderService.submit(null, submitDto("地址", 1));
            assertNotNull(result);
            assertEquals(0L, result.getUserId());
        }

        @Test @DisplayName("无地址 → submit 抛 BaseException")
        void submitNoAddress_throws() {
            OrderSubmitDTO dto = new OrderSubmitDTO();
            dto.setPayMethod(1);
            assertThrows(BaseException.class, () -> orderService.submit(USER_ID, dto));
        }

        @Test @DisplayName("确认他人订单收货 → 抛 BaseException")
        void confirmOthersOrder_throws() {
            MallOrder o = order(ORDER_ID, OrderStatus.SHIPPED.getBackendValue(), 9999L,
                    BigDecimal.valueOf(400), BigDecimal.valueOf(400));
            when(mallOrderMapper.getById(ORDER_ID)).thenReturn(o);

            assertThrows(BaseException.class,
                    () -> orderService.confirm(ORDER_ID, USER_ID));
        }
    }
}
