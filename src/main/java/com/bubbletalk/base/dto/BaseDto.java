package com.bubbletalk.base.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BaseDto {
    private String code = "0000";
    private String message = "정상";
    private Object result;

    public BaseDto(String code, String message) {
        this.code = code;
        this.message = message;
    }
    
    public BaseDto(Object result) {
        this.result = result;
    }
}
