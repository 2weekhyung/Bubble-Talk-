package com.bubbletalk.chat.service;

import com.bubbletalk.chat.entity.ChatMessage;
import com.bubbletalk.security.entity.ForbiddenWord;
import com.bubbletalk.security.repository.ForbiddenWordRepository;
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

    private final ForbiddenWordRepository forbiddenWordRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String RATE_LIMIT_KEY_PREFIX = "chat:ratelimit:";

    /**
     * [메시지 처리 로직]
     * 채팅 내용에 금칙어가 있으면 ***로 바꾸고, 도배 여부를 확인합니다.
     */
    public ChatMessage processMessage(String content, String senderIp) {
        
        // 1. [도배 방지] Redis를 이용한 Rate Limiting
        // 1초에 3번 이상 보내면 시스템 메시지로 차단 알림을 보냅니다.
        if (isRateLimited(senderIp)) {
            log.warn("도배 감지: IP={}", senderIp);
            return ChatMessage.create("SYSTEM", "⚠️ 메시지 전송이 너무 빠릅니다. (1초당 3회 제한)");
        }

        // 2. [금칙어 필터링]
        // DB에서 관리자가 등록한 금칙어 목록을 가져옵니다.
        List<String> forbiddenWords = forbiddenWordRepository.findAll()
                .stream()
                .map(ForbiddenWord::getWord)
                .toList();

        // 사용자가 보낸 문장에서 금칙어를 찾아 ***로 치환합니다.
        String filteredContent = content;
        boolean isFiltered = false;
        
        for (String word : forbiddenWords) {
            if (filteredContent.contains(word)) {
                log.info("금칙어 감지됨: '{}' (작성자 IP: {})", word, senderIp);
                String replacement = "*".repeat(word.length());
                filteredContent = filteredContent.replace(word, replacement);
                isFiltered = true;
            }
        }

        if (isFiltered) {
            log.info("필터링 적용 완료: {} -> {}", content, filteredContent);
        }
        
        // 가공이 완료된 메시지 객체를 반환 (이후 소켓 컨트롤러를 통해 모두에게 전송됨)
        return ChatMessage.create(senderIp, filteredContent);
    }

    /**
     * [도배 감지 알고리즘]
     * Redis의 increment 기능을 사용하여 특정 IP의 1초 내 요청 횟수를 셉니다.
     */
    private boolean isRateLimited(String ip) {
        String key = RATE_LIMIT_KEY_PREFIX + ip;
        
        // Redis에서 이 키의 값을 1 올림
        Long count = redisTemplate.opsForValue().increment(key);
        
        if (count != null && count == 1) {
            // 처음 요청한 거라면, 1초 뒤에 이 기록이 자동으로 지워지도록 설정 (유효기간 1초)
            redisTemplate.expire(key, 1, TimeUnit.SECONDS);
        }
        
        // 1초 내에 3번을 초과해서 요청했다면 true 반환 (도배임)
        return count != null && count > 3;
    }
}
