package com.lanou.wordsync.version.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档版本，对应表 tb_version。
 */
@Data
public class DocumentVersion {

    /** 版本记录 ID（主键） */
    private Long versionId;

    /** 所属文档 ID */
    private Long docId;

    /** 版本号，从 1 开始 */
    private Integer versionNo;

    /** 该版本在 MinIO 中的对象 key */
    private String storageKey;

    /** 文件大小（字节） */
    private Long size;

    /** 文件 SHA-256 */
    private String sha256;

    /** 来源：UPLOAD / ONLYOFFICE / WEBDAV / ROLLBACK */
    private String source;

    /** 操作人用户 ID */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
