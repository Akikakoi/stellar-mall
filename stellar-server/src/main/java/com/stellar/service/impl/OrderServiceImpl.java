package com.stellar.service.impl;

import com.stellar.constant.MessageConstant;
import com.stellar.dto.OrderSubmitDTO;
import com.stellar.entity.*;
import com.stellar.enumeration.OrderStatus;
import com.stellar.exception.BaseException;
import com.stellar.mapper.*;
import com.stellar.service.CouponService;
import com.stellar.service.OrderService;
import com.stellar.service.NotificationService;
import com.stellar.service.PointsService;
import com.stellar.service.SkuStockService;
import com.stellar.service.UserMessageService;
import com.stellar.vo.MallOrderItemVO;
import com.stellar.vo.MallOrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    /** 测试里 mock 的顺序：CartMapper, SkuMapper, SpuMapper, SkuStockService, MallOrderMapper, MallOrderItemMapper */
    private final CartMapper cartMapper;
    private final SkuMapper skuMapper;
    private final SpuMapper spuMapper;
    private final SkuStockService skuStockService;
    private final MallOrderMapper mallOrderMapper;
    private final MallOrderItemMapper mallOrderItemMapper;
    private final CouponService couponService;
    private final UserMessageService userMessageService;
    private final com.stellar.service.WalletService walletService;
    private final NotificationService notificationService;
    private final PointsService pointsService;

    // -------- 提交订单 --------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MallOrder submit(Long userId, OrderSubmitDTO dto) {
        Long uid = userId != null ? userId : 0L;
        validateSubmitDto(dto);

        List<Cart> carts = cartMapper.listCheckedByUserId(uid);
        if (carts == null || carts.isEmpty()) {
            throw new BaseException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        List<OrderLine> lines = buildLinesFromCarts(carts);
        deductStock(lines);
        List<Long> cartIdsToDelete = carts.stream().map(Cart::getId).collect(Collectors.toList());

        return createOrder(uid, dto.getAddress(), dto.getPayMethod(), dto.getRemark(),
                calculateTotal(lines), lines, cartIdsToDelete,
                dto.getUserCouponId(), dto.getDiscountAmount(),
                dto.getUsePoints() != null && dto.getUsePoints(), dto.getPointsAmount());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MallOrder submitDirect(Long userId, OrderSubmitDTO dto) {
        Long uid = userId != null ? userId : 0L;
        log.info("[OrderServiceImpl] submitDirect, userId={}, dto={}, items={}",
                userId, dto, dto != null ? dto.getItems() : null);
        validateSubmitDto(dto);
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            log.warn("[OrderServiceImpl] items is null or empty");
            throw new BaseException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        List<OrderLine> lines = buildLinesFromItems(dto.getItems());
        deductStock(lines);

        // 直接购买模式下，仅当显式要求清空购物车时，才清理用户已勾选的购物车项
        List<Long> cartIdsToDelete = null;
        boolean shouldClearCart = dto.getClearCart() == null || Boolean.TRUE.equals(dto.getClearCart());
        if (shouldClearCart) {
            List<Cart> checkedCarts = cartMapper.listCheckedByUserId(uid);
            if (checkedCarts != null && !checkedCarts.isEmpty()) {
                cartIdsToDelete = checkedCarts.stream().map(Cart::getId).collect(Collectors.toList());
            }
        }

        return createOrder(uid, dto.getAddress(), dto.getPayMethod(), dto.getRemark(),
                calculateTotal(lines), lines, cartIdsToDelete,
                dto.getUserCouponId(), dto.getDiscountAmount(),
                dto.getUsePoints() != null && dto.getUsePoints(), dto.getPointsAmount());
    }

    private void validateSubmitDto(OrderSubmitDTO dto) {
        if (dto == null || dto.getAddress() == null || dto.getAddress().trim().isEmpty()) {
            throw new BaseException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
    }

    private List<OrderLine> buildLinesFromCarts(List<Cart> carts) {
        // 批量查询 SKU，消除逐条 getById 的 N+1 读
        List<Long> skuIds = carts.stream()
                .map(Cart::getSkuId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, Sku> skuMap = loadSkusByIds(skuIds);
        // 批量查询 SPU（取自 cart.spuId）
        List<Long> spuIds = carts.stream()
                .map(Cart::getSpuId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, Spu> spuMap = loadSpusByIds(spuIds);

        List<OrderLine> lines = new ArrayList<>(carts.size());
        for (Cart c : carts) {
            Sku sku = skuMap.get(c.getSkuId());
            if (sku == null) throw new BaseException(MessageConstant.SKU_NOT_FOUND);
            if (sku.getStatus() == null || sku.getStatus() != 1) {
                throw new BaseException("SKU（id=" + sku.getId() + "）已停售，无法下单");
            }
            int qty = c.getQty() == null ? 1 : c.getQty();
            checkStock(sku, qty);
            Spu spu = c.getSpuId() == null ? null : spuMap.get(c.getSpuId());
            lines.add(buildLine(c, sku, spu, qty));
        }
        return lines;
    }

    private List<OrderLine> buildLinesFromItems(List<OrderSubmitDTO.OrderItemDTO> items) {
        List<Long> skuIds = items.stream()
                .map(OrderSubmitDTO.OrderItemDTO::getSkuId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, Sku> skuMap = loadSkusByIds(skuIds);
        // SPU 取自 sku.spuId
        List<Long> spuIds = skuMap.values().stream()
                .map(Sku::getSpuId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, Spu> spuMap = loadSpusByIds(spuIds);

        List<OrderLine> lines = new ArrayList<>(items.size());
        for (OrderSubmitDTO.OrderItemDTO item : items) {
            if (item.getSkuId() == null) {
                throw new BaseException(MessageConstant.ILLEGAL_PARAMETER);
            }
            Sku sku = skuMap.get(item.getSkuId());
            if (sku == null) throw new BaseException(MessageConstant.SKU_NOT_FOUND);
            if (sku.getStatus() == null || sku.getStatus() != 1) {
                throw new BaseException("SKU（id=" + sku.getId() + "）已停售，无法下单");
            }
            int qty = item.getQuantity() == null ? 1 : item.getQuantity();
            checkStock(sku, qty);
            BigDecimal extraAmount = item.getExtraAmount() == null ? BigDecimal.ZERO : item.getExtraAmount();
            Spu spu = sku.getSpuId() == null ? null : spuMap.get(sku.getSpuId());
            lines.add(buildLine(null, sku, spu, qty, extraAmount));
        }
        return lines;
    }

    /** 批量查 SKU 并按 id 索引，空入参返回空 Map。 */
    private Map<Long, Sku> loadSkusByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyMap();
        List<Sku> skus = skuMapper.listByIds(ids);
        if (skus == null) return Collections.emptyMap();
        return skus.stream().collect(Collectors.toMap(Sku::getId, s -> s, (a, b) -> a));
    }

    /** 批量查 SPU 并按 id 索引，空入参返回空 Map。 */
    private Map<Long, Spu> loadSpusByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyMap();
        List<Spu> spus = spuMapper.listByIds(ids);
        if (spus == null) return Collections.emptyMap();
        return spus.stream().collect(Collectors.toMap(Spu::getId, s -> s, (a, b) -> a));
    }

    private void checkStock(Sku sku, int qty) {
        int stock = sku.getStock() == null ? 0 : sku.getStock();
        if (stock < qty) {
            throw new BaseException(MessageConstant.STOCK_NOT_ENOUGH
                    + " (skuId=" + sku.getId() + ", stock=" + stock + ", need=" + qty + ")");
        }
    }

    private OrderLine buildLine(Cart cart, Sku sku, Spu spu, int qty) {
        return buildLine(cart, sku, spu, qty, BigDecimal.ZERO);
    }

    private OrderLine buildLine(Cart cart, Sku sku, Spu spu, int qty, BigDecimal extraAmount) {
        BigDecimal price = sku.getPrice() == null ? BigDecimal.ZERO : sku.getPrice();
        BigDecimal subtotal = price.multiply(BigDecimal.valueOf(qty));
        return new OrderLine(cart, sku, spu, qty, price, subtotal, extraAmount);
    }

    private void deductStock(List<OrderLine> lines) {
        for (OrderLine line : lines) {
            skuStockService.deduct(line.sku.getId(), line.qty);
        }
    }

    private BigDecimal calculateTotal(List<OrderLine> lines) {
        return lines.stream()
                .map(line -> line.subtotal.add(line.extraAmount))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private MallOrder createOrder(Long uid, String address, Integer payMethod, String remark,
                                   BigDecimal total, List<OrderLine> lines, List<Long> cartIdsToDelete,
                                   Long userCouponId, BigDecimal discountAmount,
                                   boolean usePoints, BigDecimal requestedPointsAmount) {
        // 校验并使用优惠券
        BigDecimal discount = BigDecimal.ZERO;
        if (userCouponId != null) {
            UserCoupon userCoupon = couponService.getUserCoupon(userCouponId);
            if (userCoupon == null || !uid.equals(userCoupon.getUserId())) {
                throw new BaseException("优惠券不存在或无权限使用");
            }
            if (userCoupon.getStatus() == null || userCoupon.getStatus() != 1) {
                throw new BaseException("优惠券状态异常，无法使用");
            }
            if (total.compareTo(userCoupon.getConditionAmount() == null ? BigDecimal.ZERO : userCoupon.getConditionAmount()) < 0) {
                throw new BaseException("订单金额未达到优惠券使用门槛");
            }
            discount = discountAmount == null ? BigDecimal.ZERO : discountAmount;
            if (discount.compareTo(total) > 0) {
                discount = total;
            }
        }

        BigDecimal         payAmountBeforePoints = total.subtract(discount).max(BigDecimal.ZERO);

        // 对浮点数精度做归一化，防止前端 JS 浮点漂移（如 599.8999999999999）
        if (discountAmount != null) {
            discount = discountAmount.setScale(2, java.math.RoundingMode.HALF_UP);
            payAmountBeforePoints = total.subtract(discount).max(BigDecimal.ZERO);
        }

        // 构造订单（先用原始 payAmount，积分抵扣后再更新）
        MallOrder order = MallOrder.builder()
                .orderNo(generateOrderNo())
                .userId(uid)
                .totalAmount(total)
                .payAmount(payAmountBeforePoints)
                .status(OrderStatus.PENDING.getBackendValue())
                .address(address)
                .payMethod(payMethod == null ? 1 : payMethod)
                .remark(remark)
                .pointsDeducted(0)
                .pointsAmount(BigDecimal.ZERO)
                .build();
        mallOrderMapper.insert(order);

        // ---- 积分抵扣：订单创建后再冻结积分（需要真实的 orderId） ----
        Integer pointsDeducted = 0;
        BigDecimal pointsAmount = BigDecimal.ZERO;
        if (usePoints && payAmountBeforePoints.compareTo(BigDecimal.ZERO) > 0) {
            try {
                BigDecimal requestedAmount = requestedPointsAmount != null ? requestedPointsAmount : payAmountBeforePoints;
                int frozen = pointsService.freezePointsForOrder(uid, order.getId(), requestedAmount, payAmountBeforePoints);
                if (frozen > 0) {
                    pointsDeducted = frozen;
                    pointsAmount = BigDecimal.valueOf(frozen)
                            .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                    BigDecimal payAmount = payAmountBeforePoints.subtract(pointsAmount).max(BigDecimal.ZERO);
                    // 更新订单的积分抵扣信息和实付金额
                    mallOrderMapper.update(MallOrder.builder()
                            .id(order.getId())
                            .payAmount(payAmount)
                            .pointsDeducted(pointsDeducted)
                            .pointsAmount(pointsAmount)
                            .build());
                    order.setPayAmount(payAmount);
                    order.setPointsDeducted(pointsDeducted);
                    order.setPointsAmount(pointsAmount);
                }
            } catch (Exception e) {
                log.error("[OrderService] 积分冻结失败，本次下单不使用积分: orderId={}, userId={}", order.getId(), uid, e);
            }
        }

        // 标记优惠券已使用
        if (userCouponId != null) {
            couponService.useCoupon(userCouponId, order.getId());
        }

        // 写明细（快照化 SPU 名 / SKU 规格 / 单价）——批量插入，一次 SQL 写完
        List<MallOrderItem> items = new ArrayList<>(lines.size());
        for (OrderLine line : lines) {
            items.add(MallOrderItem.builder()
                    .orderId(order.getId())
                    .spuId(line.sku.getSpuId())
                    .skuId(line.sku.getId())
                    .spuName(line.spu == null ? line.sku.getName() : line.spu.getName())
                    .skuSpecs(line.sku.getSpecs())
                    .price(line.price)
                    .qty(line.qty)
                    .subtotal(line.subtotal)
                    .extraAmount(line.extraAmount.compareTo(BigDecimal.ZERO) > 0 ? line.extraAmount : null)
                    .build());
        }
        if (!items.isEmpty()) {
            mallOrderItemMapper.insertBatch(items);
        }

        // 清理已下单的购物车项（前端直传模式时 cartIdsToDelete 为 null）
        if (cartIdsToDelete != null && !cartIdsToDelete.isEmpty()) {
            cartMapper.deleteByIds(cartIdsToDelete);
        }

        return order;
    }

    // -------- 模拟支付 --------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pay(Long orderId, Long userId, Integer payMethod) {
        MallOrder order = requireOrder(orderId, userId);
        if (!OrderStatus.PENDING.getBackendValue().equals(order.getStatus())) {
            throw new BaseException(MessageConstant.ORDER_STATUS_ERROR
                    + "（当前状态=" + order.getStatus() + "，仅 " + OrderStatus.PENDING.getDescription() + " 可支付）");
        }
        // 如果指定了支付方式，更新订单
        int actualPayMethod = (payMethod != null) ? payMethod
                : (order.getPayMethod() != null ? order.getPayMethod() : 1);
        if (payMethod != null && !Integer.valueOf(payMethod).equals(order.getPayMethod())) {
            mallOrderMapper.updatePayMethod(orderId, payMethod);
        }
        // 钱包支付 (payMethod=4) 走钱包扣款
        if (actualPayMethod == 4) {
            walletService.payByWallet(userId, orderId);
        } else {
            // 其他支付方式：模拟直接变更为已支付
            mallOrderMapper.updateStatus(orderId, OrderStatus.PAID.getBackendValue());
        }
        // 累加商品销量
        incrSaleCountForOrder(orderId);
        // 积分抵扣：将冻结的积分转为实际消费
        consumeOrderPointsQuietly(userId, orderId);
        // 下单奖励积分（不影响支付主流程）
        earnOrderPointsQuietly(userId, orderId, order.getPayAmount());
    }

    /** 支付后累加订单中各 SPU 的销量。 */
    private void incrSaleCountForOrder(Long orderId) {
        try {
            List<MallOrderItem> items = mallOrderItemMapper.listByOrderId(orderId);
            if (items != null) {
                for (MallOrderItem item : items) {
                    if (item.getSpuId() != null && item.getQty() != null && item.getQty() > 0) {
                        spuMapper.incrSaleCount(item.getSpuId(), item.getQty());
                    }
                }
            }
        } catch (Exception e) {
            log.error("[OrderService] 销量累加失败（支付不受影响）: orderId={}", orderId, e);
        }
    }

    /**
     * 支付后发放积分。任何异常不抛出，确保支付主流程不受积分系统影响。
     */
    private void earnOrderPointsQuietly(Long userId, Long orderId, BigDecimal payAmount) {
        try {
            pointsService.earnByOrder(userId, orderId, payAmount);
        } catch (Exception e) {
            log.error("[OrderService] 积分发放失败（支付不受影响）: orderId={}, userId={}, payAmount={}",
                    orderId, userId, payAmount, e);
        }
    }

    /**
     * 支付后将冻结的积分转为实际消费。任何异常不抛出，确保支付主流程不受积分系统影响。
     */
    private void consumeOrderPointsQuietly(Long userId, Long orderId) {
        try {
            pointsService.consumeFrozenPointsForOrder(userId, orderId);
        } catch (Exception e) {
            log.error("[OrderService] 积分扣减失败（支付不受影响）: orderId={}, userId={}",
                    orderId, userId, e);
        }
    }

    /**
     * 取消订单时解冻积分。任何异常不抛出，取消失败不影响积分。
     */
    private void unfreezeOrderPointsQuietly(Long userId, Long orderId) {
        try {
            pointsService.unfreezePointsForOrder(userId, orderId);
        } catch (Exception e) {
            log.error("[OrderService] 积分解冻失败（取消不受影响）: orderId={}, userId={}",
                    orderId, userId, e);
        }
    }

    // -------- 取消订单（逐条 rollback 库存）。仅 PENDING 待付款可取消；PAID 退款需走售后审批单独流程 --------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long orderId, Long userId) {
        MallOrder order = requireOrder(orderId, userId);
        if (!OrderStatus.PENDING.getBackendValue().equals(order.getStatus())) {
            throw new BaseException(MessageConstant.ORDER_STATUS_ERROR
                    + "（当前状态=" + order.getStatus() + "，仅 " + OrderStatus.PENDING.getDescription() + " 订单可取消）");
        }

        List<MallOrderItem> items = mallOrderItemMapper.listByOrderId(orderId);
        if (items != null) {
            for (MallOrderItem it : items) {
                skuStockService.rollback(it.getSkuId(),
                        it.getQty() == null ? 0 : it.getQty());
            }
        }

        mallOrderMapper.updateStatus(orderId, OrderStatus.CANCELLED.getBackendValue());

        // 解冻积分
        unfreezeOrderPointsQuietly(userId, orderId);

        // 退还优惠券
        couponService.returnCouponByOrderId(orderId);
    }

    // -------- 管理端发货 --------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ship(Long orderId, String trackingNo, String deliveryCompany) {
        MallOrder order = mallOrderMapper.getById(orderId);
        if (order == null) {
            throw new BaseException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (!OrderStatus.PAID.getBackendValue().equals(order.getStatus())) {
            throw new BaseException(MessageConstant.ORDER_STATUS_ERROR
                    + "（当前状态=" + order.getStatus() + "，仅 " + OrderStatus.PAID.getDescription() + " 可发货）");
        }
        mallOrderMapper.ship(orderId, trackingNo, deliveryCompany);

        // 自动发送发货通知给用户
        String content = "您的订单 " + order.getOrderNo() + " 已发货";
        if (trackingNo != null && !trackingNo.isEmpty()) {
            content += "，快递单号：" + trackingNo;
            if (deliveryCompany != null && !deliveryCompany.isEmpty()) {
                content += "（" + deliveryCompany + "）";
            }
        }
        content += "，请注意查收。";
        userMessageService.createMessage(order.getUserId(), "ORDER_SHIPPED",
                "订单已发货", content, orderId);
        log.info("[OrderService] 订单 {} 发货通知已发送给用户 {}", order.getOrderNo(), order.getUserId());

        // 异步短信/邮件通知
        notificationService.sendOrderShippedNotice(order);
    }

    // -------- 管理端分页查询 --------

    @Override
    public com.stellar.result.PageResult pageOrders(int page, int pageSize, String status, String orderNo,
                                                    String startTime, String endTime) {
        int offset = (page - 1) * pageSize;
        List<MallOrder> list = mallOrderMapper.listAll(offset, pageSize, status, orderNo, startTime, endTime);
        int total = mallOrderMapper.count(status, orderNo, startTime, endTime);
        List<MallOrderVO> vos = toOrderVOs(list);
        return new com.stellar.result.PageResult((long) total, vos);
    }

    // -------- 管理端删除订单 --------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrder(Long orderId) {
        MallOrder order = mallOrderMapper.getById(orderId);
        if (order == null) {
            throw new BaseException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (!isDeletable(order.getStatus())) {
            throw new BaseException(MessageConstant.ORDER_STATUS_ERROR
                    + "（当前状态=" + order.getStatus() + "，仅 "
                    + OrderStatus.COMPLETED.getDescription() + "/" + OrderStatus.CANCELLED.getDescription() + " 订单可删除）");
        }
        mallOrderItemMapper.deleteByOrderId(orderId);
        mallOrderMapper.deleteById(orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrder(Long orderId, Long userId) {
        MallOrder order = requireOrder(orderId, userId);
        if (!isDeletable(order.getStatus())) {
            throw new BaseException(MessageConstant.ORDER_STATUS_ERROR
                    + "（当前状态=" + order.getStatus() + "，仅 "
                    + OrderStatus.COMPLETED.getDescription() + "/" + OrderStatus.CANCELLED.getDescription() + " 订单可删除）");
        }
        mallOrderItemMapper.deleteByOrderId(orderId);
        mallOrderMapper.deleteById(orderId);
    }

    // -------- 订单详情/列表 --------

    @Override
    public MallOrderVO getDetail(Long orderId, Long userId) {
        MallOrder order = requireOrder(orderId, userId);
        Map<Long, List<MallOrderItem>> itemMap = loadItemsByOrderIds(Collections.singletonList(orderId));
        List<MallOrderItem> items = itemMap.getOrDefault(orderId, Collections.emptyList());
        Map<Long, String> imageMap = loadSpuMainImages(extractSpuIds(items));
        return toOrderVO(order, items, imageMap);
    }

    @Override
    public List<MallOrderVO> listByUser(Long userId) {
        return listByUser(userId, null);
    }

    @Override
    public List<MallOrderVO> listByUser(Long userId, Integer statusCode) {
        if (userId == null) return Collections.emptyList();
        // 前端数字 → 后端字符串集合
        List<String> statusList = null;
        if (statusCode != null) {
            String s = toBackendStatus(statusCode);
            if (s == null) return Collections.emptyList(); // 未启用的状态 tab，直接返回空
            statusList = Collections.singletonList(s);
        }
        List<MallOrder> list = (statusList == null)
                ? mallOrderMapper.listByUserId(userId)
                : mallOrderMapper.listByUserIdAndStatus(userId, statusList);
        return toOrderVOs(list);
    }

    /** 前端数字 status → 后端字符串。 */
    static String toBackendStatus(Integer code) {
        OrderStatus status = OrderStatus.fromFrontendCode(code);
        return status == null ? null : status.getBackendValue();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long orderId, Long userId) {
        MallOrder order = requireOrder(orderId, userId);
        if (!OrderStatus.SHIPPED.getBackendValue().equals(order.getStatus())) {
            throw new BaseException(MessageConstant.ORDER_STATUS_ERROR
                    + "（当前状态=" + order.getStatus() + "，仅 " + OrderStatus.SHIPPED.getDescription() + " 可确认收货）");
        }
        mallOrderMapper.updateStatus(orderId, OrderStatus.COMPLETED.getBackendValue());

        // 异步短信通知
        notificationService.sendOrderReceivedNotice(order);
    }

    // ================= 内部工具 =================

    private MallOrder requireOrder(Long orderId, Long userId) {
        if (orderId == null || userId == null) {
            throw new BaseException(MessageConstant.ILLEGAL_PARAMETER);
        }
        MallOrder order = mallOrderMapper.getById(orderId);
        if (order == null) {
            throw new BaseException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (!userId.equals(order.getUserId())) {
            throw new BaseException(MessageConstant.NO_PERMISSION);
        }
        return order;
    }

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private static String generateOrderNo() {
        String ts = LocalDateTime.now().format(NO_FMT);
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "SO" + ts + uuid;
    }

    private List<MallOrderVO> toOrderVOs(List<MallOrder> orders) {
        if (orders == null || orders.isEmpty()) return Collections.emptyList();
        List<Long> orderIds = orders.stream().map(MallOrder::getId).collect(Collectors.toList());
        Map<Long, List<MallOrderItem>> itemMap = loadItemsByOrderIds(orderIds);
        List<MallOrderItem> allItems = itemMap.values().stream()
                .flatMap(List::stream).collect(Collectors.toList());
        Map<Long, String> imageMap = loadSpuMainImages(extractSpuIds(allItems));
        List<MallOrderVO> vos = new ArrayList<>(orders.size());
        for (MallOrder o : orders) {
            vos.add(toOrderVO(o, itemMap.getOrDefault(o.getId(), Collections.emptyList()), imageMap));
        }
        return vos;
    }

    private Map<Long, List<MallOrderItem>> loadItemsByOrderIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) return Collections.emptyMap();
        List<MallOrderItem> items = mallOrderItemMapper.listByOrderIds(orderIds);
        if (items == null) return Collections.emptyMap();
        return items.stream().collect(Collectors.groupingBy(MallOrderItem::getOrderId));
    }

    private Set<Long> extractSpuIds(List<MallOrderItem> items) {
        if (items == null) return Collections.emptySet();
        return items.stream()
                .filter(it -> it != null && it.getSpuId() != null)
                .map(MallOrderItem::getSpuId)
                .collect(Collectors.toSet());
    }

    private Map<Long, String> loadSpuMainImages(Set<Long> spuIds) {
        if (spuIds == null || spuIds.isEmpty()) return Collections.emptyMap();
        List<Spu> spus = spuMapper.listByIds(new ArrayList<>(spuIds));
        if (spus == null) return Collections.emptyMap();
        Map<Long, String> map = new HashMap<>(spus.size());
        for (Spu s : spus) {
            if (s != null && s.getId() != null) {
                map.put(s.getId(), s.getMainImage());
            }
        }
        return map;
    }

    private MallOrderVO toOrderVO(MallOrder o, List<MallOrderItem> items, Map<Long, String> spuImageMap) {
        List<MallOrderItemVO> ivos = items == null ? Collections.emptyList() :
                items.stream().map(it -> {
                    String pic = null;
                    if (it.getSpuId() != null && spuImageMap != null) {
                        pic = spuImageMap.get(it.getSpuId());
                    }
                    return MallOrderItemVO.builder()
                            .id(it.getId())
                            .spuId(it.getSpuId())
                            .skuId(it.getSkuId())
                            .spuName(it.getSpuName())
                            .skuSpecs(it.getSkuSpecs())
                            .price(it.getPrice())
                            .qty(it.getQty())
                            .subtotal(it.getSubtotal())
                            .extraAmount(it.getExtraAmount())
                            .pic(pic)
                            .build();
                }).collect(Collectors.toList());
        // 前端数字 status：0已取消/1待付款/2待发货/3待收货/4待评价/5已完成/6退款中
        Integer statusCode = toStatusCode(o.getStatus());
        return MallOrderVO.builder()
                .id(o.getId())
                .orderNo(o.getOrderNo())
                .status(o.getStatus())
                .statusCode(statusCode)
                .totalAmount(o.getTotalAmount())
                .payAmount(o.getPayAmount())
                .address(o.getAddress())
                .payMethod(o.getPayMethod())
                .remark(o.getRemark())
                .pointsDeducted(o.getPointsDeducted())
                .pointsAmount(o.getPointsAmount())
                .createTime(o.getCreateTime() == null ? null : o.getCreateTime().toString())
                .items(ivos)
                .build();
    }

    /** 后端字符串状态 → 前端数字状态。 */
    static Integer toStatusCode(String s) {
        OrderStatus status = OrderStatus.fromBackendValue(s);
        return status == null ? null : status.getFrontendCode();
    }

    private static boolean isDeletable(String status) {
        return OrderStatus.COMPLETED.getBackendValue().equals(status)
                || OrderStatus.CANCELLED.getBackendValue().equals(status)
                || OrderStatus.REFUNDED.getBackendValue().equals(status);
    }

    // -------- 退款相关（供售后模块调用） --------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRefunding(Long orderId) {
        mallOrderMapper.updateStatus(orderId, OrderStatus.REFUNDING.getBackendValue());
        log.info("[OrderService] 订单 {} 售后申请已提交，状态标记为退款中", orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeRefund(Long orderId) {
        MallOrder order = mallOrderMapper.getById(orderId);
        if (order == null) {
            throw new BaseException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 允许 COMPLETED/PAID/SHIPPED 状态的订单完成退款
        if (OrderStatus.CANCELLED.getBackendValue().equals(order.getStatus())) {
            throw new BaseException(MessageConstant.ORDER_STATUS_ERROR
                    + "（当前状态=" + order.getStatus() + "，已取消的订单不可退款）");
        }

        // 回滚库存
        List<MallOrderItem> items = mallOrderItemMapper.listByOrderId(orderId);
        if (items != null) {
            for (MallOrderItem it : items) {
                skuStockService.rollback(it.getSkuId(),
                        it.getQty() == null ? 0 : it.getQty());
            }
        }

        mallOrderMapper.markRefunded(orderId);
        log.info("[OrderService] 订单 {} 退款完成，库存已回滚，已标记退款", orderId);
    }

    /** 内部行对象：一次 for 循环的计算结果传给后续步骤，避免重复查询。 */
    private static class OrderLine {
        final Cart cart;
        final Sku sku;
        final Spu spu;
        final int qty;
        final BigDecimal price;
        final BigDecimal subtotal;
        final BigDecimal extraAmount;
        OrderLine(Cart cart, Sku sku, Spu spu, int qty, BigDecimal price, BigDecimal subtotal, BigDecimal extraAmount) {
            this.cart = cart; this.sku = sku; this.spu = spu;
            this.qty = qty; this.price = price; this.subtotal = subtotal;
            this.extraAmount = extraAmount == null ? BigDecimal.ZERO : extraAmount;
        }
    }

    // ====================== 订单自动过期 ======================

    /** 订单过期时间（分钟） */
    private static final int ORDER_EXPIRE_MINUTES = 15;

    @Override
    public int cancelExpiredOrders(int limit) {
        java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusMinutes(ORDER_EXPIRE_MINUTES);
        List<MallOrder> expiredList = mallOrderMapper.listExpiredPending(cutoff);
        if (expiredList.isEmpty()) return 0;

        int cancelled = 0;
        for (MallOrder order : expiredList) {
            if (cancelled >= limit) break;
            try {
                cancelOrderInternal(order);
                cancelled++;
            } catch (Exception e) {
                log.error("[OrderService] 自动取消过期订单失败: orderId={}, orderNo={}", order.getId(), order.getOrderNo(), e);
            }
        }
        log.info("[OrderService] 自动取消了 {} 笔过期订单（共扫描到 {} 笔）", cancelled, expiredList.size());
        return cancelled;
    }

    /**
     * 内部取消订单：不校验用户归属，仅对被 CancelExpired 调用使用。
     * 逻辑与 cancel 一致：回滚库存 + 改状态 + 解冻积分 + 退优惠券。
     */
    private void cancelOrderInternal(MallOrder order) {
        if (!OrderStatus.PENDING.getBackendValue().equals(order.getStatus())) {
            log.info("[OrderService] 订单 {} 已非待付款状态（当前={}），跳过自动取消", order.getId(), order.getStatus());
            return;
        }

        List<MallOrderItem> items = mallOrderItemMapper.listByOrderId(order.getId());
        if (items != null) {
            for (MallOrderItem it : items) {
                skuStockService.rollback(it.getSkuId(), it.getQty() == null ? 0 : it.getQty());
            }
        }

        mallOrderMapper.updateStatus(order.getId(), OrderStatus.CANCELLED.getBackendValue());
        unfreezeOrderPointsQuietly(order.getUserId(), order.getId());
        couponService.returnCouponByOrderId(order.getId());

        log.info("[OrderService] 订单 {} 已自动过期取消（超 {} 分钟未支付）", order.getId(), ORDER_EXPIRE_MINUTES);
    }
}
