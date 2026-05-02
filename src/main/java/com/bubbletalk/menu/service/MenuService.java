package com.bubbletalk.menu.service;

import com.bubbletalk.base.dto.BaseResDto;
import com.bubbletalk.global.constant.RedisKey;
import com.bubbletalk.menu.dto.res.DailyMenuResDto;
import com.bubbletalk.menu.dto.res.MenuListResDto;
import com.bubbletalk.menu.entity.DailyMenu;
import com.bubbletalk.menu.entity.LunchHistory;
import com.bubbletalk.menu.repository.LunchHistoryRepository;
import com.bubbletalk.menu.repository.MenuRepository;
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
    private final LunchHistoryRepository lunchHistoryRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 새로운 메뉴를 생성하거나, 이미 있으면 자동으로 투표합니다.
     */
    @Transactional
    public void saveAndVote(String menuName, String voterIp) {
        DailyMenu menu = menuRepository.findByMenuName(menuName).orElse(null);

        if (menu == null) {
            // [신규 등록]
            menu = DailyMenu.builder()
                    .menuName(menuName)
                    .finalScore(0L)
                    .build();
            menu = menuRepository.save(menu);
            
            // Redis ZSET 초기화 (0점)
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            redisTemplate.opsForZSet().add(RedisKey.LUNCH_RANKING.with(today), menu.getId().toString(), 0);
            log.info("새로운 메뉴 등록: {}", menuName);
        }

        // [공통] 투표 진행 (중복 투표 체크는 increaseVote 내부에서 수행)
        increaseVote(menu.getId(), voterIp);
    }

    /**
     * [투표 로직] 특정 메뉴에 표를 던집니다.
     */
    @Transactional
    public void increaseVote(Long menuId, String voterIp) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String rankingKey = RedisKey.LUNCH_RANKING.with(today);
        String voterKey = RedisKey.LUNCH_VOTER.with(today + ":" + menuId);

        // 1. [중복 방지]
        Boolean alreadyVoted = redisTemplate.opsForSet().isMember(voterKey, voterIp);
        if (Boolean.TRUE.equals(alreadyVoted)) {
            throw new IllegalStateException("이미 이 메뉴에 화력을 지원하셨습니다!");
        }

        // 2. [Redis ZSET] 점수 상승
        redisTemplate.opsForZSet().incrementScore(rankingKey, menuId.toString(), 1);
        redisTemplate.opsForSet().add(voterKey, voterIp);
    }

    /**
     * [실시간 순위 조회]
     */
    public DailyMenuResDto getTopRankings() {
        try {
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String rankingKey = RedisKey.LUNCH_RANKING.with(today);

            Set<Object> topIds = redisTemplate.opsForZSet().reverseRange(rankingKey, 0, 9);
            
            if (topIds == null || topIds.isEmpty()) {
                return DailyMenuResDto.builder().menuList(Collections.emptyList()).build();
            }

            List<Long> ids = topIds.stream()
                    .map(id -> Long.valueOf(String.valueOf(id).replace("\"", "")))
                    .collect(Collectors.toList());

            List<DailyMenu> menus = menuRepository.findAllById(ids);
            
            List<MenuListResDto> rankings = menus.stream()
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

            return DailyMenuResDto.builder().menuList(rankings).build();
        } catch (Exception e) {
            log.error("랭킹 조회 중 오류 발생", e);
            return DailyMenuResDto.builder().menuList(Collections.emptyList()).build();
        }
    }

    /**
     * [최종 정산] Redis의 임시 데이터를 DB(LunchHistory)로 옮깁니다.
     * 매일 정해진 시간(예: 12시)에 스케줄러에 의해 실행됩니다.
     */
    @Transactional
    public void syncRedisToDb() {
        String todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String rankingKey = RedisKey.LUNCH_RANKING.with(todayStr);

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
