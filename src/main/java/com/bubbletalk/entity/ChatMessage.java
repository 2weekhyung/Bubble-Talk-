package com.bubbletalk.entity;

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

    private String senderIp;
    private String content;
    private int ttl;
    private LocalDateTime timestamp;

    public static ChatMessage create(String senderIp, String content) {
        return ChatMessage.builder()
                .senderIp(senderIp)
                .content(content)
                .ttl(5)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
