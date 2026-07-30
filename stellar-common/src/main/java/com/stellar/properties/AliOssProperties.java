package com.stellar.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云 OSS 配置（可与 RAG 共用桶，通过 prefix 区分目录）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "stellar.alioss")
public class AliOssProperties {
    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;
}
