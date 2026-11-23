package org.jeecg.modules.mail.dto;

import java.util.LinkedHashMap;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @Description: 发信日志聚合统计（接口文档 §3.4 GET /system/logs/stat）
 * @Author: mail-dev
 * @Date: 2026-11-17
 */
@Data
@Schema(description = "发信日志聚合统计")
public class MailLogStatDTO {

    @Schema(description = "按发送状态计数（key=状态码，value=条数）")
    private Map<Integer, Long> statusCounts = new LinkedHashMap<>();

    @Schema(description = "按模板计数（value 降序，最多 20 条）")
    private Map<String, Long> templateCounts = new LinkedHashMap<>();
}
