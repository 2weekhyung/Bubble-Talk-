package com.bubbletalk.config;

import com.bubbletalk.chatroom.service.ChatRoomService;
import com.bubbletalk.global.constant.RedisKey;
import com.bubbletalk.securitylog.entity.EventType;
import com.bubbletalk.securitylog.entity.Severity;
import com.bubbletalk.securitylog.service.SecurityEventLogService;
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
    private final ActiveWebSocketSessionRegistry activeSessionRegistry;
    private final SecurityEventLogService securityEventLogService;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        log.info("websocket connected: sessionId={}", sessionId);
        registerActiveSession(sessionId);
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        log.info("websocket disconnected: sessionId={}", event.getSessionId());
        var roomActors = chatRoomService.getSessionRoomActors(event.getSessionId());
        chatRoomService.unregisterSessionFromAllRooms(event.getSessionId())
                .forEach((roomCode, currentCount) -> {
                    String actor = roomActors != null ? roomActors.get(roomCode) : null;
                    securityEventLogService.logEvent(
                            EventType.ROOM_LEAVE,
                            Severity.INFO,
                            roomCode,
                            actor,
                            null,
                            null,
                            "WebSocket:SessionDisconnectEvent",
                            "disconnect로 사용자 채팅방 퇴장"
                    );
                    securityEventLogService.logEvent(
                            EventType.SYSTEM_MESSAGE_SEND,
                            Severity.INFO,
                            roomCode,
                            actor,
                            null,
                            null,
                            "WebSocket:/topic/rooms/" + roomCode + "/bubbles",
                            "disconnect 퇴장 시스템 메시지 전송"
                    );
                    messagingTemplate.convertAndSend(
                            "/topic/rooms/" + roomCode + "/bubbles",
                            com.bubbletalk.chat.entity.ChatMessage.system(
                                    roomCode,
                                    formatAnonymousName(actor) + "님이 나갔습니다."
                            )
                    );
                    messagingTemplate.convertAndSend("/topic/rooms/" + roomCode + "/user-count", currentCount);
                });
        unregisterActiveSession(event.getSessionId());
    }

    private String formatAnonymousName(String requesterId) {
        if (requesterId == null || requesterId.isBlank()) {
            return "익명 사용자";
        }
        String value = requesterId.contains(":")
                ? requesterId.substring(requesterId.indexOf(':') + 1)
                : requesterId;
        String shortId = value.length() > 4 ? value.substring(value.length() - 4) : value;
        return "익명 " + shortId.toUpperCase();
    }

    private void registerActiveSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("websocket connect event without sessionId");
            return;
        }
        activeSessionRegistry.register(sessionId);
        redisTemplate.opsForSet().add(RedisKey.CHAT_ACTIVE_SESSIONS.getPrefix(), sessionId);
        broadcastActiveSessionCount();
    }

    private void unregisterActiveSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        activeSessionRegistry.unregister(sessionId);
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
