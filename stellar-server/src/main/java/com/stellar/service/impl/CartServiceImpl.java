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

        Sku sku = skuMapper.getById(dto.getSkuId());
        if (sku == null) throw new BaseException(MessageConstant.SKU_NOT_FOUND);
        if (sku.getStatus() == null || sku.getStatus() != 1) {
            throw new BaseException("该 SKU 已停售");
        }

        Cart existing = cartMapper.getByUserIdAndSkuId(uid, dto.getSkuId());
        if (existing != null) {
            Cart upd = new Cart();
            upd.setId(existing.getId());
            upd.setQty((existing.getQty() == null ? 0 : existing.getQty()) + qty);
            cartMapper.update(upd);
        } else {
            Cart c = new Cart();
            c.setUserId(uid);
            c.setSkuId(sku.getId());
            c.setSpuId(sku.getSpuId());
            c.setQty(qty);
            c.setChecked(1);
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

        // 批量查 SKU / SPU 组装 VO（简化版：单条循环查询，真实项目可改批量 SELECT IN）
        Map<Long, Sku> skuMap = new java.util.HashMap<>();
        for (Long sid : skuIds) {
            Sku s = skuMapper.getById(sid);
            if (s != null) skuMap.put(sid, s);
        }
        Map<Long, Spu> spuMap = new java.util.HashMap<>();
        for (Long pid : spuIds) {
            Spu p = spuMapper.getById(pid);
            if (p != null) spuMap.put(pid, p);
        }

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
