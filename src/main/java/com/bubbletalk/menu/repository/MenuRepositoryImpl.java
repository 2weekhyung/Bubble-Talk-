package com.bubbletalk.menu.repository;

import com.bubbletalk.menu.entity.DailyMenu;
import com.bubbletalk.menu.entity.QDailyMenu;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.Optional;
@RequiredArgsConstructor
public class MenuRepositoryImpl implements MenuRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<DailyMenu> findByMenuName(String menuName) {
        QDailyMenu menu = QDailyMenu.dailyMenu;

        DailyMenu result = queryFactory
                .selectFrom(menu)
                .where(menu.menuName.eq(menuName))
                .fetchOne();

        return Optional.ofNullable(result);
    }
}
