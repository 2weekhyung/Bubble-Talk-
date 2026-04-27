package com.bubbletalk.menu.dto.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * [오늘의 메뉴 응답 DTO]
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyMenuResDto {
    private List<MenuListResDto> menuList;
}
