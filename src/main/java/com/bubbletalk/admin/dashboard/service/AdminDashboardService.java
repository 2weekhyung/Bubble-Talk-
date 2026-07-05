package com.bubbletalk.admin.dashboard.service;

import com.bubbletalk.admin.dashboard.dto.AdminDashboardSummaryResDto;
import com.bubbletalk.chat.entity.ChatMessage;
import com.bubbletalk.chatroom.dto.AdminChatRoomResDto;
import com.bubbletalk.chatroom.entity.RoomStatus;
import com.bubbletalk.chatroom.service.ChatRoomService;
import com.bubbletalk.global.constant.RedisKey;
import com.bubbletalk.menu.service.MenuService;
import com.bubbletalk.securitylog.entity.EventType;
import com.bubbletalk.securitylog.entity.Severity;
import com.bubbletalk.securitylog.service.SecurityEventLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final ChatRoomService chatRoomService;
    private final MenuService menuService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final SecurityEventLogService securityEventLogService;

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

    public Page<AdminChatRoomResDto> getRooms(Pageable pageable) {
        return chatRoomService.getAdminRooms(pageable);
    }

    public Page<AdminChatRoomResDto> getRooms(Pageable pageable, Boolean privateRoom, RoomStatus status) {
        return chatRoomService.getAdminRooms(pageable, privateRoom, status);
    }

    public AdminChatRoomResDto closeRoom(String roomCode) {
        AdminChatRoomResDto result = chatRoomService.closeRoom(roomCode);
        securityEventLogService.logEvent(
                EventType.ADMIN_ROOM_CLOSED,
                Severity.WARN,
                result.getRoomCode(),
                null,
                null,
                null,
                "/api/admin/rooms/" + result.getRoomCode() + "/close",
                "관리자 채팅방 종료"
        );
        securityEventLogService.logEvent(
                EventType.SYSTEM_MESSAGE_SEND,
                Severity.INFO,
                result.getRoomCode(),
                null,
                null,
                null,
                "/topic/rooms/" + result.getRoomCode() + "/bubbles",
                "종료 시스템 메시지 전송"
        );
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + result.getRoomCode() + "/bubbles",
                ChatMessage.system(result.getRoomCode(), "관리자에 의해 채팅방이 종료되었습니다.")
        );
        messagingTemplate.convertAndSend("/topic/rooms/" + result.getRoomCode() + "/user-count", 0L);
        return result;
    }

    private long countStatus(List<AdminChatRoomResDto> rooms, RoomStatus status) {
        return rooms.stream().filter(room -> room.getStatus() == status).count();
    }
}
