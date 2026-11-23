package com.lanou.wordsync.version.service;

import com.lanou.wordsync.audit.service.AuditService;
import com.lanou.wordsync.common.exception.BizException;
import com.lanou.wordsync.infra.kafka.KafkaEventPublisher;
import com.lanou.wordsync.infra.kafka.KafkaTopics;
import com.lanou.wordsync.infra.minio.StorageService;
import com.lanou.wordsync.infra.redis.DistributedLockService;
import com.lanou.wordsync.realtime.RealtimePublisher;
import com.lanou.wordsync.version.entity.DocumentVersion;
import com.lanou.wordsync.version.mapper.DocumentCurrentMapper;
import com.lanou.wordsync.version.mapper.DocumentVersionMapper;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.baomidou.dynamic.datasource.annotation.DS;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 版本服务：新增版本、查询版本、回滚。
 *
 * 上传/OnlyOffice 保存/WebDAV 上传/回滚最终都会调用 createVersion，
 * 因此这里统一做：分布式锁 + MinIO 存储 + 版本表 + 当前版本更新 + 审计 + 实时通知 + Kafka 事件。
 */
@DS("wordsync")
@Service
public class VersionService {

    public static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private static final String LOCK_KEY_PREFIX = "doc:lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private final DocumentVersionMapper versionMapper;
    private final DocumentCurrentMapper currentMapper;
    private final StorageService storageService;
    private final AuditService auditService;
    private final RealtimePublisher realtimePublisher;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final DistributedLockService lockService;
    private final StringRedisTemplate redis;

    public VersionService(DocumentVersionMapper versionMapper,
                          DocumentCurrentMapper currentMapper,
                          StorageService storageService,
                          AuditService auditService,
                          RealtimePublisher realtimePublisher,
                          KafkaEventPublisher kafkaEventPublisher,
                          DistributedLockService lockService,
                          StringRedisTemplate redis) {
        this.versionMapper = versionMapper;
        this.currentMapper = currentMapper;
        this.storageService = storageService;
        this.auditService = auditService;
        this.realtimePublisher = realtimePublisher;
        this.kafkaEventPublisher = kafkaEventPublisher;
        this.lockService = lockService;
        this.redis = redis;
    }

    /** 兼容原有调用：默认写审计日志。 */
    @Transactional(rollbackFor = Exception.class)
    public DocumentVersion createVersion(Long docId,
                                         Integer versionNo,
                                         byte[] content,
                                         String fileName,
                                         String contentType,
                                         String source,
                                         Long userId) {
        return createVersion(docId, versionNo, content, fileName, contentType, source, userId, true);
    }

    /**
     * 创建新版本。
     *
     * @param writeAudit false 时由调用方统一写审计，避免 OnlyOffice 回调出现两条日志
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentVersion createVersion(Long docId,
                                         Integer versionNo,
                                         byte[] content,
                                         String fileName,
                                         String contentType,
                                         String source,
                                         Long userId,
                                         boolean writeAudit) {
        if (docId == null || versionNo == null || versionNo < 1) {
            throw new BizException(400, "docId/versionNo 不合法");
        }
        if (content == null || content.length == 0) {
            throw new BizException(400, "文件内容为空");
        }

        String lockKey = LOCK_KEY_PREFIX + docId;
        String lockToken = lockService.tryLock(lockKey, LOCK_TTL);
        if (lockToken == null) {
            throw new BizException(423, "文档正在保存，请稍后重试");
        }
        try {
            return createVersionInternal(docId, versionNo, content, fileName,
                    contentType, source, userId, writeAudit);
        } finally {
            lockService.unlock(lockKey, lockToken);
        }
    }

    /**
     * 真正的写版本逻辑。调用方必须已经持有 doc:lock:{docId}。
     */
    private DocumentVersion createVersionInternal(Long docId,
                                                  Integer versionNo,
                                                  byte[] content,
                                                  String fileName,
                                                  String contentType,
                                                  String source,
                                                  Long userId,
                                                  boolean writeAudit) {
        String safeName = safeFileName(fileName);
        String objectKey = "docs/" + docId + "/v" + versionNo + "/" + safeName;
        String hash = sha256(content);

        // 1) 上传到 MinIO
        storageService.upload(objectKey, content,
                contentType == null || contentType.isBlank() ? DOCX_CONTENT_TYPE : contentType);

        // 2) 写版本表
        DocumentVersion version = new DocumentVersion();
        version.setDocId(docId);
        version.setVersionNo(versionNo);
        version.setStorageKey(objectKey);
        version.setSize((long) content.length);
        version.setSha256(hash);
        version.setSource(source);
        version.setCreatedBy(userId);
        versionMapper.insert(version);

        // 3) 更新文档主表的“当前版本”
        currentMapper.updateCurrent(docId, versionNo, objectKey);
        // 当前版本变了，删除文档元数据缓存，下一次 getById 重新查库
        redis.delete("doc:meta:" + docId);

        // 4) 审计日志（OnlyOffice 回调由控制器统一写一条）
        if (writeAudit) {
            auditService.log(docId, userId, source,
                    "版本 " + versionNo + "，文件 " + safeName,
                    null, MDC.get("traceId"));
        }

        // 5) WebSocket 通知在线客户端
        realtimePublisher.publish(docId, versionNo, source, userId, hash);

        // 6) Kafka 文档变更事件
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("documentId", docId);
        event.put("versionNo", versionNo);
        event.put("action", source);
        event.put("userId", userId);
        event.put("filename", safeName);
        event.put("sha256", hash);
        event.put("timestamp", System.currentTimeMillis());
        kafkaEventPublisher.publish(KafkaTopics.WORD_SYNC_EVENTS, String.valueOf(docId), event);

        return version;
    }

    public List<DocumentVersion> list(Long docId) {
        return versionMapper.selectByDocId(docId);
    }

    public DocumentVersion get(Long docId, Integer versionNo) {
        DocumentVersion version = versionMapper.selectByDocIdAndVersion(docId, versionNo);
        if (version == null) {
            throw new BizException(404, "版本不存在");
        }
        return version;
    }

    public byte[] download(Long docId, Integer versionNo) {
        return download(docId, versionNo, null, null);
    }

    public byte[] download(Long docId, Integer versionNo, Long userId, String ip) {
        DocumentVersion version = get(docId, versionNo);
        byte[] bytes = storageService.download(version.getStorageKey());
        auditService.log(docId, userId, "DOWNLOAD_VERSION",
                "下载版本 " + versionNo, ip, MDC.get("traceId"));
        return bytes;
    }

    /**
     * 获取文档“当前版本”的 SHA-256，作为 ETag 使用。
     *
     * 注意：不能直接取最新版本，因为回滚后 current_version 可能指向历史版本。
     */
    public String getCurrentSha256(Long docId) {
        Integer currentVersion = currentMapper.selectCurrentVersion(docId);
        if (currentVersion == null || currentVersion < 1) {
            return null;
        }
        DocumentVersion version = versionMapper.selectByDocIdAndVersion(docId, currentVersion);
        return version == null ? null : version.getSha256();
    }

    /** 判断新内容是否和当前版本完全一致，用于 OnlyOffice 保存去重。 */
    public boolean isSameAsCurrent(Long docId, byte[] content) {
        String current = getCurrentSha256(docId);
        if (current == null || content == null) {
            return false;
        }
        return current.equalsIgnoreCase(sha256(content));
    }

    /**
     * 回滚：把目标历史版本的内容复制成一个新版本，当前版本指向新版本。
     *
     * 这样历史版本不会被破坏，同时达到“内容也回滚”的效果。
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentVersion rollback(Long docId, Integer versionNo, Long userId) {
        if (docId == null || versionNo == null || versionNo < 1) {
            throw new BizException(400, "docId/versionNo 不合法");
        }

        String lockKey = LOCK_KEY_PREFIX + docId;
        String lockToken = lockService.tryLock(lockKey, LOCK_TTL);
        if (lockToken == null) {
            throw new BizException(423, "文档正在保存，请稍后重试");
        }
        try {
            DocumentVersion target = get(docId, versionNo);
            Integer current = currentMapper.selectCurrentVersion(docId);

            // 已经是当前版本时不重复生成新版本
            if (current != null && current.equals(target.getVersionNo())) {
                auditService.log(docId, userId, "ROLLBACK_NOOP",
                        "当前已经是版本 " + versionNo, null, MDC.get("traceId"));
                return target;
            }

            int nextVersion = (current == null ? 0 : current) + 1;
            byte[] content = storageService.download(target.getStorageKey());
            String fileName = fileNameFromStorageKey(target.getStorageKey());

            return createVersionInternal(docId, nextVersion, content, fileName,
                    DOCX_CONTENT_TYPE, "ROLLBACK", userId, true);
        } finally {
            lockService.unlock(lockKey, lockToken);
        }
    }

    /** 防止文件名里带路径，MinIO 的 object key 只保留文件名本身 */
    private String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "document.docx";
        }
        String name = fileName.replace("\\", "/");
        int idx = name.lastIndexOf('/');
        if (idx >= 0) {
            name = name.substring(idx + 1);
        }
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        return name.isBlank() ? "document.docx" : name;
    }

    private String fileNameFromStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return "document.docx";
        }
        int idx = storageKey.lastIndexOf('/');
        return idx >= 0 ? storageKey.substring(idx + 1) : storageKey;
    }

    /** 计算 SHA-256，用于校验、去重和 ETag */
    private String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("计算 SHA-256 失败", e);
        }
    }
}
