package com.bubbletalk.config;

import com.bubbletalk.global.constant.RedisKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * [웹소켓 이벤트 리스너]
 * 웹소켓 연결 및 해제 시점을 감지하여 실시간 접속자 수를 관리합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessageSendingOperations messagingTemplate;

    /**
     * [연결 시 이벤트]
     * 사용자가 웹소켓에 접속하면 Redis 카운트를 증가시키고 브로드캐스팅합니다.
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        log.info("새로운 웹소켓 연결 감지");
        updateUserCount(1);
    }

    /**
     * [연결 해제 시 이벤트]
     * 사용자가 브라우저를 닫거나 연결을 끊으면 Redis 카운트를 감소시키고 브로드캐스팅합니다.
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        log.info("웹소켓 연결 종료 감지");
        updateUserCount(-1);
    }

    /**
     * [접속자 수 업데이트 및 전송]
     * Redis의 atomic increment 연산을 사용하여 숫자를 정밀하게 관리하고
     * 모든 클라이언트에게 /topic/user-count 채널로 숫자를 보냅니다.
     */
    private void updateUserCount(int delta) {
        String key = RedisKey.CHAT_USER_COUNT.getPrefix();
        
        // Redis 카운트 업데이트
        Long currentCount = redisTemplate.opsForValue().increment(key, delta);
        
        // 음수가 되는 경우(드문 케이스)를 대비하여 0으로 보정
        if (currentCount != null && currentCount < 0) {
            redisTemplate.opsForValue().set(key, 0);
            currentCount = 0L;
        }

        log.info("현재 실시간 접속자 수: {}", currentCount);

        // 모든 클라이언트에게 브로드캐스팅
        messagingTemplate.convertAndSend("/topic/user-count", currentCount);
    }
}
