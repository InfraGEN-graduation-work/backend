package com.infragen.infragen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class InfragenApplication {

	public static void main(String[] args) {
		SpringApplication.run(InfragenApplication.class, args);
	}

}
