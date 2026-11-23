package com.lanou.wordsync.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;

/**
 * WebSocket 处理器：连接时从 URL 的 ?docId= 参数订阅文档，
 * 同时把 ?userId= 加入 Redis 在线用户集合。
 */
@Component
public class DocWebSocketHandler extends TextWebSocketHandler {

    private final RealtimePublisher realtimePublisher;
    private final OnlineUserService onlineUserService;

    public DocWebSocketHandler(RealtimePublisher realtimePublisher,
                               OnlineUserService onlineUserService) {
        this.realtimePublisher = realtimePublisher;
        this.onlineUserService = onlineUserService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long docId = parseLongParam(session, "docId");
        Long userId = parseLongParam(session, "userId");
        if (docId != null) {
            realtimePublisher.register(docId, session);
            onlineUserService.join(docId, userId == null ? 0L : userId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long docId = parseLongParam(session, "docId");
        Long userId = parseLongParam(session, "userId");
        if (docId != null) {
            realtimePublisher.unregister(docId, session);
            onlineUserService.leave(docId, userId == null ? 0L : userId);
        }
    }

    private Long parseLongParam(WebSocketSession session, String name) {
        try {
            URI uri = session.getUri();
            if (uri == null || uri.getQuery() == null) {
                return null;
            }
            for (String pair : uri.getQuery().split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2 && name.equals(kv[0])) {
                    return Long.parseLong(kv[1]);
                }
            }
        } catch (Exception ignored) {
            // 参数不合法就不订阅
        }
        return null;
    }
}
