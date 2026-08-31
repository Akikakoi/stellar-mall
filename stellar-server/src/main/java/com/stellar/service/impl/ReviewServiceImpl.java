package com.stellar.service.impl;

import com.stellar.context.BaseContext;
import com.stellar.entity.Review;
import com.stellar.exception.BaseException;
import com.stellar.mapper.MallOrderItemMapper;
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

/**
 * 评价服务实现类。
 * <p>
 * 提供评价的提交、回复、隐藏/显示、分页查询及平均评分统计等功能。
 * 提交评价时会异步发放积分，积分发放失败不影响评价主流程。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewMapper reviewMapper;
    private final SpuMapper spuMapper;
    private final PointsService pointsService;
    private final MallOrderItemMapper mallOrderItemMapper;

    /**
     * 提交评价。
     * <p>
     * 自动填充当前登录用户ID、状态、创建/更新时间等字段，
     * 提交成功后异步发放积分（积分发放失败不影响评价）。
     * </p>
     *
     * @param review 评价实体
     * @return 评价ID
     */
    @Override
    @Transactional
    public Long submit(Review review) {
        Long userId = BaseContext.getCurrentId();
        // 仅允许购买过该商品的用户评价，防止刷评价赚积分
        if (review.getSpuId() == null) {
            throw new BaseException("评价缺少商品信息");
        }
        int bought = mallOrderItemMapper.countBoughtByUser(userId, review.getSpuId());
        if (bought <= 0) {
            throw new BaseException("仅购买过该商品的用户可评价");
        }
        review.setUserId(userId);
        review.setStatus(1);
        review.setCreateTime(LocalDateTime.now());
        review.setCreateUser(userId);
        review.setUpdateTime(LocalDateTime.now());
        review.setUpdateUser(userId);
        reviewMapper.insert(review);
        // 维护 SPU 评价数（可见评价 +1）
        if (review.getSpuId() != null) {
            spuMapper.incrCommentCount(review.getSpuId(), 1);
        }
        // 评价获得积分（不影响评价主流程）
        try {
            pointsService.earnByReview(userId, review.getId());
        } catch (Exception e) {
            log.error("[ReviewService] 评价积分发放失败（评价不受影响）: userId={}, reviewId={}",
                    userId, review.getId(), e);
        }
        return review.getId();
    }

    /**
     * 商家回复评价。
     *
     * @param id    评价ID
     * @param reply 回复内容
     */
    @Override
    @Transactional
    public void reply(Long id, String reply) {
        reviewMapper.updateReply(id, reply);
    }

    /**
     * 隐藏评价（将状态设为0），并同步扣减 SPU 评价数。
     *
     * @param id 评价ID
     */
    @Override
    @Transactional
    public void hide(Long id) {
        Review r = reviewMapper.getById(id);
        if (r == null) return;
        if (r.getStatus() != null && r.getStatus() == 1) {
            reviewMapper.updateStatus(id, 0);
            spuMapper.incrCommentCount(r.getSpuId(), -1);
        }
    }

    /**
     * 显示评价（将状态设为1），并同步累加 SPU 评价数。
     *
     * @param id 评价ID
     */
    @Override
    @Transactional
    public void show(Long id) {
        Review r = reviewMapper.getById(id);
        if (r == null) return;
        if (r.getStatus() != null && r.getStatus() == 0) {
            reviewMapper.updateStatus(id, 1);
            spuMapper.incrCommentCount(r.getSpuId(), 1);
        }
    }

    /**
     * 按SPU ID分页查询评价。
     *
     * @param spuId    SPU ID
     * @param page     页码（从1开始）
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @Override
    public PageResult pageBySpuId(Long spuId, Integer page, Integer pageSize) {
        int p = (page == null || page < 1) ? 1 : page;
        int ps = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        List<Review> list = reviewMapper.pageBySpuId(spuId, (p - 1) * ps, ps);
        long total = reviewMapper.countBySpuId(spuId);
        return new PageResult(total, list == null ? new ArrayList<>() : list);
    }

    /**
     * 查询指定SPU的平均评分。
     *
     * @param spuId SPU ID
     * @return 平均评分
     */
    @Override
    public BigDecimal avgRating(Long spuId) {
        return reviewMapper.avgRatingBySpuId(spuId);
    }

    /**
     * 后台管理端分页查询所有评价（支持多条件筛选）。
     *
     * @param spuId    SPU ID（可选）
     * @param status   状态（可选）
     * @param spuName  SPU名称（可选，模糊匹配）
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @Override
    public PageResult pageAll(Long spuId, Integer status, String spuName, Integer page, Integer pageSize) {
        int p = (page == null || page < 1) ? 1 : page;
        int ps = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        List<Review> list = reviewMapper.pageAll(spuId, status, spuName, (p - 1) * ps, ps);
        long total = reviewMapper.countAll(spuId, status, spuName);
        return new PageResult(total, list == null ? new ArrayList<>() : list);
    }
}
