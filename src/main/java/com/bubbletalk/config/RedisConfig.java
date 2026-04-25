package com.bubbletalk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * [Redis 설정 클래스]
 * 실시간 투표 데이터(ZSET)와 도배 방지용 데이터를 저장하기 위한 Redis 설정을 담당합니다.
 */
@Configuration
public class RedisConfig {

    /**
     * Redis 작업을 수행할 Template 빈을 설정합니다.
     * 데이터가 JSON 형태로 읽기 쉽게 저장되도록 직렬화 설정을 포함합니다.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 키(Key)는 일반 문자열로 저장
        template.setKeySerializer(new StringRedisSerializer());
        // 값(Value)은 JSON 형태로 직렬화하여 객체를 그대로 저장 가능하게 함
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());

        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        return template;
    }
}
