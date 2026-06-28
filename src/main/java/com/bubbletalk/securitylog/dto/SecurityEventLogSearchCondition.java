package com.bubbletalk.securitylog.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class SecurityEventLogSearchCondition {
    private String eventType;
    private String severity;
    private String roomCode;
    private String guestId;
    private String ipAddress;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
