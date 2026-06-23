package com.bubbletalk.chatroom.service;

import com.bubbletalk.chatroom.dto.ChatRoomCreateReqDto;
import com.bubbletalk.chatroom.dto.ChatRoomResDto;
import com.bubbletalk.chatroom.entity.ChatRoom;
import com.bubbletalk.chatroom.entity.RoomStatus;
import com.bubbletalk.chatroom.repository.ChatRoomRepository;
import com.bubbletalk.global.constant.RedisKey;
import com.bubbletalk.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
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

    private final ChatRoomRepository chatRoomRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public ChatRoomResDto createRoom(ChatRoomCreateReqDto reqDto) {
        String name = normalizeName(reqDto != null ? reqDto.getName() : null);
        String description = normalizeDescription(reqDto != null ? reqDto.getDescription() : null);
        boolean privateRoom = reqDto != null && Boolean.TRUE.equals(reqDto.getIsPrivate());
        int maxParticipants = normalizeMaxParticipants(reqDto != null ? reqDto.getMaxParticipants() : null);

        ChatRoom room = ChatRoom.builder()
                .roomCode(generateRoomCode())
                .name(name)
                .description(description)
                .privateRoom(privateRoom)
                .maxParticipants(maxParticipants)
                .build();

        ChatRoom savedRoom = chatRoomRepository.save(room);
        return toResponse(savedRoom);
    }

    public List<ChatRoomResDto> getPublicRooms() {
        return chatRoomRepository.findByPrivateRoomFalseAndStatusNotOrderByCreatedDateDesc(RoomStatus.CLOSED).stream()
                .map(this::toResponse)
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
            redisTemplate.opsForSet().remove(roomGuestsKey(room.getRoomCode()), requesterId);
        }
        return toResponse(room);
    }

    public long registerSession(String roomCode, String sessionId, String requesterId) {
        ChatRoom room = getRoomOrThrow(roomCode);
        validateRequester(requesterId);
        validateSessionId(sessionId);

        String sessionsKey = roomSessionsKey(room.getRoomCode());
        Boolean alreadyJoined = redisTemplate.opsForSet().isMember(sessionsKey, sessionId);
        if (!Boolean.TRUE.equals(alreadyJoined)) {
            validateJoinable(room);
        }

        redisTemplate.opsForSet().add(sessionsKey, sessionId);
        redisTemplate.opsForSet().add(roomGuestsKey(room.getRoomCode()), requesterId);
        redisTemplate.opsForSet().add(sessionRoomsKey(sessionId), room.getRoomCode());
        return getCurrentParticipants(room.getRoomCode());
    }

    public Map<String, Long> unregisterSessionFromAllRooms(String sessionId) {
        Map<String, Long> result = new HashMap<>();
        if (sessionId == null || sessionId.isBlank()) {
            return result;
        }

        String sessionRoomsKey = sessionRoomsKey(sessionId);
        var roomCodes = redisTemplate.opsForSet().members(sessionRoomsKey);
        if (roomCodes == null || roomCodes.isEmpty()) {
            return result;
        }

        for (Object roomCodeObj : roomCodes) {
            String roomCode = String.valueOf(roomCodeObj);
            redisTemplate.opsForSet().remove(roomSessionsKey(roomCode), sessionId);
            result.put(roomCode, getCurrentParticipants(roomCode));
        }

        redisTemplate.delete(sessionRoomsKey);
        return result;
    }

    public long getCurrentParticipants(String roomCode) {
        Long size = redisTemplate.opsForSet().size(roomSessionsKey(roomCode));
        return size != null ? size : 0L;
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

    private String roomSessionsKey(String roomCode) {
        return RedisKey.ROOM.with(roomCode + ":sessions");
    }

    private String roomGuestsKey(String roomCode) {
        return RedisKey.ROOM.with(roomCode + ":guests");
    }

    private String sessionRoomsKey(String sessionId) {
        return RedisKey.ROOM_SESSION_ROOMS.with(sessionId);
    }
}
