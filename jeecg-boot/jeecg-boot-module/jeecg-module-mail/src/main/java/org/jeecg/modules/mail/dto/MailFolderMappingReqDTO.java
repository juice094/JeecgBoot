package org.jeecg.modules.mail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @Description: 文件夹人工映射请求（接口文档 §4.2 PUT /accounts/{id}/folders/mapping）
 * @Author: mail-dev
 * @Date: 2026-11-17
 */
@Data
@Schema(description = "文件夹人工映射请求")
public class MailFolderMappingReqDTO {

    @Schema(description = "远端文件夹名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String remoteName;

    @Schema(description = "规范文件夹（MailCanonicalFolder code，如 Sent）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String canonical;
}
