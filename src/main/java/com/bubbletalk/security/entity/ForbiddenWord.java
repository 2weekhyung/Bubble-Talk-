package com.bubbletalk.security.entity;

import com.bubbletalk.base.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "forbidden_words")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ForbiddenWord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String word;

    @Builder
    public ForbiddenWord(String word) {
        this.word = word;
    }
}
