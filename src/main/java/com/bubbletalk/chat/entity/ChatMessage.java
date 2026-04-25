package com.bubbletalk.chat.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * [채팅 메시지 객체]
 * 사용자 간에 실시간으로 주고받는 메시지 데이터를 담는 클래스입니다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    /**
     * 보낸 사람의 IP 주소 (익명 구분용)
     */
    private String senderIp;

    /**
     * 메시지 내용
     */
    private String content;

    /**
     * 화면에 유지될 시간 (초 단위, 기본 5초)
     */
    private int ttl;

    /**
     * 메시지가 생성된 시간
     */
    private LocalDateTime timestamp;

    /**
     * [정적 팩토리 메서드] 
     * 기본 5초의 TTL을 가진 메시지 객체를 생성합니다.
     */
    public static ChatMessage create(String senderIp, String content) {
        return ChatMessage.builder()
                .senderIp(senderIp)
                .content(content)
                .ttl(5)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
