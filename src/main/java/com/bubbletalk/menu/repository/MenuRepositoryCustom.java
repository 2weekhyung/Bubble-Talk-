package com.bubbletalk.menu.repository;

import com.bubbletalk.menu.entity.DailyMenu;
import java.util.List;
import java.util.Optional;

public interface MenuRepositoryCustom {
    // [추가] 메뉴 이름으로 단건 조회
    Optional<DailyMenu> findByMenuName(String menuName);
}


