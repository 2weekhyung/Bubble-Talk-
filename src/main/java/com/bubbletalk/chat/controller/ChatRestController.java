package com.bubbletalk.chat.controller;

import com.bubbletalk.base.dto.BaseResDto;
import com.bubbletalk.chat.entity.ChatMessage;
import com.bubbletalk.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * [채팅 관련 REST API 컨트롤러]
 * 초기 진입 시 필요한 채팅 데이터를 제공합니다.
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatRestController {

    private final ChatService chatService;

    /**
     * [GET] /api/chat/active
     * 현재 Redis에 남아있는(10초가 지나지 않은) 활성 메시지들을 조회합니다.
     */
    @GetMapping("/active")
    public ResponseEntity<BaseResDto> getActiveMessages() {
        List<ChatMessage> activeMessages = chatService.getActiveMessages();
        return ResponseEntity.ok(BaseResDto.ok(activeMessages));
    }
}
