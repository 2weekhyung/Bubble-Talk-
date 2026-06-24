package com.bubbletalk.admin.dashboard.service;

import com.bubbletalk.admin.dashboard.dto.AdminDashboardSummaryResDto;
import com.bubbletalk.chatroom.dto.AdminChatRoomResDto;
import com.bubbletalk.chatroom.entity.RoomStatus;
import com.bubbletalk.chatroom.service.ChatRoomService;
import com.bubbletalk.global.constant.RedisKey;
import com.bubbletalk.menu.service.MenuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final ChatRoomService chatRoomService;
    private final MenuService menuService;
    private final RedisTemplate<String, Object> redisTemplate;

    public AdminDashboardSummaryResDto getSummary() {
        List<AdminChatRoomResDto> rooms = chatRoomService.getAdminRooms();

        long activeSessions = 0L;
        long todayMenuCount = 0L;
        long todayVoteCount = 0L;
        boolean redisAvailable = true;

        try {
            Long sessionCount = redisTemplate.opsForSet()
                    .size(RedisKey.CHAT_ACTIVE_SESSIONS.getPrefix());
            activeSessions = sessionCount != null ? sessionCount : 0L;
            todayMenuCount = menuService.getTodayMenuCount();
            todayVoteCount = menuService.getTodayVoteCount();
        } catch (RuntimeException e) {
            redisAvailable = false;
            log.warn("admin dashboard Redis summary unavailable", e);
        }

        return AdminDashboardSummaryResDto.builder()
                .totalRooms(rooms.size())
                .publicRooms(rooms.stream().filter(room -> !room.isPrivateRoom()).count())
                .privateRooms(rooms.stream().filter(AdminChatRoomResDto::isPrivateRoom).count())
                .openRooms(countStatus(rooms, RoomStatus.OPEN))
                .fullRooms(countStatus(rooms, RoomStatus.FULL))
                .closedRooms(countStatus(rooms, RoomStatus.CLOSED))
                .activeSessions(activeSessions)
                .activeGuests(null)
                .todayMenuCount(todayMenuCount)
                .todayVoteCount(todayVoteCount)
                .redisAvailable(redisAvailable)
                .build();
    }

    public List<AdminChatRoomResDto> getRooms() {
        return chatRoomService.getAdminRooms();
    }

    public AdminChatRoomResDto closeRoom(String roomCode) {
        return chatRoomService.closeRoom(roomCode);
    }

    private long countStatus(List<AdminChatRoomResDto> rooms, RoomStatus status) {
        return rooms.stream().filter(room -> room.getStatus() == status).count();
    }
}
