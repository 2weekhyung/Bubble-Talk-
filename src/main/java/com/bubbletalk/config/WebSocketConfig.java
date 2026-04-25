package com.bubbletalk.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * [웹소켓 설정 클래스]
 * 실시간 채팅과 실시간 투표 랭킹 전송을 위한 STOMP 웹소켓 설정을 담당합니다.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 클라이언트에서 메시지를 보낼 때 사용하는 주소 접두사 (예: /app/chat/send)
        config.setApplicationDestinationPrefixes("/app");
        
        // 서버에서 클라이언트에게 메시지를 쏠 때 사용하는 주제 접두사 (예: /topic/bubbles)
        config.enableSimpleBroker("/topic");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 웹소켓 연결 엔드포인트 설정 (SockJS 사용)
        registry.addEndpoint("/ws-bubble")
                .setAllowedOriginPatterns("*") // 모든 오리진 허용 (개발 편의성)
                .withSockJS();
    }
}
