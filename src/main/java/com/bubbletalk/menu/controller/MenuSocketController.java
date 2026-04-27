package com.bubbletalk.menu.controller;

import com.bubbletalk.menu.dto.res.DailyMenuResDto;
import com.bubbletalk.menu.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * [WebSocket 전용 컨트롤러]
 * 서버가 접속 중인 모든 클라이언트(사용자)에게 실시간 데이터를 푸시할 때 사용합니다.
 */
@Controller
@RequiredArgsConstructor
public class MenuSocketController {

    private final MenuService menuService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * [개념] 브로드캐스팅(Broadcasting)
     * 누군가 새 메뉴를 추가하거나 투표를 하면, 서버에서 이 메서드를 호출합니다.
     * 호출 시 /topic/menus를 구독 중인 모든 사용자에게 최신 랭킹 리스트를 즉시 전송합니다.
     */
    public void broadcastMenuUpdate() {
        // 1. Service를 통해 DB 및 Redis에서 최신 순위 데이터를 가져옵니다.
        DailyMenuResDto topMenus = menuService.getTopRankings();
        
        // 2. "/topic/menus" 채널로 데이터를 쏩니다.
        messagingTemplate.convertAndSend("/topic/menus", topMenus);
    }

    /**
     * [시스템 메시지 브로드캐스팅]
     * 새 메뉴 등록 알림 등을 채팅창(버블)에 띄워 모든 사용자에게 알립니다.
     */
    public void broadcastSystemMessage(String content) {
        // 채팅 메시지 규격에 맞춰 전송 (간단하게 맵이나 익명 클래스로 전송 가능)
        // ChatMessage 엔티티나 DTO 구조에 맞춥니다.
        messagingTemplate.convertAndSend("/topic/bubbles", java.util.Map.of(
            "content", content,
            "sender", "SYSTEM",
            "isSystem", true
        ));
    }
}
