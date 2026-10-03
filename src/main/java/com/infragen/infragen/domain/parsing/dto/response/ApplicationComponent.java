package com.infragen.infragen.domain.parsing.dto.response;

import com.infragen.infragen.global.enums.ComponentType;

/**
 * 애플리케이션 타입 컴포넌트가 공통으로 제공하는 실행 정보다.
 *
 * <p>CLOUD_DEPLOY renderer가 앱 타입을 구분하지 않고 쓰는 값만 둔다. 타입 전용 속성은 각 하위 DTO가 가진다.
 */
public abstract class ApplicationComponent extends BaseComponent {

    protected ApplicationComponent(String nodeId, float positionX, float positionY, ComponentType componentType) {
        super(nodeId, positionX, positionY, componentType);
    }

    /** @return 컨테이너가 노출할 애플리케이션 포트 */
    public abstract int getPort();
}
