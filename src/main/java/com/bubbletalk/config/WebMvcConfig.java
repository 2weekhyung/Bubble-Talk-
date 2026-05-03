package com.bubbletalk.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * [Web MVC 설정]
 * 생성한 인터셉터를 등록하고 적용할 URL 패턴을 설정합니다.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final LunchEventInterceptor lunchEventInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(lunchEventInterceptor)
                .addPathPatterns("/api/menu/add", "/api/menu/vote"); // 투표 및 메뉴 추가 API만 차단
    }
}
