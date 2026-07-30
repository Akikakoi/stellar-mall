package com.stellar.service;

import com.stellar.entity.Review;
import com.stellar.result.PageResult;

import java.math.BigDecimal;

public interface ReviewService {
    Long submit(Review review);
    void reply(Long id, String reply);
    void hide(Long id);
    void show(Long id);
    PageResult pageBySpuId(Long spuId, Integer page, Integer pageSize);
    BigDecimal avgRating(Long spuId);
    PageResult pageAll(Long spuId, Integer status, String spuName, Integer page, Integer pageSize);
}
