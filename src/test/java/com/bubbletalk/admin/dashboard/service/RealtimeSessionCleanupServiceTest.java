package com.bubbletalk.admin.dashboard.service;

import com.bubbletalk.chatroom.service.ChatRoomService;
import com.bubbletalk.config.ActiveWebSocketSessionRegistry;
import com.bubbletalk.global.constant.RedisKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealtimeSessionCleanupServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private SetOperations<String, Object> setOperations;

    @Mock
    private ActiveWebSocketSessionRegistry activeSessionRegistry;

    @Mock
    private ChatRoomService chatRoomService;

    @Test
    void cleanupRemovesOnlySessionsMissingFromLocalRegistry() {
        String roomSessionsKey = RedisKey.roomSessions("ROOM0001");
        when(redisTemplate.keys("room:*:sessions")).thenReturn(Set.of(roomSessionsKey));
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(RedisKey.CHAT_ACTIVE_SESSIONS.getPrefix()))
                .thenReturn(Set.of("active-session", "stale-session"));
        when(setOperations.members(roomSessionsKey)).thenReturn(Set.of("stale-session"));
        when(activeSessionRegistry.isActive("active-session")).thenReturn(true);
        when(activeSessionRegistry.isActive("stale-session")).thenReturn(false);
        when(chatRoomService.unregisterSessionFromAllRooms("stale-session"))
                .thenReturn(Map.of("ROOM0001", 0L));
        when(setOperations.isMember(roomSessionsKey, "stale-session")).thenReturn(true);
        when(chatRoomService.unregisterSession("ROOM0001", "stale-session")).thenReturn(0L);

        var result = service().cleanupStaleSessions();

        assertEquals(2, result.getScannedSessions());
        assertEquals(1, result.getRemovedSessions());
        assertEquals(1, result.getScannedRooms());
        assertEquals(1, result.getAffectedRooms());
        verify(setOperations).remove(RedisKey.CHAT_ACTIVE_SESSIONS.getPrefix(), "stale-session");
        verify(setOperations, never()).remove(RedisKey.CHAT_ACTIVE_SESSIONS.getPrefix(), "active-session");
    }

    @Test
    void cleanupReturnsSafeEmptyResultWhenNoSessionsExist() {
        when(redisTemplate.keys("room:*:sessions")).thenReturn(Set.of());
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(RedisKey.CHAT_ACTIVE_SESSIONS.getPrefix())).thenReturn(Set.of());

        var result = service().cleanupStaleSessions();

        assertEquals(0, result.getScannedSessions());
        assertEquals(0, result.getRemovedSessions());
        assertEquals("정리할 stale session이 없습니다.", result.getMessage());
    }

    @Test
    void cleanupReturnsSafeMessageWhenRedisIsUnavailable() {
        when(redisTemplate.keys("room:*:sessions"))
                .thenThrow(new IllegalStateException("redis unavailable"));

        var result = service().cleanupStaleSessions();

        assertEquals(0, result.getRemovedSessions());
        assertEquals("Redis 오류로 stale session 정리를 수행하지 못했습니다.", result.getMessage());
    }

    private RealtimeSessionCleanupService service() {
        return new RealtimeSessionCleanupService(
                redisTemplate,
                activeSessionRegistry,
                chatRoomService
        );
    }
}
