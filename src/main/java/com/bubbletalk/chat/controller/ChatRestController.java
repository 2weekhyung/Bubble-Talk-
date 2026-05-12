package com.bubbletalk.chat.controller;

import com.bubbletalk.base.dto.BaseResDto;
import com.bubbletalk.chat.entity.ChatMessage;
import com.bubbletalk.chat.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Chat", description = "익명 채팅 관련 API")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatRestController {

    private final ChatService chatService;

    /**
     * [GET] /api/chat/active
     * 현재 Redis에 남아있는(10초가 지나지 않은) 활성 메시지들을 조회합니다.
     */
    @Operation(summary = "활성 채팅 메시지 조회", description = "현재 화면에 떠다녀야 하는 (삭제되지 않은) 모든 채팅 메시지를 가져옵니다.")
    @GetMapping("/active")
    public ResponseEntity<BaseResDto> getActiveMessages() {
        List<ChatMessage> activeMessages = chatService.getActiveMessages();
        return ResponseEntity.ok(BaseResDto.ok(activeMessages));
    }
}
