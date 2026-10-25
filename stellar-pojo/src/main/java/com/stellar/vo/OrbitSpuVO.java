package com.stellar.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 首页首屏环绕商品卡片 VO。
 * <p>
 * 只下发卡片真正需要的字段，避免像 SPU 详情那样把描述、SKU 列表整包带下来
 * （8 张卡片的响应体因此从数十 KB 降到 ~1KB）。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrbitSpuVO implements Serializable {

    /** SPU ID（卡片跳转详情用） */
    private Long id;

    /** 商品名称（卡片无障碍标签） */
    private String name;

    /** 主图 URL */
    private String mainImage;
}
