package com.stellar.service;

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
}
