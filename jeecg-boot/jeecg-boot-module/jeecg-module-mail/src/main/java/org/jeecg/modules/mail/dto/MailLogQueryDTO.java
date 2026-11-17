package org.jeecg.modules.mail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @Description: 发信日志查询条件（接口文档 §3.4 GET /system/logs）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 时间窗为创建时间（create_time），格式 yyyy-MM-dd HH:mm:ss，由 Service 层解析。
 */
@Data
@Schema(description = "发信日志查询条件")
public class MailLogQueryDTO {

    @Schema(description = "页码，默认 1")
    private long pageNo = 1;

    @Schema(description = "每页条数，默认 10，最大 100", defaultValue = "10")
    private long pageSize = 10;

    @Schema(description = "发送状态：0 待发送 / 1 发送中 / 2 成功 / 3 失败 / 4 死信")
    private Integer status;

    @Schema(description = "模板编码")
    private String templateCode;

    @Schema(description = "收件人指纹（SHA-256 前 8 位，不出现完整地址）")
    private String toHash;

    @Schema(description = "链路 id")
    private String traceId;

    @Schema(description = "创建时间起（yyyy-MM-dd HH:mm:ss）")
    private String beginTime;

    @Schema(description = "创建时间止（yyyy-MM-dd HH:mm:ss）")
    private String endTime;
}
