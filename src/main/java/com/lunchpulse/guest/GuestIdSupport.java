package com.lunchpulse.guest;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Component
public class GuestIdSupport {

    public static final String COOKIE_NAME = "LP_GUEST_ID";
    private static final String LEGACY_COOKIE_NAME = "BT_GUEST_ID";
    private static final Duration COOKIE_MAX_AGE = Duration.ofDays(30);

    private final boolean secureCookie;

    public GuestIdSupport(@Value("${lunch-pulse.guest.cookie-secure:false}") boolean secureCookie) {
        this.secureCookie = secureCookie;
    }

    public String resolveOrCreate(HttpServletRequest request, HttpServletResponse response) {
        return resolve(request).orElseGet(() -> {
            String guestId = UUID.randomUUID().toString();
            addCookie(response, guestId);
            return guestId;
        });
    }

    public Optional<String> resolve(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }

        return Arrays.stream(request.getCookies())
                // 리브랜딩 전 쿠키도 읽어 기존 익명 사용자의 제한/투표 이력을 갑자기 초기화하지 않습니다.
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()) || LEGACY_COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private void addCookie(HttpServletResponse response, String guestId) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, guestId)
                .path("/")
                .maxAge(COOKIE_MAX_AGE)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
