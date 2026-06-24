package com.bubbletalk.chatroom.service;

import com.bubbletalk.chatroom.dto.ChatRoomCreateReqDto;
import com.bubbletalk.chatroom.dto.ChatRoomResDto;
import com.bubbletalk.chatroom.entity.ChatRoom;
import com.bubbletalk.chatroom.entity.RoomStatus;
import com.bubbletalk.chatroom.repository.ChatRoomRepository;
import com.bubbletalk.global.constant.RedisKey;
import com.bubbletalk.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private SetOperations<String, Object> setOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private ChatRoomService chatRoomService;

    @BeforeEach
    void setUp() {
        chatRoomService = new ChatRoomService(chatRoomRepository, redisTemplate);
    }

    @Test
    @DisplayName("public room creation succeeds")
    void createRoom_PublicSuccess() {
        ChatRoomCreateReqDto req = createReq("점심 채팅", "공개방", false, 10);
        when(chatRoomRepository.saveAndFlush(any(ChatRoom.class))).thenAnswer(invocation -> invocation.getArgument(0));
        mockAnyRoomSize(0L);

        ChatRoomResDto result = chatRoomService.createRoom(req);

        assertEquals("점심 채팅", result.getName());
        assertFalse(result.isPrivate());
        assertEquals(10, result.getMaxParticipants());
        assertEquals(RoomStatus.OPEN, result.getStatus());
    }

    @Test
    @DisplayName("private room creation succeeds")
    void createRoom_PrivateSuccess() {
        ChatRoomCreateReqDto req = createReq("비밀 채팅", null, true, null);
        when(chatRoomRepository.saveAndFlush(any(ChatRoom.class))).thenAnswer(invocation -> invocation.getArgument(0));
        mockAnyRoomSize(0L);

        ChatRoomResDto result = chatRoomService.createRoom(req);

        assertTrue(result.isPrivate());
        assertEquals(10, result.getMaxParticipants());
    }

    @Test
    @DisplayName("room code unique collision is retried")
    void createRoom_RetriesUniqueCollision() {
        ChatRoomCreateReqDto req = createReq("room", null, false, 10);
        when(chatRoomRepository.saveAndFlush(any(ChatRoom.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate room_code"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        mockAnyRoomSize(0L);

        ChatRoomResDto result = chatRoomService.createRoom(req);

        assertEquals("room", result.getName());
        verify(chatRoomRepository, times(2)).saveAndFlush(any(ChatRoom.class));
    }

    @Test
    @DisplayName("blank room name fails")
    void createRoom_BlankNameFails() {
        assertThrows(BusinessException.class, () -> chatRoomService.createRoom(createReq(" ", null, false, 10)));
    }

    @Test
    @DisplayName("max participants below 2 fails")
    void createRoom_MaxParticipantsTooSmallFails() {
        assertThrows(BusinessException.class, () -> chatRoomService.createRoom(createReq("방", null, false, 1)));
    }

    @Test
    @DisplayName("max participants over 50 fails")
    void createRoom_MaxParticipantsTooLargeFails() {
        assertThrows(BusinessException.class, () -> chatRoomService.createRoom(createReq("방", null, false, 51)));
    }

    @Test
    @DisplayName("public room list excludes private rooms")
    void getPublicRooms_OnlyPublicRooms() {
        ChatRoom publicRoom = room("PUBLIC01", "공개방", false, 10);
        when(chatRoomRepository.findByPrivateRoomFalseAndStatusNotOrderByCreatedDateDesc(RoomStatus.CLOSED))
                .thenReturn(List.of(publicRoom));
        mockRoomSize(roomSessionsKey("PUBLIC01"), 2L);

        List<ChatRoomResDto> result = chatRoomService.getPublicRooms();

        assertEquals(1, result.size());
        assertEquals("PUBLIC01", result.get(0).getRoomCode());
        assertEquals(2L, result.get(0).getCurrentParticipants());
    }

    @Test
    @DisplayName("admin room list includes public, private, and closed rooms")
    void getAdminRooms_IncludesAllRoomTypes() {
        ChatRoom publicRoom = room("PUBLIC01", "공개방", false, 10);
        ChatRoom privateRoom = room("PRIVATE1", "비밀방", true, 10);
        ChatRoom closedRoom = room("CLOSED01", "닫힌방", false, 10);
        org.springframework.test.util.ReflectionTestUtils.setField(closedRoom, "status", RoomStatus.CLOSED);
        when(chatRoomRepository.findAllByOrderByCreatedDateDesc())
                .thenReturn(List.of(publicRoom, privateRoom, closedRoom));
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.size(anyString())).thenReturn(0L);

        var result = chatRoomService.getAdminRooms();

        assertEquals(3, result.size());
        assertTrue(result.stream().anyMatch(room -> room.isPrivateRoom()));
        assertTrue(result.stream().anyMatch(room -> room.getStatus() == RoomStatus.CLOSED));
    }

    @Test
    @DisplayName("room can be fetched by room code")
    void getRoom_ByRoomCode() {
        ChatRoom room = room("ROOM0001", "방", false, 10);
        when(chatRoomRepository.findByRoomCode("ROOM0001")).thenReturn(Optional.of(room));
        mockRoomSize(roomSessionsKey("ROOM0001"), 0L);

        ChatRoomResDto result = chatRoomService.getRoom("ROOM0001");

        assertEquals("ROOM0001", result.getRoomCode());
    }

    @Test
    @DisplayName("unknown room code fails")
    void getRoom_UnknownFails() {
        when(chatRoomRepository.findByRoomCode("UNKNOWN")).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> chatRoomService.getRoom("UNKNOWN"));
    }

    @Test
    @DisplayName("closed room join fails")
    void joinRoom_ClosedFails() {
        ChatRoom room = room("CLOSED01", "닫힌방", false, 10);
        org.springframework.test.util.ReflectionTestUtils.setField(room, "status", RoomStatus.CLOSED);
        when(chatRoomRepository.findByRoomCode("CLOSED01")).thenReturn(Optional.of(room));

        assertThrows(BusinessException.class, () -> chatRoomService.joinRoom("CLOSED01", "guest:abc"));
    }

    @Test
    @DisplayName("admin close marks room closed, records closedAt, and clears room Redis keys")
    void closeRoom_ClosesRoomAndCleansRedis() {
        ChatRoom room = room("ROOM0001", "방", false, 10);
        when(chatRoomRepository.findByRoomCode("ROOM0001")).thenReturn(Optional.of(room));
        when(chatRoomRepository.saveAndFlush(room)).thenReturn(room);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(RedisKey.roomSessions("ROOM0001")))
                .thenReturn(Set.of("session-1", "session-2"));
        when(setOperations.size(RedisKey.roomSessions("ROOM0001"))).thenReturn(0L);

        var result = chatRoomService.closeRoom("ROOM0001");

        assertEquals(RoomStatus.CLOSED, room.getStatus());
        assertTrue(room.getClosedAt() != null);
        assertEquals(RoomStatus.CLOSED, result.getStatus());
        verify(setOperations).remove(RedisKey.sessionRooms("session-1"), "ROOM0001");
        verify(setOperations).remove(RedisKey.sessionRooms("session-2"), "ROOM0001");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> keysCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(redisTemplate).delete(keysCaptor.capture());
        assertTrue(keysCaptor.getValue().contains(RedisKey.roomSessions("ROOM0001")));
        assertTrue(keysCaptor.getValue().contains(RedisKey.roomGuests("ROOM0001")));
        assertTrue(keysCaptor.getValue().contains(RedisKey.roomSessionActors("ROOM0001")));
    }

    @Test
    @DisplayName("closing an already closed room is idempotent")
    void closeRoom_AlreadyClosedIsSafe() {
        ChatRoom room = room("CLOSED01", "닫힌방", false, 10);
        LocalDateTime closedAt = LocalDateTime.now().minusMinutes(1);
        org.springframework.test.util.ReflectionTestUtils.setField(room, "status", RoomStatus.CLOSED);
        org.springframework.test.util.ReflectionTestUtils.setField(room, "closedAt", closedAt);
        when(chatRoomRepository.findByRoomCode("CLOSED01")).thenReturn(Optional.of(room));
        when(chatRoomRepository.saveAndFlush(room)).thenReturn(room);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(RedisKey.roomSessions("CLOSED01"))).thenReturn(Set.of());
        when(setOperations.size(RedisKey.roomSessions("CLOSED01"))).thenReturn(0L);

        chatRoomService.closeRoom("CLOSED01");

        assertEquals(closedAt, room.getClosedAt());
        assertEquals(RoomStatus.CLOSED, room.getStatus());
    }

    @Test
    @DisplayName("room remains closed when Redis cleanup fails")
    void closeRoom_RedisFailureDoesNotRollbackClosedState() {
        ChatRoom room = room("ROOM0001", "방", false, 10);
        when(chatRoomRepository.findByRoomCode("ROOM0001")).thenReturn(Optional.of(room));
        when(chatRoomRepository.saveAndFlush(room)).thenReturn(room);
        when(redisTemplate.opsForSet()).thenThrow(new IllegalStateException("redis unavailable"));

        var result = chatRoomService.closeRoom("ROOM0001");

        assertEquals(RoomStatus.CLOSED, room.getStatus());
        assertTrue(room.getClosedAt() != null);
        assertEquals(RoomStatus.CLOSED, result.getStatus());
    }

    @Test
    @DisplayName("full room join fails")
    void joinRoom_FullFails() {
        ChatRoom room = room("FULL0001", "가득찬방", false, 2);
        when(chatRoomRepository.findByRoomCode("FULL0001")).thenReturn(Optional.of(room));
        mockRoomSize(roomSessionsKey("FULL0001"), 2L);

        assertThrows(BusinessException.class, () -> chatRoomService.joinRoom("FULL0001", "guest:abc"));
    }

    @Test
    @DisplayName("current participants are calculated from Redis session set size")
    void getCurrentParticipants_UsesSessionSetSize() {
        mockRoomSize(roomSessionsKey("ROOM0001"), 3L);

        assertEquals(3L, chatRoomService.getCurrentParticipants("ROOM0001"));
    }

    @Test
    @DisplayName("current participants fall back to zero when Redis is unavailable")
    void getCurrentParticipants_RedisFailureFallsBackToZero() {
        when(redisTemplate.opsForSet()).thenThrow(new IllegalStateException("redis unavailable"));

        assertEquals(0L, chatRoomService.getCurrentParticipants("ROOM0001"));
    }

    @Test
    @DisplayName("session registration uses atomic Redis capacity check")
    void registerSession_UsesAtomicCapacityCheck() {
        ChatRoom room = room("ROOM0001", "room", false, 2);
        when(chatRoomRepository.findByRoomCode("ROOM0001")).thenReturn(Optional.of(room));
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(1L);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        long result = chatRoomService.registerSession("ROOM0001", "session-1", "guest:abc");

        assertEquals(1L, result);
        verify(setOperations).add(RedisKey.roomGuests("ROOM0001"), "guest:abc");
        verify(hashOperations).put(RedisKey.roomSessionActors("ROOM0001"), "session-1", "guest:abc");
        verify(setOperations).add(RedisKey.sessionRooms("session-1"), "ROOM0001");
    }

    @Test
    @DisplayName("atomic Redis capacity check rejects a full room")
    void registerSession_FullRoomFailsAtomically() {
        ChatRoom room = room("ROOM0001", "room", false, 2);
        when(chatRoomRepository.findByRoomCode("ROOM0001")).thenReturn(Optional.of(room));
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(-1L);

        assertThrows(BusinessException.class,
                () -> chatRoomService.registerSession("ROOM0001", "session-1", "guest:abc"));

        verify(redisTemplate, never()).opsForHash();
    }

    @Test
    @DisplayName("disconnect removes session from joined room sets")
    void unregisterSessionFromAllRooms_RemovesSession() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(setOperations.members(RedisKey.ROOM_SESSION_ROOMS.with("session-1"))).thenReturn(Set.of("ROOM0001"));
        when(setOperations.size(roomSessionsKey("ROOM0001"))).thenReturn(0L);
        when(hashOperations.get(RedisKey.roomSessionActors("ROOM0001"), "session-1")).thenReturn("guest:abc");
        when(hashOperations.values(RedisKey.roomSessionActors("ROOM0001"))).thenReturn(List.of());

        var result = chatRoomService.unregisterSessionFromAllRooms("session-1");

        verify(setOperations).remove(roomSessionsKey("ROOM0001"), "session-1");
        verify(setOperations).remove(RedisKey.roomGuests("ROOM0001"), "guest:abc");
        verify(redisTemplate).delete(RedisKey.ROOM_SESSION_ROOMS.with("session-1"));
        assertEquals(0L, result.get("ROOM0001"));
    }

    private ChatRoomCreateReqDto createReq(String name, String description, Boolean isPrivate, Integer maxParticipants) {
        ChatRoomCreateReqDto req = new ChatRoomCreateReqDto();
        req.setName(name);
        req.setDescription(description);
        req.setIsPrivate(isPrivate);
        req.setMaxParticipants(maxParticipants);
        return req;
    }

    private ChatRoom room(String roomCode, String name, boolean privateRoom, int maxParticipants) {
        return ChatRoom.builder()
                .roomCode(roomCode)
                .name(name)
                .privateRoom(privateRoom)
                .maxParticipants(maxParticipants)
                .build();
    }

    private void mockRoomSize(String key, Long size) {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.size(key)).thenReturn(size);
    }

    private void mockAnyRoomSize(Long size) {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.size(startsWith(RedisKey.ROOM.getPrefix()))).thenReturn(size);
    }

    private String roomSessionsKey(String roomCode) {
        return RedisKey.ROOM.with(roomCode + ":sessions");
    }
}
