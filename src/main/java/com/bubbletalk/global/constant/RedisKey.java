package com.bubbletalk.global.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * [Redis 키 관리 Enum]
 * 프로젝트 전체에서 사용하는 Redis 키를 한곳에서 관리하여 오타를 방지하고 유지보수성을 높입니다.
 */
@Getter
@RequiredArgsConstructor
public enum RedisKey {

    /**
     * 채팅 도배 방지용 키 (Prefix)
     * 구조: chat:ratelimit:{ip}
     */
    CHAT_RATELIMIT("chat:ratelimit:"),

    /**
     * 금칙어 목록 저장용 키 (Set)
     * 구조: chat:forbidden
     */
    CHAT_FORBIDDEN("chat:forbidden"),

    /**
     * 채팅 메시지 버블 저장용 키 (Value, Prefix)
     * 구조: chat:bubble:{uuid}
     */
    CHAT_BUBBLE("chat:bubble:"),

    /**
     * 실시간 접속자 수 저장용 키 (Value)
     * 구조: chat:user:count
     */
    CHAT_USER_COUNT("chat:user:count"),

    CHAT_ACTIVE_SESSIONS("chat:active:sessions"),

    ROOM("room:"),

    ROOM_CREATE_RATELIMIT("room:create:ratelimit:"),

    ROOM_SESSION_ROOMS("room:session:rooms:"),

    /**
     * 점심 메뉴 실시간 랭킹 키 (ZSet, Prefix)
     * 구조: lunch:ranking:{yyyyMMdd}
     */
    LUNCH_RANKING("lunch:ranking:"),

    /**
     * 투표자 중복 체크용 키 (Set, Prefix)
     * 구조: lunch:voters:{yyyyMMdd}:{menuId}
     */
    LUNCH_VOTER("lunch:voters:"),

    MENU_ADD_RATELIMIT("menu:add:ratelimit:"),

    /**
     * 점심 메뉴 타임 어택 활성화 상태 키 (Value)
     * 구조: lunch:event:status
     */
    LUNCH_EVENT_STATUS("lunch:event:status"),

    /**
     * 점심 투표 운영 시작 시간 (Value)
     * 구조: lunch:event:start-time
     */
    LUNCH_START_TIME("lunch:event:start-time"),

    /**
     * 점심 투표 운영 종료 시간 (Value)
     * 구조: lunch:event:end-time
     */
    LUNCH_END_TIME("lunch:event:end-time");

    private final String prefix;

    /**
     * 기본 접두사 뒤에 특정 식별자를 붙여 전체 키를 생성합니다.
     */
    public String with(Object suffix) {
        return this.prefix + suffix.toString();
    }

    public static String roomSessions(String roomCode) {
        return ROOM.with(roomCode + ":sessions");
    }

    public static String roomGuests(String roomCode) {
        return ROOM.with(roomCode + ":guests");
    }

    public static String roomSessionActors(String roomCode) {
        return ROOM.with(roomCode + ":session-actors");
    }

    public static String sessionRooms(String sessionId) {
        return ROOM_SESSION_ROOMS.with(sessionId);
    }
}
