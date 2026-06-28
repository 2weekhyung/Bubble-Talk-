package com.bubbletalk.securitylog.dto;

import com.bubbletalk.securitylog.entity.EventType;
import com.bubbletalk.securitylog.entity.SecurityEventLog;
import com.bubbletalk.securitylog.entity.Severity;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SecurityEventLogResponseDto {
    private Long id;
    private EventType eventType;
    private Severity severity;
    private String roomCode;
    private String guestId;
    private String ipAddress;
    private String reason;
    private LocalDateTime createdAt;

    public static SecurityEventLogResponseDto from(SecurityEventLog log) {
        return SecurityEventLogResponseDto.builder()
                .id(log.getId())
                .eventType(log.getEventType())
                .severity(log.getSeverity())
                .roomCode(log.getRoomCode())
                .guestId(log.getGuestId())
                .ipAddress(log.getIpAddress())
                .reason(log.getReason())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
