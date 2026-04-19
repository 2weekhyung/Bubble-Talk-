package org.example.bubbletalk.domain.chat.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    private String senderIp;   // 익명 식별자 (IP 등)
    private String content;
    private int ttl;           // 생명주기 (초)
    private LocalDateTime timestamp;

    public static ChatMessage create(String senderIp, String content) {
        return ChatMessage.builder()
                .senderIp(senderIp)
                .content(content)
                .ttl(5) // 기본 5초
                .timestamp(LocalDateTime.now())
                .build();
    }
}
