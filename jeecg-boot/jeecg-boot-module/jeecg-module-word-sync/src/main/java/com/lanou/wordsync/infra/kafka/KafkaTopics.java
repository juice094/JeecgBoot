package com.lanou.wordsync.infra.kafka;

/**
 * Kafka topic 常量。
 * 注意：topic 已在 Docker 中手动创建，名字必须与下面保持一致。
 */
public final class KafkaTopics {

    private KafkaTopics() {
    }

    /** 文档变更事件：上传、保存、回滚、WebDAV 上传 */
    public static final String WORD_SYNC_EVENTS = "word.sync.events";

    /** 审计事件 */
    public static final String WORD_AUDIT_EVENTS = "word.audit.events";
}
