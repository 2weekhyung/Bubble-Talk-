package com.bubbletalk.global.exception;

import com.bubbletalk.base.dto.BaseResDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * [API 에러 처리] 비즈니스 로직 예외 발생 시 JSON 응답
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<BaseResDto> handleBusinessException(BusinessException e) {
        log.error("Business Exception: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new BaseResDto(e.getCode(), e.getMessage()));
    }

    /**
     * [일반 에러 처리] 그 외 모든 예외 발생 시 에러 페이지로 이동하거나 JSON 응답
     */
    @ExceptionHandler(Exception.class)
    public Object handleException(Exception e, HttpServletRequest request) {
        log.error("Unhandled Exception: ", e);

        // 요청이 JSON(API)인 경우 JSON 반환
        String header = request.getHeader("Accept");
        if (header != null && header.contains("application/json")) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResDto("5000", "서버 내부 오류가 발생했습니다."));
        }

        // 일반 페이지 요청인 경우 error.html 뷰 반환
        ModelAndView mav = new ModelAndView();
        mav.addObject("message", e.getMessage());
        mav.setViewName("error");
        return mav;
    }
}
