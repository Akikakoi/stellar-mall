package com.stellar.service;

import java.util.List;

/**
 * 站点级配置服务。
 */
public interface SiteConfigService {

    /** 获取商城主页背景图 URL（空串 = 使用默认背景图） */
    String getBgImage();

    /**
     * 保存商城主页背景图。
     *
     * @param bgImage 背景图 OSS URL；null / 空串 = 恢复默认（删除配置行）
     */
    void saveBgImage(String bgImage);

    /** 获取首屏环绕商品候选池 ID 列表（空列表 = 前台从整个商品库随机抽取） */
    List<Long> getHomeOrbitSpuIds();

    /**
     * 保存首屏环绕商品候选池。
     *
     * @param spuIds 商品 SPU ID 列表（前台每次从中随机抽取展示）；null / 空列表 = 删除配置行
     */
    void saveHomeOrbitSpuIds(List<Long> spuIds);
}
