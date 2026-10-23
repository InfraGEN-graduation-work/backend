package com.infragen.infragen.domain.generation.generator.compose;

import com.infragen.infragen.domain.parsing.dto.response.ApplicationComponent;
import com.infragen.infragen.global.enums.ComponentType;
import org.springframework.stereotype.Component;

/** LOCAL_DEV에서 호스트로 실행하는 Node.js 앱의 {@code .env} 적용 방법과 DB 중립 변수를 안내한다. */
@Component
public class NodeJsLocalApplicationGuideRenderer implements LocalApplicationGuideRenderer {

    @Override
    public ComponentType getApplicationType() {
        return ComponentType.NODE_JS;
    }

    /**
     * @return {@code --env-file}로 {@code .env}를 읽는 방법, DB 중립 변수, 직접 맞출 listen 포트를 안내하는 섹션
     */
    @Override
    public String guideSection(ApplicationComponent application) {
        int port = application.getPort();
        // CLOUD와 달리 LOCAL 매퍼는 PORT를 .env에 넣지 않으므로 앱이 포트를 직접 맞춰야 한다고 안내한다.
        return """
            ## Node.js 앱 (포트 `%d`)

            LOCAL_DEV에서는 앱을 컨테이너가 아니라 호스트에서 직접 실행하고, 의존 인프라에는 `localhost`의 포트로 접속합니다.

            ### `local/.env` 값을 앱에 넣기

            Node.js는 `.env` 파일을 자동으로 읽지 않습니다. Node.js 20.6.0 이상이면 `--env-file`로 읽고, 그보다 낮으면 dotenv 같은 라이브러리로 읽어 주세요. 이미 설정된 환경변수가 파일 값보다 우선합니다.

            ```
            node --env-file=local/.env server.js
            ```

            ### 접속 설정

            - DB나 Redis를 연결하면 접속 정보가 `MYSQL_HOST`, `MYSQL_PORT`처럼 종류별 변수로 `local/.env`에 들어 있습니다. 앱 코드에서 이 변수로 연결 설정을 직접 만들어 주세요.
            - LOCAL_DEV는 `PORT`를 넣지 않습니다. 앱은 그래프에 입력한 포트(`%d`)로 직접 listen해 주세요. NGINX를 연결했다면 NGINX가 이 포트로 요청을 전달합니다.
            """.formatted(port, port);
    }
}
