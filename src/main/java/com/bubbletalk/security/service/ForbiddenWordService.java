package com.bubbletalk.security.service;

import com.bubbletalk.global.constant.RedisKey;
import com.bubbletalk.security.entity.ForbiddenWord;
import com.bubbletalk.security.repository.ForbiddenWordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * [금칙어 관리 서비스]
 * DB와 Redis 캐시를 동기화하며 금칙어 CRUD를 처리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ForbiddenWordService {

    private final ForbiddenWordRepository forbiddenWordRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * [금칙어 추가]
     * DB에 저장하고 Redis Set 캐시에도 즉시 반영합니다.
     */
    @Transactional
    public void addWord(String word) {
        if (forbiddenWordRepository.existsByWord(word)) {
            throw new IllegalArgumentException("이미 등록된 금칙어입니다: " + word);
        }

        ForbiddenWord forbiddenWord = ForbiddenWord.builder()
                .word(word)
                .build();
        forbiddenWordRepository.save(forbiddenWord);

        // Redis 캐시에 추가
        redisTemplate.opsForSet().add(RedisKey.CHAT_FORBIDDEN.getPrefix(), word);
        log.info("금칙어 추가 및 캐싱 완료: {}", word);
    }

    /**
     * [금칙어 삭제]
     * DB에서 삭제하고 Redis Set 캐시에서도 제거합니다.
     */
    @Transactional
    public void deleteWord(Long id) {
        ForbiddenWord word = forbiddenWordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 금칙어 ID입니다."));
        
        forbiddenWordRepository.delete(word);

        // Redis 캐시에서 제거
        redisTemplate.opsForSet().remove(RedisKey.CHAT_FORBIDDEN.getPrefix(), word.getWord());
        log.info("금칙어 삭제 및 캐시 갱신 완료: {}", word.getWord());
    }

    /**
     * [금칙어 목록 조회]
     * 우선순위: Redis 캐시 -> (없으면) DB 조회 후 캐싱
     */
    public List<String> getForbiddenWords() {
        String key = RedisKey.CHAT_FORBIDDEN.getPrefix();
        
        // 1. Redis에서 먼저 조회
        Set<Object> cachedWords = redisTemplate.opsForSet().members(key);

        if (cachedWords != null && !cachedWords.isEmpty()) {
            return cachedWords.stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }

        // 2. 캐시가 비어있다면 DB에서 로드 (Cache Aside 패턴)
        log.warn("금칙어 캐시가 비어있어 DB에서 직접 로드합니다.");
        List<String> wordsFromDb = forbiddenWordRepository.findAll().stream()
                .map(ForbiddenWord::getWord)
                .toList();

        if (!wordsFromDb.isEmpty()) {
            redisTemplate.opsForSet().add(key, wordsFromDb.toArray());
        }

        return wordsFromDb;
    }

    /**
     * [초기화] DB의 모든 금칙어를 Redis로 로드합니다.
     */
    @Transactional
    public void refreshCache() {
        String key = RedisKey.CHAT_FORBIDDEN.getPrefix();
        redisTemplate.delete(key);

        List<String> words = forbiddenWordRepository.findAll().stream()
                .map(ForbiddenWord::getWord)
                .toList();

        if (!words.isEmpty()) {
            redisTemplate.opsForSet().add(key, words.toArray());
        }
        log.info("금칙어 캐시 초기화 완료. (총 {}건)", words.size());
    }
}
