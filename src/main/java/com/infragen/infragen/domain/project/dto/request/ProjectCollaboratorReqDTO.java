package com.infragen.infragen.domain.project.dto.request;

import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;

import jakarta.validation.constraints.NotNull;

/**
 * 프로젝트 협업자 관련 요청을 위한 dto이다.
 * ProjectCollaboratorReqDTO
 */
public final class ProjectCollaboratorReqDTO {
    private ProjectCollaboratorReqDTO() {
    }

    public record Add(
            @NotNull Long memberId,
            @NotNull ProjectCollaboratorRole role
    ) {
    }

    public record ChangeRole(
            @NotNull ProjectCollaboratorRole role
    ) {
    }
}
