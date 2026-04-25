package com.bubbletalk.menu.scheduler;

import com.bubbletalk.menu.controller.MenuSocketController;
import com.bubbletalk.menu.service.MenuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MenuScheduler {

    private final MenuService menuService;
    private final MenuSocketController socketController;

    /**
     * [정산] 매일 12시 0분 0초에 실행
     * Redis 데이터를 DB로 이관하고 소켓으로 정산 완료를 알립니다.
     */
    @Scheduled(cron = "0 0 12 * * *")
    public void finishLunchVote() {
        log.info("점심 메뉴 투표 정산을 시작합니다...");
        
        // 1. Redis -> DB 이관
        menuService.syncRedisToDb();

        // 2. 최신 순위 브로드캐스팅 (정산 후 빈 리스트 또는 결과 전송)
        socketController.broadcastMenuUpdate();
        
        log.info("점심 메뉴 투표 정산이 완료되었습니다.");
    }

    /**
     * [알림] 매일 11시 0분 0초에 실행
     * 투표 시작을 알리는 로직을 추가할 수 있습니다.
     */
    @Scheduled(cron = "0 0 11 * * *")
    public void startLunchVote() {
        log.info("점심 메뉴 투표 타임어택이 시작되었습니다! (11:00 ~ 12:00)");
        // 필요 시 소켓을 통해 "투표 시작!" 메시지를 보낼 수 있습니다.
    }
}
