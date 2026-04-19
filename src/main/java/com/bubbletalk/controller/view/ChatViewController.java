package com.bubbletalk.controller.view;

import com.bubbletalk.entity.ChatMessage;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

@Controller
public class ChatViewController {

    @MessageMapping("/chat")
    @SendTo("/topic/messages")
    public ChatMessage broadcastMessage(ChatMessage message) {
        return ChatMessage.builder()
                .senderIp(message.getSenderIp())
                .content(message.getContent())
                .ttl(5)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
