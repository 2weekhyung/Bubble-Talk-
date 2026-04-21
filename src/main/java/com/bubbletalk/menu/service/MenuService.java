package com.bubbletalk.menu.service;

import com.bubbletalk.menu.entity.DailyMenu;
import com.bubbletalk.menu.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * [메뉴 서비스 계층]
 * 실제 DB와 대화하며 데이터를 가공하거나 저장합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuService {

    private final MenuRepository menuRepository;

    /**
     * 새로운 메뉴를 0점으로 DB에 저장합니다.
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
     * [중요] 특정 메뉴의 투표수를 1 올립니다.
     */
    @Transactional
    public void increaseVote(Long menuId) {
        DailyMenu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new IllegalArgumentException("해당 메뉴가 존재하지 않습니다. ID=" + menuId));
        
        // Dirty Checking(변경 감지) 기능을 통해 점수가 업데이트됩니다.
        // 현재는 DailyMenu 엔티티에 세터(Setter)가 없으므로 엔티티 내에 메서드를 만들거나
        // 간단히 reflection 등을 쓸 수 있지만, 여기서는 메서드를 추가하는 것이 정석입니다.
        menu.addScore(); 
    }

    /**
     * 상위 10개의 메뉴 순위를 가져옵니다. (QueryDSL 사용)
     */
    public List<DailyMenu> getTopRankings() {
        return menuRepository.getTopMenus(10);
    }
}
