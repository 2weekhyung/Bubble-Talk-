package com.bubbletalk.global.exception;

import com.bubbletalk.base.dto.BaseResDto;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessExceptionReturnsBadRequest() {
        ResponseEntity<BaseResDto> response = handler.handleBusinessException(
                new BusinessException("ROOM_CREATE_RATE_LIMIT", "채팅방은 30초에 한 번만 만들 수 있습니다.")
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("ROOM_CREATE_RATE_LIMIT", response.getBody().getCode());
        assertEquals("채팅방은 30초에 한 번만 만들 수 있습니다.", response.getBody().getMessage());
    }

    @Test
    void apiExceptionReturnsJsonInternalServerError() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/rooms");

        Object response = handler.handleException(new IllegalStateException("boom"), request);

        ResponseEntity<?> entity = assertInstanceOf(ResponseEntity.class, response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, entity.getStatusCode());
        BaseResDto body = assertInstanceOf(BaseResDto.class, entity.getBody());
        assertEquals("5000", body.getCode());
        assertEquals("서버 내부 오류가 발생했습니다.", body.getMessage());
    }
}
