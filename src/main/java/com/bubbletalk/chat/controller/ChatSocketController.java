package com.bubbletalk.chat.controller;

import com.bubbletalk.chat.entity.ChatMessage;
import com.bubbletalk.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatSocketController {

    private final ChatService chatService;

    /**
     * [채팅 발신 처리]
     * 사용자가 /app/chat/send 로 메시지를 보내면 호출됩니다.
     */
    @MessageMapping("/chat/send")
    @SendTo("/topic/bubbles") // 가공된 메시지를 모든 구독자에게 방송합니다.
    public ChatMessage send(String content, SimpMessageHeaderAccessor headerAccessor) {
        // [참고] WebSocket 연결 시점의 IP를 가져오려면 인터셉터 설정이 필요하지만,
        // 여기서는 세션 ID 또는 간단한 구분자를 익명 IP 대용으로 사용할 수 있습니다.
        String sessionId = headerAccessor.getSessionId();
        
        log.info("새로운 채팅 메시지 수신: 세션={}, 내용={}", sessionId, content);

        // ChatService를 통해 금칙어 필터링을 거친 후 전송합니다.
        return chatService.processMessage(content, "익명(" + sessionId.substring(0, 4) + ")");
    }
}
