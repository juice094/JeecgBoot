package org.jeecg.modules.mail.dto;

import java.util.Date;

import org.jeecg.modules.mail.entity.OaMailAccount;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @Description: 员工邮箱绑定响应（接口文档 §4.1）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 安全口径（需求文档 D-4）：响应绝不携带 IMAP/SMTP 密码密文。
 */
@Data
@Schema(description = "员工邮箱绑定响应")
public class OaMailAccountRespDTO {

    @Schema(description = "主键")
    private String id;

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

    @Schema(description = "SMTP 主机")
    private String smtpHost;

    @Schema(description = "SMTP 端口")
    private Integer smtpPort;

    @Schema(description = "SMTP 是否 SSL")
    private Boolean smtpSsl;

    @Schema(description = "SMTP 用户名")
    private String smtpUsername;

    @Schema(description = "测连状态：1 通过 / 0 未通过")
    private Integer testStatus;

    @Schema(description = "最近同步时间")
    private Date lastSyncTime;

    /**
     * 实体转响应（剥离密码）。
     */
    public static OaMailAccountRespDTO from(OaMailAccount entity) {
        if (entity == null) {
            return null;
        }
        OaMailAccountRespDTO dto = new OaMailAccountRespDTO();
        dto.setId(entity.getId());
        dto.setEmail(entity.getEmail());
        dto.setImapHost(entity.getImapHost());
        dto.setImapPort(entity.getImapPort());
        dto.setImapSsl(entity.getImapSsl());
        dto.setImapUsername(entity.getImapUsername());
        dto.setSmtpHost(entity.getSmtpHost());
        dto.setSmtpPort(entity.getSmtpPort());
        dto.setSmtpSsl(entity.getSmtpSsl());
        dto.setSmtpUsername(entity.getSmtpUsername());
        dto.setTestStatus(entity.getTestStatus());
        dto.setLastSyncTime(entity.getLastSyncTime());
        return dto;
    }
}
