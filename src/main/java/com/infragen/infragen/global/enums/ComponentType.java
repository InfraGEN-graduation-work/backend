package com.infragen.infragen.global.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ComponentType {
    // 데이터베이스 서버
    MYSQL(ComponentCategory.DATABASE, 1),
    POSTGRESQL(ComponentCategory.DATABASE, 1),
    MONGODB(ComponentCategory.DATABASE, 1),

    // 캐시 서버
    REDIS(ComponentCategory.CACHE, 2),

    // 애플리케이션
    SPRING_BOOT(ComponentCategory.APPLICATION, 3),
    
    // 웹 서버
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
