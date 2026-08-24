package com.infragen.infragen.domain.project.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProjectStatus {
    DRAFT("초안"),
    VALIDATING("검증 중"),
    COMPLETED("완료"),
    ERROR("에러 발생"),
    ;

    private final String description;
}
