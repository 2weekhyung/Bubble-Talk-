package com.bubbletalk.menu.repository;

import com.bubbletalk.menu.entity.DailyMenu;
import java.util.List;

public interface MenuRepositoryCustom {
    // QueryDSL을 사용한 투표 결과 조회 예시
    List<DailyMenu> getTopMenus(int limit);
}
