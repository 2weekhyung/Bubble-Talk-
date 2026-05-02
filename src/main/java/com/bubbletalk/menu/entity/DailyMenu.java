package com.bubbletalk.menu.entity;

import com.bubbletalk.base.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [점심 메뉴 엔티티]
 * 오늘의 전장에 투입된 점심 메뉴 정보를 관리합니다.
 */
@Entity
@Getter
@Table(name = "daily_menus")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyMenu extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 메뉴 이름 (예: 김치찌개, 돈카츠 등)
     */
    @Column(nullable = false)
    private String menuName;

    @Builder
    public DailyMenu(String menuName) {
        this.menuName = menuName;
    }
}
