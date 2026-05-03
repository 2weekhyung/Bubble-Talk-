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
     * 점심 메뉴 실시간 랭킹 키 (ZSet, Prefix)
     * 구조: lunch:ranking:{yyyyMMdd}
     */
    LUNCH_RANKING("lunch:ranking:"),

    /**
     * 투표자 중복 체크용 키 (Set, Prefix)
     * 구조: lunch:voters:{yyyyMMdd}:{menuId}
     */
    LUNCH_VOTER("lunch:voters:"),

    /**
     * 점심 메뉴 타임 어택 활성화 상태 키 (Value)
     * 구조: lunch:event:status
     */
    LUNCH_EVENT_STATUS("lunch:event:status");

    private final String prefix;

    /**
     * 기본 접두사 뒤에 특정 식별자를 붙여 전체 키를 생성합니다.
     */
    public String with(Object suffix) {
        return this.prefix + suffix.toString();
    }
}
