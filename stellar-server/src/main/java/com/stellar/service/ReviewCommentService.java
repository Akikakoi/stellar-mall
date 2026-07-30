package com.stellar.service;

import com.stellar.entity.ReviewComment;

import java.util.List;

public interface ReviewCommentService {

    /** 发表评论 */
    Long comment(Long reviewId, String content);

    /** 获取某条评价的所有评论 */
    List<ReviewComment> listByReviewId(Long reviewId);

    /** 获取某条评价的评论数 */
    long countByReviewId(Long reviewId);
}
