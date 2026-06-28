package com.bubbletalk.global.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    public static final String DEFAULT_CODE = "4000";

    private final String code;

    public BusinessException(String message) {
        super(message);
        this.code = DEFAULT_CODE;
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }
}
