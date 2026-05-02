package com.bubbletalk.security.component;

import com.bubbletalk.security.service.ForbiddenWordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * [금칙어 초기화 컴포넌트]
 * 애플리케이션이 시작될 때 DB에 저장된 금칙어 목록을 Redis 캐시로 로드합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForbiddenWordInitializer implements CommandLineRunner {

    private final ForbiddenWordService forbiddenWordService;

    @Override
    public void run(String... args) {
        log.info("애플리케이션 시작: 금칙어 캐시 초기화를 시작합니다...");
        try {
            forbiddenWordService.refreshCache();
        } catch (Exception e) {
            log.error("금칙어 캐시 초기화 중 오류 발생", e);
        }
    }
}
