package com.bubbletalk.menu.repository;

import com.bubbletalk.menu.entity.DailyMenu;
import java.util.List;
import java.util.Optional;

public interface MenuRepositoryCustom {
    // QueryDSL을 사용한 투표 결과 조회 예시
    List<DailyMenu> getTopMenus(int limit);

    // [추가] 메뉴 이름으로 단건 조회
    Optional<DailyMenu> findByMenuName(String menuName);
}


