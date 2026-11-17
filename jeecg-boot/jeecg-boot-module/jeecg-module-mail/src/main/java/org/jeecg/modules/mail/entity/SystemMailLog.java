package org.jeecg.modules.mail.entity;

import java.io.Serializable;

import org.jeecg.common.system.base.entity.JeecgEntity;

import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @Description: OA 邮件-系统发信日志（骨架）
 * @Author: juice094（原型线）
 * @Date: 2026-11-17
 * @Version: V0.1
 *
 * 唯一索引建议：(idempotency_key)；查询索引：(send_status, update_time)、(trace_id)、(template_code, create_time)。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "OA 邮件-系统发信日志")
@TableName("system_mail_log")
public class SystemMailLog extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "发送账号 id")
    private String accountId;

    @Schema(description = "模板编码")
    private String templateCode;

    @Schema(description = "收件人（逗号分隔，仅管理端可见）")
    private String toMails;

    @Schema(description = "收件人指纹（SHA-256 前 8 位；查询/统计/日志只用指纹，需求 A-7/D-4）")
    private String toHash;

    @Schema(description = "抄送（逗号分隔）")
    private String ccMails;

    @Schema(description = "密送（逗号分隔）")
    private String bccMails;

    @Schema(description = "标题快照")
    private String templateTitle;

    @Schema(description = "正文快照")
    private String templateContent;

    @Schema(description = "发送状态：0/1/2/3/4")
    private Integer sendStatus;

    @Schema(description = "重试次数")
    private Integer retryCount;

    @Schema(description = "结构化错误码")
    private String errorCode;

    @Schema(description = "错误信息")
    private String errorMsg;

    @Schema(description = "链路 id")
    private String traceId;

    @Schema(description = "幂等键（唯一索引，可空）")
    private String idempotencyKey;
}
