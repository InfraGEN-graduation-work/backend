package com.infragen.infragen.domain.generation.generator.cloud;

import java.util.List;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.global.enums.ComponentType;

/**
 * 애플리케이션 타입별로 CLOUD_DEPLOY runtime Dockerfile을 만든다.
 *
 * <p>runtime 이미지와 실행 방식은 앱 타입마다 다르므로 타입별 구현이 각자 Dockerfile 내용을 정한다.
 */
public interface RuntimeDockerfileRenderer {

    ComponentType getApplicationType();

    /**
     * 사용자가 빌드 컨텍스트에 직접 준비해야 하는 앱 파일을 경고 문서에 적을 문구로 반환한다.
     *
     * @return 마크다운 코드 표기를 포함한 파일 목록 문구. 예: {@code `app.jar`}
     */
    String requiredAppFiles();

    /**
     * 컴파일이 끝난 산출물만 복사하는 runtime-only Dockerfile을 생성한다.
     *
     * <p>{@code Dockerfile} 하나만 만든다. 생성 결과에 넣을 파일 목록은 {@link #renderFiles}로 만든다.
     * 이 메서드를 직접 호출하면 {@code .dockerignore}처럼 구현체가 함께 만드는 부속 파일이 빠진다.
     *
     * @param context 선택된 애플리케이션 정보를 담은 CLOUD_DEPLOY 컨텍스트
     * @return {@code Dockerfile} 파일
     */
    IaCFileDTO.FileContentResDTO render(CloudDeployContext context);

    /**
     * Dockerfile과 함께 빌드 컨텍스트에 두어야 할 파일 목록을 만든다. 기본값은 Dockerfile 하나다.
     *
     * @param context 선택된 애플리케이션 정보를 담은 CLOUD_DEPLOY 컨텍스트
     * @return 생성할 파일 목록. 순서대로 bundle에 담긴다.
     */
    default List<IaCFileDTO.FileContentResDTO> renderFiles(CloudDeployContext context) {
        return List.of(render(context));
    }

    /**
     * 통합 안내 문서 {@code CLOUD_DEPLOY_GUIDE.md}의 맨 앞에 넣을 앱 섹션을 만든다.
     *
     * <p>앱 파일을 어디에 두고 앱이 어떤 포트와 변수로 실행돼야 하는지를 안내한다. 무엇이 없으면 실행할 수 없는지는
     * 경고 문서가 {@link #requiredAppFiles}로 알리므로 여기서는 준비 방법을 쓴다. 섹션은 {@code ##} 제목으로 시작한다.
     *
     * <p>앱은 모든 CLOUD 산출물에 있어 빈 섹션이 맞는 경우가 없으므로 default를 두지 않는다. 새 앱 타입 renderer가 안내를
     * 빠뜨리면 컴파일되지 않는다.
     *
     * @param context 선택된 애플리케이션 정보를 담은 CLOUD_DEPLOY 컨텍스트
     * @return 앱 섹션
     */
    String guideSection(CloudDeployContext context);
}
