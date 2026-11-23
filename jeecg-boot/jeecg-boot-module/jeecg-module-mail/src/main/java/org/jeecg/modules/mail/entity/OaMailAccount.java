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
 * @Description: OA 邮件-员工邮箱账号（骨架）
 * @Author: juice094（原型线）
 * @Date: 2026-11-17
 * @Version: V0.1
 *
 * IMAP 与 SMTP 双测连通过才允许落库；密码字段密文存储。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "OA 邮件-员工邮箱账号")
@TableName("oa_mail_account")
public class OaMailAccount extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "邮箱地址")
    private String email;

    @Schema(description = "IMAP 主机")
    private String imapHost;

    @Schema(description = "IMAP 端口")
    private Integer imapPort;

    @Schema(description = "IMAP 是否 SSL")
    private Boolean imapSsl;

    @Schema(description = "IMAP 用户名")
    private String imapUsername;

    @Schema(description = "IMAP 密码（密文）")
    private String imapPassword;

    @Schema(description = "SMTP 主机")
    private String smtpHost;

    @Schema(description = "SMTP 端口")
    private Integer smtpPort;

    @Schema(description = "SMTP 是否 SSL")
    private Boolean smtpSsl;

    @Schema(description = "SMTP 用户名")
    private String smtpUsername;

    @Schema(description = "SMTP 密码（密文）")
    private String smtpPassword;

    @Schema(description = "测连状态：1 通过 / 0 未通过")
    private Integer testStatus;

    @Schema(description = "最近同步时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastSyncTime;
}
