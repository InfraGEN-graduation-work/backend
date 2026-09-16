package com.infragen.infragen.domain.project.repository.projection;

import java.time.LocalDateTime;

import com.infragen.infragen.domain.project.enums.ProjectStatus;

/** 홈 목록에 필요한 정보와 조회한 사용자의 접근 역할을 한 query에서 가져온다. */
public interface ProjectAccessPreview {
    Long getProjectId();
    String getTitle();
    String getDescription();
    ProjectStatus getStatus();
    LocalDateTime getCreatedAt();
    String getAccessRole();
}
