package com.lanou.wordsync.document.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档主表，对应 tb_document。
 */
@Data
public class Document {

    /** 文档 ID（主键 document_id） */
    private Long documentId;

    /** 标题 */
    private String title;

    /** 所有者用户 ID */
    private Long ownerId;

    /** 当前版本号，0 表示还没有版本 */
    private Integer currentVersion;

    /** 当前版本在 MinIO 的对象 key */
    private String storageKey;

    /** 状态：ACTIVE / DELETED */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
