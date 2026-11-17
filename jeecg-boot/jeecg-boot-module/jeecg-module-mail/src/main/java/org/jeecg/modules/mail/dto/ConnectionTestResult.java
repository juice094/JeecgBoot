package org.jeecg.modules.mail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @Description: 契约层-连接测试结果（Phase 1 扩展）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 对齐接口文档 §2 与 §4.1：单侧失败必须能定位失败侧与错误分类。
 * Phase 1 增量（契约"只增不改"）：imapErrorCode/smtpErrorCode 双侧分类；
 * errorCode 保留为"首个失败侧分类"语义，兼容既有消费方。
 */
@Data
@Schema(description = "契约层-连接测试结果")
public class ConnectionTestResult {

    @Schema(description = "IMAP 是否连通")
    private boolean imapOk;

    @Schema(description = "SMTP 是否连通")
    private boolean smtpOk;

    @Schema(description = "耗时毫秒")
    private long latencyMs;

    @Schema(description = "首个失败侧错误分类（imap_auth_failed / smtp_connection 等）")
    private String errorCode;

    @Schema(description = "IMAP 侧错误分类（失败时非空）")
    private String imapErrorCode;

    @Schema(description = "SMTP 侧错误分类（失败时非空）")
    private String smtpErrorCode;

    public boolean allOk() {
        return imapOk && smtpOk;
    }
}
