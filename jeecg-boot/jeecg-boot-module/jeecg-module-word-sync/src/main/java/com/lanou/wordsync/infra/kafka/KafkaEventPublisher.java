package com.lanou.wordsync.infra.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Kafka 事件发布器：统一封装发送逻辑，业务模块只需要调用 publish。
 */
@Service
public class KafkaEventPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public KafkaEventPublisher(KafkaTemplate<Object, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 发送事件。
     *
     * @param topic topic 名称
     * @param key   分区 key（本项目用 docId）
     * @param event 事件对象（会被 JsonSerializer 转成 JSON）
     */
    public void publish(String topic, String key, Object event) {
        kafkaTemplate.send(topic, key, event);
    }
}
