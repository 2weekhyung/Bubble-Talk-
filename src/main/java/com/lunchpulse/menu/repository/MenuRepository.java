package com.lunchpulse.menu.repository;

import com.lunchpulse.menu.entity.DailyMenu;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuRepository extends JpaRepository<DailyMenu, Long>, MenuRepositoryCustom {
    boolean existsByMenuName(String menuName);
}
