package com.bubbletalk.guest;

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

    public static final String COOKIE_NAME = "BT_GUEST_ID";
    private static final Duration COOKIE_MAX_AGE = Duration.ofDays(30);

    private final boolean secureCookie;

    public GuestIdSupport(@Value("${bubble-talk.guest.cookie-secure:false}") boolean secureCookie) {
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
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
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
