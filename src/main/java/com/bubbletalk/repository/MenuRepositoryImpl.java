package com.bubbletalk.repository;

import com.bubbletalk.entity.DailyMenu;
import com.bubbletalk.entity.QDailyMenu;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import java.util.List;

@RequiredArgsConstructor
public class MenuRepositoryImpl implements MenuRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<DailyMenu> getTopMenus(int limit) {
        QDailyMenu menu = QDailyMenu.dailyMenu;

        return queryFactory
                .selectFrom(menu)
                .orderBy(menu.finalScore.desc())
                .limit(limit)
                .fetch();
    }
}
