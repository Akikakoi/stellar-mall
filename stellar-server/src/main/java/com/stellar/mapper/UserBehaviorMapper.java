package com.stellar.mapper;

import com.stellar.entity.UserBehavior;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户行为埋点 Mapper。表：stellar_user_behavior（只追加）。
 */
public interface UserBehaviorMapper {

    /** 批量插入（event_time 统一取数据库 NOW()，批次内先后由自增 id 区分）。 */
    int batchInsert(@Param("list") List<UserBehavior> list);

    /**
     * 推荐模型用的近期有效行为：
     * 1) view_item / add_to_cart / favorite / order_placed（必须关联 SPU）；
     * 2) search（spu_id 为空，返回 keyword 供 Service 层匹配商品文本，作为显式意图信号）；
     * 3) view_item_list（曝光，商品列表在 extra 的 itemIds/spuIds 里，供负反馈使用）。
     * 返回字段仅建模所需列，keyword / extra 已在 SQL 侧截断，避免长文本进入内存。
     */
    List<UserBehavior> listRecentForRecommendation(@Param("days") int days);
}
