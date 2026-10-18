package com.lunchpulse.security.dto.req;

import com.lunchpulse.base.dto.BaseDto;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ForbiddenWordAddReqDto extends BaseDto {
    private String word;
}
