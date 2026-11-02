package com.stellar.elasticsearch.doc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;

import java.math.BigDecimal;

/**
 * SPU 商品搜索文档（ES 索引映射）。
 * 索引名由 {@code ElasticsearchConfig.spuIndexName()} 动态指定，此处用占位符，
 * 实际运行时通过 {@code IndexOperations} 按类创建。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "#{@elasticsearchConfig.spuIndexName()}", createIndex = false)
public class SpuDocument {

    @Id
    private Long id;

    /** 商品名称 — IK 中文分词：ik_max_word 索引 / ik_smart 搜索 */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String name;

    /** 副标题 */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String subTitle;

    /** 分类 ID */
    @Field(type = FieldType.Long)
    private Long categoryId;

    /** 分类名（冗余展示）。keyword 主字段供聚合/过滤，text 子字段供品类词文本召回：
     *  手机类商品名普遍不含"手机"二字（如"星耀 X100 青春版"），品类名参与相关性后
     *  搜"手机"可通过 categoryName.text 命中全部手机类商品。 */
    @MultiField(mainField = @Field(type = FieldType.Keyword),
            otherFields = @InnerField(
                    suffix = "text", type = FieldType.Text,
                    analyzer = "ik_max_word", searchAnalyzer = "ik_smart"))
    private String categoryName;

    /** 主图 */
    @Field(type = FieldType.Keyword, index = false)
    private String mainImage;

    /** 最低价 */
    @Field(type = FieldType.Double)
    private BigDecimal minPrice;

    /** 最高价 */
    @Field(type = FieldType.Double)
    private BigDecimal maxPrice;

    /** 销量 */
    @Field(type = FieldType.Integer)
    private Integer saleCount;

    /** 评论数 */
    @Field(type = FieldType.Integer)
    private Integer commentCount;

    /** 总库存 */
    @Field(type = FieldType.Integer)
    private Integer totalStock;

    /** 是否新品（1=是 0=否） */
    @Field(type = FieldType.Integer)
    private Integer isNew;

    /** 是否热卖（1=是 0=否） */
    @Field(type = FieldType.Integer)
    private Integer isHot;

    /** 排序值（越大越靠前） */
    @Field(type = FieldType.Integer)
    private Integer sort;

    /** 状态（1=上架 0=下架） */
    @Field(type = FieldType.Integer)
    private Integer status;

    /** 创建时间 */
    @Field(type = FieldType.Date, pattern = "yyyy-MM-dd'T'HH:mm:ss||yyyy-MM-dd HH:mm:ss||yyyy-MM-dd")
    private String createTime;

    /** 更新时间 */
    @Field(type = FieldType.Date, pattern = "yyyy-MM-dd'T'HH:mm:ss||yyyy-MM-dd HH:mm:ss||yyyy-MM-dd")
    private String updateTime;

    /** 语义向量（name+subTitle 拼接的 BGE embedding），非 @Field 由手动 mapping 管理 */
    private double[] nameVec;
}
