package com.anime.websocket;

import com.anime.dto.DanmakuDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DanmakuWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DanmakuWebSocketHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, Map<String, WebSocketSession>> videoRooms = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String videoId = extractVideoId(session);
        if (videoId != null) {
            videoRooms.computeIfAbsent(videoId, k -> new ConcurrentHashMap<>())
                    .put(session.getId(), session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String videoId = extractVideoId(session);
        if (videoId != null) {
            Map<String, WebSocketSession> room = videoRooms.get(videoId);
            if (room != null) {
                room.remove(session.getId());
                if (room.isEmpty()) {
                    videoRooms.remove(videoId);
                }
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket error: session={}", session.getId(), exception);
    }

    public void broadcastDanmaku(String videoId, Object danmakuData) {
        Map<String, WebSocketSession> room = videoRooms.get(videoId);
        if (room == null || room.isEmpty()) {
            return;
        }
        try {
            String json;

            DanmakuDto dto = (DanmakuDto) danmakuData;
            Map<String, Object> broadcastData = new HashMap<>();
            broadcastData.put("id", dto.getId());
            broadcastData.put("videoId", dto.getVideoId());
            broadcastData.put("userId", dto.getUserId());
            broadcastData.put("username", dto.getUsername());
            broadcastData.put("content", dto.getContent());
            broadcastData.put("time", dto.getTime());
            broadcastData.put("color", dto.getColor());
            broadcastData.put("fontSize", dto.getFontSize());
            json = objectMapper.writeValueAsString(broadcastData);

            TextMessage message = new TextMessage(json);
            for (WebSocketSession session : room.values()) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(message);
                    } catch (IOException e) {
                        log.error("Failed to send danmaku to session={}", session.getId(), e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to broadcast danmaku for videoId={}", videoId, e);
        }
    }

    public int getOnlineCount(String videoId) {
        Map<String, WebSocketSession> room = videoRooms.get(videoId);
        return room != null ? room.size() : 0;
    }

    private String extractVideoId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) return null;
        String path = uri.getPath();
        if (path == null) return null;
        String[] segments = path.split("/");
        if (segments.length >= 3) {
            return segments[segments.length - 1];
        }
        return null;
    }
}