package com.infragen.infragen.domain.generation.generator.compose;

import com.infragen.infragen.domain.parsing.dto.response.ApplicationComponent;
import com.infragen.infragen.global.enums.ComponentType;
import org.springframework.stereotype.Component;

/** LOCAL_DEV에서 호스트로 실행하는 Spring Boot 앱의 {@code .env} 적용 방법과 DB 접속 변수를 안내한다. */
@Component
public class SpringBootLocalApplicationGuideRenderer implements LocalApplicationGuideRenderer {

    @Override
    public ComponentType getApplicationType() {
        return ComponentType.SPRING_BOOT;
    }

    /**
     * @return 앱 포트, {@code .env}를 환경변수로 넣는 방법, 프레임워크 변수로 전달되는 DB 접속 정보를 안내하는 섹션
     */
    @Override
    public String guideSection(ApplicationComponent application) {
        int port = application.getPort();
        // .env 값은 Compose와 Node.js 기준으로만 직렬화된다(ComposeYamlSupport). 셸로 읽으면 ;, &, 백틱이 실행되므로 셸 방법은 안내하지 않는다.
        return """
            ## Spring Boot 앱 (포트 `%d`)

            LOCAL_DEV에서는 앱을 컨테이너가 아니라 호스트에서 직접 실행하고, 의존 인프라에는 `localhost`의 포트로 접속합니다.

            ### `local/.env` 값을 앱에 넣기

            Spring Boot는 `local/.env` 파일을 자동으로 읽지 않습니다. IDE 실행 구성의 환경변수에 `local/.env`의 값을 넣어 주세요. `local/.env`는 셸 문법에 맞춰 이스케이프하지 않으므로 셸의 `.`이나 `source`로 읽지 마세요. 비밀번호의 특수문자가 명령으로 실행될 수 있습니다.

            ### 접속 설정

            - 관계형 DB(MySQL, PostgreSQL)가 하나만 연결되면 `local/.env`에 `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`가 들어 있어 접속 설정 코드를 바꿀 필요가 없습니다.
            - 관계형 DB가 둘 이상이면 `SPRING_DATASOURCE_*`를 만들지 않습니다. `MYSQL_HOST`, `POSTGRES_HOST` 같은 DB별 변수로 DataSource를 직접 설정해 주세요.
            - MongoDB가 연결되면 `SPRING_MONGODB_*` 변수가 들어 있습니다.
            - 앱은 그래프에 입력한 포트(`%d`)로 listen해 주세요. NGINX를 연결했다면 NGINX가 이 포트로 요청을 전달합니다. Spring Boot의 기본 포트는 `8080`이므로, 다르면 `server.port`를 같은 값으로 설정해 주세요.
            """.formatted(port, port);
    }
}
