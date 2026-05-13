package com.infragen.infragen;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import jakarta.annotation.PostConstruct;

@EnableJpaAuditing
@SpringBootApplication
public class InfragenApplication {
    @PostConstruct
    public void started() {
        // 기본 시간대를 KST로 설정
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }
	public static void main(String[] args) {
		SpringApplication.run(InfragenApplication.class, args);
	}
}
