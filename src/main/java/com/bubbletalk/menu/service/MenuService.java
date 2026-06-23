package com.bubbletalk.menu.service;

import com.bubbletalk.base.dto.BaseResDto;
import com.bubbletalk.global.constant.RedisKey;
import com.bubbletalk.global.exception.BusinessException;
import com.bubbletalk.menu.dto.res.DailyMenuResDto;
import com.bubbletalk.menu.dto.res.LunchHistoryResDto;
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
import java.util.concurrent.TimeUnit;
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

    private static final int MENU_NAME_MAX_LENGTH = 20;
    private static final int MENU_ADD_LIMIT_SECONDS = 30;

    private final MenuRepository menuRepository;
    private final LunchHistoryRepository lunchHistoryRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 새로운 메뉴를 생성하거나, 이미 있으면 자동으로 투표합니다.
     */
    @Transactional
    public void saveMenu(String menuName) {
        String normalizedMenuName = normalizeMenuName(menuName);
        DailyMenu menu = menuRepository.findByMenuName(normalizedMenuName).orElse(null);
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String rankingKey = RedisKey.LUNCH_RANKING.with(today);

        if (menu == null) {
            menu = DailyMenu.builder()
                    .menuName(normalizedMenuName)
                    .build();
            menu = menuRepository.save(menu);
            log.info("?덈줈??硫붾돱 ?깅줉: {}", menuName);
        }

        if (redisTemplate.opsForZSet().score(rankingKey, menu.getId().toString()) == null) {
            redisTemplate.opsForZSet().add(rankingKey, menu.getId().toString(), 0);
        }
    }

    @Transactional
    public void saveMenu(String menuName, String requesterId) {
        String normalizedMenuName = normalizeMenuName(menuName);
        validateMenuAddRateLimit(requesterId);
        saveMenu(normalizedMenuName);
    }

    @Transactional
    public void saveAndVote(String menuName, String voterIp) {
        String normalizedMenuName = normalizeMenuName(menuName);
        DailyMenu menu = menuRepository.findByMenuName(normalizedMenuName).orElse(null);

        if (menu == null) {
            // [신규 등록]
            menu = DailyMenu.builder()
                    .menuName(normalizedMenuName)
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

        Long addedCount = redisTemplate.opsForSet().add(voterKey, voterIp);
        if (addedCount == null) {
            log.error("vote duplicate check failed: voterKey={}, voterId={}", voterKey, voterIp);
            throw new BusinessException("투표 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        }

        if (addedCount == 0) {
            throw new BusinessException("이미 이 메뉴에 투표했습니다.");
        }

        redisTemplate.opsForZSet().incrementScore(rankingKey, menuId.toString(), 1);
    }

    private void validateMenuAddRateLimit(String requesterId) {
        if (requesterId == null || requesterId.isBlank()) {
            throw new BusinessException("메뉴 추가 요청자를 확인할 수 없습니다.");
        }

        String key = RedisKey.MENU_ADD_RATELIMIT.with(requesterId);
        Boolean allowed = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", MENU_ADD_LIMIT_SECONDS, TimeUnit.SECONDS);

        if (allowed == null) {
            log.error("menu add rate limit check failed: requesterId={}", requesterId);
            throw new BusinessException("메뉴 추가 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        }

        if (!allowed) {
            throw new BusinessException("메뉴는 30초에 한 번만 추가할 수 있습니다.");
        }
    }

    private String normalizeMenuName(String menuName) {
        if (menuName == null) {
            throw new BusinessException("메뉴명을 입력해주세요.");
        }

        String normalized = menuName.trim();
        if (normalized.isBlank()) {
            throw new BusinessException("메뉴명을 입력해주세요.");
        }

        if (normalized.length() > MENU_NAME_MAX_LENGTH) {
            throw new BusinessException("메뉴명은 20자 이하로 입력해주세요.");
        }

        if (containsUnsafeHtml(normalized)) {
            throw new BusinessException("메뉴명에 사용할 수 없는 문자가 포함되어 있습니다.");
        }

        return normalized;
    }

    private boolean containsUnsafeHtml(String value) {
        return value.contains("<")
                || value.contains(">")
                || value.contains("&lt;")
                || value.contains("&gt;")
                || value.toLowerCase().contains("script");
    }

    /**
     * [강제 상태 변경] 투표 이벤트의 상태를 OPEN 또는 CLOSED로 변경합니다.
     */
    @Transactional
    public void updateEventStatus(String status) {
        redisTemplate.opsForValue().set(RedisKey.LUNCH_EVENT_STATUS.getPrefix(), status);
        log.info("관리자에 의해 이벤트 상태가 강제로 변경되었습니다: {}", status);
    }

    /**
     * [운영 시간 조회] Redis에서 현재 설정된 운영 시간을 가져옵니다.
     */
    public java.util.Map<String, String> getEventTimes() {
        String startTime = (String) redisTemplate.opsForValue().get(RedisKey.LUNCH_START_TIME.getPrefix());
        String endTime = (String) redisTemplate.opsForValue().get(RedisKey.LUNCH_END_TIME.getPrefix());
        
        return java.util.Map.of(
            "startTime", startTime != null ? startTime : "09:00",
            "endTime", endTime != null ? endTime : "14:00"
        );
    }

    /**
     * [운영 시간 설정] Redis에 운영 시간을 저장합니다.
     */
    @Transactional
    public void updateEventTimes(String startTime, String endTime) {
        redisTemplate.opsForValue().set(RedisKey.LUNCH_START_TIME.getPrefix(), startTime);
        redisTemplate.opsForValue().set(RedisKey.LUNCH_END_TIME.getPrefix(), endTime);
        log.info("관리자에 의해 운영 시간이 변경되었습니다: {} ~ {}", startTime, endTime);
    }

    /**
     * [데이터 초기화] 오늘의 모든 랭킹 및 투표자 이력을 삭제합니다.
     */
    @Transactional
    public void resetDailyData() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        
        // 1. 랭킹 데이터 삭제
        redisTemplate.delete(RedisKey.LUNCH_RANKING.with(today));
        
        // 2. 투표자 이력 삭제 (패턴 검색 후 일괄 삭제)
        Set<String> voterKeys = redisTemplate.keys(RedisKey.LUNCH_VOTER.with(today + ":*"));
        if (voterKeys != null && !voterKeys.isEmpty()) {
            redisTemplate.delete(voterKeys);
        }

        log.warn("관리자에 의해 오늘의 모든 투표 데이터가 초기화되었습니다.");
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
     * [이력 조회] 과거 점심 투표 결과 이력을 조회합니다.
     */
    public List<LunchHistoryResDto> getLunchHistory() {
        return lunchHistoryRepository.findAll().stream()
                .map(history -> LunchHistoryResDto.builder()
                        .id(history.getId())
                        .targetDate(history.getTargetDate())
                        .menuName(history.getMenuName())
                        .voteCount(history.getVoteCount())
                        .ranking(history.getRanking())
                        .build())
                .sorted((h1, h2) -> h2.getTargetDate().compareTo(h1.getTargetDate()))
                .collect(Collectors.toList());
    }
/**
 * [어제의 우승자 조회] 가장 최근 종료된 투표의 1위 메뉴를 가져옵니다.
 */
public LunchHistoryResDto getYesterdayWinner() {
    // 가장 최근 날짜의 1위 데이터를 조회
    return lunchHistoryRepository.findAll().stream()
            .filter(h -> h.getRanking() == 1)
            .sorted((h1, h2) -> h2.getTargetDate().compareTo(h1.getTargetDate()))
            .map(history -> LunchHistoryResDto.builder()
                    .targetDate(history.getTargetDate())
                    .menuName(history.getMenuName())
                    .voteCount(history.getVoteCount())
                    .build())
            .findFirst()
            .orElse(null);
}

/**
 * [최종 정산] Redis의 임시 데이터를 DB(LunchHistory)로 옮깁니다.
 *
   매일 정해진 시간(예: 12시)에 스케줄러에 의해 실행됩니다.
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
