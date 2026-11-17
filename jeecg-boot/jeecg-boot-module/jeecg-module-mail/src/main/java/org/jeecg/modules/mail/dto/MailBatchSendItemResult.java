package org.jeecg.modules.mail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @Description: 批量发送单条结果（接口文档 §3.3 send/batch 响应元素）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 批量逐条落日志、逐条独立成败：单条失败不影响其他条，失败原因回传给调用方。
 */
@Data
@Schema(description = "批量发送-单条结果")
public class MailBatchSendItemResult {

    @Schema(description = "请求下标（从 0 开始）")
    private int index;

    @Schema(description = "模板编码")
    private String templateCode;

    @Schema(description = "是否成功")
    private boolean success;

    @Schema(description = "成功时的日志 id")
    private String logId;

    @Schema(description = "失败时的错误信息（含错误码前缀）")
    private String errorMessage;
}
