package com.lanou.wordsync.realtime;

import tools.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 在线会话管理与消息推送。
 *
 * 连接方式：ws://127.0.0.1:8088/ws/docs?docId=1&userId=1
 * 保存文档后调用 publish，向订阅该文档的在线客户端推送 doc.updated 事件。
 */
@Component
public class RealtimePublisher {

    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final OnlineUserService onlineUserService;

    public RealtimePublisher(ObjectMapper objectMapper, OnlineUserService onlineUserService) {
        this.objectMapper = objectMapper;
        this.onlineUserService = onlineUserService;
    }

    public void register(Long docId, WebSocketSession session) {
        sessions.computeIfAbsent(docId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unregister(Long docId, WebSocketSession session) {
        Set<WebSocketSession> set = sessions.get(docId);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) {
                sessions.remove(docId);
            }
        }
    }

    /**
     * 每 30 秒给在线文档的 Redis 集合续期，避免 90 秒 TTL 到期。
     */
    @Scheduled(fixedDelay = 30000)
    public void refreshOnlineTtl() {
        for (Long docId : sessions.keySet()) {
            try {
                onlineUserService.touch(docId);
            } catch (Exception ignored) {
                // Redis 短暂不可用时不阻塞 WS
            }
        }
    }

    public void publish(Long docId, Integer versionNo, String action, Long userId) {
        publish(docId, versionNo, action, userId, null);
    }

    /**
     * 推送文档更新事件。
     * 事件名按需求文档统一为 doc.updated，并带上 sha256。
     */
    public void publish(Long docId, Integer versionNo, String action, Long userId, String sha256) {
        Set<WebSocketSession> set = sessions.get(docId);
        if (set == null || set.isEmpty()) {
            return;
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "doc.updated");
        event.put("docId", docId);
        event.put("documentId", docId);
        event.put("versionNo", versionNo);
        event.put("sha256", sha256);
        event.put("action", action);
        event.put("updatedBy", userId);
        event.put("timestamp", System.currentTimeMillis());
        try {
            String json = objectMapper.writeValueAsString(event);
            for (WebSocketSession session : set) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(new TextMessage(json));
                    } catch (Exception ignored) {
                        // 单个连接失败不影响其他连接
                    }
                }
            }
        } catch (Exception ignored) {
            // 序列化失败不阻塞业务
        }
    }
}
