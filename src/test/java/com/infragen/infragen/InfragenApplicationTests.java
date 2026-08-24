package com.infragen.infragen;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("CI/CD 환경에서의 외부 의존성에 의한 빌드 실패 방지를 위한 테스트 비활성화")
@SpringBootTest
class InfragenApplicationTests {

	@Test
	void contextLoads() {
	}

}
