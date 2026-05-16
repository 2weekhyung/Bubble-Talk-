package com.bubbletalk;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@EnableScheduling
@EnableJpaAuditing
@SpringBootApplication
public class BubbleTalkApplication {

    @PostConstruct
    public void started() {
        // 서버 시간대를 한국 시간(Asia/Seoul)으로 고정합니다.
        // Docker 컨테이너 등 UTC 기반 환경에서도 한국 시간에 맞춰 스케줄러가 작동하도록 보장합니다.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }

    public static void main(String[] args) {
        SpringApplication.run(BubbleTalkApplication.class, args);
    }

}
