package org.jeecg.modules.mail.dto;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @Description: 模板预览请求（接口文档 §3.2 POST /system/templates/{code}/preview）
 * @Author: mail-dev
 * @Date: 2026-11-17
 */
@Data
@Schema(description = "模板预览请求")
public class MailTemplatePreviewReqDTO {

    @Schema(description = "渲染参数（缺参返回 MAIL_SEND_TEMPLATE_PARAM_MISS 并列出缺失 key）")
    private Map<String, Object> params;
}
