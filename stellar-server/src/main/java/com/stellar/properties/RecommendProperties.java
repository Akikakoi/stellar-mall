package com.stellar.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 推荐算法可调参数。默认值由 scripts/recommend_eval.py --sweep 在 320 组权重中扫出：
 * CF 0.30 / 内容 0.30 / 热度 0.20 / 搜索意图 0.10；详情页 seed 相似度权重 0.45。
 * 离线 NDCG@10 从 0.425 提升到 0.519（18 个评估用户，样本小，改动需看线上 CTR 复核）。
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
    private double cfWeight = 0.30;

    /** 融合权重：类目/价格偏好 */
    private double contentWeight = 0.30;

    /** 融合权重：热度 */
    private double popularWeight = 0.20;

    /** 详情页 seed 商品 item-to-item 相似度权重（存在时重新分配上述权重） */
    private double seedWeight = 0.45;

    /**
     * 跨类目共现降权系数。当前数据里 85.8% 的共现边是跨类目的（多为游客一次浏览多个类目形成的伪共现），
     * 网格扫描显示 0.05~0.15 区间最优，取 0.10；调到 0.5 以上 NDCG 明显下滑。
     */
    private double crossCategoryCfWeight = 0.10;

    /** 类目先验倍率：base + weight * 用户类目偏好 */
    private double categoryPriorBase = 0.80;

    private double categoryPriorWeight = 0.40;

    /** seed 商品无 item 相似度时的类目/价格兜底倍率 */
    private double seedCategoryBoost = 0.80;

    private double seedPriceBoost = 0.20;

    /**
     * 搜索意图在融合总分中的权重（独立第四路，与 CF/内容/热度并列）。
     * 仅在当前画像存在搜索意图时生效，此时其余三路按比例缩放到剩余份额。
     * 离线留一法对意图不公平（测试集商品被排除，无法体现“搜索后点击”），
     * 因此保留一个小权重 0.10：离线几乎无损，又不至于压过行为信号。
     */
    private double searchIntentWeight = 0.10;

    /** 搜索词与商品文本的最低匹配分（bigram 覆盖率），低于此值视为未命中 */
    private double searchIntentMinScore = 0.20;

    /** 单条搜索行为的意图基础权重（高于 view_item 的 1.0，搜索是显式需求） */
    private double searchEventWeight = 1.50;

    /** 曝光未点击负反馈开关（view_item_list 里出现但从未交互的商品） */
    private boolean exposureNegativeEnabled = true;

    /** 每次“曝光未点击”的降权比例（累加，封顶见 maxPenalty） */
    private double exposureNegativeWeight = 0.18;

    /** 负反馈最大降权比例：0.45 表示最低保留 55% 分数，避免把商品彻底压死 */
    private double exposureNegativeMaxPenalty = 0.45;

    /** 推荐位 A/B 实验：按 user_id（游客按 device_id）稳定分桶，权重差异见 Variant */
    private Ab ab = new Ab();

    @Data
    public static class Ab {

        /** 开关；关闭时全部走 control（对照）参数 */
        private boolean enabled = true;

        /** 对照组占比（0-100），其余进入实验组 */
        private int controlPercent = 50;

        /** 分桶盐值，改动等于重新洗桶，线上实验中途不要改 */
        private String salt = "stellar-rec-20261126";

        /** 对照组：本次改动前的线上权重 */
        private Variant control = new Variant(0.50, 0.40, 0.10, 0.35, 0.45, 0.35);

        /** 实验组：网格搜索扫出的权重 */
        private Variant experiment = new Variant(0.30, 0.30, 0.20, 0.10, 0.45, 0.10);
    }

    /** 一组融合权重；顺序与构造函数一致，避免 yml 里写错字段 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Variant {
        private double cfWeight = 0.50;
        private double contentWeight = 0.40;
        private double popularWeight = 0.10;
        private double searchIntentWeight = 0.35;
        private double seedWeight = 0.45;
        private double crossCategoryWeight = 0.35;
    }
}
