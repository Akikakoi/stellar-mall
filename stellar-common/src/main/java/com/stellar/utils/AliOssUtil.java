package com.stellar.utils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.stellar.properties.AliOssProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 阿里云 OSS 上传工具（对齐 sky AliOssUtil）。
 *
 * 上传 key 格式：stellar-mall/{module}/{yyyyMMdd}/{uuid}.{ext}
 * 其中 module 由上传者传入（spu/category/employee/user），避免和 RAG 项目文件混在一起。
 */
@Slf4j
@Component
public class AliOssUtil {

    private final AliOssProperties props;

    @Autowired
    public AliOssUtil(AliOssProperties props) {
        this.props = props;
    }

    /**
     * 上传一个文件到 OSS。
     *
     * @param file   MultipartFile
     * @param module 业务模块名：spu/category/employee/user 等，用作一级目录
     * @return 完整 HTTPS 访问 URL（https://bucket.endpoint/key）
     */
    public String upload(MultipartFile file, String module) throws IOException {
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf("."));
        }
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String key = String.format("stellar-mall/%s/%s/%s%s", module, datePart, uuid, ext);

        try (InputStream is = file.getInputStream()) {
            OSS oss = new OSSClientBuilder().build(props.getEndpoint(), props.getAccessKeyId(), props.getAccessKeySecret());
            try {
                oss.putObject(props.getBucketName(), key, is);
            } finally {
                oss.shutdown();
            }
        }
        String url = String.format("https://%s.%s/%s", props.getBucketName(), props.getEndpoint(), key);
        log.info("[AliOssUtil] upload OK, module={}, url={}", module, url);
        return url;
    }
}
