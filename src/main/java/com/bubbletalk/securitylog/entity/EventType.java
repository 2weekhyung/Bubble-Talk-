package com.bubbletalk.securitylog.entity;

public enum EventType {
    ROOM_CREATED,
    ROOM_ENTER,
    ROOM_LEAVE,
    MESSAGE_SEND,
    SYSTEM_MESSAGE_SEND,
    VOTE_CREATED,
    VOTE_SUBMIT,
    ROOM_CLOSED,
    ADMIN_ROOM_CLOSED,
    STALE_SESSION_CLEANUP,
    SECURITY_LOG_VIEW
}
