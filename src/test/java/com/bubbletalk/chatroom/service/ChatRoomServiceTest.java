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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
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

    private ChatRoomService chatRoomService;

    @BeforeEach
    void setUp() {
        chatRoomService = new ChatRoomService(chatRoomRepository, redisTemplate);
    }

    @Test
    @DisplayName("public room creation succeeds")
    void createRoom_PublicSuccess() {
        ChatRoomCreateReqDto req = createReq("점심 채팅", "공개방", false, 10);
        when(chatRoomRepository.existsByRoomCode(any())).thenReturn(false);
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> invocation.getArgument(0));
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
        when(chatRoomRepository.existsByRoomCode(any())).thenReturn(false);
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> invocation.getArgument(0));
        mockAnyRoomSize(0L);

        ChatRoomResDto result = chatRoomService.createRoom(req);

        assertTrue(result.isPrivate());
        assertEquals(10, result.getMaxParticipants());
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
    @DisplayName("disconnect removes session from joined room sets")
    void unregisterSessionFromAllRooms_RemovesSession() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(RedisKey.ROOM_SESSION_ROOMS.with("session-1"))).thenReturn(Set.of("ROOM0001"));
        when(setOperations.size(roomSessionsKey("ROOM0001"))).thenReturn(0L);

        var result = chatRoomService.unregisterSessionFromAllRooms("session-1");

        verify(setOperations).remove(roomSessionsKey("ROOM0001"), "session-1");
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
