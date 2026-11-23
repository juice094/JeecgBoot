package org.jeecg.modules.mail.entity;

import java.io.Serializable;

import org.jeecg.common.system.base.entity.JeecgEntity;

import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @Description: OA 邮件-系统发信账号（骨架）
 * @Author: juice094（原型线）
 * @Date: 2026-11-17
 * @Version: V0.1
 *
 * 注意：password 字段要求密文存储（应用层对称加密），长度 ≥ 256；骨架不落加密实现。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "OA 邮件-系统发信账号")
@TableName("system_mail_account")
public class SystemMailAccount extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "SMTP 主机")
    private String host;

    @Schema(description = "SMTP 端口")
    private Integer port;

    @Schema(description = "SMTP 用户名")
    private String username;

    @Schema(description = "SMTP 密码（密文，长度≥256）")
    private String password;

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
}
