package com.bubbletalk.entity;

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
@Table(name = "daily_menus")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class DailyMenu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String menuName;

    @Column(nullable = false)
    private Long finalScore;

    @CreatedDate
    private LocalDateTime selectedAt;

    @Builder
    public DailyMenu(String menuName, Long finalScore) {
        this.menuName = menuName;
        this.finalScore = finalScore;
    }

    /**
     * [개념] 도메인 메서드
     * 엔티티 스스로 데이터를 안전하게 관리하도록 합니다. 
     * 외부에서 setter로 막 바꾸는 것보다 안전합니다.
     */
    public void addScore() {
        if (this.finalScore == null) {
            this.finalScore = 0L;
        }
        this.finalScore++;
    }
}
