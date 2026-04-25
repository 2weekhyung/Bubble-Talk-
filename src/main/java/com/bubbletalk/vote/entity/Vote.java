package com.bubbletalk.vote.entity;

import com.bubbletalk.base.entity.BaseEntity;
import com.bubbletalk.menu.entity.DailyMenu;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "votes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Vote extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_menu_id")
    private DailyMenu dailyMenu;

    private String voterIp;

    @Builder
    public Vote(DailyMenu dailyMenu, String voterIp) {
        this.dailyMenu = dailyMenu;
        this.voterIp = voterIp;
    }
}
