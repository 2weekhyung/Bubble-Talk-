package com.bubbletalk.menu.scheduler;

import com.bubbletalk.global.constant.RedisKey;
import com.bubbletalk.menu.controller.MenuSocketController;
import com.bubbletalk.menu.service.MenuService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class MenuScheduler {

    private final MenuService menuService;
    private final MenuSocketController socketController;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * [서버 시작 시 초기화]
     * 서버가 재시작되어도 현재 시간이 투표 시간(09:00~14:00)이라면 상태를 OPEN으로 설정합니다.
     */
    @PostConstruct
    public void init() {
        LocalTime now = LocalTime.now();
        if (now.isAfter(LocalTime.of(9, 0)) && now.isBefore(LocalTime.of(14, 0))) {
            redisTemplate.opsForValue().set(RedisKey.LUNCH_EVENT_STATUS.getPrefix(), "OPEN");
            log.info("[초기화] 현재 시간({})은 투표 시간입니다. 상태를 OPEN으로 설정합니다.", now);
        } else {
            // 그 외 시간대에는 CLOSED로 설정하되, 이미 데이터가 있을 수 있으므로 상태만 변경
            redisTemplate.opsForValue().set(RedisKey.LUNCH_EVENT_STATUS.getPrefix(), "CLOSED");
            log.info("[초기화] 현재 시간({})은 투표 시간이 아닙니다. 상태를 CLOSED로 설정합니다.", now);
        }
    }

    /**
     * [정산] 매일 14시 0분 0초에 실행
     * Redis 데이터를 DB로 이관하고 소켓으로 정산 완료를 알립니다.
     */
    @Scheduled(cron = "0 0 14 * * *")
    public void finishLunchVote() {
        log.info("점심 메뉴 투표 정산을 시작합니다...");
        
        // 1. 이벤트 상태 종료
        redisTemplate.opsForValue().set(RedisKey.LUNCH_EVENT_STATUS.getPrefix(), "CLOSED");

        // 2. Redis -> DB 이관
        menuService.syncRedisToDb();

        // 3. 최신 순위 브로드캐스팅 (정산 후 빈 리스트 또는 결과 전송)
        socketController.broadcastMenuUpdate();
        socketController.broadcastSystemMessage("🏁 점심 전쟁이 종료되었습니다! 정산 결과를 확인하세요.");
        
        log.info("점심 메뉴 투표 정산이 완료되었습니다.");
    }

    /**
     * [알림] 매일 9시 0분 0초에 실행
     * 투표 시작을 알리고 이벤트를 활성화합니다.
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void startLunchVote() {
        log.info("점심 메뉴 투표 타임어택이 시작되었습니다! (09:00 ~ 14:00)");
        
        // 1. 이벤트 상태 활성화
        redisTemplate.opsForValue().set(RedisKey.LUNCH_EVENT_STATUS.getPrefix(), "OPEN");

        // 2. 소켓 알림
        socketController.broadcastSystemMessage("🔥 점심 전쟁 시작! (09:00 ~ 14:00)");
        socketController.broadcastMenuUpdate();
    }
}
