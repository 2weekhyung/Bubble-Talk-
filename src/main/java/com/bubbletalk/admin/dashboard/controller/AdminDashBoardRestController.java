package com.bubbletalk.admin.dashboard.controller;

import com.bubbletalk.base.dto.BaseResDto;
import com.bubbletalk.admin.dashboard.service.AdminDashboardService;
import com.bubbletalk.menu.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * [관리자 전용 API 컨트롤러]
 * 서비스 운영 설정을 위한 REST API를 제공합니다.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminDashBoardRestController {

    private final MenuService menuService;
    private final com.bubbletalk.menu.controller.MenuSocketController socketController;
    private final AdminDashboardService adminDashboardService;
    private final com.bubbletalk.admin.dashboard.service.RealtimeSessionCleanupService realtimeSessionCleanupService;

    @GetMapping("/dashboard/summary")
    public ResponseEntity<BaseResDto> getDashboardSummary() {
        return ResponseEntity.ok(BaseResDto.ok(adminDashboardService.getSummary()));
    }

    @GetMapping("/rooms")
    public ResponseEntity<BaseResDto> getRooms() {
        return ResponseEntity.ok(BaseResDto.ok(adminDashboardService.getRooms()));
    }

    @PostMapping("/rooms/{roomCode}/close")
    public ResponseEntity<BaseResDto> closeRoom(@PathVariable String roomCode) {
        return ResponseEntity.ok(BaseResDto.ok(adminDashboardService.closeRoom(roomCode)));
    }

    @PostMapping("/realtime/cleanup-stale-sessions")
    public ResponseEntity<BaseResDto> cleanupStaleSessions() {
        return ResponseEntity.ok(BaseResDto.ok(realtimeSessionCleanupService.cleanupStaleSessions()));
    }

    /**
     * [POST] /api/admin/announcement
     * 전역 시스템 공지를 발송합니다.
     */
    @PostMapping("/announcement")
    public ResponseEntity<BaseResDto> sendAnnouncement(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        if (message != null && !message.isBlank()) {
            socketController.broadcastSystemMessage("📢 [공지] " + message);
        }
        return ResponseEntity.ok(BaseResDto.ok());
    }

    /**
     * [GET] /api/admin/lunch/times
     * 현재 설정된 점심 투표 운영 시간을 조회합니다.
     */
    @GetMapping("/lunch/times")
    public ResponseEntity<BaseResDto> getLunchTimes() {
        return ResponseEntity.ok(BaseResDto.ok(menuService.getEventTimes()));
    }

    /**
     * [POST] /api/admin/lunch/times
     * 점심 투표 운영 시간을 수정합니다.
     */
    @PostMapping("/lunch/times")
    public ResponseEntity<BaseResDto> updateLunchTimes(@RequestBody Map<String, String> body) {
        String startTime = body.get("startTime");
        String endTime = body.get("endTime");
        
        menuService.updateEventTimes(startTime, endTime);
        return ResponseEntity.ok(BaseResDto.ok());
    }
}
