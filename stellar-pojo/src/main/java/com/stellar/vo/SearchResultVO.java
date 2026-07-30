package com.stellar.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 搜索结果 VO — 包含商品列表 + 高亮 + 聚合，取代裸 PageResult。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultVO implements Serializable {

    private Long total;
    private List records;

    /** spuId → 名称高亮片段列表（每个元素是一段已加 &lt;em&gt; 标签的 HTML 片段） */
    private Map<Long, List<String>> highlights;

    /** 聚合数据：分类计数 + 价格区间计数 */
    private AggregationVO aggregations;
}
