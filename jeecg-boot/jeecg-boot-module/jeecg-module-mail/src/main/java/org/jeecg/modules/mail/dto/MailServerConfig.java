package org.jeecg.modules.mail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @Description: 契约层-邮件服务器配置（骨架）
 * @Author: juice094（原型线）
 * @Date: 2026-11-17
 * @Version: V0.2
 *
 * 对齐接口文档 §2 MailServerConfig。password 为明文入参，落库前必须应用层加密。
 */
@Data
@Schema(description = "契约层-邮件服务器配置")
public class MailServerConfig {

    @Schema(description = "主机", requiredMode = Schema.RequiredMode.REQUIRED)
    private String host;

    @Schema(description = "端口", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer port;

    @Schema(description = "是否 SSL")
    private boolean ssl;

    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @Schema(description = "密码（明文入参，落库前加密）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
