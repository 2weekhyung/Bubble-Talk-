package com.bubbletalk.menu.service;

import com.bubbletalk.menu.entity.DailyMenu;
import com.bubbletalk.menu.repository.MenuRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional // 테스트 완료 후 데이터를 자동으로 롤백하여 DB를 깨끗하게 유지합니다.
public class MenuServiceTest {

    @Autowired
    private MenuService menuService;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    @DisplayName("신규 메뉴 등록 시 즉시 1표가 반영되어야 한다")
    void saveAndVote_NewMenu() {
        // Given
        String menuName = "JUnit제육";
        String voterIp = "127.0.0.1";

        // When
        menuService.saveAndVote(menuName, voterIp);

        // Then
        Optional<DailyMenu> menu = menuRepository.findByMenuName(menuName);
        assertTrue(menu.isPresent());
        assertEquals(1L, menu.get().getFinalScore()); // 즉시 1표 반영 확인
    }

    @Test
    @DisplayName("이미 존재하는 메뉴를 입력하면 새로운 메뉴가 생성되지 않고 기존 메뉴에 투표가 되어야 한다")
    void saveAndVote_DuplicateMenu() {
        // Given
        String menuName = "중복치킨";
        menuService.saveAndVote(menuName, "1.1.1.1"); // 첫 번째 등록 및 투표

        // When
        menuService.saveAndVote(menuName, "2.2.2.2"); // 다른 IP로 동일 메뉴 입력

        // Then
        long count = menuRepository.findAll().stream()
                .filter(m -> m.getMenuName().equals(menuName))
                .count();
        
        assertEquals(1, count); // 메뉴는 여전히 1개여야 함
        
        DailyMenu menu = menuRepository.findByMenuName(menuName).orElseThrow();
        assertEquals(2L, menu.get().getFinalScore()); // 점수는 2점이 되어야 함
    }

    @Test
    @DisplayName("동일한 IP로 같은 메뉴에 두 번 투표하면 예외가 발생해야 한다")
    void increaseVote_DuplicateVoter() {
        // Given
        String menuName = "중복방지김밥";
        String voterIp = "192.168.0.1";
        menuService.saveAndVote(menuName, voterIp);

        // When & Then
        DailyMenu menu = menuRepository.findByMenuName(menuName).orElseThrow();
        
        // 동일 IP로 또 투표 시 IllegalStateException 발생 여부 검증
        assertThrows(IllegalStateException.class, () -> {
            menuService.increaseVote(menu.getId(), voterIp);
        });
    }
}
