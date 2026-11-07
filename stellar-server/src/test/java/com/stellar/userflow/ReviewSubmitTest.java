package com.stellar.userflow;

import com.stellar.context.BaseContext;
import com.stellar.entity.Review;
import com.stellar.exception.BaseException;
import com.stellar.mapper.MallOrderItemMapper;
import com.stellar.mapper.ReviewMapper;
import com.stellar.mapper.SpuMapper;
import com.stellar.service.PointsService;
import com.stellar.service.impl.ReviewServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评价提交：同一订单同一规格不可重复评价，避免重复发放积分。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewService — 评价防重复")
class ReviewSubmitTest {

    private static final Long USER_ID = 8888L;
    private static final Long SPU_ID = 1L;
    private static final Long ORDER_ID = 100L;
    private static final Long SKU_ID = 10L;

    @Mock private ReviewMapper reviewMapper;
    @Mock private SpuMapper spuMapper;
    @Mock private PointsService pointsService;
    @Mock private MallOrderItemMapper mallOrderItemMapper;
    @Mock private CacheManager cacheManager;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private ReviewServiceImpl reviewService;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(USER_ID);
        when(mallOrderItemMapper.countBoughtByUser(USER_ID, SPU_ID)).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        BaseContext.remove();
    }

    private Review review() {
        return Review.builder().spuId(SPU_ID).orderId(ORDER_ID).skuId(SKU_ID)
                .rating(5).content("很好").build();
    }

    @Test
    @DisplayName("同一订单同一规格已评价 → 拒绝，不再发积分")
    void duplicateReview_rejected() {
        when(reviewMapper.countByUserOrderSku(USER_ID, ORDER_ID, SKU_ID)).thenReturn(1L);

        assertThrows(BaseException.class, () -> reviewService.submit(review()));

        verify(reviewMapper, never()).insert(any());
        verify(pointsService, never()).earnByReview(anyLong(), anyLong());
    }

    @Test
    @DisplayName("缺少订单/规格 → 退化为同一 SPU 查重，重复评价仍拒绝")
    void missingOrderOrSku_fallsBackToSpuGuard() {
        Review review = review();
        review.setOrderId(null);
        when(reviewMapper.countByUserIdAndSpuId(USER_ID, SPU_ID)).thenReturn(1L);

        assertThrows(BaseException.class, () -> reviewService.submit(review));

        verify(reviewMapper, never()).insert(any());
    }

    @Test
    @DisplayName("首次评价 → 写入并发放积分")
    void firstReview_success() {
        when(reviewMapper.countByUserOrderSku(USER_ID, ORDER_ID, SKU_ID)).thenReturn(0L);
        doAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setId(9L);
            return 1;
        }).when(reviewMapper).insert(any(Review.class));
        when(spuMapper.incrCommentCount(SPU_ID, 1)).thenReturn(1);

        Long id = reviewService.submit(review());

        assertEquals(9L, id);
        verify(reviewMapper).insert(any(Review.class));
        verify(pointsService).earnByReview(USER_ID, 9L);
    }
}