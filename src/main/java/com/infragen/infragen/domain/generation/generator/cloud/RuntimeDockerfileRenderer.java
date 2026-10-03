package com.infragen.infragen.domain.generation.generator.cloud;

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
     * 컴파일이 끝난 산출물만 복사하는 runtime-only Dockerfile을 생성한다.
     *
     * @param context 선택된 애플리케이션 정보를 담은 CLOUD_DEPLOY 컨텍스트
     * @return {@code Dockerfile} 파일
     */
    IaCFileDTO.FileContentResDTO render(CloudDeployContext context);
}
