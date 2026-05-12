package com.bubbletalk.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * [Swagger/OpenAPI 설정 클래스]
 * API 문서화의 전역 설정을 담당합니다.
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Bubble Talk API 명세서")
                        .description("실시간 점심 메뉴 전쟁 및 익명 채팅 서비스의 API 문서입니다.")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Bubble Talk Team")
                                .email("admin@bubbletalk.com")));
    }
}
