package com.stellar.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 推荐接口响应：推荐列表 + 本次使用的策略，便于前端展示和排查。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendResultVO implements Serializable {

    private List<RecommendItemVO> items;

    /** hybrid（有行为）/ popular（冷启动） */
    private String strategy;

    /** 是否基于登录用户行为做了个性化 */
    private Boolean personalized;

    private Long userId;

    private Long generatedAt;

    private String note;
}
