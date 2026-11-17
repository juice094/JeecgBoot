package org.jeecg.modules.mail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @Description: 契约层-邮件索引检索请求（骨架）
 * @Author: juice094（原型线）
 * @Date: 2026-11-17
 * @Version: V0.2
 */
@Data
@Schema(description = "契约层-邮件索引检索请求")
public class MailSearchReqDTO {

    @Schema(description = "账号 id")
    private String accountId;

    @Schema(description = "规范文件夹")
    private String canonicalFolder;

    @Schema(description = "关键词（主题/摘要）")
    private String keyword;

    @Schema(description = "仅未读")
    private Boolean unreadOnly;

    @Schema(description = "页码，默认 1")
    private Integer pageNo;

    @Schema(description = "每页条数，默认 10，上限 100")
    private Integer pageSize;
}
