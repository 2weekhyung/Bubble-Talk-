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
     * 새로운 점심 메뉴를 전장에 투입하거나, 중복 시 자동 투표합니다.
     */
    @PostMapping("/add")
    public ResponseEntity<BaseResDto> addMenu(@RequestBody MenuAddReqDto reqDto, HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        try {
            // 1. 메뉴 저장 및 투표 통합 처리
            menuService.saveAndVote(reqDto.getMenuName(), ip);
            
            // 2. 실시간 전파
            socketController.broadcastMenuUpdate();
            socketController.broadcastSystemMessage("🚀 [" + reqDto.getMenuName() + "] 화력 지원 개시!");
            
            return ResponseEntity.ok(BaseResDto.ok());
        } catch (IllegalStateException e) {
            // 이미 투표한 경우 등
            return ResponseEntity.badRequest().body(new BaseResDto("4002", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new BaseResDto("5000", "처리 중 오류가 발생했습니다."));
        }
    }

    /**
     * [POST] /api/menu/vote
     * 특정 메뉴에 투표(화력 지원)를 합니다.
     */
    @PostMapping("/vote")
    public ResponseEntity<BaseResDto> vote(@RequestBody MenuVoteReqDto reqDto, HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        try {
            menuService.increaseVote(reqDto.getMenuId(), ip);
            socketController.broadcastMenuUpdate();
            return ResponseEntity.ok(BaseResDto.ok());
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(new BaseResDto("4002", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new BaseResDto("5000", "이미 이 메뉴에 화력을 지원하셨습니다!"));
        }
    }
}
