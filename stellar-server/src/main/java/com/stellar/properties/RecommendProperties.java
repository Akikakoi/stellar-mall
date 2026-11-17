package com.stellar.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 推荐算法可调参数。默认值与离线评估脚本保持一致：
 * CF 0.50 / 内容 0.40 / 热度 0.10；详情页 seed 相似度权重 0.45。
 */
@Data
@Component
@ConfigurationProperties(prefix = "stellar.recommend")
public class RecommendProperties {

    /** 行为回溯天数 */
    private int behaviorDays = 90;

    /** 内存模型刷新间隔（毫秒） */
    private long modelTtlMs = 60_000L;

    /** 每个用户/设备参与相似度计算的商品数上限 */
    private int maxItemsPerUser = 40;

    /** 每个商品保留的相似商品数上限 */
    private int maxSimilarPerItem = 60;

    /** 推荐结果每类目最多条数 */
    private int maxPerCategory = 4;

    /** 单次返回条数上限 */
    private int maxLimit = 30;

    /** 融合权重：行为共现 */
    private double cfWeight = 0.50;

    /** 融合权重：类目/价格偏好 */
    private double contentWeight = 0.40;

    /** 融合权重：热度 */
    private double popularWeight = 0.10;

    /** 详情页 seed 商品 item-to-item 相似度权重（存在时重新分配上述权重） */
    private double seedWeight = 0.45;

    /** 跨类目共现降权系数 */
    private double crossCategoryCfWeight = 0.35;

    /** 类目先验倍率：base + weight * 用户类目偏好 */
    private double categoryPriorBase = 0.80;

    private double categoryPriorWeight = 0.40;

    /** seed 商品无 item 相似度时的类目/价格兜底倍率 */
    private double seedCategoryBoost = 0.80;

    private double seedPriceBoost = 0.20;
}
