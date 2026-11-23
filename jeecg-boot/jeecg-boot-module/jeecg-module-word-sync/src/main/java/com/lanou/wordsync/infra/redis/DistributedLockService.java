package com.lanou.wordsync.infra.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * 基于 Redis 的简单分布式锁。
 *
 * 本项目用 doc:lock:{docId} 保证同一文档的保存/上传/回滚串行执行，
 * 避免两个请求同时算出相同的 versionNo。
 */
@Service
public class DistributedLockService {

    private final StringRedisTemplate redis;

    public DistributedLockService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 尝试加锁。
     *
     * @return 成功返回锁 token；失败返回 null
     */
    public String tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean ok = redis.opsForValue().setIfAbsent(key, token, ttl);
        return Boolean.TRUE.equals(ok) ? token : null;
    }

    /**
     * 释放锁：只有 token 匹配才删除，避免误删别人的锁。
     */
    public void unlock(String key, String token) {
        if (key == null || token == null) {
            return;
        }
        String saved = redis.opsForValue().get(key);
        if (token.equals(saved)) {
            redis.delete(key);
        }
    }
}
