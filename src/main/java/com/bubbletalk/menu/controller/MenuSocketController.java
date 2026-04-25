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
        // 클라이언트(main.js)는 이 경로를 구독(subscribe)하고 있다가 데이터를 받으면 화면을 갱신합니다.
        messagingTemplate.convertAndSend("/topic/menus", topMenus);
    }
}
