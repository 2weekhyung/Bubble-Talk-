package com.bubbletalk.chat.service;

import com.bubbletalk.chat.entity.ChatMessage;
import com.bubbletalk.global.constant.RedisKey;
import com.bubbletalk.global.exception.BusinessException;
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

    private static final int CHAT_WINDOW_SECONDS = 10;
    private static final int CHAT_MAX_MESSAGES = 5;
    private static final int CHAT_MUTE_SECONDS = 30;
    private static final int DUPLICATE_BLOCK_SECONDS = 10;
    private static final int CHAT_MAX_CONTENT_LENGTH = 200;

    private final ForbiddenWordService forbiddenWordService;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * [메시지 처리 로직]
     * 채팅 내용에 금칙어가 있으면 ***로 바꾸고, 도배 여부를 확인한 후 Redis에 저장합니다.
     */
    public ChatMessage processMessage(String content, String senderIp) {
        return processMessage(content, senderIp, null);
    }

    public ChatMessage processMessage(String content, String senderIp, String senderClientId) {
        return processMessage(content, senderIp, null, senderClientId);
    }

    public ChatMessage processMessage(String content, String senderIp, String senderGuestId, String senderClientId) {
        return processMessage(content, senderIp, senderGuestId, senderClientId, null);
    }

    public ChatMessage processMessage(String content, String senderIp, String senderGuestId, String senderClientId, String roomCode) {
        String normalizedContent = normalizeContent(content);

        validateChatRateLimit(senderIp, senderGuestId, senderClientId, normalizedContent);

        // 2. [금칙어 필터링] Redis 캐시에서 목록을 가져옵니다. (고속 조회)
        List<String> forbiddenWords = forbiddenWordService.getForbiddenWords();
        log.debug("적용 중인 금칙어 목록: {}", forbiddenWords);

        String filteredContent = normalizedContent;
        for (String word : forbiddenWords) {
            if (filteredContent.contains(word)) {
                log.info("금칙어 감지: '{}' -> 필터링 수행", word);
                String replacement = "*".repeat(word.length());
                filteredContent = filteredContent.replace(word, replacement);
            }
        }

        log.debug("필터링 전: {}, 필터링 후: {}", content, filteredContent);

        // 3. [메시지 객체 생성 및 Redis 저장]
        ChatMessage chatMessage = ChatMessage.create(senderIp, senderGuestId, senderClientId, roomCode, filteredContent);
        saveMessageToRedis(chatMessage);

        return chatMessage;
    }

    private String normalizeContent(String content) {
        if (content == null) {
            throw new BusinessException("메시지를 입력해주세요.");
        }

        String normalized = content.trim();
        if (normalized.isBlank()) {
            throw new BusinessException("메시지를 입력해주세요.");
        }

        if (normalized.length() > CHAT_MAX_CONTENT_LENGTH) {
            throw new BusinessException("메시지는 200자 이하로 입력해주세요.");
        }

        if (containsUnsafeHtml(normalized)) {
            throw new BusinessException("메시지에 사용할 수 없는 문자가 포함되어 있습니다.");
        }

        return normalized;
    }

    private boolean containsUnsafeHtml(String value) {
        String lowerValue = value.toLowerCase();
        return value.contains("<")
                || value.contains(">")
                || lowerValue.contains("&lt;")
                || lowerValue.contains("&gt;")
                || lowerValue.contains("script");
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
    private void validateChatRateLimit(String ip, String senderGuestId, String senderClientId, String content) {
        String actorKey = getChatActorKey(ip, senderGuestId, senderClientId);
        String muteKey = RedisKey.CHAT_RATELIMIT.with("mute:" + actorKey);
        String windowKey = RedisKey.CHAT_RATELIMIT.with("window:" + actorKey);
        String lastMessageKey = RedisKey.CHAT_RATELIMIT.with("last:" + actorKey);

        if (Boolean.TRUE.equals(redisTemplate.hasKey(muteKey))) {
            Long ttl = redisTemplate.getExpire(muteKey, TimeUnit.SECONDS);
            long remainingSeconds = ttl != null && ttl > 0 ? ttl : CHAT_MUTE_SECONDS;
            throw new BusinessException("도배가 감지되어 잠시 채팅이 제한되었습니다. " + remainingSeconds + "초 후 다시 시도해주세요.");
        }

        Object lastMessage = redisTemplate.opsForValue().get(lastMessageKey);
        if (content.equals(lastMessage)) {
            throw new BusinessException("같은 메시지를 연속으로 보낼 수 없습니다.");
        }

        Long count = redisTemplate.opsForValue().increment(windowKey);
        if (count != null && count == 1) {
            redisTemplate.expire(windowKey, CHAT_WINDOW_SECONDS, TimeUnit.SECONDS);
        }

        if (count != null && count > CHAT_MAX_MESSAGES) {
            redisTemplate.opsForValue().set(muteKey, "1", CHAT_MUTE_SECONDS, TimeUnit.SECONDS);
            redisTemplate.delete(windowKey);
            log.warn("채팅 도배 감지: actor={}, IP={}", actorKey, ip);
            throw new BusinessException("도배가 감지되어 30초 동안 채팅이 제한됩니다.");
        }

        redisTemplate.opsForValue().set(lastMessageKey, content, DUPLICATE_BLOCK_SECONDS, TimeUnit.SECONDS);
    }

    private String getChatActorKey(String ip, String senderGuestId, String senderClientId) {
        if (senderGuestId != null && !senderGuestId.isBlank()) {
            return "guest:" + senderGuestId;
        }
        if (senderClientId != null && !senderClientId.isBlank()) {
            return "client:" + senderClientId;
        }
        return "ip:" + ip;
    }
}
