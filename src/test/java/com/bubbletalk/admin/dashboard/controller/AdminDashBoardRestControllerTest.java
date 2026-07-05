package com.bubbletalk.admin.dashboard.controller;

import com.bubbletalk.admin.dashboard.service.AdminDashboardService;
import com.bubbletalk.admin.dashboard.service.RealtimeSessionCleanupService;
import com.bubbletalk.base.dto.BaseResDto;
import com.bubbletalk.chatroom.dto.AdminChatRoomResDto;
import com.bubbletalk.chatroom.entity.RoomStatus;
import com.bubbletalk.menu.controller.MenuSocketController;
import com.bubbletalk.menu.service.MenuService;
import com.bubbletalk.securitylog.dto.SecurityEventLogResponseDto;
import com.bubbletalk.securitylog.dto.SecurityEventLogSearchCondition;
import com.bubbletalk.securitylog.entity.EventType;
import com.bubbletalk.securitylog.entity.Severity;
import com.bubbletalk.securitylog.service.SecurityEventLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashBoardRestControllerTest {

    @Mock
    private MenuService menuService;

    @Mock
    private MenuSocketController menuSocketController;

    @Mock
    private AdminDashboardService adminDashboardService;

    @Mock
    private RealtimeSessionCleanupService realtimeSessionCleanupService;

    @Mock
    private SecurityEventLogService securityEventLogService;

    @Mock
    private HttpServletRequest request;

    @Test
    void securityEventsApiLogsViewEventAndReturnsPage() {
        SecurityEventLogSearchCondition condition = new SecurityEventLogSearchCondition();
        condition.setRoomCode("ROOM0001");
        PageRequest pageable = PageRequest.of(0, 20);
        when(securityEventLogService.search(condition, pageable))
                .thenReturn(new PageImpl<>(List.of(SecurityEventLogResponseDto.builder()
                        .id(1L)
                        .eventType(EventType.ROOM_CREATED)
                        .severity(Severity.INFO)
                        .roomCode("ROOM0001")
                        .build())));

        var response = controller().getSecurityEvents(condition, pageable, request);

        BaseResDto body = response.getBody();
        assertEquals("0000", body.getCode());
        verify(securityEventLogService).logEvent(
                EventType.SECURITY_LOG_VIEW,
                Severity.INFO,
                "ROOM0001",
                null,
                request,
                "관리자 보안 이벤트 로그 조회"
        );
    }

    @Test
    void adminRoomsApiReturnsPagedRooms() {
        PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdDate"));
        when(adminDashboardService.getRooms(pageable, true, RoomStatus.OPEN))
                .thenReturn(new PageImpl<>(List.of(AdminChatRoomResDto.builder()
                        .roomCode("ROOM0001")
                        .name("room")
                        .status(RoomStatus.OPEN)
                        .build()), pageable, 1));

        var response = controller().getRooms(pageable, "PRIVATE", "OPEN");

        BaseResDto body = response.getBody();
        assertEquals("0000", body.getCode());
        verify(adminDashboardService).getRooms(pageable, true, RoomStatus.OPEN);
    }

    private AdminDashBoardRestController controller() {
        return new AdminDashBoardRestController(
                menuService,
                menuSocketController,
                adminDashboardService,
                realtimeSessionCleanupService,
                securityEventLogService
        );
    }
}
