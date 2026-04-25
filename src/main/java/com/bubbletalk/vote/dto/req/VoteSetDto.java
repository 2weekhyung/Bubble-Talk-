package com.bubbletalk.vote.dto.req;

import com.bubbletalk.base.dto.BaseDto;
import lombok.Data;

@Data
public class VoteSetDto extends BaseDto {
    private Long menuId;
    private String ip;

}
