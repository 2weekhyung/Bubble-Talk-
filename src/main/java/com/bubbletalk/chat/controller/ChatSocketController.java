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
    @SendTo("/topic/bubbles")
    public ChatMessage send(String content, SimpMessageHeaderAccessor headerAccessor) {
        // 인터셉터에서 저장한 실제 IP를 가져옵니다.
        String clientIp = (String) headerAccessor.getSessionAttributes().get("client-ip");
        
        // IP가 없는 예외 상황(직접 연결 등)에는 세션 ID를 식별자로 사용합니다.
        if (clientIp == null) {
            clientIp = headerAccessor.getSessionId();
        }
        
        log.info("채팅 메시지 수신: IP={}, 내용={}", clientIp, content);

        // ChatService를 통해 필터링, 도배 방지, Redis 저장 후 반환
        return chatService.processMessage(content, clientIp);
    }
}
