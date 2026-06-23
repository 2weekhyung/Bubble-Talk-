package com.bubbletalk.menu.controller;

import com.bubbletalk.base.dto.BaseResDto;
import com.bubbletalk.guest.GuestIdSupport;
import com.bubbletalk.global.constant.RedisKey;
import com.bubbletalk.menu.dto.req.MenuAddReqDto;
import com.bubbletalk.menu.dto.req.MenuVoteReqDto;
import com.bubbletalk.menu.dto.res.DailyMenuResDto;
import com.bubbletalk.menu.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * [메뉴 및 투표 관련 REST API 컨트롤러]
 * 화면(js)에서 보내는 HTTP 요청을 받아 비즈니스 로직(Service)을 실행하고 결과를 돌려줍니다.
 */
@Tag(name = "Lunch Menu", description = "점심 메뉴 전쟁 및 투표 관련 API")
@RestController
@RequestMapping("/api/menu")
@RequiredArgsConstructor
public class MenuRestController {

    private final MenuService menuService;
    private final MenuSocketController socketController;
    private final RedisTemplate<String, Object> redisTemplate;
    private final GuestIdSupport guestIdSupport;

    /**
     * [GET] /api/menu/status
     * 현재 점심 이벤트 활성화 상태를 조회합니다.
     */
    @Operation(summary = "이벤트 상태 조회", description = "현재 점심 메뉴 투표가 활성화(OPEN) 상태인지 확인합니다.")
    @GetMapping("/status")
    public ResponseEntity<BaseResDto> getStatus() {
        Object status = redisTemplate.opsForValue().get(RedisKey.LUNCH_EVENT_STATUS.getPrefix());
        return ResponseEntity.ok(BaseResDto.ok(Map.of("status", status != null ? status : "CLOSED")));
    }

    /**
     * [GET] /api/menu/rankings
     * 현재 투표 순위 리스트를 가져옵니다. 
     * 페이지가 처음 열릴 때 호출되어 초기 화면을 그려줍니다.
     */
    @Operation(summary = "실시간 랭킹 조회", description = "현재 투표 기준 실시간 상위 10개 메뉴를 가져옵니다.")
    @GetMapping("/rankings")
    public ResponseEntity<BaseResDto> getRankings() {
        // Service를 통해 DB/Redis에 저장된 현재 순위를 가져옴
        DailyMenuResDto rankings = menuService.getTopRankings();
        return ResponseEntity.ok(BaseResDto.ok(rankings));
    }

    /**
     * [GET] /api/menu/init-data
     * 메인 화면 초기화에 필요한 통합 데이터를 가져옵니다. (어제 우승자, 운영 시간 등)
     */
    @Operation(summary = "메인 초기 데이터 조회", description = "어제 우승자 정보와 운영 종료 시간을 가져옵니다.")
    @GetMapping("/init-data")
    public ResponseEntity<BaseResDto> getInitData() {
        Map<String, String> times = menuService.getEventTimes();
        com.bubbletalk.menu.dto.res.LunchHistoryResDto winner = menuService.getYesterdayWinner();
        
        return ResponseEntity.ok(BaseResDto.ok(Map.of(
            "endTime", times.get("endTime"),
            "yesterdayWinner", winner != null ? winner.getMenuName() : "없음",
            "yesterdayVotes", winner != null ? winner.getVoteCount().toString() : "0"
        )));
    }

    /**
     * [POST] /api/menu/add
     * 새로운 점심 메뉴를 전장에 투입하거나, 중복 시 자동 투표합니다.
     */
    @Operation(summary = "메뉴 추가 및 투표", description = "새로운 메뉴를 등록하거나, 이미 존재하는 경우 해당 메뉴에 자동으로 투표합니다.")
    @PostMapping("/add")
    public ResponseEntity<BaseResDto> addMenu(@RequestBody MenuAddReqDto reqDto,
                                              @RequestHeader(value = "X-Client-Id", required = false) String clientId,
                                              HttpServletRequest request) {
        String requesterId = resolveVoterId(clientId, request);
        try {
            // 1. 메뉴 저장 및 투표 통합 처리
            menuService.saveMenu(reqDto.getMenuName(), requesterId);
            
            // 2. 실시간 전파 (목록만 갱신)
            socketController.broadcastMenuUpdate();
            
            return ResponseEntity.ok(BaseResDto.ok());
        } catch (com.bubbletalk.global.exception.BusinessException e) {
            return ResponseEntity.badRequest().body(new BaseResDto("4002", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new BaseResDto("5000", "처리 중 오류가 발생했습니다."));
        }
    }

    /**
     * [POST] /api/menu/admin/status
     * [관리자] 이벤트 상태를 강제로 변경합니다.
     */
    @Operation(summary = "이벤트 상태 강제 변경", description = "[관리자] 투표 이벤트를 강제로 OPEN 또는 CLOSED 상태로 전환합니다.")
    @PostMapping("/admin/status")
    public ResponseEntity<BaseResDto> updateStatus(@RequestBody Map<String, String> body) {
        String status = body.get("status");
        menuService.updateEventStatus(status);
        socketController.broadcastMenuUpdate(); // 상태 변경 알림을 위해 목록 갱신 호출
        return ResponseEntity.ok(BaseResDto.ok());
    }

    /**
     * [POST] /api/menu/admin/reset
     * [관리자] 오늘의 모든 데이터를 초기화합니다.
     */
    @Operation(summary = "오늘의 데이터 초기화", description = "[관리자] 오늘 발생한 모든 랭킹 및 투표 데이터를 삭제합니다. 복구 불가능합니다.")
    @PostMapping("/admin/reset")
    public ResponseEntity<BaseResDto> resetData() {
        menuService.resetDailyData();
        socketController.broadcastMenuUpdate();
        return ResponseEntity.ok(BaseResDto.ok());
    }

    /**
     * [GET] /api/menu/admin/history
     * [관리자] 과거 점심 투표 이력을 조회합니다.
     */
    @Operation(summary = "투표 이력 조회", description = "[관리자] 과거에 종료된 점심 투표 결과 이력을 모두 가져옵니다.")
    @GetMapping("/admin/history")
    public ResponseEntity<BaseResDto> getHistory() {
        return ResponseEntity.ok(BaseResDto.ok(menuService.getLunchHistory()));
    }

    /**
     * [POST] /api/menu/vote
     * 특정 메뉴에 투표(화력 지원)를 합니다.
     */
    @Operation(summary = "메뉴 투표 (화력 지원)", description = "특정 메뉴 ID에 대해 1표를 추가합니다. 중복 투표 시 에러가 발생합니다.")
    @PostMapping("/vote")
    public ResponseEntity<BaseResDto> vote(@RequestBody MenuVoteReqDto reqDto,
                                           @RequestHeader(value = "X-Client-Id", required = false) String clientId,
                                           HttpServletRequest request) {
        String voterId = resolveVoterId(clientId, request);
        try {
            menuService.increaseVote(reqDto.getMenuId(), voterId);
            socketController.broadcastMenuUpdate();
            return ResponseEntity.ok(BaseResDto.ok());
        } catch (com.bubbletalk.global.exception.BusinessException e) {
            return ResponseEntity.badRequest().body(new BaseResDto("4002", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new BaseResDto("5000", "처리 중 오류가 발생했습니다."));
        }
    }

    private String resolveVoterId(String clientId, HttpServletRequest request) {
        return guestIdSupport.resolve(request)
                .map(guestId -> "guest:" + guestId)
                .orElseGet(() -> resolveLegacyVoterId(clientId, request));
    }

    private String resolveLegacyVoterId(String clientId, HttpServletRequest request) {
        if (clientId != null && !clientId.isBlank()) {
            return "client:" + clientId;
        }
        return "ip:" + request.getRemoteAddr();
    }
}
