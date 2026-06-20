package com.infragen.infragen.domain.parsing.enums;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ComponentType {
    MYSQL(ComponentCategory.DATABASE, 1),
    POSTGRESQL(ComponentCategory.DATABASE, 1),
    MONGODB(ComponentCategory.DATABASE, 1),

    REDIS(ComponentCategory.CACHE, 2),

    SPRING_BOOT(ComponentCategory.APPLICATION, 3),

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