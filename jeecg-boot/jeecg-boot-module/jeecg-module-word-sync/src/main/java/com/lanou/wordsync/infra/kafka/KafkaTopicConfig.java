package com.lanou.wordsync.infra.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Spring Boot 启动时自动创建 topic。
 * 如果 topic 已手动创建，KafkaAdmin 会检查存在，不会重复创建。
 */
@Configuration
public class KafkaTopicConfig {

    /** 文档变更事件：3 个分区，按 docId 做 key，保证同一文档事件有序 */
    @Bean
    public NewTopic wordSyncEventsTopic() {
        return TopicBuilder.name(KafkaTopics.WORD_SYNC_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /** 审计事件：1 个分区即可 */
    @Bean
    public NewTopic wordAuditEventsTopic() {
        return TopicBuilder.name(KafkaTopics.WORD_AUDIT_EVENTS)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
