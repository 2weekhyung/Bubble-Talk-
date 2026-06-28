package com.bubbletalk.securitylog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "security_event_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecurityEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity;

    @Column(name = "room_code", length = 100)
    private String roomCode;

    @Column(name = "guest_id", length = 100)
    private String guestId;

    @Column(name = "ip_address", length = 100)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "request_uri", length = 500)
    private String requestUri;

    @Column(length = 1000)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public SecurityEventLog(EventType eventType,
                            Severity severity,
                            String roomCode,
                            String guestId,
                            String ipAddress,
                            String userAgent,
                            String requestUri,
                            String reason) {
        this.eventType = eventType;
        this.severity = severity;
        this.roomCode = roomCode;
        this.guestId = guestId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.requestUri = requestUri;
        this.reason = reason;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
