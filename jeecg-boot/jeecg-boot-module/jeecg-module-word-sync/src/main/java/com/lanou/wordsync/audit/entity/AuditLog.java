package com.lanou.wordsync.audit.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志，对应表 tb_audit_log。
 */
@Data
public class AuditLog {

    /** 日志 ID（主键） */
    private Long logId;

    /** 文档 ID */
    private Long docId;

    /** 操作人 ID */
    private Long userId;

    /** 动作：UPLOAD/DOWNLOAD/SAVE/ROLLBACK/WEBDAV_PUT/DELETE */
    private String action;

    /** 详情，可存 JSON 或文本 */
    private String detail;

    /** 客户端 IP */
    private String ip;

    /** 链路追踪 ID */
    private String traceId;

    /** 操作时间 */
    private LocalDateTime createdAt;
}
