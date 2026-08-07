package com.stellar.service.impl;

import com.stellar.context.BaseContext;
import com.stellar.entity.ReviewComment;
import com.stellar.mapper.ReviewCommentMapper;
import com.stellar.service.ReviewCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价评论服务实现。
 * <p>
 * 提供对评价进行评论、查询评论列表和统计评论数量等功能。
 * 评论创建者信息从当前登录上下文获取。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class ReviewCommentServiceImpl implements ReviewCommentService {

    private final ReviewCommentMapper commentMapper;

    /**
     * 对指定评价发表评论。评论者从当前登录上下文获取。
     *
     * @param reviewId 评价ID
     * @param content  评论内容
     * @return 新创建的评论ID
     */
    @Override
    @Transactional
    public Long comment(Long reviewId, String content) {
        Long userId = BaseContext.getCurrentId();
        ReviewComment c = ReviewComment.builder()
                .reviewId(reviewId)
                .userId(userId)
                .content(content)
                .status(1)
                .createTime(LocalDateTime.now())
                .createUser(userId)
                .updateTime(LocalDateTime.now())
                .updateUser(userId)
                .build();
        commentMapper.insert(c);
        return c.getId();
    }

    /**
     * 查询指定评价下的所有评论。
     *
     * @param reviewId 评价ID
     * @return 评论列表
     */
    @Override
    public List<ReviewComment> listByReviewId(Long reviewId) {
        return commentMapper.listByReviewId(reviewId);
    }

    /**
     * 统计指定评价下的评论数量。
     *
     * @param reviewId 评价ID
     * @return 评论数量
     */
    @Override
    public long countByReviewId(Long reviewId) {
        return commentMapper.countByReviewId(reviewId);
    }
}
