package com.lanou.wordsync.start;

import com.lanou.wordsync.infra.kafka.KafkaTopics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka 消费端验证。
 *
 * 消费 word.sync.events / word.audit.events，并通过 /api/kafka/status 暴露计数，
 * 用于证明保存/回滚后事件确实能被消费。
 */
@Component
public class KafkaEventListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventListener.class);

    private final AtomicLong syncCount = new AtomicLong();
    private final AtomicLong auditCount = new AtomicLong();

    @KafkaListener(topics = KafkaTopics.WORD_SYNC_EVENTS, groupId = "word-sync-events")
    public void onSyncEvent(ConsumerRecord<String, Object> record) {
        syncCount.incrementAndGet();
        log.info("Kafka 收到文档事件: key={}, value={}", record.key(), record.value());
    }

    @KafkaListener(topics = KafkaTopics.WORD_AUDIT_EVENTS, groupId = "word-sync-audit")
    public void onAuditEvent(ConsumerRecord<String, Object> record) {
        auditCount.incrementAndGet();
        log.info("Kafka 收到审计事件: key={}, value={}", record.key(), record.value());
    }

    public long getSyncCount() {
        return syncCount.get();
    }

    public long getAuditCount() {
        return auditCount.get();
    }
}
