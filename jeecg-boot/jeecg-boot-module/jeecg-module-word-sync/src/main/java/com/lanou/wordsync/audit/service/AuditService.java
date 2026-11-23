package com.lanou.wordsync.audit.service;

import com.lanou.wordsync.audit.entity.AuditLog;
import com.lanou.wordsync.audit.mapper.AuditLogMapper;
import com.lanou.wordsync.common.result.PageResult;
import com.lanou.wordsync.infra.kafka.KafkaEventPublisher;
import com.lanou.wordsync.infra.kafka.KafkaTopics;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import com.baomidou.dynamic.datasource.annotation.DS;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 审计日志服务：
 * 1. 写 tb_audit_log；
 * 2. 发一条 word.audit.events 事件给下游（可选消费）。
 */
@DS("wordsync")
@Service
public class AuditService {

    private final AuditLogMapper auditLogMapper;
    private final KafkaEventPublisher kafkaEventPublisher;

    public AuditService(AuditLogMapper auditLogMapper,
                        KafkaEventPublisher kafkaEventPublisher) {
        this.auditLogMapper = auditLogMapper;
        this.kafkaEventPublisher = kafkaEventPublisher;
    }

    /**
     * 记录一条审计日志。
     */
    public void log(Long docId, Long userId, String action, String detail, String ip, String traceId) {
        AuditLog log = new AuditLog();
        log.setDocId(docId);
        log.setUserId(userId);
        log.setAction(action);
        log.setDetail(detail);
        log.setIp(ip);
        log.setTraceId(traceId == null ? MDC.get("traceId") : traceId);
        auditLogMapper.insert(log);

        // 发 Kafka 审计事件，方便后续接入日志分析/告警
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("documentId", docId);
        event.put("action", action);
        event.put("userId", userId);
        event.put("detail", detail);
        event.put("ip", ip);
        event.put("traceId", log.getTraceId());
        event.put("timestamp", System.currentTimeMillis());
        kafkaEventPublisher.publish(KafkaTopics.WORD_AUDIT_EVENTS, String.valueOf(docId), event);
    }

    /**
     * 分页查询某文档的审计日志。
     */
    public PageResult<AuditLog> page(Long docId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = (safePage - 1) * safeSize;
        long total = auditLogMapper.countByDocId(docId);
        return new PageResult<>(total, auditLogMapper.selectByDocId(docId, offset, safeSize));
    }
}
