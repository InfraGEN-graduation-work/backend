package com.infragen.infragen.global.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {
    /** 만료 시각 비교처럼 시간에 의존하는 로직이 테스트에서 고정 Clock으로 바꿔 쓸 수 있게 시스템 시각을 주입한다. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
