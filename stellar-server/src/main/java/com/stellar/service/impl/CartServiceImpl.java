package com.stellar.service.impl;

import com.stellar.constant.MessageConstant;
import com.stellar.dto.CartAddDTO;
import com.stellar.dto.CartUpdateDTO;
import com.stellar.entity.Cart;
import com.stellar.entity.Sku;
import com.stellar.entity.Spu;
import com.stellar.exception.BaseException;
import com.stellar.mapper.CartMapper;
import com.stellar.mapper.SkuMapper;
import com.stellar.mapper.SpuMapper;
import com.stellar.service.CartService;
import com.stellar.vo.CartVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

        // 保障服务费必须非负，防止前端传负值压低订单总价
        BigDecimal extraAmount = dto.getExtraAmount() == null ? BigDecimal.ZERO : dto.getExtraAmount();
        if (extraAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BaseException(MessageConstant.ILLEGAL_PARAMETER + "（保障服务费不能为负数）");
        }

        Sku sku = skuMapper.getById(dto.getSkuId());
        if (sku == null) throw new BaseException(MessageConstant.SKU_NOT_FOUND);
        if (sku.getStatus() == null || sku.getStatus() != 1) {
            throw new BaseException("该 SKU 已停售");
        }

        // 是否携带保障服务选择：serviceInfo 非 null 表示本次有明确的服务选择（含空数组=清空）
        boolean hasServiceSelection = dto.getServiceInfo() != null;

        Cart existing = cartMapper.getByUserIdAndSkuId(uid, dto.getSkuId());
        if (existing != null) {
            Cart upd = new Cart();
            upd.setId(existing.getId());
            upd.setQty((existing.getQty() == null ? 0 : existing.getQty()) + qty);
            // 重复加购同一 SKU 时，以最近一次的保障服务选择为准（serviceInfo=null 表示未选，保留原值）
            if (hasServiceSelection) {
                upd.setExtraAmount(extraAmount);
                upd.setServiceInfo(dto.getServiceInfo());
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
                c.setServiceInfo(dto.getServiceInfo());
            }
            cartMapper.insert(c);
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
