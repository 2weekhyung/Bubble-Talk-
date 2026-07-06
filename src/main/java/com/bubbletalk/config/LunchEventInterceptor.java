package com.bubbletalk.config;

import com.bubbletalk.base.dto.BaseResDto;
import com.bubbletalk.menu.service.MenuService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * [점심 타임어택 잠금 인터셉터]
 * 이벤트 시간(11:00 ~ 12:00) 외에는 투표 및 메뉴 추가 API 접근을 차단합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LunchEventInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;
    private final MenuService menuService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Redis에서 현재 이벤트 상태 조회
        String status = menuService.refreshEventStatusBySchedule();

        // 상태가 'OPEN'이 아니면 차단 (최초 실행 시 null일 수 있으므로 CLOSED와 동일하게 취급)
        if (!"OPEN".equals(status)) {
            log.warn("차단된 접근: 현재 점심 전쟁 시간이 아닙니다. (Status: {})", status);
            
            // Redis에서 현재 설정된 운영 시간 조회 (없으면 기본값)
            java.util.Map<String, String> eventTimes = menuService.getEventTimes();
            String startTime = eventTimes.get("startTime");
            String endTime = eventTimes.get("endTime");

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");

            BaseResDto errorResponse = new BaseResDto(
                    "4030",
                    String.format("지금은 휴전 중입니다. (전쟁 시간: %s ~ %s)", startTime, endTime)
            );

            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
            return false;
        }

        return true;
    }
}
