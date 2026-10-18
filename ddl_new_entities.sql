-- BubbleTalk 신규 엔티티 DDL
-- Target: MySQL 8.x / MariaDB
-- Created for manually creating tables used by recently added domain features.

CREATE TABLE IF NOT EXISTS chat_room (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_code VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    is_private BOOLEAN NOT NULL DEFAULT FALSE,
    max_participants INT NOT NULL DEFAULT 10,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_date DATETIME(6) NULL,
    modified_date DATETIME(6) NULL,
    closed_at DATETIME(6) NULL,

    CONSTRAINT uk_chat_room_room_code UNIQUE (room_code),
    CONSTRAINT chk_chat_room_max_participants CHECK (max_participants BETWEEN 2 AND 50),
    CONSTRAINT chk_chat_room_status CHECK (status IN ('OPEN', 'FULL', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_chat_room_public_list
    ON chat_room (is_private, status, created_date);

CREATE TABLE IF NOT EXISTS security_event_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    room_code VARCHAR(100) NULL,
    guest_id VARCHAR(100) NULL,
    ip_address VARCHAR(100) NULL,
    user_agent VARCHAR(500) NULL,
    request_uri VARCHAR(500) NULL,
    reason VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT chk_security_event_log_event_type CHECK (
        event_type IN (
            'ROOM_CREATED',
            'ROOM_ENTER',
            'ROOM_LEAVE',
            'MESSAGE_SEND',
            'SYSTEM_MESSAGE_SEND',
            'VOTE_CREATED',
            'VOTE_SUBMIT',
            'ROOM_CLOSED',
            'ADMIN_ROOM_CLOSED',
            'STALE_SESSION_CLEANUP',
            'SECURITY_LOG_VIEW'
        )
    ),
    CONSTRAINT chk_security_event_log_severity CHECK (
        severity IN ('INFO', 'WARN', 'DANGER', 'BLOCKED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_security_event_log_created_at
    ON security_event_log (created_at);

CREATE INDEX idx_security_event_log_event_type_created_at
    ON security_event_log (event_type, created_at);

CREATE INDEX idx_security_event_log_severity_created_at
    ON security_event_log (severity, created_at);

CREATE INDEX idx_security_event_log_room_code_created_at
    ON security_event_log (room_code, created_at);

CREATE INDEX idx_security_event_log_guest_id_created_at
    ON security_event_log (guest_id, created_at);
