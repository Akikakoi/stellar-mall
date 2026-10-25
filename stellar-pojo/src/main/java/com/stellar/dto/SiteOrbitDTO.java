package com.stellar.dto;

import lombok.Data;

import java.util.List;

/**
 * 站点配置保存参数：首屏环绕商品候选池。
 * <p>
 * spuIds 为首页 Hero 环绕轨道可展示的商品 SPU ID 列表，前台每次刷新从中随机抽取；
 * 传空列表 / null = 删除配置行，前台改为从整个商品库随机抽取。
 * </p>
 */
@Data
public class SiteOrbitDTO {

    /** 环绕商品候选池 SPU ID 列表（最多 20 个） */
    private List<Long> spuIds;
}
