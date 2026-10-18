package com.lunchpulse.menu.repository;

import com.lunchpulse.menu.entity.LunchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface LunchHistoryRepository extends JpaRepository<LunchHistory, Long> {
    List<LunchHistory> findByTargetDate(LocalDate targetDate);
}
