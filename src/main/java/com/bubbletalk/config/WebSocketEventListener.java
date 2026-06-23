package com.bubbletalk.config;

import com.bubbletalk.chatroom.service.ChatRoomService;
import com.bubbletalk.global.constant.RedisKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
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
        log.info("websocket connected");
        updateUserCount(1);
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        log.info("websocket disconnected: sessionId={}", event.getSessionId());
        chatRoomService.unregisterSessionFromAllRooms(event.getSessionId())
                .forEach((roomCode, currentCount) ->
                        messagingTemplate.convertAndSend("/topic/rooms/" + roomCode + "/user-count", currentCount));
        updateUserCount(-1);
    }

    private void updateUserCount(int delta) {
        String key = RedisKey.CHAT_USER_COUNT.getPrefix();
        Long currentCount = redisTemplate.opsForValue().increment(key, delta);

        if (currentCount != null && currentCount < 0) {
            redisTemplate.opsForValue().set(key, 0);
            currentCount = 0L;
        }

        log.info("current websocket user count: {}", currentCount);
        messagingTemplate.convertAndSend("/topic/user-count", currentCount);
    }
}
