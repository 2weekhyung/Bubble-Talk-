package com.bubbletalk.menu.service;

import com.bubbletalk.global.constant.RedisKey;
import com.bubbletalk.global.exception.BusinessException;
import com.bubbletalk.menu.entity.DailyMenu;
import com.bubbletalk.menu.repository.LunchHistoryRepository;
import com.bubbletalk.menu.repository.MenuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

    @Mock
    private MenuRepository menuRepository;

    @Mock
    private LunchHistoryRepository lunchHistoryRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ZSetOperations<String, Object> zSetOperations;

    @Mock
    private SetOperations<String, Object> setOperations;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private MenuService menuService;

    @BeforeEach
    void setUp() {
        menuService = new MenuService(menuRepository, lunchHistoryRepository, redisTemplate);
    }

    @Test
    @DisplayName("saveMenu registers menu with zero votes")
    void saveMenu_NewMenuWithoutVote() {
        String menuName = "test-menu";
        DailyMenu savedMenu = menu(1L, menuName);

        when(menuRepository.findByMenuName(menuName)).thenReturn(Optional.empty());
        when(menuRepository.save(any(DailyMenu.class))).thenReturn(savedMenu);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.score(todayRankingKey(), "1")).thenReturn(null);

        menuService.saveMenu(menuName);

        verify(zSetOperations).add(todayRankingKey(), "1", 0);
        verify(redisTemplate, never()).opsForSet();
    }

    @Test
    @DisplayName("saveMenu keeps duplicate menu as one row")
    void saveMenu_DuplicateMenu() {
        String menuName = "duplicate-menu";
        DailyMenu existingMenu = menu(2L, menuName);

        when(menuRepository.findByMenuName(menuName)).thenReturn(Optional.of(existingMenu));
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.score(todayRankingKey(), "2")).thenReturn(0.0);

        menuService.saveMenu(menuName);

        verify(menuRepository, never()).save(any(DailyMenu.class));
        verify(zSetOperations, never()).add(any(), any(), anyDouble());
    }

    @Test
    @DisplayName("saveMenu with requester applies rate limit and stores trimmed menu name")
    void saveMenu_WithRequesterAppliesRateLimitAndTrimsName() {
        String requesterId = "guest:abc";
        DailyMenu savedMenu = menu(3L, "김밥");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                eq(RedisKey.MENU_ADD_RATELIMIT.with(requesterId)),
                eq("1"),
                eq(30L),
                eq(TimeUnit.SECONDS)
        )).thenReturn(true);
        when(menuRepository.findByMenuName("김밥")).thenReturn(Optional.empty());
        when(menuRepository.save(any(DailyMenu.class))).thenReturn(savedMenu);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.score(todayRankingKey(), "3")).thenReturn(null);

        menuService.saveMenu("  김밥  ", requesterId);

        ArgumentCaptor<DailyMenu> captor = forClass(DailyMenu.class);
        verify(menuRepository).save(captor.capture());
        assertEquals("김밥", captor.getValue().getMenuName());
        verify(zSetOperations).add(todayRankingKey(), "3", 0);
    }

    @Test
    @DisplayName("saveMenu with requester blocks repeated add while rate limited")
    void saveMenu_WithRequesterBlocksRateLimitedRequest() {
        String requesterId = "guest:abc";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                eq(RedisKey.MENU_ADD_RATELIMIT.with(requesterId)),
                eq("1"),
                eq(30L),
                eq(TimeUnit.SECONDS)
        )).thenReturn(false);

        assertThrows(BusinessException.class, () -> menuService.saveMenu("김밥", requesterId));

        verifyNoInteractions(menuRepository);
        verify(redisTemplate, never()).opsForZSet();
    }

    @Test
    @DisplayName("saveMenu rejects invalid menu names before rate limit")
    void saveMenu_RejectsInvalidMenuNames() {
        String requesterId = "guest:abc";

        assertThrows(BusinessException.class, () -> menuService.saveMenu(null, requesterId));
        assertThrows(BusinessException.class, () -> menuService.saveMenu("   ", requesterId));
        assertThrows(BusinessException.class, () -> menuService.saveMenu("123456789012345678901", requesterId));
        assertThrows(BusinessException.class, () -> menuService.saveMenu("<script>alert(1)</script>", requesterId));
        assertThrows(BusinessException.class, () -> menuService.saveMenu("김밥<script>", requesterId));

        verify(redisTemplate, never()).opsForValue();
        verifyNoInteractions(menuRepository);
    }

    @Test
    @DisplayName("same user can vote same menu only once")
    void increaseVote_DuplicateSameMenu() {
        String voterId = "client:abc";
        String voterKey = RedisKey.LUNCH_VOTER.with(today() + ":1");

        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(setOperations.add(voterKey, voterId)).thenReturn(1L, 0L);

        menuService.increaseVote(1L, voterId);

        assertThrows(BusinessException.class, () -> menuService.increaseVote(1L, voterId));
        verify(zSetOperations).incrementScore(todayRankingKey(), "1", 1);
        verify(setOperations, times(2)).add(voterKey, voterId);
    }

    @Test
    @DisplayName("same user can vote different menus")
    void increaseVote_AllowsDifferentMenus() {
        String voterId = "client:abc";
        String voterKeyA = RedisKey.LUNCH_VOTER.with(today() + ":1");
        String voterKeyB = RedisKey.LUNCH_VOTER.with(today() + ":2");

        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(setOperations.add(voterKeyA, voterId)).thenReturn(1L);
        when(setOperations.add(voterKeyB, voterId)).thenReturn(1L);

        menuService.increaseVote(1L, voterId);
        menuService.increaseVote(2L, voterId);

        verify(zSetOperations).incrementScore(todayRankingKey(), "1", 1);
        verify(zSetOperations).incrementScore(todayRankingKey(), "2", 1);
        verify(setOperations).add(voterKeyA, voterId);
        verify(setOperations).add(voterKeyB, voterId);
    }

    @Test
    @DisplayName("same voter repeated requests increment score only once")
    void increaseVote_RepeatedSameVoterIncrementsOnlyOnce() {
        String voterId = "guest:abc";
        String voterKey = RedisKey.LUNCH_VOTER.with(today() + ":1");

        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(setOperations.add(voterKey, voterId)).thenReturn(1L, 0L, 0L);

        menuService.increaseVote(1L, voterId);
        assertThrows(BusinessException.class, () -> menuService.increaseVote(1L, voterId));
        assertThrows(BusinessException.class, () -> menuService.increaseVote(1L, voterId));

        verify(setOperations, times(3)).add(voterKey, voterId);
        verify(zSetOperations, times(1)).incrementScore(todayRankingKey(), "1", 1);
    }

    @Test
    @DisplayName("vote fails without increasing score when Redis SADD result is null")
    void increaseVote_NullAddResultDoesNotIncrementScore() {
        String voterId = "guest:abc";
        String voterKey = RedisKey.LUNCH_VOTER.with(today() + ":1");

        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.add(voterKey, voterId)).thenReturn(null);

        assertThrows(BusinessException.class, () -> menuService.increaseVote(1L, voterId));

        verify(redisTemplate, never()).opsForZSet();
    }

    @Test
    @DisplayName("today menu count uses full ranking ZSET cardinality")
    void getTodayMenuCount_UsesFullZSetCardinality() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.zCard(todayRankingKey())).thenReturn(14L);

        assertEquals(14L, menuService.getTodayMenuCount());
    }

    @Test
    @DisplayName("today vote count sums every ranking score")
    void getTodayVoteCount_SumsAllScores() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.rangeWithScores(todayRankingKey(), 0, -1)).thenReturn(Set.of(
                new DefaultTypedTuple<>("1", 3.0),
                new DefaultTypedTuple<>("2", 7.0),
                new DefaultTypedTuple<>("3", 2.0)
        ));

        assertEquals(12L, menuService.getTodayVoteCount());
    }

    private DailyMenu menu(Long id, String menuName) {
        DailyMenu menu = DailyMenu.builder()
                .menuName(menuName)
                .build();
        ReflectionTestUtils.setField(menu, "id", id);
        return menu;
    }

    private String todayRankingKey() {
        return RedisKey.LUNCH_RANKING.with(today());
    }

    private String today() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }
}
