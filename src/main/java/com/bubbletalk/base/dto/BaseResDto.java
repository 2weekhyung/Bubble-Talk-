package com.bubbletalk.base.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BaseResDto {
    private String code = "0000";
    private String message = "정상";
    private Object result;

    public BaseResDto(String code, String message) {
        this.code = code;
        this.message = message;
    }
    
    public BaseResDto(Object result) {
        this.result = result;
    }

    public static BaseResDto ok() {
        return new BaseResDto();
    }

    public static BaseResDto ok(Object result) {
        return new BaseResDto(result);
    }

    public BaseResDto fail(Object result){
        this.code = "9999";
        this.message = "실패";
        this.result = result;
        return this;
    }
}
