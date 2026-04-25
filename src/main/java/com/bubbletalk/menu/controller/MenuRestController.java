package com.bubbletalk.menu.controller;

import com.bubbletalk.base.dto.BaseResDto;
import com.bubbletalk.menu.dto.req.MenuAddReqDto;
import com.bubbletalk.menu.dto.req.MenuVoteReqDto;
import com.bubbletalk.menu.dto.res.DailyMenuResDto;
import com.bubbletalk.menu.service.MenuService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * [메뉴 및 투표 관련 REST API 컨트롤러]
 * 화면(js)에서 보내는 HTTP 요청을 받아 비즈니스 로직(Service)을 실행하고 결과를 돌려줍니다.
 */
@RestController
@RequestMapping("/api/menu")
@RequiredArgsConstructor
public class MenuRestController {

    private final MenuService menuService;
    private final MenuSocketController socketController;

    /**
     * [GET] /api/menu/rankings
     * 현재 투표 순위 리스트를 가져옵니다. 
     * 페이지가 처음 열릴 때 호출되어 초기 화면을 그려줍니다.
     */
    @GetMapping("/rankings")
    public ResponseEntity<BaseResDto> getRankings() {
        // Service를 통해 DB/Redis에 저장된 현재 순위를 가져옴
        DailyMenuResDto rankings = menuService.getTopRankings();
        return ResponseEntity.ok(BaseResDto.ok(rankings));
    }

    /**
     * [POST] /api/menu/add
     * 새로운 점심 메뉴를 전장에 투입합니다.
     */
    @PostMapping("/add")
    public ResponseEntity<BaseResDto> addMenu(@RequestBody MenuAddReqDto reqDto) {
        // 1. 새로운 메뉴를 DB에 저장
        Long id = menuService.saveMenu(reqDto.getMenuName());
        
        // 2. [실시간 전파] 새로운 메뉴가 생겼음을 모든 접속자에게 소켓으로 알림
        socketController.broadcastMenuUpdate();
        
        return ResponseEntity.ok(BaseResDto.ok(id));
    }

    /**
     * [POST] /api/menu/vote
     * 특정 메뉴에 투표(화력 지원)를 합니다.
     */
    @PostMapping("/vote")
    public ResponseEntity<BaseResDto> vote(@RequestBody MenuVoteReqDto reqDto, HttpServletRequest request) {
        // 사용자의 IP 주소를 가져와 중복 투표를 방지하는 용도로 사용합니다.
        String ip = request.getRemoteAddr();
        
        // 1. 투표 점수 올리기 및 투표 이력 저장
        menuService.increaseVote(reqDto.getMenuId(), ip);
        
        // 2. [실시간 전파] 점수가 변했음을 모든 접속자에게 실시간으로 알림 (화면의 게이지가 즉시 상승)
        socketController.broadcastMenuUpdate();
        
        return ResponseEntity.ok(BaseResDto.ok());
    }
}
