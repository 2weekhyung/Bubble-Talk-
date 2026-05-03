package com.bubbletalk.chat.service;

import com.bubbletalk.chat.entity.ChatMessage;
import com.bubbletalk.global.constant.RedisKey;
import com.bubbletalk.security.service.ForbiddenWordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * [실시간 채팅 서비스]
 * 메시지를 가공(필터링)하고 도배를 방지하는 역할을 합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private final ForbiddenWordService forbiddenWordService;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * [메시지 처리 로직]
     * 채팅 내용에 금칙어가 있으면 ***로 바꾸고, 도배 여부를 확인한 후 Redis에 저장합니다.
     */
    public ChatMessage processMessage(String content, String senderIp) {
        
        // 1. [도배 방지] Redis를 이용한 Rate Limiting
        if (isRateLimited(senderIp)) {
            log.warn("도배 감지: IP={}", senderIp);
            return ChatMessage.create("SYSTEM", "⚠️ 메시지 전송이 너무 빠릅니다. (1초당 3회 제한)");
        }

        // 2. [금칙어 필터링] Redis 캐시에서 목록을 가져옵니다. (고속 조회)
        List<String> forbiddenWords = forbiddenWordService.getForbiddenWords();

        String filteredContent = content;
        for (String word : forbiddenWords) {
            if (filteredContent.contains(word)) {
                String replacement = "*".repeat(word.length());
                filteredContent = filteredContent.replace(word, replacement);
            }
        }

        // 3. [메시지 객체 생성 및 Redis 저장]
        ChatMessage chatMessage = ChatMessage.create(senderIp, filteredContent);
        saveMessageToRedis(chatMessage);

        return chatMessage;
    }

    /**
     * [메시지 휘발성 관리]
     * Redis에 메시지를 저장하고 10초 뒤에 자동으로 삭제되도록 설정합니다.
     */
    private void saveMessageToRedis(ChatMessage message) {
        String messageId = java.util.UUID.randomUUID().toString();
        String key = RedisKey.CHAT_BUBBLE.with(messageId);

        // Redis에 메시지 저장 (10초 후 자동 소멸)
        redisTemplate.opsForValue().set(key, message, 10, TimeUnit.SECONDS);
        log.info("메시지 Redis 저장 완료 (TTL 10s): {}", key);
    }

    /**
     * [활성 메시지 조회]
     * 현재 Redis에 남아있는 (10초가 지나지 않은) 모든 메시지를 가져옵니다.
     * 새로운 접속자가 기존에 떠다니던 버블들을 볼 수 있게 합니다.
     */
    public List<ChatMessage> getActiveMessages() {
        // chat:bubble:* 패턴에 매칭되는 모든 키를 찾습니다.
        java.util.Set<String> keys = redisTemplate.keys(RedisKey.CHAT_BUBBLE.getPrefix() + "*");
        
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }

        // 키들에 해당하는 값(ChatMessage)을 한꺼번에 가져와 리스트로 반환합니다.
        return redisTemplate.opsForValue().multiGet(keys).stream()
                .filter(obj -> obj instanceof ChatMessage)
                .map(obj -> (ChatMessage) obj)
                .sorted((m1, m2) -> m1.getTimestamp().compareTo(m2.getTimestamp())) // 시간순 정렬
                .toList();
    }

    /**
     * [도배 감지 알고리즘]
     * Redis의 increment 기능을 사용하여 특정 IP의 1초 내 요청 횟수를 셉니다.
     */
    private boolean isRateLimited(String ip) {
        String key = RedisKey.CHAT_RATELIMIT.with(ip);

        Long count = redisTemplate.opsForValue().increment(key);
        
        if (count != null && count == 1) {
            redisTemplate.expire(key, 1, TimeUnit.SECONDS);
        }

        return count != null && count > 3;
    }
}
