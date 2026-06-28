package com.bubbletalk.chat.controller;

import com.bubbletalk.chat.entity.ChatMessage;
import com.bubbletalk.chat.service.ChatService;
import com.bubbletalk.chatroom.service.ChatRoomService;
import com.bubbletalk.global.exception.BusinessException;
import com.bubbletalk.securitylog.entity.EventType;
import com.bubbletalk.securitylog.entity.Severity;
import com.bubbletalk.securitylog.service.SecurityEventLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatSocketController {

    private final ChatService chatService;
    private final ChatRoomService chatRoomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final SecurityEventLogService securityEventLogService;

    @MessageMapping("/chat/send")
    @SendTo("/topic/bubbles")
    public ChatMessage send(String content, SimpMessageHeaderAccessor headerAccessor) {
        String clientId = headerAccessor.getFirstNativeHeader("clientId");

        var sessionAttributes = headerAccessor.getSessionAttributes();
        String clientIp = sessionAttributes != null ? (String) sessionAttributes.get("client-ip") : null;
        String guestId = sessionAttributes != null ? (String) sessionAttributes.get("guest-id") : null;

        if (clientIp == null) {
            clientIp = headerAccessor.getSessionId();
        }

        log.info("chat message received: guestId={}, clientId={}, ip={}, content={}", guestId, clientId, clientIp, content);
        ChatMessage chatMessage = chatService.processMessage(content, clientIp, guestId, clientId);
        logMessageEvent(chatMessage, clientIp, guestId, null, "채팅 메시지 전송");
        return chatMessage;
    }

    @MessageMapping("/rooms/{roomCode}/chat/send")
    public void sendToRoom(@DestinationVariable String roomCode,
                           String content,
                           SimpMessageHeaderAccessor headerAccessor) {
        String clientId = headerAccessor.getFirstNativeHeader("clientId");

        var sessionAttributes = headerAccessor.getSessionAttributes();
        String clientIp = sessionAttributes != null ? (String) sessionAttributes.get("client-ip") : null;
        String guestId = sessionAttributes != null ? (String) sessionAttributes.get("guest-id") : null;

        if (clientIp == null) {
            clientIp = headerAccessor.getSessionId();
        }

        String sessionId = headerAccessor.getSessionId();
        String requesterId = getRequesterId(guestId, clientId, clientIp);
        long currentCount = chatRoomService.registerSession(roomCode, sessionId, requesterId);

        ChatMessage chatMessage = chatService.processMessage(content, clientIp, guestId, clientId, roomCode);
        messagingTemplate.convertAndSend("/topic/rooms/" + roomCode + "/bubbles", chatMessage);
        messagingTemplate.convertAndSend("/topic/rooms/" + roomCode + "/user-count", currentCount);
        logMessageEvent(chatMessage, clientIp, guestId, roomCode, "채팅 메시지 전송");
    }

    @MessageMapping("/rooms/{roomCode}/join")
    public void joinRoom(@DestinationVariable String roomCode, SimpMessageHeaderAccessor headerAccessor) {
        String requesterId = getRequesterId(headerAccessor);
        String guestId = getGuestId(headerAccessor);
        String clientIp = getClientIp(headerAccessor);
        long currentCount = chatRoomService.registerSession(
                roomCode,
                headerAccessor.getSessionId(),
                requesterId
        );
        securityEventLogService.logEvent(
                EventType.ROOM_ENTER,
                Severity.INFO,
                roomCode,
                guestId,
                clientIp,
                null,
                "WebSocket:/app/rooms/" + roomCode + "/join",
                "사용자 채팅방 입장"
        );
        logSystemMessage(roomCode, guestId, clientIp, "입장 시스템 메시지 전송");
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + roomCode + "/bubbles",
                createRoomSystemMessage(roomCode, requesterId, "님이 입장했습니다.")
        );
        messagingTemplate.convertAndSend("/topic/rooms/" + roomCode + "/user-count", currentCount);
    }

    @MessageMapping("/rooms/{roomCode}/leave")
    public void leaveRoom(@DestinationVariable String roomCode, SimpMessageHeaderAccessor headerAccessor) {
        String requesterId = getRequesterId(headerAccessor);
        String guestId = getGuestId(headerAccessor);
        String clientIp = getClientIp(headerAccessor);
        long currentCount = chatRoomService.unregisterSession(roomCode, headerAccessor.getSessionId());
        securityEventLogService.logEvent(
                EventType.ROOM_LEAVE,
                Severity.INFO,
                roomCode,
                guestId,
                clientIp,
                null,
                "WebSocket:/app/rooms/" + roomCode + "/leave",
                "사용자 채팅방 퇴장"
        );
        logSystemMessage(roomCode, guestId, clientIp, "퇴장 시스템 메시지 전송");
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + roomCode + "/bubbles",
                createRoomSystemMessage(roomCode, requesterId, "님이 나갔습니다.")
        );
        messagingTemplate.convertAndSend("/topic/rooms/" + roomCode + "/user-count", currentCount);
    }

    private ChatMessage createRoomSystemMessage(String roomCode, String requesterId, String suffix) {
        return ChatMessage.system(roomCode, formatAnonymousName(requesterId) + suffix);
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

    private String getRequesterId(SimpMessageHeaderAccessor headerAccessor) {
        String clientId = headerAccessor.getFirstNativeHeader("clientId");
        var sessionAttributes = headerAccessor.getSessionAttributes();
        String clientIp = getClientIp(headerAccessor);
        String guestId = getGuestId(headerAccessor);
        if (clientIp == null) {
            clientIp = headerAccessor.getSessionId();
        }
        return getRequesterId(guestId, clientId, clientIp);
    }

    private String getGuestId(SimpMessageHeaderAccessor headerAccessor) {
        var sessionAttributes = headerAccessor.getSessionAttributes();
        return sessionAttributes != null ? (String) sessionAttributes.get("guest-id") : null;
    }

    private String getClientIp(SimpMessageHeaderAccessor headerAccessor) {
        var sessionAttributes = headerAccessor.getSessionAttributes();
        return sessionAttributes != null ? (String) sessionAttributes.get("client-ip") : null;
    }

    private String getRequesterId(String guestId, String clientId, String clientIp) {
        if (guestId != null && !guestId.isBlank()) {
            return "guest:" + guestId;
        }
        if (clientId != null && !clientId.isBlank()) {
            return "client:" + clientId;
        }
        return "ip:" + clientIp;
    }

    @MessageExceptionHandler(BusinessException.class)
    @SendToUser("/queue/errors")
    public ChatMessage handleException(BusinessException e) {
        return ChatMessage.system(null, e.getMessage());
    }

    private void logMessageEvent(ChatMessage chatMessage, String clientIp, String guestId, String roomCode, String reason) {
        EventType eventType = "SYSTEM".equals(chatMessage.getMessageType())
                ? EventType.SYSTEM_MESSAGE_SEND
                : EventType.MESSAGE_SEND;
        securityEventLogService.logEvent(
                eventType,
                Severity.INFO,
                roomCode,
                guestId,
                clientIp,
                null,
                roomCode != null ? "WebSocket:/app/rooms/" + roomCode + "/chat/send" : "WebSocket:/app/chat/send",
                reason
        );
    }

    private void logSystemMessage(String roomCode, String guestId, String clientIp, String reason) {
        securityEventLogService.logEvent(
                EventType.SYSTEM_MESSAGE_SEND,
                Severity.INFO,
                roomCode,
                guestId,
                clientIp,
                null,
                "WebSocket:/topic/rooms/" + roomCode + "/bubbles",
                reason
        );
    }
}
