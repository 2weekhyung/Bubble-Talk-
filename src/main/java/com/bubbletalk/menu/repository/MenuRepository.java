package com.bubbletalk.menu.repository;

import com.bubbletalk.menu.entity.DailyMenu;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuRepository extends JpaRepository<DailyMenu, Long>, MenuRepositoryCustom {
}
