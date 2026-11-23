package com.lanou.wordsync.realtime;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 文档在线用户集合。
 *
 * Redis Key：doc:online:{docId}
 * 类型：Set，存 userId；TTL 90 秒，WebSocket 在线期间每 30 秒续期。
 */
@Service
public class OnlineUserService {

    private static final String PREFIX = "doc:online:";
    private static final Duration TTL = Duration.ofSeconds(90);

    private final StringRedisTemplate redis;

    public OnlineUserService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void join(Long docId, Long userId) {
        String key = key(docId);
        redis.opsForSet().add(key, String.valueOf(userId == null ? 0L : userId));
        redis.expire(key, TTL);
    }

    public void leave(Long docId, Long userId) {
        String key = key(docId);
        redis.opsForSet().remove(key, String.valueOf(userId == null ? 0L : userId));
        Long size = redis.opsForSet().size(key);
        if (size == null || size == 0) {
            redis.delete(key);
        }
    }

    /** WebSocket 心跳续期：不续期的话 90 秒后在线状态会消失。 */
    public void touch(Long docId) {
        redis.expire(key(docId), TTL);
    }

    public List<String> list(Long docId) {
        Set<String> members = redis.opsForSet().members(key(docId));
        return members == null ? List.of() : new ArrayList<>(members);
    }

    private String key(Long docId) {
        return PREFIX + docId;
    }
}
