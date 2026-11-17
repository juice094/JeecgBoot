package org.jeecg.modules.mail.dto;

import java.util.Date;

import org.jeecg.modules.mail.entity.SystemMailAccount;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @Description: 系统发信账号响应（接口文档 §3.1）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 安全口径（需求文档 D-4 / A-7）：响应绝不携带密码密文，账号列表/详情只见配置元数据。
 */
@Data
@Schema(description = "系统发信账号响应")
public class SystemMailAccountRespDTO {

    @Schema(description = "主键")
    private String id;

    @Schema(description = "SMTP 主机")
    private String host;

    @Schema(description = "SMTP 端口")
    private Integer port;

    @Schema(description = "SMTP 用户名")
    private String username;

    @Schema(description = "是否 SSL")
    private Boolean sslEnable;

    @Schema(description = "是否 STARTTLS")
    private Boolean starttlsEnable;

    @Schema(description = "发件人地址")
    private String fromAddress;

    @Schema(description = "发件人显示名")
    private String fromName;

    @Schema(description = "状态：1 启用 / 0 停用")
    private Integer status;

    @Schema(description = "创建时间")
    private Date createTime;

    /**
     * 实体转响应（剥离密码）。
     */
    public static SystemMailAccountRespDTO from(SystemMailAccount entity) {
        if (entity == null) {
            return null;
        }
        SystemMailAccountRespDTO dto = new SystemMailAccountRespDTO();
        dto.setId(entity.getId());
        dto.setHost(entity.getHost());
        dto.setPort(entity.getPort());
        dto.setUsername(entity.getUsername());
        dto.setSslEnable(entity.getSslEnable());
        dto.setStarttlsEnable(entity.getStarttlsEnable());
        dto.setFromAddress(entity.getFromAddress());
        dto.setFromName(entity.getFromName());
        dto.setStatus(entity.getStatus());
        dto.setCreateTime(entity.getCreateTime());
        return dto;
    }
}
