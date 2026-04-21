package com.bubbletalk.menu.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "TB_LUNCH_HISTORY")
public class LunchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate targetDate; // 투표 날짜

    @Column(nullable = false)
    private String menuName; // 메뉴명

    @Column(nullable = false)
    private Long voteCount; // 득표수

    @Column(nullable = false)
    private Integer ranking; // 최종 순위

    @Builder
    public LunchHistory(LocalDate targetDate, String menuName, Long voteCount, Integer ranking) {
        this.targetDate = targetDate;
        this.menuName = menuName;
        this.voteCount = voteCount;
        this.ranking = ranking;
    }
}
