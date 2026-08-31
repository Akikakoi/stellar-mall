package com.stellar.service.impl;

import com.stellar.constant.MessageConstant;
import com.stellar.dto.AfterSaleAuditDTO;
import com.stellar.dto.AfterSaleReturnDTO;
import com.stellar.dto.AfterSaleSubmitDTO;
import com.stellar.entity.*;
import com.stellar.enumeration.AfterSaleStatus;
import com.stellar.enumeration.AfterSaleType;
import com.stellar.enumeration.OrderStatus;
import com.stellar.exception.BaseException;
import com.stellar.mapper.*;
import com.stellar.result.PageResult;
import com.stellar.service.AfterSaleService;
import com.stellar.service.CouponService;
import com.stellar.service.OrderService;
import com.stellar.service.PointsService;
import com.stellar.service.UserMessageService;
import com.stellar.service.WalletService;
import com.stellar.vo.AfterSaleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 售后处理服务实现类。
 * <p>
 * 提供售后申请的完整生命周期管理，包括：
 * <ul>
 *   <li>用户提交售后申请（仅退款 / 退货退款）</li>
 *   <li>用户取消售后、填写退货物流</li>
 *   <li>管理员审核售后申请</li>
 *   <li>管理员确认退款（含退款到钱包、退还优惠券、积分处理）</li>
 *   <li>用户和管理员分页查询售后记录</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AfterSaleServiceImpl implements AfterSaleService {

    private final AfterSaleMapper afterSaleMapper;
    private final MallOrderMapper mallOrderMapper;
    private final MallOrderItemMapper mallOrderItemMapper;
    private final SkuMapper skuMapper;
    private final SpuMapper spuMapper;
    private final OrderService orderService;
    private final CouponService couponService;
    private final UserMessageService userMessageService;
    private final WalletService walletService;
    private final PointsService pointsService;

    // -------- 用户提交售后 --------

    /**
     * 用户提交售后申请。
     * <p>
     * 校验订单归属和状态、未存在进行中的售后单、售后类型合法性，并基于订单明细
     * 计算实际退款金额（优惠券按比例分摊），创建售后单并返回。
     *
     * @param userId 用户ID
     * @param dto    售后提交参数
     * @return 创建的售后单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AfterSale submit(Long userId, AfterSaleSubmitDTO dto) {
        if (userId == null || dto == null || dto.getOrderId() == null || dto.getSkuId() == null) {
            throw new BaseException(MessageConstant.ILLEGAL_PARAMETER);
        }

        // 1) 校验订单归属 + 状态（必须已支付）
        MallOrder order = mallOrderMapper.getById(dto.getOrderId());
        if (order == null) {
            throw new BaseException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (!userId.equals(order.getUserId())) {
            throw new BaseException(MessageConstant.NO_PERMISSION);
        }
        String orderStatus = order.getStatus();
        if (OrderStatus.PENDING.getBackendValue().equals(orderStatus)
                || OrderStatus.CANCELLED.getBackendValue().equals(orderStatus)) {
            throw new BaseException(MessageConstant.AFTER_SALE_ORDER_NOT_PAID);
        }

        // 2) 校验无进行中的售后单
        int activeCount = afterSaleMapper.countActiveByOrderAndSku(dto.getOrderId(), dto.getSkuId());
        if (activeCount > 0) {
            throw new BaseException(MessageConstant.AFTER_SALE_ALREADY_EXISTS);
        }

        // 3) 校验售后类型
        AfterSaleType type = AfterSaleType.fromCode(dto.getType());
        if (type == null) {
            throw new BaseException(MessageConstant.ILLEGAL_PARAMETER + "：售后类型无效");
        }

        // 4) 从订单明细中获取商品价格和数量，计算实际退款金额（不允许用户自定义）
        List<MallOrderItem> orderItems = mallOrderItemMapper.listByOrderId(dto.getOrderId());
        MallOrderItem targetItem = null;
        if (orderItems != null) {
            targetItem = orderItems.stream()
                    .filter(it -> dto.getSkuId().equals(it.getSkuId()))
                    .findFirst().orElse(null);
        }
        if (targetItem == null) {
            throw new BaseException(MessageConstant.ILLEGAL_PARAMETER + "：该订单中不存在此商品");
        }
        BigDecimal price = targetItem.getPrice() == null ? BigDecimal.ZERO : targetItem.getPrice();
        int qty = targetItem.getQty() == null ? 1 : targetItem.getQty();
        BigDecimal extraAmount = targetItem.getExtraAmount() == null ? BigDecimal.ZERO : targetItem.getExtraAmount();
        BigDecimal itemAmount = price.multiply(BigDecimal.valueOf(qty)).add(extraAmount);
        if (itemAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BaseException(MessageConstant.ILLEGAL_PARAMETER + "：退款金额必须大于0");
        }

        // 按实际付款比例计算退款金额（若使用了优惠券，payAmount < totalAmount）
        BigDecimal totalAmount = order.getTotalAmount() == null ? itemAmount : order.getTotalAmount();
        BigDecimal payAmount = order.getPayAmount() == null ? itemAmount : order.getPayAmount();
        BigDecimal refundAmount;
        if (totalAmount.compareTo(BigDecimal.ZERO) > 0 && payAmount.compareTo(totalAmount) < 0) {
            // 订单使用了优惠券，按比例分配优惠折扣
            refundAmount = itemAmount.multiply(payAmount).divide(totalAmount, 2, java.math.RoundingMode.HALF_UP);
        } else {
            refundAmount = itemAmount;
        }

        // 5) 创建售后单（金额由系统计算，忽略前端提交的 amount）
        AfterSale afterSale = AfterSale.builder()
                .orderId(dto.getOrderId())
                .skuId(dto.getSkuId())
                .userId(userId)
                .type(dto.getType())
                .status(AfterSaleStatus.APPLIED.getCode())
                .reason(dto.getReason() != null ? dto.getReason() : "")
                .detail(dto.getDetail())
                .amount(refundAmount)
                .images(dto.getImages())
                .build();
        afterSaleMapper.insert(afterSale);

        // 6) 订单状态保持不变（不标记为退款中，等商家实际退款后再改为已退款）
        log.info("[AfterSaleService] 用户 {} 提交售后申请，售后单 id={}, 订单 id={}, 退款金额={}", userId, afterSale.getId(), dto.getOrderId(), refundAmount);
        return afterSale;
    }

    // -------- 用户取消售后 --------

    /**
     * 用户取消售后申请。
     * <p>
     * 仅当售后状态为"申请中"或"审核中"时可取消，取消后若该订单无其他进行中
     * 的售后单则保持订单状态不变。
     *
     * @param id     售后单ID
     * @param userId 用户ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, Long userId) {
        AfterSale afterSale = requireAfterSale(id, userId);
        Integer status = afterSale.getStatus();
        if (status != AfterSaleStatus.APPLIED.getCode()
                && status != AfterSaleStatus.AUDITING.getCode()) {
            throw new BaseException(MessageConstant.AFTER_SALE_STATUS_ERROR
                    + "（当前状态仅申请中/审核中可取消）");
        }

        afterSaleMapper.updateStatus(id, AfterSaleStatus.CANCELLED.getCode());

        // 如果该订单没有其他进行中的售后单，恢复订单状态
        restoreOrderIfNoActiveAfterSales(afterSale.getOrderId());

        log.info("[AfterSaleService] 用户 {} 取消售后单 id={}", userId, id);
    }

    // -------- 用户提交退货物流 --------

    /**
     * 用户提交退货物流单号。
     * <p>
     * 仅当售后状态为"退货中"时可填写，提交后售后状态变更为"退款中"，
     * 并同步更新订单状态为退款中。
     *
     * @param userId 用户ID
     * @param dto    退货物流参数（含售后单ID和快递单号）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitReturnTracking(Long userId, AfterSaleReturnDTO dto) {
        if (dto == null || dto.getId() == null || dto.getReturnTracking() == null || dto.getReturnTracking().trim().isEmpty()) {
            throw new BaseException(MessageConstant.ILLEGAL_PARAMETER + "：快递单号不能为空");
        }

        AfterSale afterSale = requireAfterSale(dto.getId(), userId);
        if (afterSale.getStatus() != AfterSaleStatus.RETURNING.getCode()) {
            throw new BaseException(MessageConstant.AFTER_SALE_STATUS_ERROR
                    + "（当前状态仅退货中可填写物流）");
        }

        // 状态：退货中 → 退款中（商家收到退货后自动进入退款阶段）
        AfterSale update = AfterSale.builder()
                .id(dto.getId())
                .returnTracking(dto.getReturnTracking().trim())
                .status(AfterSaleStatus.REFUNDING.getCode())
                .build();
        afterSaleMapper.update(update);

        // 同步订单状态 → 退款中
        orderService.markRefunding(afterSale.getOrderId());

        log.info("[AfterSaleService] 用户 {} 提交退货物流，售后单 id={}, 单号={}", userId, dto.getId(), dto.getReturnTracking());
    }

    // -------- 用户售后列表 --------

    /**
     * 分页查询当前用户的售后记录。
     *
     * @param userId   用户ID
     * @param page     页码（从1开始）
     * @param pageSize 每页条数
     * @return 分页结果，包含售后记录VO列表
     */
    @Override
    public PageResult pageByUser(Long userId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<AfterSale> list = afterSaleMapper.listByUserId(userId, offset, pageSize);
        int total = afterSaleMapper.countByUserId(userId);
        List<AfterSaleVO> vos = toVOList(list);
        return new PageResult((long) total, vos);
    }

    // -------- 用户售后详情 --------

    /**
     * 查询指定售后单的详情（需校验归属权）。
     *
     * @param id     售后单ID
     * @param userId 用户ID
     * @return 售后详情VO
     */
    @Override
    public AfterSaleVO getDetail(Long id, Long userId) {
        AfterSale afterSale = requireAfterSale(id, userId);
        return toVO(afterSale);
    }

    // -------- 管理端分页 --------

    /**
     * 管理端分页查询所有售后记录，支持按状态和类型筛选。
     *
     * @param page     页码（从1开始）
     * @param pageSize 每页条数
     * @param status   售后状态（可选）
     * @param type     售后类型（可选）
     * @return 分页结果，包含售后记录VO列表
     */
    @Override
    public PageResult pageAll(int page, int pageSize, Integer status, Integer type) {
        int offset = (page - 1) * pageSize;
        List<AfterSale> list = afterSaleMapper.listAll(offset, pageSize, status, type);
        int total = afterSaleMapper.count(status, type);
        List<AfterSaleVO> vos = toVOList(list);
        return new PageResult((long) total, vos);
    }

    // -------- 管理端详情 --------

    /**
     * 管理端根据ID查询售后单详情（无需校验用户归属）。
     *
     * @param id 售后单ID
     * @return 售后详情VO
     */
    @Override
    public AfterSaleVO getDetailById(Long id) {
        AfterSale afterSale = afterSaleMapper.getById(id);
        if (afterSale == null) {
            throw new BaseException(MessageConstant.AFTER_SALE_NOT_FOUND);
        }
        return toVO(afterSale);
    }

    // -------- 管理端审核 --------

    /**
     * 管理员审核售后申请。
     * <p>
     * 审核通过时：仅退款 → 退款中，退货退款 → 退货中，并发送通知给用户；
     * 审核拒绝时：售后单状态变更为已拒绝，发送通知给用户。
     *
     * @param empId 审核员工ID
     * @param dto   审核参数（含售后单ID、是否通过、备注）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long empId, AfterSaleAuditDTO dto) {
        if (dto == null || dto.getId() == null || dto.getApproved() == null) {
            throw new BaseException(MessageConstant.ILLEGAL_PARAMETER);
        }

        AfterSale afterSale = afterSaleMapper.getById(dto.getId());
        if (afterSale == null) {
            throw new BaseException(MessageConstant.AFTER_SALE_NOT_FOUND);
        }
        if (afterSale.getStatus() != AfterSaleStatus.APPLIED.getCode()) {
            throw new BaseException(MessageConstant.AFTER_SALE_STATUS_ERROR
                    + "（当前状态仅申请中可审核）");
        }

        if (Boolean.TRUE.equals(dto.getApproved())) {
            // 审核通过
            Integer type = afterSale.getType();
            Integer nextStatus;
            if (AfterSaleType.REFUND_ONLY.getCode() == type) {
                // 仅退款 → 直接进入退款中
                nextStatus = AfterSaleStatus.REFUNDING.getCode();
            } else if (AfterSaleType.RETURN_REFUND.getCode() == type) {
                // 退货退款 → 先进入用户退货中
                nextStatus = AfterSaleStatus.RETURNING.getCode();
            } else {
                // 换货暂不支持
                nextStatus = AfterSaleStatus.COMPLETED.getCode();
            }

            AfterSale update = AfterSale.builder()
                    .id(dto.getId())
                    .status(nextStatus)
                    .auditUserId(empId)
                    .auditRemark(dto.getRemark())
                    .auditTime(LocalDateTime.now())
                    .build();
            afterSaleMapper.update(update);

            // 审核通过 → 退款中时，同步订单状态
            if (nextStatus == AfterSaleStatus.REFUNDING.getCode()) {
                orderService.markRefunding(afterSale.getOrderId());
            }

            // 发送消息通知用户
            MallOrder order = mallOrderMapper.getById(afterSale.getOrderId());
            if (order != null) {
                String content = "您的售后申请（售后单号 AS" + afterSale.getId() + "）已审核通过";
                if (nextStatus == AfterSaleStatus.RETURNING.getCode()) {
                    content += "，请尽快寄回商品并填写退货物流单号。";
                } else if (nextStatus == AfterSaleStatus.REFUNDING.getCode()) {
                    content += "，退款将尽快处理。";
                }
                userMessageService.createMessage(afterSale.getUserId(), "AFTER_SALE_APPROVED",
                        "售后审核通过", content, afterSale.getOrderId());
            }

            log.info("[AfterSaleService] 员工 {} 审核通过售后单 id={}", empId, dto.getId());
        } else {
            // 审核拒绝
            AfterSale update = AfterSale.builder()
                    .id(dto.getId())
                    .status(AfterSaleStatus.REJECTED.getCode())
                    .auditUserId(empId)
                    .auditRemark(dto.getRemark())
                    .auditTime(LocalDateTime.now())
                    .build();
            afterSaleMapper.update(update);

            // 恢复订单状态
            restoreOrderIfNoActiveAfterSales(afterSale.getOrderId());

            // 发送消息通知用户
            MallOrder order = mallOrderMapper.getById(afterSale.getOrderId());
            if (order != null) {
                String content = "您的售后申请（售后单号 AS" + afterSale.getId() + "）已被拒绝";
                if (dto.getRemark() != null && !dto.getRemark().isEmpty()) {
                    content += "，原因：" + dto.getRemark();
                }
                userMessageService.createMessage(afterSale.getUserId(), "AFTER_SALE_REJECTED",
                        "售后申请被拒绝", content, afterSale.getOrderId());
            }

            log.info("[AfterSaleService] 员工 {} 拒绝售后单 id={}，原因：{}", empId, dto.getId(), dto.getRemark());
        }
    }

    // -------- 管理端确认退款 --------

    /**
     * 管理员确认退款。
     * <p>
     * 将售后单状态变更为已完成，订单状态变更为已退款并回滚库存，同时执行：
     * 退款到钱包、退还优惠券、按比例退还积分，并发送通知给用户。
     *
     * @param empId 操作员工ID
     * @param id    售后单ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmRefund(Long empId, Long id) {
        AfterSale afterSale = afterSaleMapper.getById(id);
        if (afterSale == null) {
            throw new BaseException(MessageConstant.AFTER_SALE_NOT_FOUND);
        }
        if (afterSale.getStatus() != AfterSaleStatus.REFUNDING.getCode()) {
            throw new BaseException(MessageConstant.AFTER_SALE_STATUS_ERROR
                    + "（当前状态仅退款中可确认退款）");
        }

        log.info("[AfterSaleService] 开始确认退款: id={}, orderId={}, amount={}", id, afterSale.getOrderId(), afterSale.getAmount());

        // 售后单 → 完成
        AfterSale update = AfterSale.builder()
                .id(id)
                .status(AfterSaleStatus.COMPLETED.getCode())
                .refundTime(LocalDateTime.now())
                .build();
        afterSaleMapper.update(update);
        log.info("[AfterSaleService] 售后单已更新为完成: id={}", id);

        // 订单 → REFUNDED 并回滚库存
        orderService.completeRefund(afterSale.getOrderId());
        log.info("[AfterSaleService] 订单退款完成: orderId={}", afterSale.getOrderId());

        // 退款到钱包
        walletService.refundToWallet(afterSale.getUserId(), afterSale.getOrderId(), afterSale.getAmount());
        log.info("[AfterSaleService] 钱包退款完成: userId={}, amount={}", afterSale.getUserId(), afterSale.getAmount());

        // 退还优惠券
        couponService.returnCouponByOrderId(afterSale.getOrderId());

        // 按比例退还积分
        refundPointsQuietly(afterSale);

        // 发送消息通知用户
        MallOrder order = mallOrderMapper.getById(afterSale.getOrderId());
        if (order != null) {
            String content = "您的售后申请（售后单号 AS" + afterSale.getId() + "）退款已完成";
            if (afterSale.getAmount() != null) {
                content += "，退款金额 ¥" + afterSale.getAmount().setScale(2).toPlainString();
            }
            userMessageService.createMessage(afterSale.getUserId(), "AFTER_SALE_COMPLETED",
                    "退款已完成", content, afterSale.getOrderId());
        }

        log.info("[AfterSaleService] 员工 {} 确认退款完成，售后单 id={}", empId, id);
    }

    // -------- 根据订单ID查询售后单 --------

    /**
     * 根据订单ID查询用户的售后单详情。
     * <p>
     * 校验订单归属权后再查询，若无售后记录则返回 null。
     *
     * @param orderId 订单ID
     * @param userId  用户ID
     * @return 售后详情VO，若无售后记录返回 null
     */
    @Override
    public AfterSaleVO getByOrderId(Long orderId, Long userId) {
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
        AfterSale afterSale = afterSaleMapper.getByOrderIdAndUserId(orderId, userId);
        return afterSale == null ? null : toVO(afterSale);
    }

    // ================= 内部工具 =================

    private AfterSale requireAfterSale(Long id, Long userId) {
        if (id == null || userId == null) {
            throw new BaseException(MessageConstant.ILLEGAL_PARAMETER);
        }
        AfterSale afterSale = afterSaleMapper.getById(id);
        if (afterSale == null) {
            throw new BaseException(MessageConstant.AFTER_SALE_NOT_FOUND);
        }
        if (!userId.equals(afterSale.getUserId())) {
            throw new BaseException(MessageConstant.NO_PERMISSION);
        }
        return afterSale;
    }

    /**
     * 如果订单没有其他进行中的售后单，则恢复订单原状态。
     */
    private void restoreOrderIfNoActiveAfterSales(Long orderId) {
        // 取消/拒绝售后不再恢复订单状态（订单保持原有 COMPLETED/PAID/SHIPPED 不变）
        log.info("[AfterSaleService] 订单 {} 售后单状态变更，订单状态保持不变", orderId);
    }

    private List<AfterSaleVO> toVOList(List<AfterSale> list) {
        if (list == null || list.isEmpty()) return Collections.emptyList();

        // 批量查询关联的订单、SKU、SPU
        Set<Long> orderIds = list.stream().map(AfterSale::getOrderId).collect(Collectors.toSet());
        Set<Long> skuIds = list.stream().map(AfterSale::getSkuId).collect(Collectors.toSet());

        Map<Long, MallOrder> orderMap = loadOrders(orderIds);
        Map<Long, Sku> skuMap = loadSkus(skuIds);

        // 批量查订单明细（购买数量 + 下单时的商品快照）
        Map<Long, List<MallOrderItem>> itemsMap = loadOrderItems(orderIds);

        // SPU 优先经 SKU 反查；SKU 已被删除时，用订单明细快照里的 spuId 兜底加载
        Set<Long> spuIds = skuMap.values().stream().map(Sku::getSpuId).collect(Collectors.toSet());
        itemsMap.values().stream().flatMap(List::stream)
                .filter(it -> it.getSpuId() != null
                        && it.getSkuId() != null
                        && skuIds.contains(it.getSkuId())
                        && !skuMap.containsKey(it.getSkuId()))
                .forEach(it -> spuIds.add(it.getSpuId()));
        Map<Long, Spu> spuMap = loadSpus(spuIds);

        List<AfterSaleVO> vos = new ArrayList<>(list.size());
        for (AfterSale a : list) {
            vos.add(buildVO(a, orderMap, skuMap, spuMap, itemsMap));
        }
        return vos;
    }

    private AfterSaleVO toVO(AfterSale a) {
        if (a == null) return null;
        List<AfterSaleVO> vos = toVOList(Collections.singletonList(a));
        return vos.isEmpty() ? null : vos.get(0);
    }

    private AfterSaleVO buildVO(AfterSale a, Map<Long, MallOrder> orderMap,
                                 Map<Long, Sku> skuMap, Map<Long, Spu> spuMap,
                                 Map<Long, List<MallOrderItem>> itemsMap) {
        MallOrder order = orderMap.get(a.getOrderId());
        Sku sku = skuMap.get(a.getSkuId());

        // 匹配售后单对应的订单明细（含下单时的商品名/规格/spuId 快照，SKU 被删后兜底）
        MallOrderItem matchedItem = null;
        List<MallOrderItem> items = itemsMap.get(a.getOrderId());
        if (items != null) {
            for (MallOrderItem item : items) {
                if (item.getSkuId() != null && item.getSkuId().equals(a.getSkuId())) {
                    matchedItem = item;
                    break;
                }
            }
        }

        // SPU 优先经 SKU 反查，SKU 已删除时退回订单明细快照里的 spuId
        Long spuId = sku != null && sku.getSpuId() != null ? sku.getSpuId()
                : (matchedItem != null ? matchedItem.getSpuId() : null);
        Spu spu = spuId != null ? spuMap.get(spuId) : null;

        // 购买数量：优先订单明细，缺省 1
        int qty = matchedItem != null && matchedItem.getQty() != null ? matchedItem.getQty() : 1;

        AfterSaleType type = AfterSaleType.fromCode(a.getType());
        AfterSaleStatus status = AfterSaleStatus.fromCode(a.getStatus());

        return AfterSaleVO.builder()
                .id(a.getId())
                .orderId(a.getOrderId())
                .orderNo(order != null ? order.getOrderNo() : null)
                .skuId(a.getSkuId())
                .skuSpecs(sku != null ? sku.getSpecs()
                        : (matchedItem != null ? matchedItem.getSkuSpecs() : null))
                .spuId(spu != null ? spu.getId() : spuId)
                .spuName(spu != null ? spu.getName()
                        : (matchedItem != null ? matchedItem.getSpuName()
                        : (sku != null ? sku.getName() : null)))
                .spuImage(spu != null ? spu.getMainImage() : null)
                .qty(qty)
                .userId(a.getUserId())
                .type(a.getType())
                .typeText(type != null ? type.getDescription() : null)
                .status(a.getStatus())
                .statusText(status != null ? status.getDescription() : null)
                .reason(a.getReason())
                .detail(a.getDetail())
                .amount(a.getAmount())
                .images(a.getImages())
                .auditRemark(a.getAuditRemark())
                .auditTime(a.getAuditTime() != null ? a.getAuditTime().toString() : null)
                .returnTracking(a.getReturnTracking())
                .refundTime(a.getRefundTime() != null ? a.getRefundTime().toString() : null)
                .createTime(a.getCreateTime() != null ? a.getCreateTime().toString() : null)
                .build();
    }

    private Map<Long, MallOrder> loadOrders(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyMap();
        List<MallOrder> orders = mallOrderMapper.listByIds(new ArrayList<>(ids));
        if (orders == null) return Collections.emptyMap();
        return orders.stream().collect(Collectors.toMap(MallOrder::getId, o -> o, (a, b) -> a));
    }

    private Map<Long, Sku> loadSkus(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyMap();
        List<Sku> skus = skuMapper.listByIds(new ArrayList<>(ids));
        if (skus == null) return Collections.emptyMap();
        return skus.stream().collect(Collectors.toMap(Sku::getId, s -> s, (a, b) -> a));
    }

    private Map<Long, Spu> loadSpus(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyMap();
        List<Spu> spus = spuMapper.listByIds(new ArrayList<>(ids));
        if (spus == null) return Collections.emptyMap();
        return spus.stream().collect(Collectors.toMap(Spu::getId, s -> s, (a, b) -> a));
    }

    private Map<Long, List<MallOrderItem>> loadOrderItems(Set<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) return Collections.emptyMap();
        List<MallOrderItem> items = mallOrderItemMapper.listByOrderIds(new ArrayList<>(orderIds));
        if (items == null) return Collections.emptyMap();
        return items.stream().collect(Collectors.groupingBy(MallOrderItem::getOrderId));
    }

    /**
     * 退款时处理积分：退还抵扣积分 + 收回奖励积分。异常不影响退款主流程。
     */
    private void refundPointsQuietly(AfterSale afterSale) {
        // 1) 收回订单赠送的奖励积分
        try {
            pointsService.reclaimOrderEarnPoints(afterSale.getUserId(), afterSale.getOrderId());
        } catch (Exception e) {
            log.error("[AfterSaleService] 收回奖励积分失败（退款不受影响）: orderId={}", afterSale.getOrderId(), e);
        }

        // 2) 退还抵扣积分
        try {
            MallOrder order = mallOrderMapper.getById(afterSale.getOrderId());
            if (order == null) return;
            int pointsDeducted = order.getPointsDeducted() != null ? order.getPointsDeducted() : 0;
            if (pointsDeducted <= 0) return;

            // 计算退款比例：售后退款金额 / 订单实付金额
            BigDecimal orderPayAmount = order.getPayAmount() != null ? order.getPayAmount() : BigDecimal.ZERO;
            // 订单实付需要还原为积分抵扣前的金额（payAmount + pointsAmount）
            BigDecimal pointsAmount = order.getPointsAmount() != null ? order.getPointsAmount() : BigDecimal.ZERO;
            BigDecimal actualPayBeforePoints = orderPayAmount.add(pointsAmount);
            if (actualPayBeforePoints.compareTo(BigDecimal.ZERO) <= 0) return;

            BigDecimal refundAmount = afterSale.getAmount() != null ? afterSale.getAmount() : BigDecimal.ZERO;
            BigDecimal refundRatio = refundAmount.divide(actualPayBeforePoints, 4, java.math.RoundingMode.HALF_UP);
            if (refundRatio.compareTo(BigDecimal.ONE) > 0) {
                refundRatio = BigDecimal.ONE;
            }

            pointsService.refundPointsForOrder(afterSale.getUserId(), afterSale.getOrderId(), refundRatio);
        } catch (Exception e) {
            log.error("[AfterSaleService] 积分退还失败（退款不受影响）: orderId={}, userId={}",
                    afterSale.getOrderId(), afterSale.getUserId(), e);
        }
    }
}
