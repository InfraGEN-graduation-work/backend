package com.infragen.infragen.domain.generation.generator.cloud;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import java.util.List;
import java.util.Optional;

/**
 * 노드별 안내 섹션을 모아 CLOUD_DEPLOY 통합 안내 문서 {@code CLOUD_DEPLOY_GUIDE.md}를 만든다.
 *
 * <p>사용자는 생성된 파일만 받으므로 배포 준비 사항을 노드마다 다른 파일로 나누지 않고 한 문서에 모은다.
 * plan-only 경계는 안내 사이에 묻히지 않도록 {@code CLOUD_DEPLOY_WARNING.md}에 따로 두고, 이 문서는 그 파일을 가리키기만 한다.
 */
public class CloudDeployGuideRenderer {

    /**
     * 섹션이 하나라도 있으면 안내 문서를 만든다.
     *
     * @param sections {@code ##} 제목으로 시작하는 노드별 섹션. 전달한 순서대로 넣는다
     * @return 섹션이 없으면 빈 값
     */
    public Optional<IaCFileDTO.FileContentResDTO> render(List<String> sections) {
        // 안내할 노드가 없는 그래프에서 제목만 있는 문서를 만들지 않는다.
        if (sections.isEmpty()) {
            return Optional.empty();
        }

        StringBuilder content = new StringBuilder("""
            # CLOUD_DEPLOY 배포 안내

            그래프에 있는 노드별로 배포 전에 준비할 것과 실행 방법을 안내합니다. 이 산출물은 계획 전용이며 `terraform apply`는 이 프로젝트가 실행하지 않으므로 `CLOUD_DEPLOY_WARNING.md`의 안내를 먼저 확인해 주세요.
            """);
        for (String section : sections) {
            content.append('\n').append(section);
        }

        return Optional.of(IaCFileDTO.FileContentResDTO.builder()
            .fileName("CLOUD_DEPLOY_GUIDE.md")
            .content(content.toString())
            .build());
    }
}
