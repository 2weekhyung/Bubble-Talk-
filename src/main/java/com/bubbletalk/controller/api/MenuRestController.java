package com.bubbletalk.controller.api;

import com.bubbletalk.base.dto.BaseDto;
import com.bubbletalk.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * [REST API 컨트롤러]
 * 클라이언트로부터 요청(메뉴 추가, 투표 등)을 받아 처리하고
 * 결과를 반환합니다. 데이터 저장 후 실시간 전송을 위해 소켓 컨트롤러를 호출합니다.
 */
@RestController
@RequestMapping("/api/menu")
@RequiredArgsConstructor
public class MenuRestController {

    private final MenuService menuService;
    private final MenuSocketController socketController;

    /**
     * [개념] 메뉴 추가 (REST 요청)
     * 누군가 브라우저에서 메뉴 이름을 입력하면 이 메서드가 호출되어 DB에 저장됩니다.
     */
    @PostMapping("/add")
    public BaseDto addMenu(@RequestParam String menuName) {
        try {
            // DB에 메뉴를 저장합니다.
            Long id = menuService.saveMenu(menuName);

            // [핵심] 데이터가 바뀌었으므로 모든 클라이언트에게 알려줍니다!
            socketController.broadcastMenuUpdate();

            return new BaseDto("0000", "정상 저장되었습니다.");
        } catch (Exception e) {
            return new BaseDto("9999", "실패: " + e.getMessage());
        }
    }

    /**
     * [개념] 투표 (REST 요청)
     * 특정 메뉴를 클릭하면 해당 메뉴의 점수를 1 올립니다.
     */
    @PostMapping("/vote")
    public BaseDto vote(@RequestParam Long menuId) {
        try {
            // 득표수를 올리는 비즈니스 로직(서비스에서 구현 필요)
            menuService.increaseVote(menuId);

            // [핵심] 투표로 데이터가 바뀌었으므로 즉시 방송합니다!
            socketController.broadcastMenuUpdate();

            return new BaseDto("0000", "투표가 완료되었습니다.");
        } catch (Exception e) {
            return new BaseDto("9999", "실패: " + e.getMessage());
        }
    }

    /**
     * [개념] 초기 랭킹 조회 (REST 요청)
     * 페이지가 처음 열릴 때 현재 상태를 한 번 가져옵니다.
     */
    @GetMapping("/rankings")
    public BaseDto getRankings() {
        return new BaseDto(menuService.getTopRankings());
    }
}
