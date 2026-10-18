package com.lunchpulse.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {
    public static final String DEFAULT_CODE = "4000";

    private final String code;
    private final HttpStatus status;

    public BusinessException(String message) {
        super(message);
        this.code = DEFAULT_CODE;
        this.status = HttpStatus.BAD_REQUEST;
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
        this.status = HttpStatus.BAD_REQUEST;
    }

    public BusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
