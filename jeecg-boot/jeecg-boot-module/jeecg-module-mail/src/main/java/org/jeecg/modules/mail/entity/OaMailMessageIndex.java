package org.jeecg.modules.mail.entity;

import java.io.Serializable;
import java.util.Date;

import org.jeecg.common.system.base.entity.JeecgEntity;
import org.springframework.format.annotation.DateTimeFormat;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @Description: OA 邮件-员工邮箱消息索引（骨架）
 * @Author: juice094（原型线）
 * @Date: 2026-11-17
 * @Version: V0.1
 *
 * 只存信封索引，不存正文；唯一键 (account_id, canonical_folder, imap_uid)。
 * 列表查询只查本表，详情按需 IMAP FETCH。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "OA 邮件-员工邮箱消息索引")
@TableName("oa_mail_message_index")
public class OaMailMessageIndex extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "员工邮箱账号 id")
    private String accountId;

    @Schema(description = "规范文件夹")
    private String canonicalFolder;

    @Schema(description = "IMAP UID")
    private Long imapUid;

    @Schema(description = "Message-ID")
    private String messageId;

    @Schema(description = "主题")
    private String subject;

    @Schema(description = "发件人显示名")
    private String fromName;

    @Schema(description = "发件人地址 hash（脱敏）")
    private String fromAddressHash;

    @Schema(description = "收件人摘要")
    private String toSummary;

    @Schema(description = "接收时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date receivedTime;

    @Schema(description = "是否已读")
    private Boolean readFlag;

    @Schema(description = "是否有附件")
    private Boolean hasAttachment;

    @Schema(description = "摘要片段")
    private String snippet;
}
