package com.lanou.wordsync.document.service;

import com.lanou.wordsync.document.entity.Document;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

/**
 * 文档元数据缓存。
 *
 * Redis Key：doc:meta:{docId}，TTL 300 秒。
 * 保存/回滚/删除后由 VersionService 或 DocumentService 删除缓存。
 */
@Service
public class DocumentCacheService {

    private static final String PREFIX = "doc:meta:";
    private static final Duration TTL = Duration.ofSeconds(300);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public DocumentCacheService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public Document get(Long docId) {
        try {
            String json = redis.opsForValue().get(key(docId));
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, Document.class);
        } catch (Exception e) {
            return null;
        }
    }

    public void put(Document document) {
        if (document == null || document.getDocumentId() == null) {
            return;
        }
        try {
            redis.opsForValue().set(key(document.getDocumentId()),
                    objectMapper.writeValueAsString(document), TTL);
        } catch (Exception ignored) {
            // 缓存失败不影响主流程
        }
    }

    public void evict(Long docId) {
        if (docId != null) {
            redis.delete(key(docId));
        }
    }

    private String key(Long docId) {
        return PREFIX + docId;
    }
}
