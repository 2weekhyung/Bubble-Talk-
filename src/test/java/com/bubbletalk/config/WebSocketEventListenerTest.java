package com.bubbletalk.config;

import com.bubbletalk.chatroom.service.ChatRoomService;
import com.bubbletalk.global.constant.RedisKey;
import com.bubbletalk.securitylog.service.SecurityEventLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketEventListenerTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private SetOperations<String, Object> setOperations;

    @Mock
    private SimpMessageSendingOperations messagingTemplate;

    @Mock
    private ChatRoomService chatRoomService;

    @Mock
    private ActiveWebSocketSessionRegistry activeSessionRegistry;

    @Mock
    private SecurityEventLogService securityEventLogService;

    @Mock
    private SessionDisconnectEvent disconnectEvent;

    @Test
    void disconnectUsesActiveSessionSetForGlobalUserCount() {
        when(disconnectEvent.getSessionId()).thenReturn("session-1");
        when(chatRoomService.unregisterSessionFromAllRooms("session-1")).thenReturn(Map.of());
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.size(RedisKey.CHAT_ACTIVE_SESSIONS.getPrefix())).thenReturn(2L);

        WebSocketEventListener listener =
                new WebSocketEventListener(
                        redisTemplate,
                        messagingTemplate,
                        chatRoomService,
                        activeSessionRegistry,
                        securityEventLogService
                );
        listener.handleWebSocketDisconnectListener(disconnectEvent);

        verify(activeSessionRegistry).unregister("session-1");
        verify(setOperations).remove(RedisKey.CHAT_ACTIVE_SESSIONS.getPrefix(), "session-1");
        verify(messagingTemplate).convertAndSend("/topic/user-count", 2L);
    }
}
