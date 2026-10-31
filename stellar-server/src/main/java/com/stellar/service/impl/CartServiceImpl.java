package com.stellar.service.impl;

import com.stellar.constant.MessageConstant;
import com.stellar.dto.CartAddDTO;
import com.stellar.dto.CartUpdateDTO;
import com.stellar.entity.Cart;
import com.stellar.entity.Sku;
import com.stellar.entity.Spu;
import com.stellar.exception.BaseException;
import com.stellar.mapper.CartMapper;
import com.stellar.mapper.MallOrderItemMapper;
import com.stellar.mapper.SkuMapper;
import com.stellar.mapper.SpuMapper;
import com.stellar.mapper.SpuServiceMapper;
import com.stellar.service.CartService;
import com.stellar.vo.CartVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import java.math.BigDecimal;
import java.util.stream.Collectors;

/**
 * 购物车服务实现类，提供购物车的添加、查询、更新、删除和清空功能。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartMapper cartMapper;
    private final SkuMapper skuMapper;
    private final SpuMapper spuMapper;
    private final MallOrderItemMapper mallOrderItemMapper;
    private final SpuServiceMapper spuServiceMapper;

    /** serviceInfo JSON 快照的序列化/反序列化（结构简单，线程安全复用） */
    private static final com.fasterxml.jackson.databind.ObjectMapper SERVICE_INFO_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * 添加商品到购物车。若已存在相同 SKU 则累加数量，否则新增记录。
     *
     * @param userId 用户ID
     * @param dto    添加购物车请求参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(Long userId, CartAddDTO dto) {
        if (dto == null || dto.getSkuId() == null) {
            throw new BaseException(MessageConstant.ILLEGAL_PARAMETER);
        }
        Long uid = userId != null ? userId : 0L;
        int qty = dto.getQty() == null || dto.getQty() < 1 ? 1 : dto.getQty();

        Sku sku = skuMapper.getById(dto.getSkuId());
        if (sku == null) throw new BaseException(MessageConstant.SKU_NOT_FOUND);
        if (sku.getStatus() == null || sku.getStatus() != 1) {
            throw new BaseException("该 SKU 已停售");
        }

        // 是否携带保障服务选择：serviceInfo 非 null 表示本次有明确的服务选择（含空数组=清空）
        boolean hasServiceSelection = dto.getServiceInfo() != null;

        // 保障服务费权威重算：以服务表价格为准（不信任前端 extraAmount），快照 JSON 用库内数据重建。
        // cart.extraAmount 为单价口径（未乘数量）；服务不存在/停用/未挂在该商品下都会被拒绝
        ServiceResolved svc = resolveServices(sku.getSpuId(), dto.getServiceInfo());
        BigDecimal extraAmount = svc.unitExtra();

        Cart existing = cartMapper.getByUserIdAndSkuId(uid, dto.getSkuId());

        // 每人限购校验（提前反馈；下单时 OrderService 仍有权威校验）
        checkPerUserLimitOnAdd(uid, sku, qty, existing == null ? 0 : (existing.getQty() == null ? 0 : existing.getQty()));
        if (existing != null) {
            Cart upd = new Cart();
            upd.setId(existing.getId());
            upd.setQty((existing.getQty() == null ? 0 : existing.getQty()) + qty);
            // 重复加购同一 SKU 时，以最近一次的保障服务选择为准（serviceInfo=null 表示未选，保留原值）
            if (hasServiceSelection) {
                upd.setExtraAmount(extraAmount);
                upd.setServiceInfo(svc.snapshotJson());
            }
            cartMapper.update(upd);
        } else {
            Cart c = new Cart();
            c.setUserId(uid);
            c.setSkuId(sku.getId());
            c.setSpuId(sku.getSpuId());
            c.setQty(qty);
            c.setChecked(1);
            if (hasServiceSelection && extraAmount.compareTo(BigDecimal.ZERO) > 0) {
                c.setExtraAmount(extraAmount);
                c.setServiceInfo(svc.snapshotJson());
            }
            cartMapper.insert(c);
        }
    }

    /** 保障服务重算结果（与 OrderServiceImpl 同口径）：unitExtra=单件合计；snapshotJson=库内数据重建的快照 */
    private record ServiceResolved(BigDecimal unitExtra, String snapshotJson) {
        static final ServiceResolved EMPTY = new ServiceResolved(BigDecimal.ZERO, null);
    }

    /**
     * 保障服务费权威重算：解析 serviceInfo JSON 的服务 code，校验"存在、启用、已挂在该 SPU 下"，
     * 单价合计取服务表权威值，快照用库内数据重建。JSON 为空/空数组返回 EMPTY。
     */
    private ServiceResolved resolveServices(Long spuId, String serviceInfoJson) {
        if (serviceInfoJson == null || serviceInfoJson.isBlank()) {
            return ServiceResolved.EMPTY;
        }
        List<Map<String, Object>> declared;
        try {
            declared = SERVICE_INFO_MAPPER.readValue(serviceInfoJson,
                    new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("[CartService] serviceInfo 解析失败: {}", serviceInfoJson, e);
            throw new BaseException("保障服务信息有误，请重新选择");
        }
        if (declared == null || declared.isEmpty()) {
            return ServiceResolved.EMPTY;
        }

        List<com.stellar.entity.ServiceItem> allowed = spuId == null
                ? List.of() : spuServiceMapper.listEnabledBySpuId(spuId);
        Map<String, com.stellar.entity.ServiceItem> byCode = allowed.stream()
                .collect(Collectors.toMap(com.stellar.entity.ServiceItem::getCode, s -> s, (a, b) -> a));

        java.math.BigDecimal unit = java.math.BigDecimal.ZERO;
        List<Map<String, Object>> snapshot = new ArrayList<>();
        for (Map<String, Object> d : declared) {
            Object idVal = d.get("id");
            String code = idVal == null ? "" : String.valueOf(idVal);
            com.stellar.entity.ServiceItem svc = byCode.get(code);
            if (svc == null) {
                String title = d.get("title") == null ? code : String.valueOf(d.get("title"));
                throw new BaseException("保障服务「" + title + "」不存在、已下架或该商品未配置，请重新选择");
            }
            if (svc.getPrice() != null) unit = unit.add(svc.getPrice());
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("id", svc.getCode());
            snap.put("title", svc.getTitle());
            snap.put("price", svc.getPrice());
            snapshot.add(snap);
        }
        try {
            String json = snapshot.isEmpty() ? null
                    : SERVICE_INFO_MAPPER.writeValueAsString(snapshot);
            return new ServiceResolved(unit, json);
        } catch (Exception e) {
            log.error("[CartService] 保障服务快照序列化失败", e);
            throw new BaseException("保障服务信息处理失败，请重试");
        }
    }

    /**
     * 加购时的人数限购提前校验（仅当 SPU 设置了 per_user_limit &gt; 0 时生效）。
     * 口径：购物车中该 SPU 已有件数（同 SPU 可能对应多个 SKU，聚合计算）+ 历史已购件数 + 本次加购件数 ≤ 限购数。
     *
     * @param uid          用户 ID
     * @param sku          本次加购的 SKU
     * @param qty          本次加购件数
     * @param existingQty  购物车中该 SKU 已有件数
     */
    private void checkPerUserLimitOnAdd(Long uid, Sku sku, int qty, int existingQty) {
        if (sku.getSpuId() == null) return;
        Spu spu = spuMapper.getById(sku.getSpuId());
        if (spu == null) return;
        int limit = spu.getPerUserLimit() == null ? 0 : spu.getPerUserLimit();
        if (limit <= 0) return;

        // 购物车中同 SPU 其他 SKU 的件数（排除本次 SKU，其件数由 existingQty 表示）
        int otherSkuQty = 0;
        List<CartVO> carts = list(uid);
        for (CartVO c : carts) {
            if (spu.getId().equals(c.getSpuId()) && !sku.getId().equals(c.getSkuId())) {
                otherSkuQty += c.getQty() == null ? 0 : c.getQty();
            }
        }

        int purchased = mallOrderItemMapper.sumQtyByUserAndSpu(uid, spu.getId());
        int total = purchased + otherSkuQty + existingQty + qty;
        if (total > limit) {
            int canBuy = Math.max(limit - purchased - otherSkuQty - existingQty, 0);
            throw new BaseException("商品「" + spu.getName() + "」每人限购 " + limit + " 件"
                    + (canBuy > 0 ? "，本次最多可加购 " + canBuy + " 件" : "，已达限购上限"));
        }
    }

    /**
     * 查询用户购物车列表，包含 SKU 和 SPU 详细信息。
     *
     * @param userId 用户ID
     * @return 购物车商品列表
     */
    @Override
    public List<CartVO> list(Long userId) {
        Long uid = userId != null ? userId : 0L;
        List<Cart> carts = cartMapper.listByUserId(uid);
        if (carts == null || carts.isEmpty()) return Collections.emptyList();

        List<Long> skuIds = carts.stream().map(Cart::getSkuId).distinct().collect(Collectors.toList());
        List<Long> spuIds = carts.stream().map(Cart::getSpuId).filter(x -> x != null).distinct().collect(Collectors.toList());

        // 一次 SELECT IN 取代循环单条查询，避免 N+1
        List<Sku> skus = skuIds.isEmpty() ? Collections.emptyList() : skuMapper.listByIds(skuIds);
        if (skus == null) skus = Collections.emptyList();
        Map<Long, Sku> skuMap = skus.stream()
                .filter(s -> s != null && s.getId() != null)
                .collect(Collectors.toMap(Sku::getId, s -> s, (a, b) -> a));

        List<Spu> spus = spuIds.isEmpty() ? Collections.emptyList() : spuMapper.listByIds(spuIds);
        if (spus == null) spus = Collections.emptyList();
        Map<Long, Spu> spuMap = spus.stream()
                .filter(p -> p != null && p.getId() != null)
                .collect(Collectors.toMap(Spu::getId, p -> p, (a, b) -> a));

        List<CartVO> res = new ArrayList<>(carts.size());
        for (Cart c : carts) {
            Sku s = skuMap.get(c.getSkuId());
            Spu p = c.getSpuId() == null ? null : spuMap.get(c.getSpuId());
            CartVO vo = CartVO.builder()
                    .id(c.getId())
                    .skuId(c.getSkuId())
                    .spuId(c.getSpuId())
                    .qty(c.getQty())
                    .checked(c.getChecked())
                    .spuName(p == null ? null : p.getName())
                    .spuImage(p == null ? null : p.getMainImage())
                    .skuName(s == null ? null : s.getName())
                    .skuSpecs(s == null ? null : s.getSpecs())
                    .skuPrice(s == null ? null : s.getPrice())
                    .skuImage(s == null ? null : s.getImage())
                    .extraAmount(c.getExtraAmount())
                    .serviceInfo(c.getServiceInfo())
                    .perUserLimit(p == null ? null : p.getPerUserLimit())
                    .build();
            res.add(vo);
        }
        return res;
    }

    /**
     * 更新购物车记录，支持修改数量和选中状态。
     *
     * @param userId 用户ID
     * @param dto    更新请求参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long userId, CartUpdateDTO dto) {
        if (userId == null || dto == null || dto.getId() == null) {
            throw new BaseException(MessageConstant.ILLEGAL_PARAMETER);
        }
        Cart old = cartMapper.getById(dto.getId());
        if (old == null || !userId.equals(old.getUserId())) {
            throw new BaseException("购物车记录不存在");
        }
        Cart upd = new Cart();
        upd.setId(dto.getId());
        boolean changed = false;
        if (dto.getQty() != null) {
            if (dto.getQty() < 1) throw new BaseException(MessageConstant.ILLEGAL_PARAMETER);
            upd.setQty(dto.getQty());
            changed = true;
        }
        if (dto.getChecked() != null) {
            if (dto.getChecked() != 0 && dto.getChecked() != 1) {
                throw new BaseException(MessageConstant.ILLEGAL_PARAMETER);
            }
            upd.setChecked(dto.getChecked());
            changed = true;
        }
        if (changed) cartMapper.update(upd);
    }

    /**
     * 删除指定购物车记录，仅允许删除自己的记录。
     *
     * @param userId 用户ID
     * @param cartId 购物车记录ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long cartId) {
        if (cartId == null) return;
        Long uid = userId != null ? userId : 0L;
        Cart old = cartMapper.getById(cartId);
        if (old == null) return;
        if (!uid.equals(old.getUserId())) return;
        cartMapper.deleteById(cartId);
    }

    /**
     * 清空用户购物车中所有商品。
     *
     * @param userId 用户ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clear(Long userId) {
        Long uid = userId != null ? userId : 0L;
        cartMapper.deleteByUserId(uid);
    }
}
