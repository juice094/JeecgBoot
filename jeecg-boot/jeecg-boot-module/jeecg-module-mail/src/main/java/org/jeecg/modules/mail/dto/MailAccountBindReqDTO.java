package org.jeecg.modules.mail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @Description: 契约层-员工邮箱绑定请求（骨架）
 * @Author: juice094（原型线）
 * @Date: 2026-11-17
 * @Version: V0.2
 */
@Data
@Schema(description = "契约层-员工邮箱绑定请求")
public class MailAccountBindReqDTO {

    @Schema(description = "邮箱地址", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @Schema(description = "IMAP 配置", requiredMode = Schema.RequiredMode.REQUIRED)
    private MailServerConfig imap;

    @Schema(description = "SMTP 配置", requiredMode = Schema.RequiredMode.REQUIRED)
    private MailServerConfig smtp;
}
