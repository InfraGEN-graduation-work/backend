package com.infragen.infragen.domain.generation.generator.compose;

import com.infragen.infragen.domain.parsing.dto.response.ApplicationComponent;
import com.infragen.infragen.global.enums.ComponentType;

/**
 * LOCAL_DEV 안내 문서 {@code LOCAL_DEV_GUIDE.md}에 넣을 앱 타입별 섹션을 만든다.
 *
 * <p>LOCAL_DEV는 앱을 컨테이너가 아니라 호스트에서 실행하므로 앱 타입별 Dockerfile renderer가 없다. {@code .env}를 앱에 넣는 방법과
 * 접속 변수가 앱 타입마다 달라 타입별 구현이 섹션 문구를 정한다.
 */
public interface LocalApplicationGuideRenderer {

    ComponentType getApplicationType();

    /**
     * 앱 노드 하나의 섹션을 만든다. LOCAL_DEV는 앱이 여러 개일 수 있어 노드마다 호출한다.
     *
     * @param application 이 renderer가 담당하는 타입의 앱 노드
     * @return {@code ##} 제목으로 시작하는 섹션
     */
    String guideSection(ApplicationComponent application);
}
