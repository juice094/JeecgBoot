package com.lanou.wordsync.onlyoffice;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OnlyOffice 相关配置，对应 application.properties 中 onlyoffice.*。
 */
@Data
@Component
@ConfigurationProperties(prefix = "onlyoffice")
public class OnlyOfficeProperties {

    /** OnlyOffice 服务地址，例如 http://127.0.0.1（当前容器映射 80） */
    private String serverUrl;

    /** OnlyOffice 容器访问后端下载接口的地址，例如 http://host.docker.internal:8088 */
    private String documentBaseUrl;

    /** OnlyOffice 回调后端接口的地址，例如 http://host.docker.internal:8088 */
    private String callbackBaseUrl;
}
