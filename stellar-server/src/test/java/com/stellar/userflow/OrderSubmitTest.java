package com.stellar.userflow;

import com.stellar.dto.OrderSubmitDTO;
import com.stellar.entity.*;
import com.stellar.enumeration.OrderStatus;
import com.stellar.exception.BaseException;
import com.stellar.exception.StockInsufficientException;
import com.stellar.mapper.*;
import com.stellar.service.SkuStockService;
import com.stellar.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * M2-J-G4 RED(b): 纯 Mockito 测试 OrderService.submit 流程。
 * - 场景 1：某 SKU 扣库存版本冲突 → 整体抛异常，且不写 Order / OrderItem
 * - 场景 2：所有 SKU 扣减成功 → 写 Order + 每条 OrderItem，每条 SKU 的 deduct 都执行
 */
@ExtendWith(MockitoExtension.class)
class OrderSubmitTest {

    @Mock
    private CartMapper cartMapper;
    @Mock
    private SkuMapper skuMapper;
    @Mock
    private SpuMapper spuMapper;
    @Mock
    private SkuStockService skuStockService;
    @Mock
    private MallOrderMapper mallOrderMapper;
    @Mock
    private MallOrderItemMapper mallOrderItemMapper;

    @InjectMocks
    private OrderServiceImpl orderService;

    private static final Long USER_ID = 8888L;

    private Sku buildSku(Long id, Long spuId, String name, String specs, BigDecimal price) {
        return Sku.builder()
                .id(id).spuId(spuId).name(name).specs(specs)
                .price(price).stock(100).version(0).status(1)
                .build();
    }

    private Spu buildSpu(Long id, String name) {
        return Spu.builder().id(id).name(name).mainImage("https://img.example.com/" + id + ".jpg").status(1).build();
    }

    private Cart buildCartChecked(Long skuId, Long spuId, int qty) {
        Cart c = new Cart();
        c.setId(skuId * 7 + 31);
        c.setUserId(USER_ID);
        c.setSkuId(skuId);
        c.setSpuId(spuId);
        c.setQty(qty);
        c.setChecked(1);
        return c;
    }

    private OrderSubmitDTO buildSubmitDto(String address, Integer payMethod, String remark) {
        OrderSubmitDTO dto = new OrderSubmitDTO();
        dto.setAddress(address);
        dto.setPayMethod(payMethod);
        dto.setRemark(remark);
        return dto;
    }

    // ========== 提交成功 ==========

    @Test
    void submitOrder_allSkusDeductSuccess_persistsOrderAndItems() {
        // 准备：购物车 2 条 checked
        Cart c1 = buildCartChecked(10L, 1L, 2);
        Cart c2 = buildCartChecked(20L, 2L, 1);
        List<Cart> carts = Arrays.asList(c1, c2);
        when(cartMapper.listCheckedByUserId(USER_ID)).thenReturn(carts);

        Sku s1 = buildSku(10L, 1L, "SPU1-SKU红", "颜色:红", BigDecimal.valueOf(100));
        Sku s2 = buildSku(20L, 2L, "SPU2-SKU蓝", "颜色:蓝", BigDecimal.valueOf(200));
        when(skuMapper.listByIds(anyList())).thenReturn(Arrays.asList(s1, s2));

        Spu spu1 = buildSpu(1L, "商品1");
        Spu spu2 = buildSpu(2L, "商品2");
        when(spuMapper.listByIds(anyList())).thenReturn(Arrays.asList(spu1, spu2));

        // 扣库存都成功
        doNothing().when(skuStockService).deduct(10L, 2);
        doNothing().when(skuStockService).deduct(20L, 1);

        // 订单插入
        when(mallOrderMapper.insert(any(MallOrder.class))).thenAnswer(inv -> {
            MallOrder mo = inv.getArgument(0);
            mo.setId(1000L);
            return 1;
        });
        when(mallOrderItemMapper.insertBatch(anyList())).thenReturn(2);
        when(cartMapper.deleteByIds(anyList())).thenReturn(2);

        // 执行
        OrderSubmitDTO dto = new OrderSubmitDTO();
        dto.setAddress("测试地址");
        dto.setPayMethod(1);
        dto.setRemark("test remark");
        MallOrder result = orderService.submit(USER_ID, dto);

        // 断言订单
        assertNotNull(result);
        assertEquals(Long.valueOf(1000L), result.getId());
        assertEquals(USER_ID, result.getUserId());
        assertEquals(OrderStatus.PENDING.getBackendValue(), result.getStatus());
        // 总金额：100*2 + 200*1 = 400
        assertEquals(0, result.getTotalAmount().compareTo(BigDecimal.valueOf(400)));
        assertEquals(0, result.getPayAmount().compareTo(BigDecimal.valueOf(400)));

        // 每条 SKU 的 deduct 都执行一次
        verify(skuStockService, times(1)).deduct(10L, 2);
        verify(skuStockService, times(1)).deduct(20L, 1);

        // Order + 批量写入 2 条 OrderItem（一次 insertBatch）
        verify(mallOrderMapper, times(1)).insert(any(MallOrder.class));
        ArgumentCaptor<List<MallOrderItem>> itemCaptor = ArgumentCaptor.forClass(List.class);
        verify(mallOrderItemMapper, times(1)).insertBatch(itemCaptor.capture());
        List<MallOrderItem> captured = itemCaptor.getValue();
        assertEquals(2, captured.size(), "批量写入的明细应为 2 条");

        // 第 1 条明细断言（skuId=10）
        MallOrderItem cap1 = captured.stream()
                .filter(it -> it.getSkuId().equals(10L)).findFirst()
                .orElseThrow(() -> new AssertionError("未找到 skuId=10 的明细"));
        assertEquals(1L, cap1.getSpuId());
        assertEquals(2, cap1.getQty());
        assertEquals(0, cap1.getPrice().compareTo(BigDecimal.valueOf(100)));
        assertEquals(0, cap1.getSubtotal().compareTo(BigDecimal.valueOf(200)));
        assertEquals("商品1", cap1.getSpuName());
        assertEquals("颜色:红", cap1.getSkuSpecs());

        // 第 2 条明细断言（skuId=20）
        MallOrderItem cap2 = captured.stream()
                .filter(it -> it.getSkuId().equals(20L)).findFirst()
                .orElseThrow(() -> new AssertionError("未找到 skuId=20 的明细"));
        assertEquals(2L, cap2.getSpuId());
        assertEquals(1, cap2.getQty());
        assertEquals(0, cap2.getPrice().compareTo(BigDecimal.valueOf(200)));
        assertEquals(0, cap2.getSubtotal().compareTo(BigDecimal.valueOf(200)));

        // 已下单的购物车项被清理
        verify(cartMapper, times(1)).deleteByIds(anyList());
    }

    // ========== 冲突：第二个 SKU 抛异常，整体不写 Order ==========

    @Test
    void submitOrder_whenSecondSkuDeductFails_doesNotInsertOrder_andExceptionPropagates() {
        Cart c1 = buildCartChecked(10L, 1L, 1);
        Cart c2 = buildCartChecked(20L, 2L, 1);
        when(cartMapper.listCheckedByUserId(USER_ID)).thenReturn(Arrays.asList(c1, c2));

        Sku s1 = buildSku(10L, 1L, "S1", "默认", BigDecimal.valueOf(100));
        Sku s2 = buildSku(20L, 2L, "S2", "默认", BigDecimal.valueOf(50));
        when(skuMapper.listByIds(anyList())).thenReturn(Arrays.asList(s1, s2));

        Spu spu1 = buildSpu(1L, "SPU1");
        Spu spu2 = buildSpu(2L, "SPU2");
        when(spuMapper.listByIds(anyList())).thenReturn(Arrays.asList(spu1, spu2));

        // 第一个成功，第二个版本冲突最终失败
        doNothing().when(skuStockService).deduct(10L, 1);
        doThrow(new StockInsufficientException("并发冲突"))
                .when(skuStockService).deduct(20L, 1);

        // 执行：必须抛异常
        assertThrows(StockInsufficientException.class,
                () -> orderService.submit(USER_ID, buildSubmitDto("地址", 1, null)),
                "第二个 SKU 扣库存失败必须整体抛异常");

        // 不允许写入任何 Order / OrderItem
        verify(mallOrderMapper, never()).insert(any(MallOrder.class));
        verify(mallOrderItemMapper, never()).insertBatch(anyList());
        verify(cartMapper, never()).deleteByIds(anyList());

        // 但两个 deduct 都被调用过（SkuStockService 自己内部的 rollback
        // 不是业务关心的事，这里只验证调用链）
        verify(skuStockService, times(1)).deduct(10L, 1);
        verify(skuStockService, times(1)).deduct(20L, 1);
    }

    // ========== 购物车为空 ==========

    @Test
    void submitOrder_whenCartEmpty_throwsBaseException() {
        when(cartMapper.listCheckedByUserId(USER_ID)).thenReturn(Collections.emptyList());

        assertThrows(BaseException.class,
                () -> orderService.submit(USER_ID, buildSubmitDto("地址", 1, null)),
                "购物车为空时必须抛 BaseException（对应 SHOPPING_CART_IS_NULL）");

        verify(skuStockService, never()).deduct(anyLong(), anyInt());
        verify(mallOrderMapper, never()).insert(any());
    }

    // ========== 直接购买：clearCart=false 时不清理购物车 ==========

    @Test
    void submitDirect_withClearCartFalse_doesNotDeleteCart() {
        OrderSubmitDTO.OrderItemDTO item = new OrderSubmitDTO.OrderItemDTO();
        item.setSkuId(10L);
        item.setQuantity(1);
        item.setPrice(BigDecimal.valueOf(100));

        OrderSubmitDTO dto = buildSubmitDto("地址", 1, null);
        dto.setItems(Collections.singletonList(item));
        dto.setClearCart(false); // 立即购买，不清空购物车

        Sku s1 = buildSku(10L, 1L, "S1", "默认", BigDecimal.valueOf(100));
        when(skuMapper.listByIds(anyList())).thenReturn(Collections.singletonList(s1));

        Spu spu1 = buildSpu(1L, "SPU1");
        when(spuMapper.listByIds(anyList())).thenReturn(Collections.singletonList(spu1));

        doNothing().when(skuStockService).deduct(10L, 1);

        when(mallOrderMapper.insert(any(MallOrder.class))).thenAnswer(inv -> {
            MallOrder mo = inv.getArgument(0);
            mo.setId(1000L);
            return 1;
        });
        when(mallOrderItemMapper.insertBatch(anyList())).thenReturn(1);

        MallOrder result = orderService.submitDirect(USER_ID, dto);

        assertNotNull(result);
        // 订单正常写入
        verify(mallOrderMapper, times(1)).insert(any(MallOrder.class));
        verify(mallOrderItemMapper, times(1)).insertBatch(anyList());
        // 没有查询也没有删除购物车
        verify(cartMapper, never()).listCheckedByUserId(anyLong());
        verify(cartMapper, never()).deleteByIds(anyList());
    }

    // ========== 某 SKU 已下架 ==========

    @Test
    void submitOrder_whenSkuOffShelf_throwsBaseException() {
        Cart c = buildCartChecked(30L, 3L, 1);
        when(cartMapper.listCheckedByUserId(USER_ID)).thenReturn(Collections.singletonList(c));

        Sku s = buildSku(30L, 3L, "下架SKU", "默认", BigDecimal.ONE);
        s.setStatus(0); // 停售
        when(skuMapper.listByIds(anyList())).thenReturn(Collections.singletonList(s));

        assertThrows(BaseException.class,
                () -> orderService.submit(USER_ID, buildSubmitDto("地址", 1, null)));

        verify(skuStockService, never()).deduct(anyLong(), anyInt());
        verify(mallOrderMapper, never()).insert(any());
    }
}
