package com.bubbletalk.chat.controller;

import com.bubbletalk.chat.entity.ChatMessage;
import com.bubbletalk.chat.service.ChatService;
import com.bubbletalk.chatroom.service.ChatRoomService;
import com.bubbletalk.securitylog.entity.EventType;
import com.bubbletalk.securitylog.entity.Severity;
import com.bubbletalk.securitylog.service.SecurityEventLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSocketControllerTest {

    @Mock
    private ChatService chatService;

    @Mock
    private ChatRoomService chatRoomService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private SecurityEventLogService securityEventLogService;

    @Test
    void normalChatMessageIsLoggedAsMessageSend() {
        SimpMessageHeaderAccessor accessor = accessor("session-1", "guest-1", "10.0.0.1");
        when(chatService.processMessage("hello", "10.0.0.1", "guest-1", "client-1"))
                .thenReturn(ChatMessage.create("10.0.0.1", "guest-1", "client-1", "hello"));

        controller().send("hello", accessor);

        verify(securityEventLogService).logEvent(
                EventType.MESSAGE_SEND,
                Severity.INFO,
                null,
                "guest-1",
                "10.0.0.1",
                null,
                "WebSocket:/app/chat/send",
                "채팅 메시지 전송"
        );
    }

    @Test
    void roomJoinSystemMessageIsLoggedAsSystemMessageSend() {
        SimpMessageHeaderAccessor accessor = accessor("session-1", "guest-1", "10.0.0.1");
        when(chatRoomService.registerSession("ROOM0001", "session-1", "guest:guest-1")).thenReturn(1L);

        controller().joinRoom("ROOM0001", accessor);

        verify(securityEventLogService).logEvent(
                EventType.ROOM_ENTER,
                Severity.INFO,
                "ROOM0001",
                "guest-1",
                "10.0.0.1",
                null,
                "WebSocket:/app/rooms/ROOM0001/join",
                "사용자 채팅방 입장"
        );
        verify(securityEventLogService).logEvent(
                EventType.SYSTEM_MESSAGE_SEND,
                Severity.INFO,
                "ROOM0001",
                "guest-1",
                "10.0.0.1",
                null,
                "WebSocket:/topic/rooms/ROOM0001/bubbles",
                "입장 시스템 메시지 전송"
        );
    }

    private ChatSocketController controller() {
        return new ChatSocketController(chatService, chatRoomService, messagingTemplate, securityEventLogService);
    }

    private SimpMessageHeaderAccessor accessor(String sessionId, String guestId, String ipAddress) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        accessor.setSessionId(sessionId);
        accessor.setNativeHeader("clientId", "client-1");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("guest-id", guestId);
        attributes.put("client-ip", ipAddress);
        accessor.setSessionAttributes(attributes);
        return accessor;
    }
}
