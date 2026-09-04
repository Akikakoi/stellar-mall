package com.stellar.dto;

import lombok.Data;

/**
 * 站点配置保存参数：商城主页背景图。
 * <p>
 * bgImage 为 OSS 绝对 URL；传 null / 空串 = 恢复默认背景（后端删配置行）。
 * </p>
 */
@Data
public class SiteBgDTO {

    /** 背景图 URL（空 = 恢复默认） */
    private String bgImage;
}
