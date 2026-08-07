package com.stellar.service.impl;

import com.stellar.constant.MessageConstant;
import com.stellar.entity.Favorite;
import com.stellar.entity.Spu;
import com.stellar.exception.BaseException;
import com.stellar.mapper.FavoriteMapper;
import com.stellar.mapper.SpuMapper;
import com.stellar.service.FavoriteService;
import com.stellar.vo.FavoriteVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 收藏夹服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final SpuMapper spuMapper;

    /**
     * 添加收藏。
     *
     * @param userId 用户ID
     * @param spuId  SPU ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(Long userId, Long spuId) {
        if (userId == null || spuId == null) {
            throw new BaseException(MessageConstant.ILLEGAL_PARAMETER);
        }
        // 已收藏则忽略
        Favorite existing = favoriteMapper.getByUserIdAndSpuId(userId, spuId);
        if (existing != null) return;

        Favorite f = new Favorite();
        f.setUserId(userId);
        f.setSpuId(spuId);
        favoriteMapper.insert(f);
    }

    /**
     * 取消收藏。
     *
     * @param userId 用户ID
     * @param spuId  SPU ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long userId, Long spuId) {
        if (userId == null || spuId == null) return;
        favoriteMapper.deleteByUserIdAndSpuId(userId, spuId);
    }

    /**
     * 判断是否已收藏。
     *
     * @param userId 用户ID
     * @param spuId  SPU ID
     * @return true 表示已收藏，false 表示未收藏
     */
    @Override
    public boolean isFavorited(Long userId, Long spuId) {
        if (userId == null || spuId == null) return false;
        return favoriteMapper.getByUserIdAndSpuId(userId, spuId) != null;
    }

    /**
     * 查询用户的收藏列表，包含 SPU 基本信息。
     *
     * @param userId 用户ID
     * @return 收藏列表，无数据时返回空列表
     */
    @Override
    public List<FavoriteVO> list(Long userId) {
        if (userId == null) return Collections.emptyList();
        List<Favorite> favs = favoriteMapper.listByUserId(userId);
        if (favs == null || favs.isEmpty()) return Collections.emptyList();

        List<FavoriteVO> res = new ArrayList<>(favs.size());
        for (Favorite f : favs) {
            Spu spu = spuMapper.getById(f.getSpuId());
            if (spu == null) continue;
            FavoriteVO vo = FavoriteVO.builder()
                    .id(f.getId())
                    .spuId(f.getSpuId())
                    .spuName(spu.getName())
                    .spuImage(spu.getMainImage())
                    .minPrice(spu.getMinPrice())
                    .createTime(f.getCreateTime())
                    .build();
            res.add(vo);
        }
        return res;
    }

    /**
     * 从给定的 SPU ID 列表中筛选出用户已收藏的 SPU ID。
     *
     * @param userId 用户ID
     * @param spuIds 待筛选的 SPU ID 列表
     * @return 已收藏的 SPU ID 列表
     */
    @Override
    public List<Long> listFavoritedSpuIds(Long userId, List<Long> spuIds) {
        if (userId == null || spuIds == null || spuIds.isEmpty()) return Collections.emptyList();
        return favoriteMapper.listSpuIdsByUserId(userId, spuIds);
    }
}