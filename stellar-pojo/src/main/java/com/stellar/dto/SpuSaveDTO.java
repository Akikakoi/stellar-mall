package com.stellar.dto;

import com.stellar.entity.Sku;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@ApiModel("新增/更新 SPU（带嵌套 SKU）")
public class SpuSaveDTO implements Serializable {

    @ApiModelProperty("SPU id：新增时不传，更新时必传")
    private Long id;

    @NotBlank(message = "SPU 名称不能为空")
    @Size(max = 128, message = "名称最多 128 字符")
    @ApiModelProperty(value = "SPU 名", required = true)
    private String name;

    @Size(max = 255)
    @ApiModelProperty("副标题/卖点")
    private String subtitle;

    @NotNull(message = "分类不能为空")
    @ApiModelProperty(value = "分类 id", required = true)
    private Long categoryId;
    private Long category2Id;  // 二级分类

    // ========= 图片：mainImage（标准）+ image（前端常用别名，兼容保存） =========
    @Size(max = 255)
    @ApiModelProperty("主图 URL（标准字段，优先使用）")
    private String mainImage;

    @Size(max = 255)
    @ApiModelProperty("主图 URL（前端常用别名：等价于 mainImage，若 mainImage 为空则回退到 image）")
    private String image;

    @Size(max = 2000)
    @ApiModelProperty("副图 URL 列表（分号分隔）")
    private String subImages;

    // ========= 描述：双版本 + 互相兼容 =========
    @Size(max = 16777215)
    @ApiModelProperty("商品详情 HTML（前端商品管理页面通常只维护这一份）")
    private String description;

    @Size(max = 16777215)
    @ApiModelProperty("商品详情 Markdown —— 同步给 RAG 知识库的正文；为空时自动用 description 兜底")
    private String descriptionMd;

    @ApiModelProperty("排序值，越大越靠前")
    private Integer sort;

    @ApiModelProperty("1 上架 0 下架（新增不传默认上架）")
    private Integer status;

    // ========= 价格：SKU 聚合优先；前端简单表单可直接传 price 生成默认 SKU =========
    @ApiModelProperty("最低 SKU 价格（可传可不传，不传则按 SKU / price 自动计算）")
    private BigDecimal minPrice;

    @ApiModelProperty("最高 SKU 价格（同上，可自动计算）")
    private BigDecimal maxPrice;

    @ApiModelProperty("前端简易表单直接传单一价格（非 SKU 场景）；将作为默认 SKU 价格并同步 minPrice/maxPrice")
    private BigDecimal price;

    @ApiModelProperty("总库存（同上，自动聚合 SKU 总和优先）")
    private Integer totalStock;

    @ApiModelProperty("是否新品")
    private Integer isNew;

    @ApiModelProperty("是否热卖")
    private Integer isHot;

    @Valid
    @ApiModelProperty("嵌套 SKU 列表：新增时若为空且 price 非空，自动构造 1 条默认 SKU；更新时不传则保留原 SKU")
    private List<Sku> skuList = new ArrayList<>();

    // ================= 兼容性辅助方法（Service 层调用即可，避免 Jackson 序列化暴露冗余字段） =================

    /** 取有效主图：mainImage 为空则回退 image。 */
    public String resolveMainImage() {
        if (mainImage != null && !mainImage.isEmpty()) return mainImage;
        return image;
    }

    /** 取有效 Markdown 描述：descriptionMd 为空则回退 description。 */
    public String resolveDescriptionMd() {
        if (descriptionMd != null && !descriptionMd.trim().isEmpty()) return descriptionMd;
        return description;
    }

    /** 取有效 HTML 描述：description 为空则回退 descriptionMd。 */
    public String resolveDescription() {
        if (description != null) return description;
        return descriptionMd;
    }
}
