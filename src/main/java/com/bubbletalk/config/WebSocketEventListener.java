package com.bubbletalk.config;

import com.bubbletalk.chatroom.service.ChatRoomService;
import com.bubbletalk.global.constant.RedisKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessageSendingOperations messagingTemplate;
    private final ChatRoomService chatRoomService;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        log.info("websocket connected: sessionId={}", sessionId);
        registerActiveSession(sessionId);
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        log.info("websocket disconnected: sessionId={}", event.getSessionId());
        chatRoomService.unregisterSessionFromAllRooms(event.getSessionId())
                .forEach((roomCode, currentCount) ->
                        messagingTemplate.convertAndSend("/topic/rooms/" + roomCode + "/user-count", currentCount));
        unregisterActiveSession(event.getSessionId());
    }

    private void registerActiveSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("websocket connect event without sessionId");
            return;
        }
        redisTemplate.opsForSet().add(RedisKey.CHAT_ACTIVE_SESSIONS.getPrefix(), sessionId);
        broadcastActiveSessionCount();
    }

    private void unregisterActiveSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        redisTemplate.opsForSet().remove(RedisKey.CHAT_ACTIVE_SESSIONS.getPrefix(), sessionId);
        broadcastActiveSessionCount();
    }

    private void broadcastActiveSessionCount() {
        Long size = redisTemplate.opsForSet().size(RedisKey.CHAT_ACTIVE_SESSIONS.getPrefix());
        long currentCount = size != null ? size : 0L;
        log.info("current websocket user count: {}", currentCount);
        messagingTemplate.convertAndSend("/topic/user-count", currentCount);
    }
}
