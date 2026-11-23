package org.jeecg.modules.mail.dto;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @Description: 契约层-单封发信请求 DTO（骨架）
 * @Author: juice094（原型线）
 * @Date: 2026-11-17
 * @Version: V0.1
 *
 * 对齐接口文档 §2 MailSendSingleReqDTO；业务模块只依赖本 DTO 与 MailSendApi，不接触 JavaMail 类型。
 */
@Data
@Schema(description = "契约层-单封发信请求")
public class MailSendSingleReqDTO {

    @Schema(description = "模板编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String templateCode;

    @Schema(description = "收件人（逗号分隔）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String to;

    @Schema(description = "抄送（逗号分隔）")
    private String cc;

    @Schema(description = "密送（逗号分隔）")
    private String bcc;

    @Schema(description = "模板参数", requiredMode = Schema.RequiredMode.REQUIRED)
    private Map<String, Object> params;

    @Schema(description = "幂等键（可选，24h 内同 key 返回同一 logId）")
    private String idempotencyKey;
}
