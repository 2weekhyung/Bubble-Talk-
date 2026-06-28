package com.bubbletalk.chatroom.service;

import com.bubbletalk.chatroom.dto.ChatRoomCreateReqDto;
import com.bubbletalk.chatroom.dto.ChatRoomResDto;
import com.bubbletalk.chatroom.dto.AdminChatRoomResDto;
import com.bubbletalk.chatroom.entity.ChatRoom;
import com.bubbletalk.chatroom.entity.RoomStatus;
import com.bubbletalk.chatroom.repository.ChatRoomRepository;
import com.bubbletalk.global.constant.RedisKey;
import com.bubbletalk.global.exception.BusinessException;
import com.bubbletalk.securitylog.entity.EventType;
import com.bubbletalk.securitylog.entity.Severity;
import com.bubbletalk.securitylog.service.SecurityEventLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomService {

    private static final String ROOM_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int ROOM_CODE_LENGTH = 8;
    private static final int ROOM_CODE_RETRY_COUNT = 10;
    private static final int DEFAULT_MAX_PARTICIPANTS = 10;
    private static final int MIN_MAX_PARTICIPANTS = 2;
    private static final int MAX_MAX_PARTICIPANTS = 50;
    private static final int NAME_MAX_LENGTH = 100;
    private static final int DESCRIPTION_MAX_LENGTH = 255;
    private static final int ROOM_CREATE_LIMIT_SECONDS = 30;
    private static final DefaultRedisScript<Long> REGISTER_SESSION_SCRIPT = new DefaultRedisScript<>("""
            local exists = redis.call('SISMEMBER', KEYS[1], ARGV[1])
            if exists == 1 then
                return redis.call('SCARD', KEYS[1])
            end
            local current = redis.call('SCARD', KEYS[1])
            if current >= tonumber(ARGV[2]) then
                return -1
            end
            redis.call('SADD', KEYS[1], ARGV[1])
            return current + 1
            """, Long.class);

    private final ChatRoomRepository chatRoomRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SecurityEventLogService securityEventLogService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ChatRoomResDto createRoom(ChatRoomCreateReqDto reqDto) {
        return createRoomInternal(reqDto);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ChatRoomResDto createRoom(ChatRoomCreateReqDto reqDto, String requesterId) {
        validateRoomCreateRateLimit(requesterId);
        return createRoomInternal(reqDto);
    }

    private ChatRoomResDto createRoomInternal(ChatRoomCreateReqDto reqDto) {
        String name = normalizeName(reqDto != null ? reqDto.getName() : null);
        String description = normalizeDescription(reqDto != null ? reqDto.getDescription() : null);
        boolean privateRoom = reqDto != null && Boolean.TRUE.equals(reqDto.getIsPrivate());
        int maxParticipants = normalizeMaxParticipants(reqDto != null ? reqDto.getMaxParticipants() : null);

        for (int i = 0; i < ROOM_CODE_RETRY_COUNT; i++) {
            ChatRoom room = ChatRoom.builder()
                    .roomCode(generateRoomCode())
                    .name(name)
                    .description(description)
                    .privateRoom(privateRoom)
                    .maxParticipants(maxParticipants)
                    .build();
            try {
                return toResponse(chatRoomRepository.saveAndFlush(room));
            } catch (DataIntegrityViolationException ignored) {
                // saveAndFlush runs without an outer transaction, so a unique collision can be retried safely.
            }
        }
        throw new BusinessException("채팅방 코드를 생성하지 못했습니다. 다시 시도해주세요.");
    }

    private void validateRoomCreateRateLimit(String requesterId) {
        validateRequester(requesterId);

        String key = RedisKey.ROOM_CREATE_RATELIMIT.with(requesterId);
        Boolean allowed = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", ROOM_CREATE_LIMIT_SECONDS, TimeUnit.SECONDS);

        if (allowed == null) {
            log.error("room create rate limit check failed: requesterId={}", requesterId);
            throw new BusinessException("채팅방 생성 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        }

        if (!allowed) {
            throw new BusinessException("채팅방은 30초에 한 번만 만들 수 있습니다.");
        }
    }

    public List<ChatRoomResDto> getPublicRooms() {
        return chatRoomRepository.findByPrivateRoomFalseAndStatusNotOrderByCreatedDateDesc(RoomStatus.CLOSED).stream()
                .map(this::toResponse)
                .toList();
    }

    public Page<ChatRoomResDto> getPublicRooms(Pageable pageable) {
        return chatRoomRepository.findByPrivateRoomFalseAndStatusNotOrderByCreatedDateDesc(RoomStatus.CLOSED, pageable)
                .map(this::toResponse);
    }

    public List<AdminChatRoomResDto> getAdminRooms() {
        return chatRoomRepository.findAllByOrderByCreatedDateDesc().stream()
                .map(room -> {
                    long currentParticipants = getCurrentParticipants(room.getRoomCode());
                    return AdminChatRoomResDto.from(
                            room,
                            currentParticipants,
                            getEffectiveStatus(room, currentParticipants)
                    );
                })
                .toList();
    }

    public ChatRoomResDto getRoom(String roomCode) {
        return toResponse(getRoomOrThrow(roomCode));
    }

    public ChatRoomResDto joinRoom(String roomCode, String requesterId) {
        ChatRoom room = getRoomOrThrow(roomCode);
        validateRequester(requesterId);
        validateJoinable(room);
        return toResponse(room);
    }

    public ChatRoomResDto leaveRoom(String roomCode, String requesterId) {
        ChatRoom room = getRoomOrThrow(roomCode);
        if (requesterId != null && !requesterId.isBlank()) {
            List<Object> activeActors = redisTemplate.opsForHash()
                    .values(RedisKey.roomSessionActors(room.getRoomCode()));
            if (activeActors == null || !activeActors.contains(requesterId)) {
                redisTemplate.opsForSet().remove(roomGuestsKey(room.getRoomCode()), requesterId);
            }
        }
        return toResponse(room);
    }

    @Transactional
    public AdminChatRoomResDto closeRoom(String roomCode) {
        ChatRoom room = getRoomOrThrow(roomCode);
        boolean newlyClosed = room.close();
        chatRoomRepository.saveAndFlush(room);

        try {
            cleanupClosedRoomRedis(room.getRoomCode());
        } catch (RuntimeException e) {
            log.warn("closed room Redis cleanup failed: roomCode={}", room.getRoomCode(), e);
        }

        long currentParticipants = getCurrentParticipants(room.getRoomCode());
        if (newlyClosed) {
            securityEventLogService.logEvent(
                    EventType.ROOM_CLOSED,
                    Severity.WARN,
                    room.getRoomCode(),
                    null,
                    null,
                    null,
                    null,
                    "채팅방 종료"
            );
        }
        return AdminChatRoomResDto.from(room, currentParticipants, RoomStatus.CLOSED);
    }

    public long registerSession(String roomCode, String sessionId, String requesterId) {
        ChatRoom room = getRoomOrThrow(roomCode);
        validateRequester(requesterId);
        validateSessionId(sessionId);
        if (room.getStatus() == RoomStatus.CLOSED) {
            throw new BusinessException("종료된 채팅방에는 입장할 수 없습니다.");
        }

        String normalizedRoomCode = room.getRoomCode();
        Long currentCount;
        try {
            currentCount = redisTemplate.execute(
                    REGISTER_SESSION_SCRIPT,
                    List.of(RedisKey.roomSessions(normalizedRoomCode)),
                    sessionId,
                    room.getMaxParticipants()
            );
        } catch (RuntimeException e) {
            log.error("room session registration failed: roomCode={}, sessionId={}",
                    normalizedRoomCode, sessionId, e);
            throw new BusinessException("채팅방 입장 상태를 확인하지 못했습니다. 잠시 후 다시 시도해주세요.");
        }
        if (currentCount == null) {
            throw new BusinessException("채팅방 입장 상태를 확인하지 못했습니다. 잠시 후 다시 시도해주세요.");
        }
        if (currentCount < 0) {
            throw new BusinessException("채팅방 인원이 가득 찼습니다.");
        }

        try {
            redisTemplate.opsForSet().add(RedisKey.roomGuests(normalizedRoomCode), requesterId);
            redisTemplate.opsForHash().put(RedisKey.roomSessionActors(normalizedRoomCode), sessionId, requesterId);
            redisTemplate.opsForSet().add(RedisKey.sessionRooms(sessionId), normalizedRoomCode);
            return currentCount;
        } catch (RuntimeException e) {
            unregisterSession(normalizedRoomCode, sessionId);
            throw new BusinessException("채팅방 입장 정보를 저장하지 못했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    public long unregisterSession(String roomCode, String sessionId) {
        if (roomCode == null || roomCode.isBlank() || sessionId == null || sessionId.isBlank()) {
            return 0L;
        }

        String normalizedRoomCode = roomCode.trim();
        String actorKey = RedisKey.roomSessionActors(normalizedRoomCode);
        Object requesterId = redisTemplate.opsForHash().get(actorKey, sessionId);

        redisTemplate.opsForSet().remove(RedisKey.roomSessions(normalizedRoomCode), sessionId);
        redisTemplate.opsForHash().delete(actorKey, sessionId);
        redisTemplate.opsForSet().remove(RedisKey.sessionRooms(sessionId), normalizedRoomCode);

        List<Object> activeActors = redisTemplate.opsForHash().values(actorKey);
        if (requesterId != null && (activeActors == null || !activeActors.contains(requesterId))) {
            redisTemplate.opsForSet().remove(RedisKey.roomGuests(normalizedRoomCode), requesterId);
        }
        return getCurrentParticipants(normalizedRoomCode);
    }

    public Map<String, Long> unregisterSessionFromAllRooms(String sessionId) {
        Map<String, Long> result = new HashMap<>();
        if (sessionId == null || sessionId.isBlank()) {
            return result;
        }

        String sessionRoomsKey = RedisKey.sessionRooms(sessionId);
        var roomCodes = redisTemplate.opsForSet().members(sessionRoomsKey);
        if (roomCodes == null || roomCodes.isEmpty()) {
            return result;
        }

        for (Object roomCodeObj : roomCodes) {
            String roomCode = String.valueOf(roomCodeObj);
            result.put(roomCode, unregisterSession(roomCode, sessionId));
        }

        redisTemplate.delete(sessionRoomsKey);
        return result;
    }

    public Map<String, String> getSessionRoomActors(String sessionId) {
        Map<String, String> result = new HashMap<>();
        if (sessionId == null || sessionId.isBlank()) {
            return result;
        }

        var roomCodes = redisTemplate.opsForSet().members(RedisKey.sessionRooms(sessionId));
        if (roomCodes == null || roomCodes.isEmpty()) {
            return result;
        }

        for (Object roomCodeObj : roomCodes) {
            String roomCode = String.valueOf(roomCodeObj);
            Object actor = redisTemplate.opsForHash()
                    .get(RedisKey.roomSessionActors(roomCode), sessionId);
            if (actor != null) {
                result.put(roomCode, String.valueOf(actor));
            }
        }
        return result;
    }

    public long getCurrentParticipants(String roomCode) {
        try {
            Long size = redisTemplate.opsForSet().size(RedisKey.roomSessions(roomCode));
            return size != null ? size : 0L;
        } catch (RuntimeException e) {
            log.warn("room participant count fallback to zero: roomCode={}", roomCode, e);
            return 0L;
        }
    }

    private void cleanupClosedRoomRedis(String roomCode) {
        String sessionsKey = RedisKey.roomSessions(roomCode);
        Set<Object> sessionIds = redisTemplate.opsForSet().members(sessionsKey);
        if (sessionIds != null) {
            for (Object sessionId : sessionIds) {
                redisTemplate.opsForSet().remove(RedisKey.sessionRooms(String.valueOf(sessionId)), roomCode);
            }
        }
        redisTemplate.delete(List.of(
                sessionsKey,
                RedisKey.roomGuests(roomCode),
                RedisKey.roomSessionActors(roomCode)
        ));
    }

    private ChatRoom getRoomOrThrow(String roomCode) {
        if (roomCode == null || roomCode.isBlank()) {
            throw new BusinessException("방 코드를 입력해주세요.");
        }

        return chatRoomRepository.findByRoomCode(roomCode.trim())
                .orElseThrow(() -> new BusinessException("존재하지 않는 채팅방입니다."));
    }

    private ChatRoomResDto toResponse(ChatRoom room) {
        long currentParticipants = getCurrentParticipants(room.getRoomCode());
        return ChatRoomResDto.from(room, currentParticipants, getEffectiveStatus(room, currentParticipants));
    }

    private RoomStatus getEffectiveStatus(ChatRoom room, long currentParticipants) {
        if (room.getStatus() == RoomStatus.CLOSED) {
            return RoomStatus.CLOSED;
        }
        if (currentParticipants >= room.getMaxParticipants()) {
            return RoomStatus.FULL;
        }
        return room.getStatus();
    }

    private void validateJoinable(ChatRoom room) {
        if (room.getStatus() == RoomStatus.CLOSED) {
            throw new BusinessException("종료된 채팅방에는 입장할 수 없습니다.");
        }
        if (getCurrentParticipants(room.getRoomCode()) >= room.getMaxParticipants()) {
            throw new BusinessException("채팅방 인원이 가득 찼습니다.");
        }
    }

    private void validateRequester(String requesterId) {
        if (requesterId == null || requesterId.isBlank()) {
            throw new BusinessException("익명 사용자 정보를 확인할 수 없습니다.");
        }
    }

    private void validateSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BusinessException("WebSocket 세션 정보를 확인할 수 없습니다.");
        }
    }

    private String normalizeName(String name) {
        if (name == null) {
            throw new BusinessException("방 이름을 입력해주세요.");
        }
        String normalized = name.trim();
        if (normalized.isBlank()) {
            throw new BusinessException("방 이름을 입력해주세요.");
        }
        if (normalized.length() > NAME_MAX_LENGTH) {
            throw new BusinessException("방 이름은 100자 이하로 입력해주세요.");
        }
        if (containsUnsafeHtml(normalized)) {
            throw new BusinessException("방 이름에 사용할 수 없는 문자가 포함되어 있습니다.");
        }
        return normalized;
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String normalized = description.trim();
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.length() > DESCRIPTION_MAX_LENGTH) {
            throw new BusinessException("방 설명은 255자 이하로 입력해주세요.");
        }
        if (containsUnsafeHtml(normalized)) {
            throw new BusinessException("방 설명에 사용할 수 없는 문자가 포함되어 있습니다.");
        }
        return normalized;
    }

    private int normalizeMaxParticipants(Integer maxParticipants) {
        int value = maxParticipants != null ? maxParticipants : DEFAULT_MAX_PARTICIPANTS;
        if (value < MIN_MAX_PARTICIPANTS || value > MAX_MAX_PARTICIPANTS) {
            throw new BusinessException("최대 인원은 2명 이상 50명 이하로 설정해주세요.");
        }
        return value;
    }

    private String generateRoomCode() {
        for (int i = 0; i < ROOM_CODE_RETRY_COUNT; i++) {
            String roomCode = randomRoomCode();
            if (!chatRoomRepository.existsByRoomCode(roomCode)) {
                return roomCode;
            }
        }
        throw new BusinessException("채팅방 코드를 생성하지 못했습니다. 다시 시도해주세요.");
    }

    private String randomRoomCode() {
        StringBuilder builder = new StringBuilder(ROOM_CODE_LENGTH);
        for (int i = 0; i < ROOM_CODE_LENGTH; i++) {
            builder.append(ROOM_CODE_CHARS.charAt(secureRandom.nextInt(ROOM_CODE_CHARS.length())));
        }
        return builder.toString();
    }

    private boolean containsUnsafeHtml(String value) {
        String lowerValue = value.toLowerCase();
        return value.contains("<")
                || value.contains(">")
                || lowerValue.contains("&lt;")
                || lowerValue.contains("&gt;")
                || lowerValue.contains("script");
    }

    private String roomGuestsKey(String roomCode) {
        return RedisKey.roomGuests(roomCode);
    }
}
