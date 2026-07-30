package com.stellar.service;

import com.stellar.vo.FavoriteVO;

import java.util.List;

/**
 * 收藏夹服务接口。
 */
public interface FavoriteService {

    /** 添加收藏（已收藏则忽略，不报错） */
    void add(Long userId, Long spuId);

    /** 取消收藏 */
    void remove(Long userId, Long spuId);

    /** 是否已收藏 */
    boolean isFavorited(Long userId, Long spuId);

    /** 收藏列表（含 SPU 展示信息） */
    List<FavoriteVO> list(Long userId);

    /** 批量查询：返回当前用户已收藏的 spuId 列表 */
    List<Long> listFavoritedSpuIds(Long userId, List<Long> spuIds);
}