package com.bubbletalk.chat.service;

import com.bubbletalk.chat.entity.ChatMessage;
import com.bubbletalk.global.constant.RedisKey;
import com.bubbletalk.global.exception.BusinessException;
import com.bubbletalk.security.service.ForbiddenWordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ForbiddenWordService forbiddenWordService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(forbiddenWordService, redisTemplate);
    }

    @Test
    @DisplayName("blank chat message is rejected")
    void processMessage_BlankRejected() {
        assertThrows(BusinessException.class, () -> chatService.processMessage("   ", "127.0.0.1", "guest-1", "client-1"));

        verifyNoInteractions(forbiddenWordService);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("null chat message is rejected")
    void processMessage_NullRejected() {
        assertThrows(BusinessException.class, () -> chatService.processMessage(null, "127.0.0.1", "guest-1", "client-1"));

        verifyNoInteractions(forbiddenWordService);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("chat message over 200 characters is rejected")
    void processMessage_TooLongRejected() {
        String content = "a".repeat(201);

        assertThrows(BusinessException.class, () -> chatService.processMessage(content, "127.0.0.1", "guest-1", "client-1"));

        verifyNoInteractions(forbiddenWordService);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("script tag chat message is rejected")
    void processMessage_ScriptRejected() {
        assertThrows(BusinessException.class, () -> chatService.processMessage("<script>alert(1)</script>", "127.0.0.1", "guest-1", "client-1"));

        verifyNoInteractions(forbiddenWordService);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("normal chat message is filtered, saved to Redis, and returned")
    void processMessage_NormalSaved() {
        mockRateLimit("guest:guest-1", null, 1L);
        when(forbiddenWordService.getForbiddenWords()).thenReturn(List.of("bad"));

        ChatMessage result = chatService.processMessage("  hello bad  ", "127.0.0.1", "guest-1", "client-1");

        assertEquals("hello ***", result.getContent());
        assertEquals("guest-1", result.getSenderGuestId());
        assertEquals("client-1", result.getSenderClientId());

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(valueOperations).set(startsWith(RedisKey.CHAT_BUBBLE.getPrefix()), captor.capture(), eq(10L), eq(TimeUnit.SECONDS));
        assertEquals("hello ***", captor.getValue().getContent());
    }

    @Test
    @DisplayName("same guest is rate limited after too many messages")
    void processMessage_RateLimitedByGuest() {
        mockRateLimit("guest:guest-1", null, 6L);

        assertThrows(BusinessException.class, () -> chatService.processMessage("hello", "127.0.0.1", "guest-1", "client-1"));

        verify(valueOperations).set(RedisKey.CHAT_RATELIMIT.with("mute:guest:guest-1"), "1", 30L, TimeUnit.SECONDS);
        verify(redisTemplate).delete(RedisKey.CHAT_RATELIMIT.with("window:guest:guest-1"));
        verifyNoInteractions(forbiddenWordService);
    }

    @Test
    @DisplayName("same guest cannot repeat identical message")
    void processMessage_DuplicateMessageRejected() {
        mockRateLimit("guest:guest-1", "hello", null);

        assertThrows(BusinessException.class, () -> chatService.processMessage(" hello ", "127.0.0.1", "guest-1", "client-1"));

        verify(redisTemplate, never()).opsForZSet();
        verifyNoInteractions(forbiddenWordService);
    }

    private void mockRateLimit(String actorKey, Object lastMessage, Long count) {
        String muteKey = RedisKey.CHAT_RATELIMIT.with("mute:" + actorKey);
        String windowKey = RedisKey.CHAT_RATELIMIT.with("window:" + actorKey);
        String lastMessageKey = RedisKey.CHAT_RATELIMIT.with("last:" + actorKey);

        when(redisTemplate.hasKey(muteKey)).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(lastMessageKey)).thenReturn(lastMessage);
        if (count != null) {
            when(valueOperations.increment(windowKey)).thenReturn(count);
        }
    }
}
