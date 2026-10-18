package com.lunchpulse.menu.dto.req;

import com.lunchpulse.base.dto.BaseDto;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MenuAddReqDto extends BaseDto {
    private String menuName;
}
