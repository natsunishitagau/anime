package com.anime.config;

import com.anime.websocket.DanmakuWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final DanmakuWebSocketHandler danmakuWebSocketHandler;

    public WebSocketConfig(DanmakuWebSocketHandler danmakuWebSocketHandler) {
        this.danmakuWebSocketHandler = danmakuWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(danmakuWebSocketHandler, "/ws/danmaku/{videoId}")
                .setAllowedOrigins("*");
    }
}