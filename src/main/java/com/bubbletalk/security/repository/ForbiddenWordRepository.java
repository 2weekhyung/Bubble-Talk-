package com.bubbletalk.security.repository;

import com.bubbletalk.security.entity.ForbiddenWord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * [금칙어 데이터 관리 리포지토리]
 * DB의 'forbidden_words' 테이블에 접근하여 금칙어 데이터를 처리합니다.
 */
public interface ForbiddenWordRepository extends JpaRepository<ForbiddenWord, Long> {

    /**
     * 단어의 텍스트 값을 기준으로 금칙어를 찾습니다.
     */
    Optional<ForbiddenWord> findByWord(String word);

    /**
     * 특정 단어가 이미 금칙어 목록에 존재하는지 여부를 확인합니다. (중복 등록 방지용)
     */
    boolean existsByWord(String word);
}
