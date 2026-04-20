package com.bubbletalk.controller.api;

import com.bubbletalk.entity.DailyMenu;
import com.bubbletalk.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * [WebSocket 전용 컨트롤러]
 * 서버가 모든 클라이언트에게 실시간 데이터를 보낼 때 사용합니다.
 */
@Controller
@RequiredArgsConstructor
public class MenuSocketController {

    private final MenuService menuService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * [개념] 브로드캐스팅(Broadcasting)
     * 누군가 메뉴를 추가하거나 투표하면, 서버에서 이 메서드를 호출하여
     * /topic/menus를 구독 중인 모든 사용자에게 최신 랭킹 리스트를 쏴줍니다.
     */
    public void broadcastMenuUpdate() {
        // DB에서 최신 순위를 가져옵니다.
        List<DailyMenu> topMenus = menuService.getTopRankings();
        
        // 구독 중인 클라이언트들에게 데이터를 실시간으로 보냅니다.
        // 클라이언트(main.js)는 "/topic/menus" 경로를 지켜보고 있습니다.
        messagingTemplate.convertAndSend("/topic/menus", topMenus);
    }
}
