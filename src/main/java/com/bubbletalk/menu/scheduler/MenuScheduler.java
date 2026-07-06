package com.bubbletalk.menu.scheduler;

import com.bubbletalk.menu.controller.MenuSocketController;
import com.bubbletalk.menu.service.MenuService;
import jakarta.annotation.PostConstruct;
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

    @PostConstruct
    public void init() {
        reconcileLunchVoteStatus();
    }

    @Scheduled(cron = "0 * * * * *")
    public void reconcileLunchVoteStatus() {
        String previousStatus = menuService.getStoredEventStatus();
        String currentStatus = menuService.refreshEventStatusBySchedule();

        if (currentStatus.equals(previousStatus)) {
            return;
        }

        if ("OPEN".equals(currentStatus)) {
            log.info("Lunch vote opened by configured event time.");
            socketController.broadcastSystemMessage("점심 메뉴 투표가 시작되었습니다.");
            socketController.broadcastMenuUpdate();
            return;
        }

        if ("OPEN".equals(previousStatus)) {
            log.info("Lunch vote closed by configured event time. Syncing results.");
            menuService.syncRedisToDb();
            socketController.broadcastMenuUpdate();
            socketController.broadcastSystemMessage("점심 메뉴 투표가 종료되었습니다. 정산 결과를 확인하세요.");
            log.info("Lunch vote result sync completed.");
        }
    }
}
