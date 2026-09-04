package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站点级配置（对应 stellar_site_config 表）。
 * <p>
 * 通用 key-value 配置：config_key 唯一，config_value 存 JSON 串。
 * 当前键：home_bg = {"bgImage":"<OSS url>"}（无记录 = 使用默认背景）。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteConfig implements Serializable {

    private Long id;

    /** 配置键，如 home_bg */
    private String configKey;

    /** 配置值 JSON 串，如 {"bgImage":"https://..."} */
    private String configValue;

    /** 配置说明 */
    private String remark;

    /** 最后操作人 */
    private Long updateUser;

    private LocalDateTime updateTime;
}
