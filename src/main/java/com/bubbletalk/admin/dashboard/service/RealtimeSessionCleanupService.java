package com.bubbletalk.admin.dashboard.service;

import com.bubbletalk.admin.dashboard.dto.StaleSessionCleanupResDto;
import com.bubbletalk.chatroom.service.ChatRoomService;
import com.bubbletalk.config.ActiveWebSocketSessionRegistry;
import com.bubbletalk.global.constant.RedisKey;
import com.bubbletalk.securitylog.entity.EventType;
import com.bubbletalk.securitylog.entity.Severity;
import com.bubbletalk.securitylog.service.SecurityEventLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeSessionCleanupService {

    private static final String ROOM_SESSION_PATTERN = "room:*:sessions";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ActiveWebSocketSessionRegistry activeSessionRegistry;
    private final ChatRoomService chatRoomService;
    private final SecurityEventLogService securityEventLogService;

    public StaleSessionCleanupResDto cleanupStaleSessions() {
        try {
            StaleSessionCleanupResDto result = performCleanup();
            securityEventLogService.logEvent(
                    EventType.STALE_SESSION_CLEANUP,
                    Severity.WARN,
                    null,
                    null,
                    null,
                    null,
                    "/api/admin/realtime/cleanup-stale-sessions",
                    "비정상 세션 정리"
            );
            return result;
        } catch (RuntimeException e) {
            log.warn("stale session cleanup failed because Redis is unavailable", e);
            securityEventLogService.logEvent(
                    EventType.STALE_SESSION_CLEANUP,
                    Severity.WARN,
                    null,
                    null,
                    null,
                    null,
                    "/api/admin/realtime/cleanup-stale-sessions",
                    "비정상 세션 정리 실패"
            );
            return StaleSessionCleanupResDto.builder()
                    .scannedSessions(0)
                    .removedSessions(0)
                    .scannedRooms(0)
                    .affectedRooms(0)
                    .message("Redis 오류로 stale session 정리를 수행하지 못했습니다.")
                    .build();
        }
    }

    private StaleSessionCleanupResDto performCleanup() {
        Set<String> roomSessionKeys = redisTemplate.keys(ROOM_SESSION_PATTERN);
        if (roomSessionKeys == null) {
            roomSessionKeys = Set.of();
        }

        Set<String> candidates = new HashSet<>();
        Set<Object> globalSessions = redisTemplate.opsForSet()
                .members(RedisKey.CHAT_ACTIVE_SESSIONS.getPrefix());
        addSessionIds(candidates, globalSessions);

        for (String roomSessionKey : roomSessionKeys) {
            addSessionIds(candidates, redisTemplate.opsForSet().members(roomSessionKey));
        }

        int removedSessions = 0;
        Set<String> affectedRoomCodes = new HashSet<>();

        for (String sessionId : candidates) {
            if (activeSessionRegistry.isActive(sessionId)) {
                continue;
            }

            affectedRoomCodes.addAll(
                    chatRoomService.unregisterSessionFromAllRooms(sessionId).keySet()
            );

            for (String roomSessionKey : roomSessionKeys) {
                Boolean member = redisTemplate.opsForSet().isMember(roomSessionKey, sessionId);
                if (Boolean.TRUE.equals(member)) {
                    String roomCode = extractRoomCode(roomSessionKey);
                    chatRoomService.unregisterSession(roomCode, sessionId);
                    affectedRoomCodes.add(roomCode);
                }
            }

            redisTemplate.opsForSet().remove(RedisKey.CHAT_ACTIVE_SESSIONS.getPrefix(), sessionId);
            removedSessions++;
        }

        String message = removedSessions == 0
                ? "정리할 stale session이 없습니다."
                : removedSessions + "개의 stale session을 정리했습니다.";

        log.info("stale session cleanup completed: scannedSessions={}, removedSessions={}, scannedRooms={}, affectedRooms={}",
                candidates.size(), removedSessions, roomSessionKeys.size(), affectedRoomCodes.size());

        return StaleSessionCleanupResDto.builder()
                .scannedSessions(candidates.size())
                .removedSessions(removedSessions)
                .scannedRooms(roomSessionKeys.size())
                .affectedRooms(affectedRoomCodes.size())
                .message(message)
                .build();
    }

    private void addSessionIds(Set<String> target, Set<Object> sessionIds) {
        if (sessionIds != null) {
            sessionIds.stream().map(String::valueOf).forEach(target::add);
        }
    }

    private String extractRoomCode(String roomSessionKey) {
        String prefix = RedisKey.ROOM.getPrefix();
        String suffix = ":sessions";
        return roomSessionKey.substring(prefix.length(), roomSessionKey.length() - suffix.length());
    }
}
