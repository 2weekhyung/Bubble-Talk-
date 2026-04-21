package com.bubbletalk.menu.controller;

import com.bubbletalk.menu.entity.DailyMenu;
import com.bubbletalk.menu.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * [WebSocket ?„ìš© ì»¨íŠ¸ë¡¤ëŸ¬]
 * ?œë²„ê°€ ëª¨ë“  ?´ë¼?´ì–¸?¸ì—ê²??¤ì‹œê°??°ì´?°ë? ë³´ë‚¼ ???¬ìš©?©ë‹ˆ??
 */
@Controller
@RequiredArgsConstructor
public class MenuSocketController {

    private final MenuService menuService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * [ê°œë…] ë¸Œë¡œ?œìº?¤íŒ…(Broadcasting)
     * ?„êµ°ê°€ ë©”ë‰´ë¥?ì¶”ê??˜ê±°???¬í‘œ?˜ë©´, ?œë²„?ì„œ ??ë©”ì„œ?œë? ?¸ì¶œ?˜ì—¬
     * /topic/menusë¥?êµ¬ë… ì¤‘ì¸ ëª¨ë“  ?¬ìš©?ì—ê²?ìµœì‹  ??‚¹ ë¦¬ìŠ¤?¸ë? ?´ì¤?ˆë‹¤.
     */
    public void broadcastMenuUpdate() {
        // DB?ì„œ ìµœì‹  ?œìœ„ë¥?ê°€?¸ì˜µ?ˆë‹¤.
        List<DailyMenu> topMenus = menuService.getTopRankings();
        
        // êµ¬ë… ì¤‘ì¸ ?´ë¼?´ì–¸?¸ë“¤?ê²Œ ?°ì´?°ë? ?¤ì‹œê°„ìœ¼ë¡?ë³´ëƒ…?ˆë‹¤.
        // ?´ë¼?´ì–¸??main.js)??"/topic/menus" ê²½ë¡œë¥?ì§€ì¼œë³´ê³??ˆìŠµ?ˆë‹¤.
        messagingTemplate.convertAndSend("/topic/menus", topMenus);
    }
}
