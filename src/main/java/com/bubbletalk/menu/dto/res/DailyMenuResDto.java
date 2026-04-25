package com.bubbletalk.menu.dto.res;

import lombok.Builder;
import java.util.List;

/**
 * [오늘의 메뉴 응답 DTO]
 * Java Record를 사용하여 불변성을 보장하고 보일러플레이트 코드를 제거했습니다.
 */
@Builder
public record DailyMenuResDto(
    List<MenuListResDto> menuList
) {
}
