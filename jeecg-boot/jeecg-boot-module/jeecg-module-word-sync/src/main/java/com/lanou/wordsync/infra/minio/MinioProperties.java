package com.lanou.wordsync.infra.minio;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinIO 连接配置，对应 application.properties 中 minio.* 配置项。
 */
@Data
@Component
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    /** MinIO API 地址，例如 http://127.0.0.1:9002 */
    private String endpoint;

    /** 访问密钥 */
    private String accessKey;

    /** 密钥 */
    private String secretKey;

    /** 存放 Word 文件的桶名，例如 wordsync */
    private String bucket;
}
