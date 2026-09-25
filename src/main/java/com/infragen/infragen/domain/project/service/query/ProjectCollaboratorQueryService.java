package com.infragen.infragen.domain.project.service.query;

import com.infragen.infragen.domain.project.converter.ProjectCollaboratorConverter;
import com.infragen.infragen.domain.project.dto.response.ProjectCollaboratorResDTO;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectCollaboratorQueryService {
    private final ProjectAccessService projectAccessService;
    private final ProjectCollaboratorRepository collaboratorRepository;

    /**
     * owner 또는 project 참여자가 collaborator 목록을 조회한다.
     */
    @Transactional(readOnly = true)
    public ProjectCollaboratorResDTO.ListResult getAll(Long projectId, Long memberId) {
        projectAccessService.requireReadAccess(projectId, memberId);
        return ProjectCollaboratorConverter.toListResult(
                collaboratorRepository.findAllByProjectId(projectId)
        );
    }
}
