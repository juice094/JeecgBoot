package org.jeecg.modules.mail.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @Description: 模板预览结果（接口文档 §3.2 preview）
 * @Author: mail-dev
 * @Date: 2026-11-17
 */
@Data
@Schema(description = "模板预览结果")
public class MailTemplatePreviewDTO {

    @Schema(description = "渲染后标题")
    private String title;

    @Schema(description = "渲染后正文（HTML）")
    private String content;

    @Schema(description = "模板声明的占位符参数")
    private List<String> params;
}
