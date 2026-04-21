package com.bubbletalk.vote.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "votes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id")
    private DailyMenu dailyMenu;

    @Column(nullable = false)
    private String voterIp; // ?�명 ?�표 구분???�한 IP (간단 ?�시)

    @CreatedDate
    private LocalDateTime votedAt;

    @Builder
    public Vote(DailyMenu dailyMenu, String voterIp) {
        this.dailyMenu = dailyMenu;
        this.voterIp = voterIp;
    }
}
