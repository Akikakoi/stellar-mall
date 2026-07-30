package com.stellar.service.impl;

import com.stellar.context.BaseContext;
import com.stellar.entity.Review;
import com.stellar.mapper.ReviewMapper;
import com.stellar.mapper.SpuMapper;
import com.stellar.result.PageResult;
import com.stellar.service.PointsService;
import com.stellar.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewMapper reviewMapper;
    private final SpuMapper spuMapper;
    private final PointsService pointsService;

    @Override
    @Transactional
    public Long submit(Review review) {
        Long userId = BaseContext.getCurrentId();
        review.setUserId(userId);
        review.setStatus(1);
        review.setCreateTime(LocalDateTime.now());
        review.setCreateUser(userId);
        review.setUpdateTime(LocalDateTime.now());
        review.setUpdateUser(userId);
        reviewMapper.insert(review);
        // 评价获得积分（不影响评价主流程）
        try {
            pointsService.earnByReview(userId, review.getId());
        } catch (Exception e) {
            log.error("[ReviewService] 评价积分发放失败（评价不受影响）: userId={}, reviewId={}",
                    userId, review.getId(), e);
        }
        return review.getId();
    }

    @Override
    @Transactional
    public void reply(Long id, String reply) {
        reviewMapper.updateReply(id, reply);
    }

    @Override
    @Transactional
    public void hide(Long id) {
        reviewMapper.updateStatus(id, 0);
    }

    @Override
    @Transactional
    public void show(Long id) {
        reviewMapper.updateStatus(id, 1);
    }

    @Override
    public PageResult pageBySpuId(Long spuId, Integer page, Integer pageSize) {
        int p = (page == null || page < 1) ? 1 : page;
        int ps = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        List<Review> list = reviewMapper.pageBySpuId(spuId, (p - 1) * ps, ps);
        long total = reviewMapper.countBySpuId(spuId);
        return new PageResult(total, list == null ? new ArrayList<>() : list);
    }

    @Override
    public BigDecimal avgRating(Long spuId) {
        return reviewMapper.avgRatingBySpuId(spuId);
    }

    @Override
    public PageResult pageAll(Long spuId, Integer status, String spuName, Integer page, Integer pageSize) {
        int p = (page == null || page < 1) ? 1 : page;
        int ps = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        List<Review> list = reviewMapper.pageAll(spuId, status, spuName, (p - 1) * ps, ps);
        long total = reviewMapper.countAll(spuId, status, spuName);
        return new PageResult(total, list == null ? new ArrayList<>() : list);
    }
}
