package org.jeecg.modules.mail.dto;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @Description: 契约层-邮件索引 DTO（骨架）
 * @Author: juice094（原型线）
 * @Date: 2026-11-17
 * @Version: V0.2
 *
 * Agent 只读本 DTO；发件人仅暴露 hash，不含明文地址与正文（需求文档 §4.2 / C-4）。
 */
@Data
@Schema(description = "契约层-邮件索引")
public class MailIndexDTO {

    @Schema(description = "索引 id")
    private String id;

    @Schema(description = "账号 id")
    private String accountId;

    @Schema(description = "规范文件夹")
    private String canonicalFolder;

    @Schema(description = "主题")
    private String subject;

    @Schema(description = "发件人显示名")
    private String fromName;

    @Schema(description = "发件人地址 hash")
    private String fromAddressHash;

    @Schema(description = "接收时间")
    private Date receivedTime;

    @Schema(description = "是否已读")
    private Boolean read;

    @Schema(description = "是否有附件")
    private Boolean hasAttachment;

    @Schema(description = "摘要片段")
    private String snippet;
}
