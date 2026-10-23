package com.infragen.infragen.domain.generation.generator.compose;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import java.util.List;
import java.util.Optional;

/**
 * 앱과 의존 인프라의 안내 섹션을 모아 LOCAL_DEV 안내 문서 {@code LOCAL_DEV_GUIDE.md}를 만든다.
 *
 * <p>LOCAL_DEV는 의존 인프라만 컨테이너로 실행하고 앱은 호스트에서 실행하므로, CLOUD_DEPLOY 안내와 실행 방법이 달라 문서를 따로 만든다.
 */
public class LocalDevGuideRenderer {

    /**
     * 섹션이 하나라도 있으면 안내 문서를 만든다.
     *
     * @param sections {@code ##} 제목으로 시작하는 섹션. 전달한 순서대로 넣는다
     * @return {@code local/} 접두사가 없는 파일. 섹션이 없으면 빈 값
     */
    public Optional<IaCFileDTO.FileContentResDTO> render(List<String> sections) {
        // 안내할 노드가 없는 그래프에서 제목만 있는 문서를 만들지 않는다.
        if (sections.isEmpty()) {
            return Optional.empty();
        }

        StringBuilder content = new StringBuilder("""
            # LOCAL_DEV 실행 안내

            의존 인프라는 `local/docker-compose.yml`로 실행하고, 앱은 호스트에서 직접 실행합니다. 이 문서의 명령과 경로는 `local/` 폴더가 있는 디렉터리(앱 프로젝트 루트) 기준입니다. 의존 인프라가 있으면 아래 명령으로 실행해 주세요. Compose는 `local/.env`를 읽습니다.

            ```
            docker compose -f local/docker-compose.yml up -d
            ```

            - `local/.env`에는 그래프에 입력한 비밀번호가 들어 있으므로 버전 관리에 커밋하지 마세요.
            - 그래프에 앱이 여러 개면 모든 앱이 `local/.env` 하나를 함께 씁니다. `SPRING_DATASOURCE_URL`, `MYSQL_HOST` 같은 키가 겹치면 나중에 처리한 앱의 값만 남으므로, 앱마다 연결한 DB가 다르면 각 앱의 접속 설정을 직접 확인해 주세요.
            """);
        for (String section : sections) {
            content.append('\n').append(section);
        }

        return Optional.of(IaCFileDTO.FileContentResDTO.builder()
            .fileName("LOCAL_DEV_GUIDE.md")
            .content(content.toString())
            .build());
    }
}
