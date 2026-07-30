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

@Service
@RequiredArgsConstructor
public class ReviewCommentServiceImpl implements ReviewCommentService {

    private final ReviewCommentMapper commentMapper;

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

    @Override
    public List<ReviewComment> listByReviewId(Long reviewId) {
        return commentMapper.listByReviewId(reviewId);
    }

    @Override
    public long countByReviewId(Long reviewId) {
        return commentMapper.countByReviewId(reviewId);
    }
}
