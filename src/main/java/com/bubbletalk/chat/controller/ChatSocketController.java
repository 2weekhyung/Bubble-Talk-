package com.bubbletalk.chat.controller;

import com.bubbletalk.chat.entity.ChatMessage;
import com.bubbletalk.chat.service.ChatService;
import com.bubbletalk.chatroom.service.ChatRoomService;
import com.bubbletalk.global.exception.BusinessException;
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
        return chatService.processMessage(content, clientIp, guestId, clientId);
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
    }

    @MessageMapping("/rooms/{roomCode}/join")
    public void joinRoom(@DestinationVariable String roomCode, SimpMessageHeaderAccessor headerAccessor) {
        long currentCount = chatRoomService.registerSession(
                roomCode,
                headerAccessor.getSessionId(),
                getRequesterId(headerAccessor)
        );
        messagingTemplate.convertAndSend("/topic/rooms/" + roomCode + "/user-count", currentCount);
    }

    @MessageMapping("/rooms/{roomCode}/leave")
    public void leaveRoom(@DestinationVariable String roomCode, SimpMessageHeaderAccessor headerAccessor) {
        long currentCount = chatRoomService.unregisterSession(roomCode, headerAccessor.getSessionId());
        messagingTemplate.convertAndSend("/topic/rooms/" + roomCode + "/user-count", currentCount);
    }

    private String getRequesterId(SimpMessageHeaderAccessor headerAccessor) {
        String clientId = headerAccessor.getFirstNativeHeader("clientId");
        var sessionAttributes = headerAccessor.getSessionAttributes();
        String clientIp = sessionAttributes != null ? (String) sessionAttributes.get("client-ip") : null;
        String guestId = sessionAttributes != null ? (String) sessionAttributes.get("guest-id") : null;
        if (clientIp == null) {
            clientIp = headerAccessor.getSessionId();
        }
        return getRequesterId(guestId, clientId, clientIp);
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
        return ChatMessage.create("SYSTEM", e.getMessage());
    }
}
