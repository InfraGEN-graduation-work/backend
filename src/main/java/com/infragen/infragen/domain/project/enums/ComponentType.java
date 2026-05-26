package com.infragen.infragen.domain.project.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ComponentType {
    // 우선순위: 컨테이너 실행 순서를 의미함
    // DATABASE (우선순위 1)
    MYSQL(ComponentCategory.DATABASE, 1),
    POSTGRESQL(ComponentCategory.DATABASE, 1),
    MONGODB(ComponentCategory.DATABASE, 1),

    // CACHE (우선순위 2)
    REDIS(ComponentCategory.CACHE, 2),

    // APPLICATION (우선순위 3)
    SPRING_BOOT(ComponentCategory.APPLICATION, 3),

    // WEB_SERVER (우선순위 4)
    NGINX(ComponentCategory.WEB_SERVER, 4),
    APACHE(ComponentCategory.WEB_SERVER, 4),
    ;

    private final ComponentCategory category;
    private final int startupPriority;

    public enum ComponentCategory {
        DATABASE,
        CACHE,
        APPLICATION,
        WEB_SERVER
    }
}
