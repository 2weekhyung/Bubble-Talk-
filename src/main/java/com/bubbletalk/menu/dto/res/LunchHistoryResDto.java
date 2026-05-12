package com.bubbletalk.menu.dto.res;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDate;

@Getter
@Builder
public class LunchHistoryResDto {
    private Long id;
    private LocalDate targetDate;
    private String menuName;
    private Long voteCount;
    private Integer ranking;
}
