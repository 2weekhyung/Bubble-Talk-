package com.bubbletalk.securitylog.service;

import com.bubbletalk.global.exception.BusinessException;
import com.bubbletalk.securitylog.dto.SecurityEventLogSearchCondition;
import com.bubbletalk.securitylog.entity.EventType;
import com.bubbletalk.securitylog.entity.SecurityEventLog;
import com.bubbletalk.securitylog.entity.Severity;
import com.bubbletalk.securitylog.repository.SecurityEventLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityEventLogServiceTest {

    @Mock
    private SecurityEventLogRepository securityEventLogRepository;

    @Mock
    private HttpServletRequest request;

    @Test
    void logEventSavesSecurityEventLog() {
        SecurityEventLogService service = new SecurityEventLogService(securityEventLogRepository);

        service.logEvent(
                EventType.MESSAGE_SEND,
                Severity.INFO,
                "ROOM0001",
                "guest:guest-1",
                "10.0.0.1",
                "JUnit",
                "/ws",
                "채팅 메시지 전송"
        );

        ArgumentCaptor<SecurityEventLog> captor = ArgumentCaptor.forClass(SecurityEventLog.class);
        verify(securityEventLogRepository).save(captor.capture());
        SecurityEventLog saved = captor.getValue();
        assertEquals(EventType.MESSAGE_SEND, saved.getEventType());
        assertEquals(Severity.INFO, saved.getSeverity());
        assertEquals("ROOM0001", saved.getRoomCode());
        assertEquals("guest-1", saved.getGuestId());
        assertEquals("10.0.0.1", saved.getIpAddress());
    }

    @Test
    void logEventDoesNotPropagateRepositoryFailure() {
        SecurityEventLogService service = new SecurityEventLogService(securityEventLogRepository);
        when(securityEventLogRepository.save(org.mockito.ArgumentMatchers.any(SecurityEventLog.class)))
                .thenThrow(new IllegalStateException("db unavailable"));

        assertDoesNotThrow(() -> service.logEvent(
                EventType.VOTE_SUBMIT,
                Severity.INFO,
                null,
                "guest-1",
                "10.0.0.1",
                null,
                null,
                "사용자 투표 참여"
        ));
    }

    @Test
    void extractIpAddressPrefersForwardedFor() {
        SecurityEventLogService service = new SecurityEventLogService(securityEventLogRepository);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10, 10.0.0.2");

        assertEquals("203.0.113.10", service.extractIpAddress(request));
    }

    @Test
    void searchRejectsInvalidEventType() {
        SecurityEventLogService service = new SecurityEventLogService(securityEventLogRepository);
        SecurityEventLogSearchCondition condition = new SecurityEventLogSearchCondition();
        condition.setEventType("NOT_A_TYPE");

        assertThrows(BusinessException.class, () -> service.search(condition, PageRequest.of(0, 20)));
    }
}
