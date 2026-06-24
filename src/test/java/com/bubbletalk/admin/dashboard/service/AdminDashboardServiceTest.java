package com.bubbletalk.admin.dashboard.service;

import com.bubbletalk.chatroom.dto.AdminChatRoomResDto;
import com.bubbletalk.chatroom.entity.RoomStatus;
import com.bubbletalk.chatroom.service.ChatRoomService;
import com.bubbletalk.global.constant.RedisKey;
import com.bubbletalk.menu.service.MenuService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

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

    private AdminDashboardService service() {
        return new AdminDashboardService(chatRoomService, menuService, redisTemplate);
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
