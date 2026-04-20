package com.bubbletalk.repository;

import com.bubbletalk.entity.DailyMenu;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuRepository extends JpaRepository<DailyMenu, Long>, MenuRepositoryCustom {
}
