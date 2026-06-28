package com.bubbletalk.admin.dashboard.service;

import com.bubbletalk.chatroom.dto.AdminChatRoomResDto;
import com.bubbletalk.chatroom.entity.RoomStatus;
import com.bubbletalk.chatroom.service.ChatRoomService;
import com.bubbletalk.global.constant.RedisKey;
import com.bubbletalk.menu.service.MenuService;
import com.bubbletalk.securitylog.entity.EventType;
import com.bubbletalk.securitylog.entity.Severity;
import com.bubbletalk.securitylog.service.SecurityEventLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock
    private ChatRoomService chatRoomService;

    @Mock
    private MenuService menuService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private SetOperations<String, Object> setOperations;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private SecurityEventLogService securityEventLogService;

    @Test
    void summaryContainsAllRoomTypesAndAccurateRedisTotals() {
        when(chatRoomService.getAdminRooms()).thenReturn(List.of(
                room("PUBLIC01", false, RoomStatus.OPEN),
                room("PRIVATE1", true, RoomStatus.FULL),
                room("CLOSED01", false, RoomStatus.CLOSED)
        ));
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.size(RedisKey.CHAT_ACTIVE_SESSIONS.getPrefix())).thenReturn(4L);
        when(menuService.getTodayMenuCount()).thenReturn(12L);
        when(menuService.getTodayVoteCount()).thenReturn(37L);

        var result = service().getSummary();

        assertEquals(3L, result.getTotalRooms());
        assertEquals(2L, result.getPublicRooms());
        assertEquals(1L, result.getPrivateRooms());
        assertEquals(1L, result.getOpenRooms());
        assertEquals(1L, result.getFullRooms());
        assertEquals(1L, result.getClosedRooms());
        assertEquals(4L, result.getActiveSessions());
        assertEquals(12L, result.getTodayMenuCount());
        assertEquals(37L, result.getTodayVoteCount());
        assertNull(result.getActiveGuests());
        assertTrue(result.isRedisAvailable());
    }

    @Test
    void summaryFallsBackWhenRedisIsUnavailable() {
        when(chatRoomService.getAdminRooms()).thenReturn(List.of());
        when(redisTemplate.opsForSet()).thenThrow(new IllegalStateException("redis unavailable"));

        var result = service().getSummary();

        assertFalse(result.isRedisAvailable());
        assertEquals(0L, result.getActiveSessions());
        assertEquals(0L, result.getTodayMenuCount());
        assertEquals(0L, result.getTodayVoteCount());
    }

    @Test
    void closeRoomBroadcastsClosedSystemMessage() {
        AdminChatRoomResDto closedRoom = room("ROOM0001", false, RoomStatus.CLOSED);
        when(chatRoomService.closeRoom("ROOM0001")).thenReturn(closedRoom);

        var result = service().closeRoom("ROOM0001");

        assertEquals(RoomStatus.CLOSED, result.getStatus());
        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.<String>eq("/topic/rooms/ROOM0001/bubbles"),
                messageCaptor.capture()
        );
        com.bubbletalk.chat.entity.ChatMessage chatMessage =
                (com.bubbletalk.chat.entity.ChatMessage) messageCaptor.getValue();
        assertEquals("SYSTEM", chatMessage.getMessageType());
        assertEquals("관리자에 의해 채팅방이 종료되었습니다.", chatMessage.getContent());
        verify(messagingTemplate).convertAndSend("/topic/rooms/ROOM0001/user-count", 0L);
        verify(securityEventLogService).logEvent(
                EventType.ADMIN_ROOM_CLOSED,
                Severity.WARN,
                "ROOM0001",
                null,
                null,
                null,
                "/api/admin/rooms/ROOM0001/close",
                "관리자 채팅방 종료"
        );
    }

    private AdminDashboardService service() {
        return new AdminDashboardService(chatRoomService, menuService, redisTemplate, messagingTemplate, securityEventLogService);
    }

    private AdminChatRoomResDto room(String roomCode, boolean privateRoom, RoomStatus status) {
        return AdminChatRoomResDto.builder()
                .roomCode(roomCode)
                .name(roomCode)
                .privateRoom(privateRoom)
                .status(status)
                .currentParticipants(0L)
                .maxParticipants(10)
                .build();
    }
}
