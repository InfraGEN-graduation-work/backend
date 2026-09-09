package com.infragen.infragen.domain.project.dto.response;

import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import lombok.Builder;

import java.util.List;

public final class ProjectCollaboratorResDTO {
    private ProjectCollaboratorResDTO() {
    }

    @Builder
    public record Detail(
            Long memberId,
            String nickname,
            ProjectCollaboratorRole role
    ) {
    }

    @Builder
    public record ListResult(
            List<Detail> collaborators
    ) {
    }
}
