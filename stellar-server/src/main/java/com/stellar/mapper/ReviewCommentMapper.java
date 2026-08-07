package com.stellar.mapper;

import com.stellar.entity.ReviewComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 评价回复 Mapper。表：stellar_review_comment。
 */
@Mapper
public interface ReviewCommentMapper {

    int insert(ReviewComment comment);

    /** 查询某评价的所有回复（按时间升序，含用户昵称，只查 status=1） */
    List<ReviewComment> listByReviewId(@Param("reviewId") Long reviewId);

    /** 单条评价的评论数 */
    long countByReviewId(@Param("reviewId") Long reviewId);

    /** 批量查询多个评价的回复数量 */
    List<java.util.Map<String, Object>> countByReviewIds(@Param("reviewIds") List<Long> reviewIds);
}
