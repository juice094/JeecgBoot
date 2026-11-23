package com.lanou.wordsync.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 注册 WebSocket 端点：/ws/docs
 */
@Configuration
@EnableWebSocket
public class WordSyncWebSocketConfig implements WebSocketConfigurer {

    private final DocWebSocketHandler docWebSocketHandler;

    public WordSyncWebSocketConfig(DocWebSocketHandler docWebSocketHandler) {
        this.docWebSocketHandler = docWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(docWebSocketHandler, "/word-sync/ws/docs")
                .setAllowedOrigins("*");
    }
}
