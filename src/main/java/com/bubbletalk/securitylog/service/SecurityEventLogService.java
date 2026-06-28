package com.bubbletalk.securitylog.service;

import com.bubbletalk.global.exception.BusinessException;
import com.bubbletalk.securitylog.dto.SecurityEventLogResponseDto;
import com.bubbletalk.securitylog.dto.SecurityEventLogSearchCondition;
import com.bubbletalk.securitylog.entity.EventType;
import com.bubbletalk.securitylog.entity.SecurityEventLog;
import com.bubbletalk.securitylog.entity.Severity;
import com.bubbletalk.securitylog.repository.SecurityEventLogRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityEventLogService {

    private static final int MAX_LENGTH_ROOM_CODE = 100;
    private static final int MAX_LENGTH_GUEST_ID = 100;
    private static final int MAX_LENGTH_IP_ADDRESS = 100;
    private static final int MAX_LENGTH_USER_AGENT = 500;
    private static final int MAX_LENGTH_REQUEST_URI = 500;
    private static final int MAX_LENGTH_REASON = 1000;

    private final SecurityEventLogRepository securityEventLogRepository;

    @Transactional(noRollbackFor = RuntimeException.class)
    public void logEvent(EventType eventType,
                         Severity severity,
                         String roomCode,
                         String guestId,
                         String ipAddress,
                         String userAgent,
                         String requestUri,
                         String reason) {
        try {
            securityEventLogRepository.save(SecurityEventLog.builder()
                    .eventType(eventType)
                    .severity(severity)
                    .roomCode(limit(roomCode, MAX_LENGTH_ROOM_CODE))
                    .guestId(limit(stripActorPrefix(guestId), MAX_LENGTH_GUEST_ID))
                    .ipAddress(limit(ipAddress, MAX_LENGTH_IP_ADDRESS))
                    .userAgent(limit(userAgent, MAX_LENGTH_USER_AGENT))
                    .requestUri(limit(requestUri, MAX_LENGTH_REQUEST_URI))
                    .reason(limit(reason, MAX_LENGTH_REASON))
                    .build());
        } catch (RuntimeException e) {
            log.warn("security event log save failed: eventType={}, roomCode={}, guestId={}",
                    eventType, roomCode, guestId, e);
        }
    }

    @Transactional(noRollbackFor = RuntimeException.class)
    public void logEvent(EventType eventType,
                         Severity severity,
                         String roomCode,
                         String guestId,
                         HttpServletRequest request,
                         String reason) {
        logEvent(
                eventType,
                severity,
                roomCode,
                guestId,
                extractIpAddress(request),
                extractUserAgent(request),
                extractRequestUri(request),
                reason
        );
    }

    @Transactional(readOnly = true)
    public Page<SecurityEventLogResponseDto> search(SecurityEventLogSearchCondition condition, Pageable pageable) {
        EventType eventType = parseEventType(condition != null ? condition.getEventType() : null);
        Severity severity = parseSeverity(condition != null ? condition.getSeverity() : null);

        return securityEventLogRepository.findAll(toSpecification(condition, eventType, severity), pageable)
                .map(SecurityEventLogResponseDto::from);
    }

    public String extractIpAddress(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public String extractUserAgent(HttpServletRequest request) {
        return request != null ? request.getHeader("User-Agent") : null;
    }

    public String extractRequestUri(HttpServletRequest request) {
        return request != null ? request.getRequestURI() : null;
    }

    private Specification<SecurityEventLog> toSpecification(SecurityEventLogSearchCondition condition,
                                                            EventType eventType,
                                                            Severity severity) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (eventType != null) {
                predicates.add(cb.equal(root.get("eventType"), eventType));
            }
            if (severity != null) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (condition != null) {
                if (hasText(condition.getRoomCode())) {
                    predicates.add(cb.like(root.get("roomCode"), "%" + condition.getRoomCode().trim() + "%"));
                }
                if (hasText(condition.getGuestId())) {
                    predicates.add(cb.like(root.get("guestId"), "%" + stripActorPrefix(condition.getGuestId().trim()) + "%"));
                }
                if (hasText(condition.getIpAddress())) {
                    predicates.add(cb.like(root.get("ipAddress"), "%" + condition.getIpAddress().trim() + "%"));
                }
                if (condition.getStartDate() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), condition.getStartDate()));
                }
                if (condition.getEndDate() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), condition.getEndDate()));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private EventType parseEventType(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return EventType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("올바르지 않은 eventType입니다.");
        }
    }

    private Severity parseSeverity(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return Severity.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("올바르지 않은 severity입니다.");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String stripActorPrefix(String value) {
        if (!hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("guest:")) {
            return trimmed.substring("guest:".length());
        }
        return trimmed;
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
