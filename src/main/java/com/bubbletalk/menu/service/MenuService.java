package com.bubbletalk.menu.service;

import com.bubbletalk.base.dto.BaseResDto;
import com.bubbletalk.menu.dto.res.DailyMenuResDto;
import com.bubbletalk.menu.dto.res.MenuListResDto;
import com.bubbletalk.menu.entity.DailyMenu;
import com.bubbletalk.menu.entity.LunchHistory;
import com.bubbletalk.menu.repository.LunchHistoryRepository;
import com.bubbletalk.menu.repository.MenuRepository;
import com.bubbletalk.vote.entity.Vote;
import com.bubbletalk.vote.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * [점심 메뉴 전쟁 비즈니스 로직]
 * DB와 Redis를 조율하며 메뉴 저장, 투표, 순위 계산을 처리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuService {

    private final MenuRepository menuRepository;
    private final VoteRepository voteRepository;
    private final LunchHistoryRepository lunchHistoryRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    // Redis에서 사용할 키 이름 정의 (예: lunch:ranking:20240424)
    private static final String RANKING_KEY_PREFIX = "lunch:ranking:";
    private static final String VOTER_KEY_PREFIX = "lunch:voters:";

    /**
     * 새로운 메뉴를 생성합니다.
     */
    @Transactional
    public Long saveMenu(String menuName) {
        DailyMenu menu = DailyMenu.builder()
                .menuName(menuName)
                .finalScore(0L)
                .build();
        return menuRepository.save(menu).getId();
    }

    /**
     * [투표 로직] 특정 메뉴에 표를 던집니다.
     * Redis의 ZSET(순서가 있는 세트) 기능을 사용하여 실시간 순위를 관리합니다.
     */
    @Transactional
    public void increaseVote(Long menuId, String voterIp) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String rankingKey = RANKING_KEY_PREFIX + today;
        String voterKey = VOTER_KEY_PREFIX + today + ":" + menuId;

        // 1. [중복 방지] Redis Set을 사용하여 이 IP가 이 메뉴에 이미 투표했는지 체크
        Boolean alreadyVoted = redisTemplate.opsForSet().isMember(voterKey, voterIp);
        if (Boolean.TRUE.equals(alreadyVoted)) {
            log.info("이미 투표한 사용자입니다. IP={}, MenuID={}", voterIp, menuId);
            return;
        }

        // 2. [Redis ZSET] 해당 메뉴의 점수를 1 올림 (실시간 랭킹 시스템의 핵심)
        redisTemplate.opsForZSet().incrementScore(rankingKey, menuId.toString(), 1);

        // 3. 중복 방지용 셋에 IP 추가
        redisTemplate.opsForSet().add(voterKey, voterIp);

        // 4. [DB 영속화] 투표 이력을 DB에도 남기고, 엔티티의 점수를 업데이트
        DailyMenu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new IllegalArgumentException("해당 메뉴가 존재하지 않습니다. ID=" + menuId));
        
        voteRepository.save(Vote.builder()
                .dailyMenu(menu)
                .voterIp(voterIp)
                .build());
        
        menu.addScore(); // DB의 finalScore 필드 증가
    }

    /**
     * [실시간 순위 조회] 
     * Redis ZSET에서 점수가 높은 순으로 상위 메뉴 리스트를 가져옵니다.
     */
    public DailyMenuResDto getTopRankings() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String rankingKey = RANKING_KEY_PREFIX + today;

        // Redis에서 점수가 높은 순으로 10개(0~9) 가져오기
        Set<Object> topIds = redisTemplate.opsForZSet().reverseRange(rankingKey, 0, 9);
        
        if (topIds == null || topIds.isEmpty()) {
            return DailyMenuResDto.builder()
                    .menuList(Collections.emptyList())
                    .build();
        }

        // 가져온 ID들로 DB에서 메뉴 정보를 조회
        List<Long> ids = topIds.stream()
                .map(id -> Long.valueOf(id.toString()))
                .collect(Collectors.toList());

        List<MenuListResDto> rankings = menuRepository.findAllById(ids).stream()
                .map(menu -> {
                    MenuListResDto dto = new MenuListResDto();
                    dto.setId(menu.getId());
                    dto.setMenuName(menu.getMenuName());
                    
                    Double score = redisTemplate.opsForZSet().score(rankingKey, menu.getId().toString());
                    dto.setFinalScore(score != null ? score.longValue() : 0L);
                    return dto;
                })
                .sorted((m1, m2) -> Long.compare(m2.getFinalScore(), m1.getFinalScore()))
                .collect(Collectors.toList());

        return DailyMenuResDto.builder()
                .menuList(rankings)
                .build();
    }

    /**
     * [최종 정산] Redis의 임시 데이터를 DB(LunchHistory)로 옮깁니다.
     * 매일 정해진 시간(예: 12시)에 스케줄러에 의해 실행됩니다.
     */
    @Transactional
    public void syncRedisToDb() {
        String todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String rankingKey = RANKING_KEY_PREFIX + todayStr;

        // Redis ZSET의 모든 데이터를 점수와 함께 가져옴
        Set<ZSetOperations.TypedTuple<Object>> results = 
                redisTemplate.opsForZSet().reverseRangeWithScores(rankingKey, 0, -1);

        if (results == null || results.isEmpty()) return;

        int rank = 1;
        for (ZSetOperations.TypedTuple<Object> tuple : results) {
            Long menuId = Long.valueOf(Objects.requireNonNull(tuple.getValue()).toString());
            Long score = Objects.requireNonNull(tuple.getScore()).longValue();

            DailyMenu menu = menuRepository.findById(menuId).orElse(null);
            if (menu != null) {
                // 결과 이력 테이블에 최종 순위와 득표수 저장
                lunchHistoryRepository.save(LunchHistory.builder()
                        .targetDate(LocalDate.now())
                        .menuName(menu.getMenuName())
                        .voteCount(score)
                        .ranking(rank++)
                        .build());
            }
        }

        // 정산 끝났으니 오늘 데이터 삭제 (내일을 위해 초기화)
        redisTemplate.delete(rankingKey);
        log.info("정산 완료. Redis 랭킹 데이터 삭제 완료.");
    }
}
