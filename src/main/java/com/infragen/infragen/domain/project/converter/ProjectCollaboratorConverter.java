package com.infragen.infragen.domain.project.converter;

import com.infragen.infragen.domain.project.dto.response.ProjectCollaboratorResDTO;
import com.infragen.infragen.domain.project.entity.ProjectCollaborator;

import java.util.List;

public final class ProjectCollaboratorConverter {
    private ProjectCollaboratorConverter() {
    }

    public static ProjectCollaboratorResDTO.Detail toDetail(ProjectCollaborator collaborator) {
        return ProjectCollaboratorResDTO.Detail.builder()
                .memberId(collaborator.getMember().getId())
                .nickname(collaborator.getMember().getNickname())
                .role(collaborator.getRole())
                .build();
    }

    public static ProjectCollaboratorResDTO.ListResult toListResult(
            List<ProjectCollaborator> collaborators
    ) {
        return ProjectCollaboratorResDTO.ListResult.builder()
                .collaborators(collaborators.stream()
                        .map(ProjectCollaboratorConverter::toDetail)
                        .toList())
                .build();
    }
}
